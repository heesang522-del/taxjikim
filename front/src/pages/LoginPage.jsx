import { Link, useNavigate } from 'react-router-dom'
import AuthCard from '../components/auth/AuthCard'
import KakaoLoginButton from '../components/auth/KakaoLoginButton'
import { Button, Field, Input, Row, Stack } from '../components/ui'
import { useAuthStore } from '../store/useAuthStore'

export default function LoginPage() {
  const navigate = useNavigate()
  const login = useAuthStore((state) => state.login)

  // MOCK: 입력값을 검증하지 않고 로그인 상태만 true로 바꾼다
  function handleSubmit(e) {
    e.preventDefault()
    alert('로그인되었습니다. 대시보드로 이동합니다.')
    login()
    navigate('/')
  }

  const linkStyle = { color: 'var(--text-muted)', fontWeight: 600 }

  return (
    <AuthCard icon="logIn" title="로그인" description="TaxJikim 서비스에 오신 것을 환영합니다.">
      <form onSubmit={handleSubmit}>
        <Stack $gap={16}>
          <Field>
            <span>아이디</span>
            <Input type="text" required placeholder="userid123" />
          </Field>
          <Field>
            <span>비밀번호</span>
            <Input type="password" required placeholder="••••••••" />
          </Field>
          <Button type="submit" $variant="primary" $block>
            로그인
          </Button>
          <KakaoLoginButton />
        </Stack>
      </form>
      <Row $justify="space-between" style={{ paddingTop: 8, borderTop: '1px solid var(--border-soft)', fontSize: 12, color: 'var(--text-muted)' }}>
        <Button as={Link} to="/find-account" $variant="link" $size="sm" style={linkStyle}>
          아이디 찾기
        </Button>
        <span>|</span>
        <Button as={Link} to="/find-account" $variant="link" $size="sm" style={linkStyle}>
          비밀번호 찾기
        </Button>
        <span>|</span>
        <Button as={Link} to="/signup" $variant="link" $size="sm" style={linkStyle}>
          회원가입
        </Button>
      </Row>
    </AuthCard>
  )
}
