import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { useTransactionStore } from '../store/useTransactionStore'
import { summarize } from '../utils/summary'
import {
  calcIncomeTax,
  calcLocalIncomeTax,
  calcWithholding,
  findBracket,
} from '../utils/tax'
import CategoryChart from '../components/CategoryChart'
import MonthlyChart from '../components/MonthlyChart'
import {
  Button,
  Card,
  Grid,
  PageHeader,
  Section,
  SectionHint,
  SectionTitle,
  StatTile,
  Table,
} from '../components/ui'

const won = (n) => `${Math.round(n).toLocaleString('ko-KR')}원`

const CompareTable = styled(Table)`
  th:nth-child(2),
  th:nth-child(3),
  td:nth-child(2),
  td:nth-child(3) {
    text-align: center;
  }
`

const GuideList = styled.ul`
  margin: 14px 0 0;
  padding-left: 18px;
  font-size: 13.5px;
  color: var(--text);
  line-height: 1.8;
`

const COMPARISON_ROWS = [
  ['종합소득세 신고', '필요 (동일하게 계산)', '필요 (동일하게 계산)'],
  ['부가가치세 신고', '해당 없음', '필요 (간이·일반과세자)'],
  ['세금계산서 발행', '불가', '가능'],
  ['매입세액공제', '불가', '조건부 가능 (일반과세자)'],
  ['경비 증빙 방식', '원천징수영수증(3.3%) 기준', '장부 기장으로 경비 처리'],
  ['건강보험', '지역가입자 유지', '사업장/지역가입 전환 검토 필요'],
]

