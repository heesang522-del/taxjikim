// 로그인 직전에 보던 페이지를 기억해 두었다가, 로그인에 성공하면 그곳으로 돌려보낸다.
const KEY = 'taxlab.return-to.v1';
// 로그인·차단 안내 페이지와 계산 대기 화면은 돌아갈 곳이 아니다 (대기 화면은 자동으로 결과로 넘어가는 중간 화면)
const SKIP = ['/login', '/signup', '/find-account', '/calculator/loading', '/blocked'];

export const rememberPath = (path) => {
  if (SKIP.some((p) => path === p || path.startsWith(`${p}?`))) return;
  try { sessionStorage.setItem(KEY, path); } catch { /* 저장이 막힌 경우 메인으로 돌아간다 */ }
};

/** 기억해 둔 경로를 꺼내고 지운다. 없으면 메인('/') */
export const takeReturnTo = () => {
  try {
    const path = sessionStorage.getItem(KEY);
    sessionStorage.removeItem(KEY);
    return path || '/';
  } catch {
    return '/';
  }
};
