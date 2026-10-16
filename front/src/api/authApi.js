import { client } from './client.js';

// 실제 인증 요청은 기존 공통 client와 Vite /api 프록시를 거친다.
async function requestAuth(config) {
  try {
    const response = await client.request(config);
    if (!response.data?.success) {
      const error = new Error(response.data?.error?.message ?? '요청을 처리하지 못했습니다.');
      error.code = response.data?.error?.code;
      throw error;
    }
    return response.data;
  } catch (cause) {
    const detail = cause.response?.data?.error;
    const error = new Error(
      detail?.message ??
        (cause.response
          ? '서버 오류가 발생했습니다.'
          : cause.isAxiosError
            ? '서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.'
            : cause.message)
    );
    error.code = detail?.code ?? cause.code;
    error.details = detail?.details ?? cause.details;
    throw error;
  }
}

export function loginSession(loginId, password, rememberMe = false) {
  // 화면의 선택값을 서버에 전달해 기존 쿠키·리프레시 토큰 발급 정책을 적용한다.
  return requestAuth({
    url: '/auth/sessions',
    method: 'POST',
    data: { loginId, password, rememberMe },
  });
}

export function logoutSession() {
  return requestAuth({
    url: '/auth/sessions/current',
    method: 'DELETE',
  });
}
