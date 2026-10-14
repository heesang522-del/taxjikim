export const BOARD_TABS = ['전체', '정보 공유', '팁', '공지'];
export const BOARD_WRITE_TABS = BOARD_TABS.filter((t) => t !== '전체');
export const ME = '민준'; // 프로토타입: 로그인한 사용자의 닉네임. 이 닉네임이 쓴 글만 수정·삭제할 수 있다.

// 프로토타입용 계정. 가입하면 localStorage에 계정이 추가된다. 실제 서비스에서는 서버가 비밀번호를 해시로 보관한다.
// role: 'member' | 'admin'. 관리자 계정은 공지 작성과 /admin 접근이 가능하다.
// status: '정상' | '정지'. 정지 계정은 로그인할 수 없고(관리자 정지·로그인 반복 실패 자동 정지 모두) 관리자가 해제해야 한다.
export const ACCOUNT_SEED = [
  { id: 'minjun', pw: 'taxlab1234', name: '김민준', nickname: ME, birth: '1994-03-02', email: 'minjun@example.com', phone: '', role: 'member', status: '정상', joinedAt: '2025-03-02' },
  { id: 'admin', pw: 'admin1234', name: '운영팀', nickname: '운영팀', birth: '1990-01-01', email: 'admin@example.com', phone: '', role: 'admin', status: '정상', joinedAt: '2025-01-01' },
  { id: 'jeolsemon', pw: 'taxlab1234', name: '이절세', nickname: '절세몬', birth: '1991-06-11', email: 'jeolsemon@example.com', phone: '', role: 'member', status: '정상', joinedAt: '2025-01-18' },
  { id: 'taxnewbie', pw: 'taxlab1234', name: '박세무', nickname: '세무초보', birth: '1996-09-21', email: 'taxnewbie@example.com', phone: '', role: 'member', status: '정상', joinedAt: '2025-02-11' },
  { id: 'working', pw: 'taxlab1234', name: '최작업', nickname: '작업중', birth: '1993-12-05', email: 'working@example.com', phone: '', role: 'member', status: '정상', joinedAt: '2025-04-27' },
  { id: 'organizer', pw: 'taxlab1234', name: '정정리', nickname: '정리왕', birth: '1989-04-30', email: 'organizer@example.com', phone: '', role: 'member', status: '정지', joinedAt: '2025-05-30' },
];

