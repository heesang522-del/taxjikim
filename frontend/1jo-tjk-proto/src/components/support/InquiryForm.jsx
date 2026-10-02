import { Button, Grid, Input, Stack, Textarea } from '../ui'

export default function InquiryForm() {
  // MOCK: 서버로 보내지 않고 접수 안내만 띄운 뒤 폼을 비운다
  function handleSubmit(e) {
    e.preventDefault()
    const form = e.currentTarget
    alert('1:1 문의가 성공적으로 접수되었습니다. 오전 9시 ~ 저녁 6시 사이에 신속히 답변드리겠습니다.')
    form.reset()
  }

  return (
    <Stack $gap={16} style={{ paddingTop: 16, borderTop: '1px solid var(--border-soft)' }}>
      <h3 style={{ fontSize: 14, fontWeight: 700 }}>1:1 문의하기 (운영시간 내 빠른 답변 제공)</h3>
      <form onSubmit={handleSubmit}>
        <Stack $gap={12}>
          <Grid $cols={2} $gap={12}>
            <Input $small type="text" required placeholder="성함" />
            <Input $small type="email" required placeholder="회신받을 이메일" />
          </Grid>
          <Input $small type="text" required placeholder="문의 제목" />
          <Textarea $small required rows={4} placeholder="문의하실 내용을 자세히 적어주세요..." />
          <Button type="submit" $variant="primary" $block $size="sm" style={{ padding: 12 }}>
            1:1 문의 접수하기
          </Button>
        </Stack>
      </form>
    </Stack>
  )
}
