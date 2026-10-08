import { Button, Input, Stack } from '../ui'

// 아이디 찾기 / 비밀번호 재설정 박스. 입력칸은 fields로 받는다
export default function RecoveryBox({ title, fields, buttonLabel, onSubmit }) {
  return (
    <Stack $gap={12} style={{ padding: 16, borderRadius: 12, border: '1px solid var(--border)', background: 'var(--bg)' }}>
      <h3 style={{ fontSize: 12, fontWeight: 700, color: 'var(--primary)' }}>{title}</h3>
      {fields.map((f) => (
        <Input key={f.placeholder} $small type={f.type} placeholder={f.placeholder} />
      ))}
      <Button $variant="primary" $size="sm" $block onClick={onSubmit}>
        {buttonLabel}
      </Button>
    </Stack>
  )
}
