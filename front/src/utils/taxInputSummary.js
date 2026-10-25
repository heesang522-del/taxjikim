import { normalizeTaxKind, parseTaxAmount } from '../store/useTaxStore';

export function getTransactionCashAmount(row) {
  // 세전 금액을 실제 입금액으로 대체하지 않는다.
  const actualAmount = parseTaxAmount(row.actualAmount);
  return actualAmount ?? (row.amountBasis === 'GROSS' ? null : parseTaxAmount(row.amt));
}

export function getTaxInputSummary(rows) {
  return rows.reduce(
    (summary, row) => {
      const amount = getTransactionCashAmount(row);
      if (amount === null) {
        summary.missingAmountCount += 1;
        return summary;
      }
      const kind = normalizeTaxKind(row.kind);
      if (kind === 'in') summary.income += amount;
      else if (kind === 'out') summary.expense += amount;
      else summary.unknown += amount;
      // 미분류는 수입·지출 구분이 아닌 카테고리의 지정 여부로 판단한다.
      const category = String(row.cat ?? '').trim();
      if (!category || ['UNCLASSIFIED', '미분류', '미기재'].includes(category.toUpperCase())) {
        summary.unclassified += amount;
      }
      return summary;
    },
    { income: 0, expense: 0, unknown: 0, unclassified: 0, missingAmountCount: 0 }
  );
}
