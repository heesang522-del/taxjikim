import Icon from '../Icon'
import { Card, CardTitle, Row, Stack } from '../ui'

const won = (n) => `${n.toLocaleString('ko-KR')}원`

export default function AnnualSummaryCard({ yearly }) {
  return (
    <Card>
      <Stack $gap={16}>
        <CardTitle>
          <Icon name="barChart" size={16} /> 연도별 내 수익 / 지출 요약
        </CardTitle>
        <Stack $gap={12}>
          {yearly.map((row, i) => (
            <Row
              key={row.year}
              $justify="space-between"
              style={{ padding: 12, borderRadius: 12, background: 'var(--bg)', fontSize: 12, fontWeight: 500 }}
            >
              <span style={{ color: 'var(--text-muted)' }}>{row.year}</span>
              {/* 첫 줄(진행중인 연도)만 강조색 */}
              <span style={{ fontWeight: 700, color: i === 0 ? 'var(--primary)' : 'var(--text-h)' }}>
                수익 {won(row.income)} / 지출 {won(row.expense)}
              </span>
            </Row>
          ))}
        </Stack>
      </Stack>
    </Card>
  )
}
