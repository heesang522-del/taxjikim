import styled from 'styled-components'
import Icon from '../Icon'
import { Badge, Button, Card, Row } from '../ui'

const Avatar = styled.div`
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: var(--primary-border);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;

  button {
    position: absolute;
    right: 0;
    bottom: 0;
    padding: 8px;
    border: none;
    border-radius: 50%;
    background: var(--primary);
    color: #fff;
    cursor: pointer;
    display: flex;
  }
`

export default function ProfileCard({ profile }) {
  return (
    <Card $pad={32}>
      <Row $gap={24} $wrap>
        <Avatar>
          <Icon name="user" size={48} />
          <button aria-label="프로필 사진 변경">
            <Icon name="camera" size={16} />
          </button>
        </Avatar>
        <div style={{ flex: 1, minWidth: 200 }}>
          <Row $gap={8}>
            <h2 style={{ fontSize: 24, fontWeight: 700 }}>{profile.name}</h2>
            <Badge>{profile.job}</Badge>
          </Row>
          <p style={{ fontSize: 14, color: 'var(--text-muted)' }}>
            {profile.userId} | {profile.email}
          </p>
          {profile.emailVerified && (
            <p style={{ fontSize: 12, color: 'var(--success)', fontWeight: 500 }}>✓ 이메일 인증 완료된 계정</p>
          )}
        </div>
        <Button $variant="dark" $size="sm" onClick={() => alert('회원 정보가 수정되었습니다.')}>
          변경사항 저장
        </Button>
      </Row>
    </Card>
  )
}
