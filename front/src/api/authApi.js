// 인증 요청은 Vite의 /api 프록시를 통해 백엔드에 전달한다.
async function requestAuth(path, options) {
  const response = await fetch(`/api/v1/auth${path}`, {
    credentials: 'same-origin',
    ...options,
  });
  let result;
  try {
    result = await response.json();
  } catch {
    throw new Error('서버 응답을 받지 못했습니다. 잠시 후 다시 시도해주세요.');
  }
  if (!response.ok || !result.success) {
    const error = new Error(result.error?.message ?? '요청을 처리하지 못했습니다.');
    error.code = result.error?.code;
    error.fields = result.error?.fields;
    throw error;
  }
  return result;
}

export function loginSession(loginId, password) {
  return requestAuth('/sessions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ loginId, password, rememberMe: false }),
  });
}

export function logoutSession(accessToken) {
  return requestAuth('/sessions/current', {
    method: 'DELETE',
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });
}
