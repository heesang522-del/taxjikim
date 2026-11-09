import { client } from './client';

const CHATBOT_SUBSCRIBE_URL = '/api/v1/chatbot/subscribe';
const CHATBOT_WAIT_TIMEOUT_MS = 180000;

async function requestChatbot(config) {
  try {
    const response = await client.request(config);
    const result = response.data;

    if (!result?.success) {
      const error = new Error(result?.error?.message ?? '챗봇 요청을 처리하지 못했습니다.');

      error.code = result?.error?.code;
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

    error.status = cause.response?.status;
    error.code = detail?.code ?? cause.code;
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
    let cancelling = false;

    function cleanup() {
      clearTimeout(timeoutId);
      eventSource?.close();

      signal?.removeEventListener('abort', handleAbort);
    }

    function finishSuccess(data) {
      if (settled) return;

      settled = true;
      cleanup();

      resolve({
        success: true,
        data,
        meta: null,
        error: null,
      });
    }

    function finishFailure(error) {
      if (settled) return;

      settled = true;
      cleanup();

      reject(error);
    }

    async function cancelOnServer(reason) {
      if (settled || cancelling) return;

      // 취소 확인 중에는 늦게 도착한 SSE 이벤트를 반영하지 않는다.
      cancelling = true;

      clearTimeout(timeoutId);
      eventSource?.close();

      if (!submitted) {
        finishFailure(new Error(reason));
        return;
      }

      try {
        // 원래 질문의 signal을 전달하면 취소 요청도 중단되므로 제외한다.
        const result = await requestChatbot({
          url: `/chatbot/conversations/${conversationId}/messages/cancel`,
          method: 'POST',
          data: {
            requestId,
          },
          timeout: 10000,
        });

        if (result.data?.cancelled === true) {
          finishFailure(
            new Error(
              `${reason} 서버 취소가 확인되어 예약한 질문 횟수가 해제되었습니다. ` +
                '대화 기록과 사용량을 다시 조회해주세요.'
            )
          );

          return;
        }

        if (typeof result.data?.reply === 'string') {
          // 최종 저장이 먼저 끝났다면 서버가 반환한 성공 답변을 표시한다.
          finishSuccess({
            reply: result.data.reply,
            remaining: result.data.remaining,
          });

          return;
        }

        finishFailure(
          new Error(
            `${reason} 서버의 취소 상태를 확인하지 못했습니다. ` +
              '횟수가 차감될 수 있으니 대화 기록과 사용량을 다시 조회해주세요.'
          )
        );
      } catch {
        finishFailure(
          new Error(
            `${reason} 서버 취소를 확인하지 못했습니다. ` +
              '횟수가 차감될 수 있으니 대화 기록과 사용량을 다시 조회해주세요.'
          )
        );
      }
    }

    function handleAbort() {
      void cancelOnServer('답변 수신을 종료했습니다.');
    }

    function readEvent(event) {
      try {
        return JSON.parse(event.data);
      } catch {
        void cancelOnServer('응답 형식을 확인하지 못했습니다.');
        return null;
      }
    }

    function isCurrentRequest(data) {
      return data?.requestId === requestId && data.conversationId === conversationId;
    }

    if (signal?.aborted) {
      handleAbort();
      return;
    }

    signal?.addEventListener('abort', handleAbort, {
      once: true,
    });

    timeoutId = setTimeout(() => {
      void cancelOnServer('답변 대기 시간이 초과되었습니다.');
    }, CHATBOT_WAIT_TIMEOUT_MS);

    try {
      eventSource = new EventSource(CHATBOT_SUBSCRIBE_URL, {
        withCredentials: true,
      });

      eventSource.addEventListener('chatbot-ready', () => {
        if (settled || submitted || cancelling) return;

        // 연결이 준비되면 질문을 한 번만 보낸다.
        submitted = true;

        requestChatbot({
          url: `/chatbot/conversations/${conversationId}/messages`,
          method: 'POST',
          data: {
            requestId,
            message,
          },
        }).catch((error) => {
          if (settled || cancelling) return;

          // 접수 거절이 확인되면 서버 오류와 입력란 오류 정보를 그대로 전달한다.
          if ([400, 401, 403, 404, 409].includes(error.status) || error.code === 'CHATBOT_BUSY') {
            finishFailure(error);
            return;
          }

          // 접수 여부가 불확실하면 서버에서 같은 요청을 확인하고 취소한다.
          void cancelOnServer('질문 접수 응답을 확인하지 못했습니다.');
        });
      });

      eventSource.addEventListener('chatbot-delta', (event) => {
        if (settled || cancelling) return;

        const data = readEvent(event);

        if (!isCurrentRequest(data)) return;

        if (typeof data.delta !== 'string') {
          void cancelOnServer('중간 답변 형식이 올바르지 않습니다.');
          return;
        }

        try {
          onDelta?.(data.delta);
        } catch {
          void cancelOnServer('중간 답변 표시를 중단했습니다.');
        }
      });

      eventSource.addEventListener('chatbot-done', (event) => {
        if (settled || cancelling) return;

        const data = readEvent(event);

        if (!isCurrentRequest(data)) return;

        if (typeof data.reply !== 'string') {
          void cancelOnServer('최종 답변 형식이 올바르지 않습니다.');
          return;
        }

        finishSuccess({
          reply: data.reply,
          remaining: data.remaining,
        });
      });

      eventSource.addEventListener('chatbot-failed', (event) => {
        if (settled || cancelling) return;

        const data = readEvent(event);

        if (!isCurrentRequest(data)) return;

        const error = new Error(data.message ?? '답변 처리가 중단되었습니다. 대화 기록과 사용량을 다시 조회해주세요.');

        error.code = data.code;

        finishFailure(error);
      });

      eventSource.onerror = () => {
        void cancelOnServer('답변 연결이 끊겼습니다.');
      };
    } catch {
      void cancelOnServer('답변 연결을 열지 못했습니다.');
    }
  });
}
