import { categorizeExpense } from './categorize'

export function summarize(transactions) {
  const totalIncome = transactions
    .filter((tx) => tx.type === '수입')
    .reduce((sum, tx) => sum + tx.amount, 0)

  const expenses = transactions.filter((tx) => tx.type === '지출')
  const totalExpense = expenses.reduce((sum, tx) => sum + tx.amount, 0)

  const categoryTotals = new Map()
  for (const tx of expenses) {
    const category = categorizeExpense(tx.description)
    categoryTotals.set(category, (categoryTotals.get(category) ?? 0) + tx.amount)
  }
  const byCategory = [...categoryTotals.entries()]
    .map(([category, amount]) => ({ category, amount }))
    .sort((a, b) => b.amount - a.amount)

  // 월별 합계: 날짜('YYYY-MM-DD') 앞 7자리를 월 키로 묶는다
  const monthTotals = new Map()
  for (const tx of transactions) {
    const month = tx.date.slice(0, 7)
    const row = monthTotals.get(month) ?? { month, income: 0, expense: 0 }
    if (tx.type === '수입') row.income += tx.amount
    else row.expense += tx.amount
    monthTotals.set(month, row)
  }
  const byMonth = [...monthTotals.values()].sort((a, b) => a.month.localeCompare(b.month))

  return {
    totalIncome,
    totalExpense,
    netProfit: totalIncome - totalExpense,
    byCategory,
    byMonth,
  }
}
