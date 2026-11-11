// DB 컬럼과 별도로 계산기에서 받는 입력 범위를 정한다.
const MAX_DEPENDENT_COUNT = 100;
const MAX_WON = 9_999_999_999_999;
const MAX_DEDUCTED_TAX_RATE = 99.9999;

export function parseWon(value) {
  const text = String(value ?? '').trim();
  if (!/^(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)$/.test(text)) return null;

  const amount = Number(text.replace(/,/g, ''));
  return amount <= MAX_WON ? amount : null;
}

export function parseDeductedTaxRate(value) {
  const text = String(value ?? '').trim();
  if (!/^[0-9]+(?:\.[0-9]{1,4})?$/.test(text)) return null;

  const rate = Number(text);
  return rate <= MAX_DEDUCTED_TAX_RATE ? rate : null;
}

export function parseDependentCount(value) {
  const text = String(value ?? '').trim();
  if (!/^[0-9]+$/.test(text)) return null;

  const count = Number(text);
  return count <= MAX_DEPENDENT_COUNT ? count : null;
}

export function taxProfileNumbers(profile) {
  // 가족 수와 기부금은 미입력 시 0으로 저장한다.
  const dependentCount = parseDependentCount(String(profile.dependentCount ?? '').trim() || '0');
  if (dependentCount === null) {
    throw new Error('공제 대상 가족 수는 0~100 사이의 정수로 입력해주세요.');
  }

  const generalDonationAmount = parseWon(String(profile.generalDonationAmount ?? '').trim() || '0');
  const specialDonationAmount = parseWon(String(profile.specialDonationAmount ?? '').trim() || '0');

  if (generalDonationAmount === null || specialDonationAmount === null) {
    const label = generalDonationAmount === null ? '일반기부금' : '특례기부금';
    throw new Error(label + '은 0~9,999,999,999,999원 사이의 정수로 입력해주세요.');
  }

  return { dependentCount, generalDonationAmount, specialDonationAmount };
}

export function calculatePreTaxIncome(actualAmount, deductionRate) {
  if (actualAmount === null || deductionRate === '' || deductionRate === null) return null;
  const rate = Number(deductionRate);
  if (!Number.isSafeInteger(actualAmount) || actualAmount < 0 || !Number.isFinite(rate) || rate < 0 || rate >= 100)
    return null;
  // 실제 입금액을 차감 후 비율로 나누며 원 단위 반올림으로 역산한다.
  const gross = Math.round(actualAmount / (1 - rate / 100));
  return Number.isSafeInteger(gross) && gross <= MAX_WON ? gross : null;
}
