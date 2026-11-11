const LOGIN_ID_RE = /^[a-zA-Z0-9_]{4,20}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const HANGUL_RE = /\p{Script=Hangul}/u;

export function getLoginIdError(value) {
  const loginId = value.trim();
  if (!loginId) return '필수 입력 항목입니다.';
  return LOGIN_ID_RE.test(loginId) ? '' : '아이디는 영문·숫자·_ 4~20자로 입력하세요.';
}

export function getEmailError(value) {
  const email = value.trim();
  if (!email) return '필수 입력 항목입니다.';
  if ([...email].length > 255) return '255자 이하로 입력해주세요.';
  if (HANGUL_RE.test(email)) return '이메일에는 한글을 사용할 수 없습니다.';
  return EMAIL_RE.test(email) ? '' : '이메일 형식을 확인하세요.';
}
