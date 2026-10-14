// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useState } from 'react';
import { Button, TextField } from '../common/index';
import { useAuth } from '../../store/useAuthStore';

/** onSuccess()는 로그인에 성공했을 때, onBlocked()는 정지된 계정일 때, onTab(tab)은 회원가입·아이디 찾기·비밀번호 찾기 링크를 눌렀을 때 부른다. 실패하면 이유를 폼 아래에 보여준다. */
export function LoginForm({ onSuccess, onBlocked, onTab }) {
  const { login } = useAuth();
  const [id, setId] = useState('');
  const [pw, setPw] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return;
    if (!id.trim() || !pw) {
      setError('아이디와 비밀번호를 입력하세요');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      // 서버 인증 결과로만 성공 여부를 판단하며 localStorage 계정은 검사하지 않는다.
      const result = await login(id, pw);
      if (result.ok) {
        onSuccess();
        return;
      }
      if (result.reason === 'suspended') {
        onBlocked();
        return;
      }
      setError(result.message || '로그인하지 못했습니다. 다시 시도해주세요.');
    } catch {
      setError('로그인하지 못했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="grid" style={{ gap: 12 }} onSubmit={handleSubmit}>
      <TextField
        id="lid"
        label="아이디"
        value={id}
        onChange={(e) => setId(e.target.value)}
        autoComplete="username"
        disabled={submitting}
      />
      <TextField
        id="lpw"
        label="비밀번호"
        type="password"
        value={pw}
        onChange={(e) => setPw(e.target.value)}
        autoComplete="current-password"
        disabled={submitting}
      />
      <div className="row" style={{ justifyContent: 'center', gap: 4 }}>
        <Button variant="link" size="sm" onClick={() => onTab('signup')}>
          회원가입
        </Button>
        <span className="muted small" aria-hidden="true">
          |
        </span>
        <Button variant="link" size="sm" onClick={() => onTab('findid')}>
          아이디 찾기
        </Button>
        <span className="muted small" aria-hidden="true">
          |
        </span>
        <Button variant="link" size="sm" onClick={() => onTab('findpw')}>
          비밀번호 찾기
        </Button>
      </div>
      {error && (
        <p className="small" role="alert" style={{ color: 'var(--danger)' }}>
          {error}
        </p>
      )}
      <Button type="submit" variant="primary" size="lg" disabled={submitting}>
        {submitting ? '로그인 중...' : '로그인'}
      </Button>
    </form>
  );
}
