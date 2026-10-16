import { INCOME_CATEGORIES, EXPENSE_CATEGORIES } from '../data/options';

export function getImportCategories(kind) {
  return kind === 'in' ? INCOME_CATEGORIES : kind === 'out' ? EXPENSE_CATEGORIES : [];
}

export function getInitialReviewValues(row) {
  const kind = ['in', 'out'].includes(row.kind) ? row.kind : '';
  // 추정된 카테고리는 확정값으로 미리 선택하지 않는다.
  const uncertainCategory = row.reasons.some((reason) => reason.startsWith('카테고리'));
  return {
    kind,
    cat: !uncertainCategory && getImportCategories(kind).includes(row.cat) ? row.cat : '',
    date: row.date || '',
    memo: row.memo === '(내용 없음)' ? '' : row.memo || '',
    amt: row.amt ? String(row.amt) : '',
  };
}

export function validateReviewValues({ kind, date, cat, memo, amt }) {
  if (!['in', 'out'].includes(kind)) return '수입 또는 지출을 선택해 주세요.';
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return '날짜를 확인해 주세요.';
  const parsedDate = new Date(`${date}T00:00:00Z`);
  if (Number.isNaN(parsedDate.getTime()) || parsedDate.toISOString().slice(0, 10) !== date) return '실제로 존재하는 날짜를 입력해 주세요.';
  if (!getImportCategories(kind).includes(cat)) return '카테고리를 선택해 주세요.';
  if (!memo.trim()) return '원본을 확인하고 거래 내용을 입력해 주세요.';
  // 음수·소수·문자를 지워 다른 금액으로 바꾸지 않고, 올바른 원 단위 정수만 허용한다.
  if (!/^(?:\d+|\d{1,3}(?:,\d{3})+)$/.test(String(amt))) return '금액은 양의 정수로 입력해 주세요.';
  const value = Number(String(amt).replaceAll(',', ''));
  if (!Number.isSafeInteger(value) || value <= 0) return '금액 범위를 확인해 주세요.';
  return '';
}
