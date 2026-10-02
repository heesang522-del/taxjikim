// 거래 내역을 합계 / 월별 합계로 요약한다
// 금액은 입력 중 빈 문자열일 수 있어서 숫자로 바꿔서 더한다
const amountOf = (tx) => Number(tx.amount) || 0

export function summarize(transactions) {
  const totalIncome = transactions
    .filter((tx) => tx.type === '수입')
    .reduce((sum, tx) => sum + amountOf(tx), 0)
  const totalExpense = transactions
    .filter((tx) => tx.type === '지출')
    .reduce((sum, tx) => sum + amountOf(tx), 0)

  // 월별 합계: 날짜('YYYY-MM-DD') 앞 7자리를 월 키로 묶는다
  const monthTotals = new Map()
  for (const tx of transactions) {
    if (!tx.date) continue
    const month = tx.date.slice(0, 7)
    const row = monthTotals.get(month) ?? { month, income: 0, expense: 0 }
    if (tx.type === '수입') row.income += amountOf(tx)
    else row.expense += amountOf(tx)
    monthTotals.set(month, row)
  }
  const byMonth = [...monthTotals.values()].sort((a, b) => a.month.localeCompare(b.month))

  return { totalIncome, totalExpense, netProfit: totalIncome - totalExpense, byMonth }
}
