import Icon from '../Icon'
import { Button, Card, CardTitle, DividedList, Row, Stack } from '../ui'

export default function UserManagement({ users }) {
  return (
    <Card>
      <Stack $gap={16}>
        <CardTitle>
          <Icon name="users" size={16} /> 회원 정보 및 그룹 관리 / 밴(Ban)
        </CardTitle>
        <DividedList>
          {users.map((u) => (
            <Row key={u.id} $justify="space-between" style={{ padding: '12px 0', fontSize: 12 }}>
              <div>
                <span style={{ fontWeight: 700 }}>{u.name}</span>
                <p style={{ color: 'var(--text-faint)' }}>{u.desc}</p>
              </div>
              <Row $gap={8}>
                <Button $variant="soft" $size="sm" onClick={() => alert(u.groupMsg)}>
                  그룹변경
                </Button>
                <Button $variant="danger" $size="sm" onClick={() => alert(u.banMsg)}>
                  차단(Ban)
                </Button>
              </Row>
            </Row>
          ))}
        </DividedList>
      </Stack>
    </Card>
  )
}
