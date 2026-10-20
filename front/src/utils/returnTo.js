// 로그인 직전에 보던 페이지를 기억해 두었다가, 로그인에 성공하면 그곳으로 돌려보낸다.
// 복귀 주소는 페이지 메모리에만 보관하며 새로고침하면 초기화한다.
let returnPath = null;
// 로그인·계정 찾기 페이지와 자동으로 결과로 넘어가는 계산 대기 화면은 복귀 경로에서 제외한다.
const SKIP = ['/login', '/signup', '/find-account', '/calculator/loading'];

export const rememberPath = (path) => {
  if (SKIP.some((p) => path === p || path.startsWith(`${p}?`))) return;
  returnPath = path;
};

/** 기억해 둔 경로를 꺼내고 지운다. 없으면 메인('/') */
export const takeReturnTo = () => {
  const path = returnPath;
  returnPath = null;
  return path || '/';
};