// 프로토타입용 예시 글. 실제 후기나 세법 안내가 아니다.
export const BOARD_SEED = [
  { id: 'p1', tab: '정보 공유', title: '3.3% 프리랜서 5월 신고 후기', author: '절세몬', views: 128, createdAt: '2025-05-28', body: '올해 처음 5월에 종합소득세를 신고해 봤어요.\n\n홈택스에서 지급명세서 내역이 미리 채워져 있어서 생각보다 어렵지 않았습니다. 다만 경비로 넣을 영수증은 미리 정리해 두지 않아서 며칠 걸렸어요.\n\n내년에는 월별로 증빙을 모아 둘 생각입니다.' },
  { id: 'p2', tab: '정보 공유', title: '단순경비율 대상인지 확인하는 방법', author: '세무초보', views: 94, createdAt: '2025-05-14', body: '단순경비율은 대상 기준이 해마다 달라질 수 있어서, 신고 전에 국세청 안내를 꼭 확인하세요.\n\n저는 홈택스 모두채움 안내문에서 적용 가능 여부를 먼저 봤습니다. 확실하지 않다면 세무사에게 문의하는 게 안전해요.' },
  { id: 'p3', tab: '정보 공유', title: '노란우산공제 가입해 본 후기', author: '작업중', views: 77, createdAt: '2025-04-09', body: '연 소득에 따라 공제 한도가 달라진다고 해서 가입 전에 시뮬레이션부터 돌려 봤어요.\n\n납입 금액을 정하고 나니 과세표준이 얼마나 줄어드는지 감이 잡혀서 결정하기 쉬웠습니다. 중도 해지 조건은 꼭 읽어 보세요.' },
  { id: 'p4', tab: '팁', title: '경비 증빙 사진 정리하는 폴더 구조', author: '정리왕', views: 61, createdAt: '2025-08-21', body: '연도/월/카테고리 순서로 폴더를 만들고, 파일 이름은 날짜_내용_금액으로 통일했어요.\n\n예: 2025/03/장비/0312_맥북_1500000.jpg\n\n나중에 엑셀로 옮길 때 파일 이름만 복사해도 되어서 편합니다.' },
  { id: 'p5', tab: '팁', title: '월별 카드내역 CSV 한 번에 뽑기', author: '엑셀러', views: 45, createdAt: '2025-07-03', body: '카드사 사이트에서 기간을 1년으로 늘려 한 번에 내려받으면 월별로 따로 받을 필요가 없어요.\n\n받은 파일에서 사업 관련 지출만 걸러 두면 입력이 훨씬 빨라집니다.' },
  { id: 'p6', tab: '공지', title: '[공지] 2025년 귀속 세율 기준 반영 안내', author: '운영팀', views: 1204, createdAt: '2025-09-01', body: '2025년 귀속 기준으로 세율과 공제 항목을 반영했습니다.\n\n현재 화면의 세율과 공제 한도는 예시값이며, 실제 세액은 신고 시 달라질 수 있습니다.' },
  { id: 'p7', tab: '공지', title: '[공지] 시뮬레이션 비교 기능 추가', author: '운영팀', views: 812, createdAt: '2025-08-15', body: '추가 지출, 노란우산공제 납입 같은 가정값을 바꿔 현재 결과와 나란히 비교하는 기능을 추가했습니다.\n\n시나리오는 최대 3개까지 저장해 비교할 수 있습니다.' },
  { id: 'p9', tab: '팁', title: '월 300 부업 문의주세요 (광고)', author: '홍보왕', views: 9, createdAt: '2025-09-22', body: '(샘플) 광고성 링크를 반복해서 올리는 글입니다. 신고 흐름 확인용 예시입니다.' },
  { id: 'p8', tab: '팁', title: '연말 전에 확인하는 경비 체크리스트', author: ME, views: 12, createdAt: '2025-09-20', body: '10~12월에 빠뜨리기 쉬운 항목을 정리해 봤어요.\n\n- 장비·소프트웨어 구독\n- 교육·도서 구입\n- 통신비\n- 작업 공간 비용\n\n증빙을 남겨 두면 입력할 때 편합니다.' },
];

export const HELP_TABS = [['faq', '자주 묻는 질문'], ['lib', '자료실'], ['qna', 'Q&A']];
// FAQ 예시 글. 관리자 페이지에서 등록·수정하면 이 시드 대신 저장된 값을 쓴다.
export const FAQ_SEED = [
  { id: 'f1', q: '입력한 금액은 어디에 저장되나요?', a: '로그인 상태에서만 연도별로 저장됩니다. 게스트 체험 데이터는 저장되지 않습니다.' },
  { id: 'f2', q: '3.3% 원천징수를 어떻게 계산하나요?', a: '입금액 기준 입력을 켜면 세전 수입을 역산하고 소득세 3%를 기납부세액으로 반영합니다.' },
  { id: 'f3', q: '예측은 얼마나 정확한가요?', a: '최근 6개월 평균으로 남은 달을 채웁니다. 10~12월 실제 값을 넣으면 정확해집니다.' },
];
export const LIBRARY = ['종합소득세 신고 달력 (PDF)', '경비 처리 가능 항목 체크리스트 (PDF)'];

