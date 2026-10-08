import Icon from '../Icon'
import { Button, Card, CardTitle, Input, Row, Stack } from '../ui'

export default function InquiryManagement({ inquiry }) {
  return (
    <Card>
      <Stack $gap={16}>
        <CardTitle>
          <Icon name="messageSquare" size={16} /> 사용자 문의 접수 및 처리 알림 전송
        </CardTitle>
        <Stack $gap={8} style={{ padding: 12, borderRadius: 12, background: 'var(--bg)', fontSize: 12 }}>
          <Row $justify="space-between" style={{ fontWeight: 700 }}>
            <span>{inquiry.title}</span>
            <span style={{ color: 'var(--danger)' }}>{inquiry.status}</span>
          </Row>
          <p style={{ color: 'var(--text-muted)' }}>{inquiry.meta}</p>
          <Input $small type="text" placeholder="처리 결과 답변을 입력하세요..." />
          <Button
            $variant="primary"
            $size="sm"
            $block
            onClick={() => alert('답변이 등록되었으며 사용자에게 알림(SMS/푸시)이 전송되었습니다.')}
          >
            답변 등록 및 알림 전송
          </Button>
        </Stack>
      </Stack>
    </Card>
  )
}
