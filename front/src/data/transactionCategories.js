const COMMON_CATEGORIES = [
  { value: 'OTHER', label: '기타' },
  { value: 'UNCLASSIFIED', label: '미분류' },
];

export const INCOME_TRANSACTION_CATEGORIES = [
  { value: 'YOUTUBE_SETTLEMENT', label: '유튜브 플랫폼 정산' },
  { value: 'AD_SPONSORSHIP', label: '광고·협찬' },
  { value: 'EXTERNAL_SUPPORT', label: '외부 후원' },
  ...COMMON_CATEGORIES,
];

export const EXPENSE_TRANSACTION_CATEGORIES = [
  { value: 'SOFTWARE_LICENSE', label: '소프트웨어' },
  { value: 'EQUIPMENT', label: '하드웨어' },
  { value: 'TELECOMMUNICATION', label: '통신비' },
  { value: 'TRANSPORT', label: '교통비' },
  { value: 'SUPPLIES_PROPS', label: '소모품·소품' },
  { value: 'SPACE_RENTAL', label: '공간 대여' },
  { value: 'OUTSOURCING', label: '외주비' },
  { value: 'AD_PROMOTION', label: '광고·홍보' },
  ...COMMON_CATEGORIES,
];

export function getTransactionCategoryLabel(code) {
  const value = String(code ?? '').trim();
  // 영문 코드는 그대로 보관하고 화면에서만 한글 표시명으로 바꾼다.
  return (
    [...INCOME_TRANSACTION_CATEGORIES, ...EXPENSE_TRANSACTION_CATEGORIES].find((category) => category.value === value)
      ?.label ??
    (value || '미분류')
  );
}
