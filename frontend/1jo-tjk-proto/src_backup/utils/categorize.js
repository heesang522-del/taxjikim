// 규칙 기반 임시 분류 — 실제 서비스에서는 ML 모델이 거래 내용을 보고 카테고리를 예측할 예정
export const EXPENSE_CATEGORIES = [
  { key: '장비', keywords: ['카메라', '마이크', '조명', '삼각대', '렌즈'] },
  { key: '편집', keywords: ['편집 프로그램', '편집', '구독', '음원', '이미지', '폰트'] },
  { key: '인건비', keywords: ['편집자', '썸네일', '인건비', '작업비'] },
  { key: '촬영·작업 공간', keywords: ['스튜디오', '대여', '작업실', '렌탈'] },
  { key: '기타 활동 지출', keywords: ['통신', '이동', '소품', '교통', '택시'] },
]

export const UNKNOWN_CATEGORY = '확인 필요'

export function categorizeExpense(description) {
  const matched = EXPENSE_CATEGORIES.find(({ keywords }) =>
    keywords.some((keyword) => description.includes(keyword)),
  )
  return matched ? matched.key : UNKNOWN_CATEGORY
}
