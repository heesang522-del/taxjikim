export const ROUTES = {
  home: '/',
  calcInput: '/calculator/input',
  calcLoading: '/calculator/loading',
  calcResult: '/calculator/result',
  calcSim: '/calculator/sim',
  auth: '/login',
  signup: '/signup',
  findAccount: '/find-account',
  mypage: '/mypage',
  board: '/community',
  boardWrite: '/community/new',
  boardPost: (id) => `/community/${id}`,
  boardEdit: (id) => `/community/${id}/edit`,
  blocked: '/blocked',
  help: '/support',
  helpQna: '/support?tab=qna',
  qnaPost: (id) => `/support/qna/${id}`,
  admin: '/admin',
  adminReports: '/admin/reports',
  adminInquiries: '/admin/inquiries',
  adminChats: '/admin/chats',
};

// 헤더 가운데 메뉴. 관리자·마이페이지·로그인은 Header에서 따로 그린다.
export const NAV_ITEMS = [
  { to: ROUTES.home, label: '홈', end: true },
  { to: '/calculator', label: '세금 계산·시뮬레이션' },
  { to: ROUTES.board, label: '게시판' },
  { to: ROUTES.help, label: '고객센터' },
];
