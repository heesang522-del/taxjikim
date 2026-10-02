// 거래 내역 초기값 (HTML 시안의 기본 2건)
export const initialTransactions = [
  { id: 1, type: '수입', date: '2026-03-01', description: '3월 외주 개발 용역비', category: '소프트웨어 개발비', amount: 5000000 },
  { id: 2, type: '지출', date: '2026-03-10', description: '맥북 프로 구입', category: '장비구입비', amount: 3800000 },
]

// '테스트 샘플 자동입력' 버튼용 예시 데이터 (수입 5건, 지출 4건)
export const sampleTransactions = [
  { id: 101, type: '수입', date: '2026-01-15', description: '1월 외주 개발 용역비', category: '소프트웨어 개발비', amount: 4500000 },
  { id: 102, type: '수입', date: '2026-02-12', description: '2월 외주 개발 용역비', category: '소프트웨어 개발비', amount: 5200000 },
  { id: 103, type: '수입', date: '2026-03-05', description: '3월 외주 개발 용역비', category: '소프트웨어 개발비', amount: 6100000 },
  { id: 104, type: '수입', date: '2026-04-10', description: '4월 외주 개발 용역비', category: '소프트웨어 개발비', amount: 4800000 },
  { id: 105, type: '수입', date: '2026-05-08', description: '5월 외주 개발 용역비', category: '소프트웨어 개발비', amount: 5500000 },
  { id: 106, type: '지출', date: '2026-01-20', description: '서버 호스팅 비용', category: '통신비/서버비', amount: 300000 },
  { id: 107, type: '지출', date: '2026-03-10', description: '노트북 구입', category: '장비구입비', amount: 3800000 },
  { id: 108, type: '지출', date: '2026-04-02', description: '개발 도구 구독', category: '소프트웨어 구독', amount: 120000 },
  { id: 109, type: '지출', date: '2026-05-15', description: '사무용품', category: '기타 경비', amount: 80000 },
]
