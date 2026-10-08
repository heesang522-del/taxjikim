import Icon from '../Icon'
import { Card, CardTitle, Field, Input, Stack } from '../ui'

export default function ProfileEditCard({ profile }) {
  return (
    <Card>
      <Stack $gap={16}>
        <CardTitle>
          <Icon name="sliders" size={16} /> 기본 정보 수정
        </CardTitle>
        <Field>
          <span>닉네임</span>
          <Input type="text" defaultValue={profile.nickname} />
        </Field>
        <Field>
          <span>이메일 주소</span>
          <Input type="email" defaultValue={profile.email} />
        </Field>
      </Stack>
    </Card>
  )
}
