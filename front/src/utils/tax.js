// 2023~2025년 귀속 종합소득세 세율표 (국세청 기준)
// 산출세액 = 과세표준 × 세율 - 누진공제액
export const INCOME_TAX_BRACKETS = [
  { upTo: 14_000_000, rate: 0.06, deduction: 0 },
  { upTo: 50_000_000, rate: 0.15, deduction: 1_260_000 },
  { upTo: 88_000_000, rate: 0.24, deduction: 5_760_000 },
  { upTo: 150_000_000, rate: 0.35, deduction: 15_440_000 },
  { upTo: 300_000_000, rate: 0.38, deduction: 19_940_000 },
  { upTo: 500_000_000, rate: 0.4, deduction: 25_940_000 },
  { upTo: 1_000_000_000, rate: 0.42, deduction: 35_940_000 },
  { upTo: Infinity, rate: 0.45, deduction: 65_940_000 },
]

const LOCAL_INCOME_TAX_RATE = 0.1 // 지방소득세 = 종합소득세의 10%
export const FREELANCER_WITHHOLDING_RATE = 0.033 // 프리랜서 사업소득 원천징수세율 3.3%

export function findBracket(taxBase) {
  return INCOME_TAX_BRACKETS.find((bracket) => taxBase <= bracket.upTo)
}

export function calcIncomeTax(taxBase) {
  if (taxBase <= 0) return 0
  const bracket = findBracket(taxBase)
  return Math.max(0, Math.round(taxBase * bracket.rate - bracket.deduction))
}

export function calcLocalIncomeTax(incomeTax) {
  return Math.round(incomeTax * LOCAL_INCOME_TAX_RATE)
}

export function calcWithholding(totalIncome) {
  return Math.round(totalIncome * FREELANCER_WITHHOLDING_RATE)
}
