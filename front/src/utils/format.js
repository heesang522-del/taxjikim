export const won = (n) => Math.round(n).toLocaleString('ko-KR') + '원';

export const man = (n) => {
  const v = Math.round(n / 1e4);
  return (v < 0 ? '-' : '') + Math.abs(v).toLocaleString('ko-KR') + '만원';
};

/** 아이디 앞 3자만 남기고 가린다 (짧으면 1자). 예: minjun → min*** */
export const maskId = (id) => id.slice(0, id.length > 3 ? 3 : 1) + '***';

/** 이메일 아이디 부분 앞 2자만 남기고 가린다. 예: minjun@example.com → mi***@example.com */
export const maskEmail = (email) => {
  const [local, domain] = email.split('@');
  return `${local.slice(0, 2)}***@${domain}`;
};

export const signedMan =(n) => (n > 0 ? '+' : n < 0 ? '−' : '') + Math.abs(Math.round(n / 1e4)).toLocaleString('ko-KR') + '만원';
