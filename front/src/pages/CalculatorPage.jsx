import { useEffect, useMemo, useState } from 'react'
import CalcSubTabs from '../components/calculator/CalcSubTabs'
import InputForm from '../components/calculator/InputForm'
import LoadingScreen from '../components/calculator/LoadingScreen'
import ResultView from '../components/calculator/ResultView'
import AsyncBoundary from '../components/AsyncBoundary'
import MockBadge from '../components/MockBadge'
import { Row, Stack } from '../components/ui'
import { useTransactionStore } from '../store/useTransactionStore'
import { useApi } from '../utils/useApi'
import { summarize } from '../utils/summary'
import { calcIncomeTax, calcLocalIncomeTax, calcWithholding } from '../utils/tax'
import { getCalcOptions } from '../api/calculator'

// 화면 상태: 'input'(입력) → 'loading'(계산 중) → 'output'(결과)
export default function CalculatorPage() {
  const { status, data: options } = useApi(getCalcOptions)
  const [view, setView] = useState('input')
  const [progress, setProgress] = useState(10)
  const [profile, setProfile] = useState({ age: '32', region: '서울특별시', job: '개발자 (소프트웨어)' })

  const transactions = useTransactionStore((state) => state.transactions)
  const addTransaction = useTransactionStore((state) => state.addTransaction)
  const updateTransaction = useTransactionStore((state) => state.updateTransaction)
  const removeTransaction = useTransactionStore((state) => state.removeTransaction)
  const loadSample = useTransactionStore((state) => state.loadSample)

  // 계산 로딩 연출: 10 → 40 → 70 → 100% 로 올라가면 결과 화면으로 넘어간다
  useEffect(() => {
    if (view !== 'loading') return
    let timeout
    const interval = setInterval(() => {
      setProgress((p) => {
        const next = p + 30
        if (next >= 100) {
          clearInterval(interval)
          timeout = setTimeout(() => setView('output'), 400)
        }
        return next
      })
    }, 300)
    return () => {
      clearInterval(interval)
      clearTimeout(timeout)
    }
  }, [view])

  // 세액 계산: 수입 - 지출 = 과세표준(단순 추정), 누진세율 적용 후 지방소득세 10% 가산
  const result = useMemo(() => {
    const summary = summarize(transactions)
    const taxBase = Math.max(0, summary.netProfit)
    const incomeTax = calcIncomeTax(taxBase)
    const localTax = calcLocalIncomeTax(incomeTax)
    const totalTax = incomeTax + localTax
    const withholding = calcWithholding(summary.totalIncome)
    return { ...summary, incomeTax, localTax, totalTax, withholding, balance: totalTax - withholding }
  }, [transactions])

  // 진행률을 10%로 되돌린 뒤 로딩 화면으로 전환
  function startCalculation() {
    setProgress(10)
    setView('loading')
  }

  function handleAdd() {
    // 옵션을 불러오기 전에는 거래를 추가하지 않고, Compiler의 사전 계산도 안전하게 처리한다.
    if (!options) return
    addTransaction({
      type: '지출',
      date: '2026-03-15',
      description: '',
      category: options?.categories?.[1] ?? '',
      amount: '',
    })
  }

  function handleLoadSample() {
    loadSample()
    alert('테스트 샘플 데이터(수입 5건, 지출 4건)가 입력되었습니다.')
  }

  return (
    <Stack $gap={32}>
      <Row $justify="space-between">
        <CalcSubTabs active={view === 'output' ? 'output' : 'input'} onChange={setView} />
        <MockBadge />
      </Row>
      <AsyncBoundary status={status}>
        {options && view === 'input' && (
          <InputForm
            options={options}
            profile={profile}
            onProfileChange={setProfile}
            transactions={transactions}
            onAdd={handleAdd}
            onChange={updateTransaction}
            onRemove={removeTransaction}
            onLoadSample={handleLoadSample}
            onSubmit={startCalculation}
          />
        )}
        {view === 'loading' && <LoadingScreen progress={progress} />}
        {options && view === 'output' && (
          <ResultView result={result} count={transactions.length} options={options} onRetry={() => setView('input')} />
        )}
      </AsyncBoundary>
    </Stack>
  )
}
