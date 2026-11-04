import { client } from './client';

// 공통 응답 형식을 확인하고 서버 오류 메시지를 전달한다.
async function requestChatbot(config) {
  try {
    const response = await client.request(config);
    const result = response.data;

    if (!result?.success) {
      const error = new Error(
          result?.error?.message ?? '챗봇 요청을 처리하지 못했습니다.'
      );

      error.fields = result?.error?.fields;
      throw error;
    }

    return result;
  } catch (cause) {
    const detail = cause.response?.data?.error;

    const error = new Error(
        detail?.message ??
        (cause.isAxiosError
            ? '서버 응답을 확인하지 못했습니다. 대화 기록을 확인해주세요.'
            : cause.message)
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

export function sendMessage(conversationId, message) {
  return requestChatbot({
    url: `/chatbot/conversations/${conversationId}/messages`,
    method: 'POST',
    data: { message },
    // AI 응답을 기다릴 수 있도록 요청 제한 시간을 늘린다.
    timeout: 60000,
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
