import { Link, useNavigate } from 'react-router-dom'
import AuthCard from '../components/auth/AuthCard'
import { Button, Field, Grid, Input, Row, Select, Stack } from '../components/ui'

const JOB_OPTIONS = [
  '직군을 선택하세요',
  '개발자 (소프트웨어/웹/앱)',
  '크리에이터 (유튜버/스트리머)',
  '디자이너 (UI/UX, 일러스트 등)',
  '기타 프리랜서',
]

const SectionLabel = ({ children, color = 'var(--primary)' }) => (
  <h3 style={{ fontSize: 12, fontWeight: 700, color, letterSpacing: '0.05em' }}>{children}</h3>
)

export default function SignupPage() {
  const navigate = useNavigate()

  // MOCK: 서버 없이 완료 안내만 하고 로그인 화면으로 이동
  function handleSubmit(e) {
    e.preventDefault()
    alert('회원가입이 성공적으로 완료되었습니다! 로그인 페이지로 이동합니다.')
    navigate('/login')
  }

  return (
    <AuthCard
      width={576}
      icon="userPlus"
      title="회원가입"
      description="프리랜서 및 크리에이터 맞춤형 세무 서비스를 시작하세요."
    >
      <form onSubmit={handleSubmit}>
        <Stack $gap={16}>
          <Stack $gap={12}>
            <SectionLabel>필수 입력 정보</SectionLabel>
            <Field>
              <span>이름 *</span>
              <Input type="text" required placeholder="홍길동" />
            </Field>
            <Grid $cols={2} $gap={8}>
              <Field>
                <span>아이디 *</span>
                <Input type="text" required placeholder="userid123" />
              </Field>
              <Field>
                <span>닉네임 *</span>
                <Input type="text" required placeholder="절세왕프리랜서" />
              </Field>
            </Grid>
            <Grid $cols={2} $gap={8}>
              <Field>
                <span>비밀번호 *</span>
                <Input type="password" required placeholder="••••••••" />
              </Field>
              <Field>
                <span>비밀번호 확인 *</span>
                <Input type="password" required placeholder="••••••••" />
              </Field>
            </Grid>
            <Field>
              <span>생년월일 *</span>
              <Input type="date" required />
            </Field>
            <Row $align="flex-end" $gap={8}>
              <Field style={{ flex: 1 }}>
                <span>이메일 *</span>
                <Input type="email" required placeholder="user@example.com" />
              </Field>
              <Button type="button" $size="sm" style={{ padding: '11px 16px' }} onClick={() => alert('인증번호가 발송되었습니다.')}>
                이메일 인증
              </Button>
            </Row>
            <Field>
              <span>프로필 이미지</span>
              <input type="file" style={{ fontSize: 12 }} />
            </Field>
          </Stack>

          <Stack $gap={12} style={{ paddingTop: 8, borderTop: '1px solid var(--border-soft)' }}>
            <SectionLabel color="var(--text-muted)">선택 입력 정보</SectionLabel>
            <Field>
              <span>직군 카테고리</span>
              <Select>
                {JOB_OPTIONS.map((o) => (
                  <option key={o}>{o}</option>
                ))}
              </Select>
            </Field>
            <Field>
              <span>주소</span>
              <Input type="text" placeholder="서울특별시 강남구 테헤란로 123" />
            </Field>
            <Field>
              <span>휴대폰 번호</span>
              <Input type="tel" placeholder="010-0000-0000" />
            </Field>
          </Stack>

          <Button type="submit" $variant="primary" $block $size="lg">
            회원가입 완료
          </Button>
        </Stack>
      </form>
      <p style={{ textAlign: 'center', fontSize: 12, color: 'var(--text-muted)' }}>
        이미 계정이 있으신가요?{' '}
        <Button as={Link} to="/login" $variant="link" $size="sm" style={{ color: 'var(--primary)' }}>
          로그인
        </Button>
      </p>
    </AuthCard>
  )
}
