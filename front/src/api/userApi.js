import { client } from './client.js';

async function requestUser(config) {
  try {
    const response = await client.request(config);
    if (!response.data?.success) {
      const error = new Error(response.data?.error?.message ?? '요청을 처리하지 못했습니다.');
      error.code = response.data?.error?.code;
      error.fields = response.data?.error?.fields;
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
    error.fields = detail?.fields ?? cause.fields;
    throw error;
  }
}

export function checkLoginId(loginId) {
  return requestUser({ url: '/users/check-id', params: { loginId } });
}

export function checkNickname(nickname) {
  return requestUser({ url: '/users/check-nickname', params: { nickname } });
}

export function checkEmail(email) {
  return requestUser({ url: '/users/check-email', params: { email } });
}

export function sendEmailVerification(email) {
  return requestUser({ url: '/email-verifications', method: 'POST', data: { email } });
}

export function verifyEmail(email, code) {
  return requestUser({ url: '/email-verifications', method: 'PATCH', data: { email, code } });
}

export function createUser(user, profile) {
  // 프로필을 선택했을 때만 multipart로 보내며, 경계값은 브라우저가 설정한다.
  if (profile) {
    const form = new FormData();
    Object.entries(user).forEach(([key, value]) => {
      if (value != null) form.append(key, value);
    });
    form.append('profile', profile);
    return requestUser({ url: '/users', method: 'POST', data: form });
  }
  return requestUser({ url: '/users', method: 'POST', data: user });
}

export function sendAccountLookupCode(name, email) {
  return requestUser({ url: '/auth/account-lookups/email-verifications', method: 'POST', data: { name, email } });
}

export function sendPasswordResetCode(loginId, email) {
  return requestUser({ url: '/auth/password-resets/email-verifications', method: 'POST', data: { loginId, email } });
}

export function findLoginId(name, email, code) {
  return requestUser({ url: '/auth/account-lookups', method: 'POST', data: { name, email, code } });
}

export function resetPassword(loginId, email, newPassword) {
  return requestUser({ url: '/auth/password-resets', method: 'POST', data: { loginId, email, newPassword } });
}

function authorization(accessToken) {
  return { Authorization: `Bearer ${accessToken}` };
}

export function getMyProfile(accessToken) {
  return requestUser({ url: '/users/me', headers: authorization(accessToken) });
}

export function updateMyNickname(accessToken, nickname) {
  return requestUser({
    url: '/users/me/nickname',
    method: 'PATCH',
    headers: authorization(accessToken),
    data: { nickname },
  });
}

export function updateMyEmail(accessToken, email) {
  return requestUser({ url: '/users/me/email', method: 'PATCH', headers: authorization(accessToken), data: { email } });
}

export function updateMyPassword(accessToken, currentPassword, newPassword) {
  return requestUser({
    url: '/users/me/password',
    method: 'PUT',
    headers: authorization(accessToken),
    data: { currentPassword, newPassword },
  });
}

export function updateMyProfileImage(accessToken, profile, deleteProfile = false) {
  const form = new FormData();
  form.append('deleteProfile', String(deleteProfile));
  if (profile) form.append('profile', profile);
  return requestUser({
    url: '/users/me/profile-image',
    method: 'PUT',
    headers: authorization(accessToken),
    data: form,
  });
}

export function deleteMyAccount(accessToken, currentPassword) {
  return requestUser({
    url: '/users/me',
    method: 'DELETE',
    headers: authorization(accessToken),
    data: { currentPassword },
  });
}