export default function OutputPage() {
  const navigate = useNavigate()
  const transactions = useTransactionStore((state) => state.transactions)
  const [showGuide, setShowGuide] = useState(false)

  const result = useMemo(() => {
    const { totalIncome, totalExpense, netProfit, byCategory, byMonth } = summarize(transactions)
    const taxBase = Math.max(0, netProfit)
    const bracket = findBracket(taxBase)
    const incomeTax = calcIncomeTax(taxBase)
    const localTax = calcLocalIncomeTax(incomeTax)
    const totalTax = incomeTax + localTax
    const withholding = calcWithholding(totalIncome)
    const balance = totalTax - withholding

    return {
      totalIncome,
      totalExpense,
      netProfit,
      byCategory,
      byMonth,
      taxBase,
      bracket,
      incomeTax,
      localTax,
      totalTax,
      withholding,
      balance,
    }
  }, [transactions])

  return (
    <>
      <PageHeader>
        <h1>세금 분석 결과</h1>
        <p>입력한 거래 내역을 기준으로 추정한 예상 세액입니다.</p>
      </PageHeader>

      <Section>
        <Grid $cols={4}>
          <StatTile>
            <div className="label">총수입</div>
            <div className="value">{won(result.totalIncome)}</div>
          </StatTile>
          <StatTile>
            <div className="label">총지출(필요경비)</div>
            <div className="value">{won(result.totalExpense)}</div>
          </StatTile>
          <StatTile>
            <div className="label">순이익</div>
            <div className="value">{won(result.netProfit)}</div>
          </StatTile>
          <StatTile $color="var(--accent)">
            <div className="label">예상 세액 합계</div>
            <div className="value">{won(result.totalTax)}</div>
          </StatTile>
        </Grid>
      </Section>

      <Section>
        <SectionTitle>산출 근거</SectionTitle>
        <SectionHint>
          기본공제 등 개인별 소득공제는 반영하지 않은 단순 추정치이며, 실제 세액은 신고 시 달라질 수
          있습니다.
        </SectionHint>
        <Card style={{ padding: 0 }}>
          <Table>
            <tbody>
              <tr>
                <td>총수입</td>
                <td className="amount">{won(result.totalIncome)}</td>
              </tr>
              <tr>
                <td>− 필요경비(총지출)</td>
                <td className="amount">{won(result.totalExpense)}</td>
              </tr>
              <tr>
                <td>= 소득금액(과세표준)</td>
                <td className="amount">{won(result.taxBase)}</td>
              </tr>
              <tr>
                <td>적용 세율 구간</td>
                <td className="amount">
                  {Math.round(result.bracket.rate * 100)}% (누진공제 {won(result.bracket.deduction)})
                </td>
              </tr>
              <tr>
                <td>종합소득세 산출세액</td>
                <td className="amount">{won(result.incomeTax)}</td>
              </tr>
              <tr>
                <td>+ 지방소득세 (10%)</td>
                <td className="amount">{won(result.localTax)}</td>
              </tr>
              <tr>
                <td>
                  <strong>예상 세액 합계</strong>
                </td>
                <td className="amount">
                  <strong>{won(result.totalTax)}</strong>
                </td>
              </tr>
              <tr>
                <td>− 기납부세액 (원천징수 3.3%)</td>
                <td className="amount">{won(result.withholding)}</td>
              </tr>
              <tr>
                <td>
                  <strong>{result.balance >= 0 ? '예상 추가 납부액' : '예상 환급액'}</strong>
                </td>
                <td className="amount">
                  <strong>{won(Math.abs(result.balance))}</strong>
                </td>
              </tr>
            </tbody>
          </Table>
        </Card>
      </Section>

      <Section>
        <SectionTitle>지출 카테고리별 시각화</SectionTitle>
        <SectionHint>지출 금액이 큰 카테고리부터 정렬했습니다.</SectionHint>
        <Card>
          {result.byCategory.length > 0 ? (
            <CategoryChart data={result.byCategory} />
          ) : (
            <p style={{ color: 'var(--text-muted)', fontSize: 14 }}>지출 내역이 없습니다.</p>
          )}
        </Card>
      </Section>

      <Section>
        <SectionTitle>월별 수입·지출 추이</SectionTitle>
        <SectionHint>입력한 거래 내역을 월 단위로 합산했습니다.</SectionHint>
        <Card>
          {result.byMonth.length > 0 ? (
            <MonthlyChart data={result.byMonth} />
          ) : (
            <p style={{ color: 'var(--text-muted)', fontSize: 14 }}>거래 내역이 없습니다.</p>
          )}
        </Card>
      </Section>

      <Section>
        <SectionTitle>사업자 등록 전·후 비교</SectionTitle>
        <SectionHint>종합소득세 금액 자체는 동일하며, 등록 여부에 따라 신고 의무와 혜택이 달라집니다.</SectionHint>
        <Card style={{ padding: 0 }}>
          <CompareTable>
            <thead>
              <tr>
                <th>항목</th>
                <th>등록 전</th>
                <th>등록 후</th>
              </tr>
            </thead>
            <tbody>
              {COMPARISON_ROWS.map(([label, before, after]) => (
                <tr key={label}>
                  <td>{label}</td>
                  <td>{before}</td>
                  <td>{after}</td>
                </tr>
              ))}
            </tbody>
          </CompareTable>
        </Card>
      </Section>

      <Section>
        <SectionTitle>다음 단계</SectionTitle>
        <Grid $cols={2}>
          <Card>
            <h3 style={{ fontSize: 15, marginBottom: 6 }}>다른 시나리오로 시뮬레이션</h3>
            <p style={{ color: 'var(--text-muted)', fontSize: 13.5, marginBottom: 14 }}>
              거래 내역을 수정해 세액이 어떻게 달라지는지 확인해보세요.
            </p>
            <Button $variant="primary" onClick={() => navigate('/')}>
              입력 화면으로 이동
            </Button>
          </Card>
          <Card>
            <h3 style={{ fontSize: 15, marginBottom: 6 }}>사업자 등록 안내</h3>
            <p style={{ color: 'var(--text-muted)', fontSize: 13.5, marginBottom: 14 }}>
              등록 절차가 궁금하다면 간단한 안내를 확인해보세요.
            </p>
            <Button onClick={() => setShowGuide((v) => !v)}>
              {showGuide ? '안내 닫기' : '안내 보기'}
            </Button>
            {showGuide && (
              <GuideList>
                <li>업종 코드 확인 (예: 1인 미디어콘텐츠창작자)</li>
                <li>홈택스 또는 정부24에서 사업자등록 신청</li>
                <li>과세유형 선택 (간이과세자 / 일반과세자)</li>
                <li>사업용 계좌 및 사업용 신용카드 등록</li>
              </GuideList>
            )}
          </Card>
        </Grid>
      </Section>
    </>
  )
}
