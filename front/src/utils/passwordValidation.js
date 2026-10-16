// 새 비밀번호 입력 조건을 회원가입·변경·재설정 화면에서 공유한다.
export const PASSWORD_GUIDE = '8~20자, 영문·숫자·특수문자(!@#$%^&*) 각각 포함, 공백 금지';

export function getPasswordError(password) {
  if (!password.trim()) return '비밀번호를 입력해주세요.';
  if (/\s/.test(password)) return '비밀번호에는 공백을 포함할 수 없습니다.';
  if (password.length < 8 || password.length > 20) return '비밀번호는 8~20자로 입력해주세요.';
  // 세미 프로젝트와 동일하게 영문·숫자·지정 특수문자를 각각 하나 이상 포함한다.
  if (!/[a-zA-Z]/.test(password) || !/[0-9]/.test(password) || !/[!@#$%^&*]/.test(password))
    return '영문, 숫자, 특수문자(!@#$%^&*)를 모두 포함해주세요.';
  return '';
}
