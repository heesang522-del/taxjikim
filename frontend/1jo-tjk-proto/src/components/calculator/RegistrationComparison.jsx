import Icon from '../Icon'
import { Badge, Card, CardTitle, Row, Stack, Table } from '../ui'

// rows: [항목, 등록 전, 등록 후]
export default function RegistrationComparison({ rows }) {
  return (
    <Card>
      <Stack $gap={16}>
        <Row $justify="space-between">
          <CardTitle>
            <Icon name="scale" size={16} /> 사업자 등록 전·후 세금 비교
          </CardTitle>
          <Badge $tone="warn">예시값 · 확인 필요</Badge>
        </Row>
        <p style={{ fontSize: 12, color: 'var(--text-muted)' }}>
          종합소득세 금액 자체는 동일하며, 등록 여부에 따라 신고 의무와 혜택이 달라집니다.
        </p>
        <div style={{ overflowX: 'auto' }}>
          <Table>
            <thead>
              <tr>
                <th>항목</th>
                <th>등록 전</th>
                <th>등록 후</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(([label, before, after]) => (
                <tr key={label}>
                  <td style={{ padding: 12, fontWeight: 600 }}>{label}</td>
                  <td style={{ padding: 12 }}>{before}</td>
                  <td style={{ padding: 12 }}>{after}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        </div>
      </Stack>
    </Card>
  )
}
