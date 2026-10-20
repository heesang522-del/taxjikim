// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import styled from 'styled-components';
import { Button, TextField } from '../common/index';
import { useAuth } from '../../store/useAuthStore';

// 로그인 성공 처리와 계정 화면 이동을 연결하고, 실패 안내는 폼 아래에 표시
export function LoginForm({ onSuccess, onTab }) {
  const { login } = useAuth();
  const [id, setId] = useState('');
  const [pw, setPw] = useState('');
  // 자동 로그인 선택값을 로그인 요청에 전달한다. 기본값은 미선택이다.
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [attempt, setAttempt] = useState(null);
  const [now, setNow] = useState(() => Date.now());
  const loginId = id.trim().toLowerCase();
  const currentAttempt = attempt?.loginId === loginId ? attempt : null;
  const secondsLeft = Math.max(0, Math.ceil(((currentAttempt?.deadline ?? 0) - now) / 1000));
  const locked = currentAttempt?.locked ?? false;
  const temporarilyBlocked = secondsLeft > 0;

  const applyAttempt = (targetId, details, permanentlyLocked = false) => {
    // 서버가 계산한 남은 시간을 기준으로 브라우저 시계 차이를 보정한다.
    const duration = details?.blockedUntil
      ? Math.max(0, Date.parse(details.blockedUntil) - Date.parse(details.serverTime))
      : 0;
    const receivedAt = Date.now();
    setNow(receivedAt);
    setAttempt({
      loginId: targetId,
      deadline: receivedAt + duration,
      locked: permanentlyLocked || Boolean(details?.locked),
      failedAttempts: details?.failedAttempts ?? 0,
    });
  };

  useEffect(() => {
    if (!temporarilyBlocked) return;
    // 카운트다운만 화면에서 갱신하며 매초 서버에 요청하지 않는다.
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [temporarilyBlocked]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting || secondsLeft > 0) return;
    if (!id.trim() || !pw) {
      setError('아이디와 비밀번호를 입력하세요');
      return;
    }
    setSubmitting(true);
    // 차단 상태는 아이디 입력 중 조회하지 않고 로그인 요청의 응답으로만 확인한다.
    setError('');
    try {
      // 서버 인증 결과로만 성공 여부를 판단하며 localStorage 계정은 검사하지 않는다.
      const result = await login(id, pw, rememberMe);
      if (result.ok) {
        onSuccess();
        return;
      }
      // 정지 계정도 별도 화면으로 이동하지 않고 서버의 안내 문구를 표시한다.
      setError(result.message || '로그인하지 못했습니다. 다시 시도해주세요.');
      if (result.details || result.code === 'LOCKED') applyAttempt(loginId, result.details, result.code === 'LOCKED');
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
        onChange={(e) => {
          setId(e.target.value);
          setError('');
        }}
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
      <RememberMeOption>
        <input
          type="checkbox"
          checked={rememberMe}
          onChange={(event) => setRememberMe(event.target.checked)}
          disabled={submitting}
        />
        <span>자동 로그인</span>
      </RememberMeOption>
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
      {secondsLeft > 0 && (
        <div className="small" role="status" style={{ color: 'var(--danger)' }}>
          <p>
            다시 시도까지 {String(Math.floor(secondsLeft / 60)).padStart(2, '0')}:
            {String(secondsLeft % 60).padStart(2, '0')}
          </p>
        </div>
      )}
      {!locked && currentAttempt?.failedAttempts === 9 && (
        <p className="small" role="status">
          다음 실패 시 계정이 잠기며 관리자 해제가 필요합니다.
        </p>
      )}
      <Button type="submit" variant="primary" size="lg" disabled={submitting || secondsLeft > 0}>
        {submitting ? '로그인 중...' : secondsLeft > 0 ? '잠시 후 다시 시도해주세요' : '로그인'}
      </Button>
    </form>
  );
}

const RememberMeOption = styled.label`
  display: inline-flex;
  align-items: center;
  justify-self: start;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid transparent;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s;

  &:hover {
    background: var(--primary-bg);
  }

  &:has(input:checked) {
    background: var(--primary-bg);
    border-color: var(--primary-border);
    color: var(--primary);
  }

  &:has(input:focus-visible) {
    outline: 2px solid var(--primary);
    outline-offset: 2px;
  }

  &:has(input:disabled) {
    opacity: 0.6;
    cursor: default;
  }

  input {
    width: 18px;
    height: 18px;
    margin: 0;
    accent-color: var(--primary);
    cursor: inherit;
  }
`;
