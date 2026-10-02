import Icon from '../Icon'
import { Card, IconBox, Stack } from '../ui'

// 회원가입 / 로그인 / 계정 찾기 공통 카드: 아이콘 + 제목 + 설명 + 내용
export default function AuthCard({ width = 448, icon, title, description, children }) {
  return (
    <Card style={{ maxWidth: width, margin: '0 auto' }} $pad={32}>
      <Stack $gap={24}>
        <div style={{ textAlign: 'center', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
          {icon && (
            <IconBox>
              <Icon name={icon} size={24} />
            </IconBox>
          )}
          <h2 style={{ fontSize: icon ? 24 : 20, fontWeight: 700 }}>{title}</h2>
          <p style={{ fontSize: icon ? 14 : 12, color: 'var(--text-muted)' }}>{description}</p>
        </div>
        {children}
      </Stack>
    </Card>
  )
}
