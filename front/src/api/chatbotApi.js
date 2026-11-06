import { client } from './client';

const CHATBOT_SUBSCRIBE_URL = '/api/v1/chatbot/subscribe';
const CHATBOT_WAIT_TIMEOUT_MS = 180000;

async function requestChatbot(config) {
  try {
    const response = await client.request(config);
    const result = response.data;

    if (!result?.success) {
      const error = new Error(result?.error?.message ?? '챗봇 요청을 처리하지 못했습니다.');

      error.fields = result?.error?.fields;
      throw error;
    }

    return result;
  } catch (cause) {
    const detail = cause.response?.data?.error;

    const error = new Error(
      detail?.message ??
        (cause.isAxiosError ? '서버 응답을 확인하지 못했습니다. 대화 기록을 확인해주세요.' : cause.message)
    );

    error.fields = detail?.fields ?? cause.fields;
    throw error;
  }
}

export function getConversations() {
  return requestChatbot({
    url: '/chatbot/conversations',
  });
}

export function createConversation() {
  return requestChatbot({
    url: '/chatbot/conversations',
    method: 'POST',
  });
}

export function getMessages(conversationId) {
  return requestChatbot({
    url: `/chatbot/conversations/${conversationId}/messages`,
  });
}

export function deleteConversation(conversationId) {
  return requestChatbot({
    url: `/chatbot/conversations/${conversationId}`,
    method: 'DELETE',
  });
}

export function getChatbotUsage() {
  return requestChatbot({
    url: '/chatbot/usage',
  });
}

export function sendMessage(conversationId, message, { onDelta, signal } = {}) {
  return new Promise((resolve, reject) => {
    const requestId = crypto.randomUUID();

    let eventSource;
    let timeoutId;
    let settled = false;
    let submitted = false;

    // 현재 요청의 연결과 이벤트만 정리한다.
    function cleanup() {
      clearTimeout(timeoutId);
      eventSource?.close();
      signal?.removeEventListener('abort', handleAbort);
    }

    function finishSuccess(result) {
      if (settled) return;

      settled = true;
      cleanup();
      resolve(result);
    }

    function finishFailure(error) {
      if (settled) return;

      settled = true;
      cleanup();
      reject(error);
    }

    function handleAbort() {
      finishFailure(new Error('답변 수신을 종료했습니다. 서버 처리는 계속될 수 있습니다.'));
    }

    function readEvent(event) {
      try {
        return JSON.parse(event.data);
      } catch {
        finishFailure(new Error('챗봇 응답 형식을 확인하지 못했습니다.'));

        return null;
      }
    }

    // 동일 사용자에게 전달되는 다른 질문의 이벤트를 제외한다.
    function isCurrentRequest(data) {
      return data?.requestId === requestId && data.conversationId === conversationId;
    }

    if (signal?.aborted) {
      handleAbort();
      return;
    }

    signal?.addEventListener('abort', handleAbort, { once: true });

    timeoutId = setTimeout(() => {
      finishFailure(new Error('답변 대기 시간이 초과됐습니다. 대화 기록을 확인해주세요.'));
    }, CHATBOT_WAIT_TIMEOUT_MS);

    try {
      eventSource = new EventSource(CHATBOT_SUBSCRIBE_URL, { withCredentials: true });

      eventSource.addEventListener('chatbot-ready', () => {
        if (settled || submitted) return;

        // 연결 준비 완료 후 질문을 한 번만 전송한다.
        submitted = true;

        requestChatbot({
          url: `/chatbot/conversations/${conversationId}/messages/async`,
          method: 'POST',
          signal,
          data: {
            requestId,
            message,
          },
        }).catch(finishFailure);
      });

      eventSource.addEventListener('chatbot-delta', (event) => {
        if (settled) return;

        const data = readEvent(event);

        if (!data || !isCurrentRequest(data)) return;

        if (typeof data.delta !== 'string') {
          finishFailure(new Error('중간 답변 형식이 올바르지 않습니다.'));
          return;
        }

        try {
          // 최종 완료를 기다리지 않고 중간 내용을 화면에 전달한다.
          onDelta?.(data.delta);
        } catch {
          finishFailure(new Error('중간 답변을 표시하지 못했습니다.'));
        }
      });

      eventSource.addEventListener('chatbot-done', (event) => {
        if (settled) return;

        const data = readEvent(event);

        if (!data || !isCurrentRequest(data)) return;

        finishSuccess({
          success: true,
          data: {
            reply: data.reply,
            remaining: data.remaining,
          },
          meta: null,
          error: null,
        });
      });

      eventSource.addEventListener('chatbot-failed', (event) => {
        if (settled) return;

        const data = readEvent(event);

        if (!data || !isCurrentRequest(data)) return;

        const error = new Error(data.message ?? '답변 처리에 실패했습니다.');

        error.code = data.code;
        finishFailure(error);
      });

      eventSource.onerror = () => {
        // 자동 재전송으로 질문과 사용량이 중복되지 않게 종료한다.
        finishFailure(
          new Error(submitted ? '답변 연결이 끊겼습니다. 대화 기록을 확인해주세요.' : '챗봇 연결을 열지 못했습니다.')
        );
      };
    } catch (cause) {
      finishFailure(cause);
    }
  });
}
