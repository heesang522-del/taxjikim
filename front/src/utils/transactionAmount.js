export function calculatePreTaxIncome(actualAmount, deductionRate) {
  if (actualAmount === null || deductionRate === '' || deductionRate === null) return null;
  const rate = Number(deductionRate);
  if (!Number.isSafeInteger(actualAmount) || actualAmount < 0 || !Number.isFinite(rate) || rate < 0 || rate >= 100)
    return null;
  // 실제 입금액을 차감 후 비율로 나누며 원 단위 반올림으로 역산한다.
  const gross = Math.round(actualAmount / (1 - rate / 100));
  return Number.isSafeInteger(gross) ? gross : null;
}
