import { Link, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import AuthCard from '../components/auth/AuthCard';
import KakaoLoginButton from '../components/auth/KakaoLoginButton';
import { Button, Field, Input, Row, Stack } from '../components/ui';
import { useAuthStore } from '../store/useAuthStore';

export function LoginPage() {
  const navigate = useNavigate();
  const login = useAuthStore((state) => state.login);
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  async function handleSubmit(e) {
    e.preventDefault();
    if (isSubmitting) return;
    setIsSubmitting(true);
    setErrorMessage('');
    try {
      // 백엔드가 인증한 경우에만 로그인 상태를 저장하고 홈으로 이동한다.
      await login(loginId.trim(), password);
      navigate('/');
    } catch (error) {
      setErrorMessage(error.message || '로그인하지 못했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setIsSubmitting(false);
    }
  }

  const linkStyle = { color: 'var(--text-muted)', fontWeight: 600 };

  return (
    <AuthCard icon="logIn" title="로그인" description="TaxJikim 서비스에 오신 것을 환영합니다.">
      <form onSubmit={handleSubmit}>
        <Stack $gap={16}>
          <Field>
            <span>아이디</span>
            <Input
              name="loginId"
              type="text"
              autoComplete="username"
              required
              placeholder="userid123"
              value={loginId}
              onChange={(event) => setLoginId(event.target.value)}
              disabled={isSubmitting}
            />
          </Field>
          <Field>
            <span>비밀번호</span>
            <Input
              name="password"
              type="password"
              autoComplete="current-password"
              required
              placeholder="••••••••"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              disabled={isSubmitting}
            />
          </Field>
          {errorMessage && <p role="alert">{errorMessage}</p>}
          <Button type="submit" $variant="primary" $block disabled={isSubmitting}>
            {isSubmitting ? '로그인 중...' : '로그인'}
          </Button>
          <KakaoLoginButton />
        </Stack>
      </form>
      <Row
        $justify="space-between"
        style={{ paddingTop: 8, borderTop: '1px solid var(--border-soft)', fontSize: 12, color: 'var(--text-muted)' }}
      >
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
  );
}