// Q&A 예시 글. visibility: 'public'(모두 열람) | 'private'(작성자·관리자만 열람). answer가 있으면 답변 완료.
export const INQUIRY_SEED = [
  { id: 'q1', title: '노란우산공제 한도는 어떻게 되나요?', body: '사업소득 규모별로 공제 한도가 다르다고 들었는데, 올해 기준으로 얼마인지 궁금합니다.', visibility: 'public', author: '세무초보', status: '답변 완료', answer: '소득 규모에 따라 한도가 달라지며, 홈택스 공제 안내 화면에서 정확한 한도를 확인하실 수 있습니다.', createdAt: '2025-08-02' },
  { id: 'q2', title: '사업자 등록 시점 문의', body: '프리랜서로 일하다가 사업자 등록을 언제 하는 게 유리한지 궁금합니다.', visibility: 'public', author: '작업중', status: '답변 대기', answer: null, createdAt: '2025-09-10' },
  { id: 'q3', title: '결제 정보 변경 요청', body: '등록된 결제 수단을 바꾸고 싶은데 어디서 해야 하나요? 개인 정보라 비공개로 문의드립니다.', visibility: 'private', author: ME, status: '답변 완료', answer: '마이페이지 > 결제 정보에서 직접 변경하실 수 있습니다. 변경 후에도 문제가 있으면 다시 문의해 주세요.', createdAt: '2025-09-02' },
];

export const ADMIN_STATS = {
  age: [['20대', 18], ['30대', 46], ['40대', 27], ['50대+', 9]],
  income: [['~3천만', 22], ['~5천만', 41], ['~8천만', 26], ['8천만+', 11]],
  region: [['서울', 38], ['경기', 29], ['부산', 8], ['기타', 25]],
};

// 댓글 예시. deletedByAdmin이 true면 관리자에 의해 삭제된 댓글로 표시한다.
export const COMMENT_SEED = [
  { id: 'c1', postId: 'p1', author: '세무초보', body: '저도 처음엔 막막했는데 도움이 됐어요, 감사합니다!', createdAt: '2025-05-29', deletedByAdmin: false },
  { id: 'c2', postId: 'p1', author: '악플러', body: '(샘플) 욕설이 포함된 댓글입니다. 신고 흐름 확인용 예시입니다.', createdAt: '2025-05-30', deletedByAdmin: false },
];

// 신고된 글·댓글. targetType: 'post' | 'comment'. 관리자가 삭제 또는 반려하면 status가 '처리됨', result가 '삭제'|'반려'가 된다.
export const REPORT_SEED = [
  { id: 'r1', targetType: 'post', postId: 'p9', commentId: null, reason: '광고성 링크 반복', reporter: '세무초보', createdAt: '2025-09-23', status: '대기', result: null },
  { id: 'r2', targetType: 'comment', postId: 'p1', commentId: 'c2', reason: '욕설', reporter: '절세몬', createdAt: '2025-09-24', status: '대기', result: null },
  { id: 'r3', targetType: 'post', postId: 'p5', commentId: null, reason: '허위 정보', reporter: '작업중', createdAt: '2025-09-10', status: '처리됨', result: '반려' },
];

// 모델·처리 현황: 사용 지표
export const USAGE_METRICS = { calcRuns: 482, uploadAttempts: 96, avgCalcMs: 340 };

// 모델·처리 현황: 업로드·계산 실패 이력
export const FAILURE_LOG_SEED = [
  { id: 'fl1', type: '업로드', reason: '지원하지 않는 파일 형식 (.hwp)', user: '정리왕', occurredAt: '2025-09-18 14:02' },
  { id: 'fl2', type: '업로드', reason: '필수 컬럼 누락 (거래일자)', user: '엑셀러', occurredAt: '2025-09-20 09:41' },
  { id: 'fl3', type: '계산', reason: '수입 입력값 없음', user: '작업중', occurredAt: '2025-09-21 18:15' },
  { id: 'fl4', type: '계산', reason: '소득 유형 미선택으로 경비율 추천 실패', user: '세무초보', occurredAt: '2025-09-25 11:07' },
];
