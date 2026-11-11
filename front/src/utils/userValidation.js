// 입력칸의 길이 제한과 검증 함수가 같은 기준을 참조한다.
export const USER_INPUT_LIMITS = {
  loginId: 20,
  name: 50,
  email: 255,
  nickname: 50,
  verificationCode: 6,
};
export const LOGIN_ID_MIN_LENGTH = 4;

// 새 비밀번호 입력 조건을 회원가입·변경·재설정 화면에서 공유한다.
export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 20;
export const PASSWORD_GUIDE = `${PASSWORD_MIN_LENGTH}~${PASSWORD_MAX_LENGTH}자, 영문·숫자·특수문자(!@#$%^&*) 각각 포함, 공백 금지`;

const LOGIN_ID_RE = /^[a-zA-Z0-9_]+$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const HANGUL_RE = /\p{Script=Hangul}/u;

export function getLoginIdError(value) {
  const loginId = value.trim();
  if (!loginId) return '필수 입력 항목입니다.';
  if (loginId.length < LOGIN_ID_MIN_LENGTH || loginId.length > USER_INPUT_LIMITS.loginId || !LOGIN_ID_RE.test(loginId))
    return `아이디는 영문·숫자·_ ${LOGIN_ID_MIN_LENGTH}~${USER_INPUT_LIMITS.loginId}자로 입력하세요.`;
  return '';
}

export function getEmailError(value) {
  const email = value.trim();
  if (!email) return '필수 입력 항목입니다.';
  if ([...email].length > USER_INPUT_LIMITS.email) return `${USER_INPUT_LIMITS.email}자 이하로 입력해주세요.`;
  if (HANGUL_RE.test(email)) return '이메일에는 한글을 사용할 수 없습니다.';
  return EMAIL_RE.test(email) ? '' : '이메일 형식을 확인하세요.';
}

export function getPasswordError(password) {
  if (!password.trim()) return '비밀번호를 입력해주세요.';
  if (/\s/.test(password)) return '비밀번호에는 공백을 포함할 수 없습니다.';
  if (password.length < PASSWORD_MIN_LENGTH || password.length > PASSWORD_MAX_LENGTH)
    return `비밀번호는 ${PASSWORD_MIN_LENGTH}~${PASSWORD_MAX_LENGTH}자로 입력해주세요.`;
  // 세미 프로젝트와 동일하게 영문·숫자·지정 특수문자를 각각 하나 이상 포함한다.
  if (!/[a-zA-Z]/.test(password) || !/[0-9]/.test(password) || !/[!@#$%^&*]/.test(password))
    return '영문, 숫자, 특수문자(!@#$%^&*)를 모두 포함해주세요.';
  return '';
}
