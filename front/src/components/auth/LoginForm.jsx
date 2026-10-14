// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { Button, TextField } from '../common/index';
import { useAuth, MAX_LOGIN_FAILS } from '../../store/useAuthStore';
import { formatClock } from '../../utils/useVerifyCode';

/** onSuccess()는 로그인에 성공했을 때, onBlocked()는 정지된 계정일 때, onTab(tab)은 회원가입·아이디 찾기·비밀번호 찾기 링크를 눌렀을 때 부른다. 실패하면 이유를 폼 아래에 보여준다. */
export default function LoginForm({ onSuccess, onBlocked, onTab }) {
  const { login, lockLeft } = useAuth();
  const [id, setId] = useState('');
  const [pw, setPw] = useState('');
  const [error, setError] = useState('');
  const [locked, setLocked] = useState(0); // 남은 잠금 초
  const [finalLock, setFinalLock] = useState(false); // 마지막(5분) 잠금이면 정지 경고를 보여준다

  // 잠금이 걸려 있는 동안 1초마다 남은 시간을 줄인다.
  useEffect(() => {
    if (!locked) return undefined;
    const t = setInterval(() => setLocked(lockLeft(id.trim())), 1000);
    return () => clearInterval(t);
  }, [locked, id, lockLeft]);

  const submit = (e) => {
    e.preventDefault();
    if (!id.trim() || !pw) { setError('아이디와 비밀번호를 입력하세요'); return; }
    const r = login(id, pw);
    if (r.ok) { onSuccess(); return; }
    if (r.reason === 'suspended') { onBlocked(); return; }
    if (r.reason === 'locked') {
      setLocked(r.secondsLeft);
      setFinalLock(Boolean(r.finalLock));
      setError(`로그인 실패가 ${MAX_LOGIN_FAILS}회를 넘어 잠시 잠겼습니다.`);
      return;
    }
    setLocked(0);
    setFinalLock(false);
    setError(r.reason === 'no-id'
      ? `존재하지 않는 아이디입니다. (${r.remaining > 0 ? `남은 시도 ${r.remaining}회` : '다음 실패 시 잠깁니다'})`
      : `비밀번호가 일치하지 않습니다. (${r.remaining > 0 ? `남은 시도 ${r.remaining}회` : '다음 실패 시 잠깁니다'})`);
  };

  return (
    <form className="grid" style={{ gap: 12 }} onSubmit={submit}>
      <TextField id="lid" label="아이디" value={id} onChange={(e) => setId(e.target.value)} autoComplete="username" />
      <TextField id="lpw" label="비밀번호" type="password" value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="current-password" />
      <div className="row" style={{ justifyContent: 'center', gap: 4 }}>
        <Button variant="link" size="sm" onClick={() => onTab('signup')}>회원가입</Button>
        <span className="muted small" aria-hidden="true">|</span>
        <Button variant="link" size="sm" onClick={() => onTab('findid')}>아이디 찾기</Button>
        <span className="muted small" aria-hidden="true">|</span>
        <Button variant="link" size="sm" onClick={() => onTab('findpw')}>비밀번호 찾기</Button>
      </div>
      {error && <p className="small" role="alert" style={{ color: 'var(--danger)' }}>{error}{locked > 0 && ` ${formatClock(locked)} 후 다시 시도하세요.`}{locked > 0 && finalLock && ' 이 잠금이 풀린 뒤에도 실패하면 계정이 정지됩니다.'}</p>}
      <Button type="submit" variant="primary" size="lg" disabled={locked > 0}>{locked > 0 ? `${formatClock(locked)} 후 다시 시도` : '로그인'}</Button>
      <p className="small muted">{MAX_LOGIN_FAILS}회를 넘겨 실패하면 30초, 1분, 3분, 5분 순서로 잠기고, 5분 잠금 뒤에도 실패하면 계정이 정지됩니다.</p>
    </form>
  );
}
