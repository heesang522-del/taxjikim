import { Card, Grid } from '../ui'

const won = (n) => `${Math.round(n).toLocaleString('ko-KR')}원`

// 입력한 거래 내역으로 계산한 값만 보여준다 (result는 CalculatorPage의 계산 결과)
export default function SummaryCards({ result, count }) {
  const { totalIncome, totalExpense, netProfit, incomeTax, localTax, totalTax, withholding, balance } = result

  return (
    <Grid $cols={3} $gap={24}>
      <Card style={{ background: 'var(--primary-dark)', borderColor: 'var(--primary-dark)', color: '#fff' }}>
        <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--primary-light)' }}>예상 세액 (종합소득세 + 지방소득세)</span>
        <h2 style={{ fontSize: 30, fontWeight: 800, color: '#fff', margin: '8px 0' }}>{won(totalTax)}</h2>
        <p style={{ fontSize: 12, color: 'var(--primary-light)' }}>
          종합소득세 {won(incomeTax)} + 지방소득세 {won(localTax)}
        </p>
        <p style={{ fontSize: 12, color: 'var(--primary-light)' }}>
          기납부(원천징수 3.3%) {won(withholding)} → {balance >= 0 ? '예상 추가 납부' : '예상 환급'} {won(Math.abs(balance))}
        </p>
      </Card>
      <Card>
        <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)' }}>총 수입</span>
        <h2 style={{ fontSize: 30, fontWeight: 800, margin: '8px 0' }}>{won(totalIncome)}</h2>
        <p style={{ fontSize: 12, color: 'var(--success)', fontWeight: 500 }}>입력한 거래 {count}건 기준</p>
      </Card>
      <Card>
        <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)' }}>총 지출 (필요경비)</span>
        <h2 style={{ fontSize: 30, fontWeight: 800, margin: '8px 0' }}>{won(totalExpense)}</h2>
        <p style={{ fontSize: 12, color: 'var(--text-faint)' }}>순이익 {won(netProfit)}</p>
      </Card>
    </Grid>
  )
}
