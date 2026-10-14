// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useState } from 'react';
import { Button, TextField } from '../common/index';
import { useAuth } from '../../store/useAuthStore';
import { useToast } from '../../store/useToastStore';
import { useVerifyCode, formatClock } from '../../utils/useVerifyCode';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_RE = /^01\d-?\d{3,4}-?\d{4}$/;
const DUP_LABEL = { id: '아이디', nickname: '닉네임', email: '이메일' };

/** onDone()은 가입이 끝났을 때 부른다 (완료 팝업 → 로그인 화면은 AuthPage가 처리). */
export default function SignupForm({ onDone }) {
  const toast = useToast();
  const { signup, findDuplicate } = useAuth();
  const code = useVerifyCode();
  const [f, setF] = useState({ name: '', nickname: '', id: '', birth: '', pw: '', pw2: '', email: '', code: '', phone: '' });
  const [imageName, setImageName] = useState('');
  const [verified, setVerified] = useState(false);
  const set = (k) => (e) => {
    setF((v) => ({ ...v, [k]: e.target.value }));
    if (k === 'email') setVerified(false);
  };

  const sendCode = () => {
    if (!EMAIL_RE.test(f.email)) { toast('이메일 형식을 확인하세요'); return; }
    const dup = findDuplicate({ id: '', nickname: '', email: f.email });
    if (dup === 'email') { toast('이미 가입된 이메일입니다'); return; }
    setVerified(false);
    setF((v) => ({ ...v, code: '' }));
    toast(`인증번호를 보냈습니다 (프로토타입: ${code.send()}) · 3분 안에 입력하세요`);
  };

  const checkCode = () => {
    const r = code.verify(f.code);
    if (r === 'none') { toast('먼저 인증번호를 받아 주세요'); return; }
    if (r === 'expired') { toast('인증번호가 만료되었습니다. 다시 받아 주세요'); return; }
    if (r === 'wrong') { toast('인증번호가 일치하지 않습니다'); return; }
    setVerified(true);
    toast('이메일 인증이 완료되었습니다');
  };

  const submit = (e) => {
    e.preventDefault();
    const required = [['이름', f.name], ['닉네임', f.nickname], ['아이디', f.id], ['생년월일', f.birth], ['비밀번호', f.pw], ['이메일', f.email]];
    const missing = required.find(([, v]) => !v.trim());
    if (missing) { toast(`${missing[0]}을(를) 입력하세요`); return; }
    if (!/^[a-z0-9_]{4,20}$/i.test(f.id)) { toast('아이디는 영문·숫자·_ 4~20자로 입력하세요'); return; }
    if (f.pw.length < 8) { toast('비밀번호는 8자 이상으로 입력하세요'); return; }
    if (f.pw !== f.pw2) { toast('비밀번호가 일치하지 않습니다'); return; }
    if (!EMAIL_RE.test(f.email)) { toast('이메일 형식을 확인하세요'); return; }
    if (f.phone.trim() && !PHONE_RE.test(f.phone.trim())) { toast('휴대폰 번호 형식을 확인하세요 (선택 항목이라 비워도 됩니다)'); return; }
    if (!verified) {
      toast(code.expired ? '인증번호가 만료되었습니다. 다시 받아 주세요' : '이메일 인증을 완료하세요');
      return;
    }
    const r = signup({ id: f.id.trim(), pw: f.pw, name: f.name.trim(), nickname: f.nickname.trim(), birth: f.birth, email: f.email.trim(), phone: f.phone.trim() });
    if (!r.ok) { toast(`이미 사용 중인 ${DUP_LABEL[r.field]}입니다`); return; }
    onDone();
  };

  return (
    <form className="grid" style={{ gap: 12 }} onSubmit={submit}>
      <div className="row">
        <div className="avatar" aria-hidden="true">+</div>
        <TextField id="su0" label="프로필 이미지 (선택)" type="file" accept="image/*" onChange={(e) => setImageName(e.target.files[0]?.name ?? '')} />
      </div>
      {imageName && <p className="small muted">선택한 이미지: {imageName}</p>}
      <div className="row"><TextField id="su1" label="이름" value={f.name} onChange={set('name')} /><TextField id="su2" label="닉네임" value={f.nickname} onChange={set('nickname')} /></div>
      <div className="row"><TextField id="su3" label="아이디" value={f.id} onChange={set('id')} autoComplete="username" /><TextField id="su4" label="생년월일" type="date" value={f.birth} onChange={set('birth')} /></div>
      <div className="row"><TextField id="su5" label="비밀번호 (8자 이상)" type="password" value={f.pw} onChange={set('pw')} autoComplete="new-password" /><TextField id="su6" label="비밀번호 확인" type="password" value={f.pw2} onChange={set('pw2')} autoComplete="new-password" /></div>
      <div className="row"><TextField id="su7" label="이메일" type="email" placeholder="name@example.com" value={f.email} onChange={set('email')} /><Button onClick={sendCode}>{code.sent ? '다시 받기' : '인증번호 받기'}</Button></div>
      <div className="row">
        <TextField id="su8" label="이메일 인증번호" placeholder="6자리" value={f.code} onChange={set('code')} disabled={verified} />
        <Button onClick={checkCode} disabled={verified}>{verified ? '인증 완료' : '인증 확인'}</Button>
      </div>
      {code.sent && !verified && (
        <p className="small" style={{ color: code.expired ? 'var(--danger)' : 'var(--text-muted)' }}>
          {code.expired ? '인증번호가 만료되었습니다. 다시 받아 주세요.' : `인증번호 유효시간 ${formatClock(code.secondsLeft)}`}
        </p>
      )}
      <details open>
        <summary className="muted small">선택 입력</summary>
        <div className="row" style={{ marginTop: 8 }}>
          <TextField id="su10" label="휴대폰 번호 (선택)" placeholder="010-0000-0000" value={f.phone} onChange={set('phone')} />
        </div>
      </details>
      <Button type="submit" variant="primary" size="lg">가입하기</Button>
    </form>
  );
}
