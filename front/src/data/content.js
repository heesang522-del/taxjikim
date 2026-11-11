export const POST_TABS = ['전체', '정보 공유', '팁', '공지'];
export const POST_WRITE_TABS = POST_TABS.filter((t) => t !== '전체');
export const ME = '민준'; // 프로토타입: 로그인한 사용자의 닉네임. 이 닉네임이 쓴 글만 수정·삭제할 수 있다.

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

// 신고된 글. targetType: 'post'. 관리자가 삭제 또는 반려하면 status가 '처리됨', result가 '삭제'|'반려'가 된다.
// 게시글·댓글 신고는 백엔드로 연동되어 여기서 더 이상 예시 데이터를 쓰지 않는다.
// 이 mock은 백엔드에 없는 postId를 가진 브라우저 전용 예시 글을 신고할 때만 쓰인다.
export const REPORT_SEED = [];

// 모델·처리 현황: 사용 지표
export const USAGE_METRICS = { calcRuns: 482, uploadAttempts: 96, avgCalcMs: 340 };

// 모델·처리 현황: 업로드·계산 실패 이력
export const FAILURE_LOG_SEED = [
  { id: 'fl1', type: '업로드', reason: '지원하지 않는 파일 형식 (.hwp)', user: '정리왕', occurredAt: '2025-09-18 14:02' },
  { id: 'fl2', type: '업로드', reason: '필수 컬럼 누락 (거래일자)', user: '엑셀러', occurredAt: '2025-09-20 09:41' },
  { id: 'fl3', type: '계산', reason: '수입 입력값 없음', user: '작업중', occurredAt: '2025-09-21 18:15' },
  { id: 'fl4', type: '계산', reason: '소득 유형 미선택으로 경비율 추천 실패', user: '세무초보', occurredAt: '2025-09-25 11:07' },
];
