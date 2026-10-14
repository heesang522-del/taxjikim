// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useState } from 'react';
import { Button, Icon, KeyValue, Modal, TextField } from '../common/index';
import { maskEmail, maskId } from '../../utils/format';
import { useToast } from '../../store/useToastStore';
import { useAuth } from '../../store/useAuthStore';
import { useVerifyCode, formatClock } from '../../utils/useVerifyCode';

/**
 * mode: 'id' (이름+이메일) | 'pw' (아이디+이메일). 이메일 인증을 통과하면
 * id는 결과를 알림 창으로 띄우고, pw는 비밀번호 재설정 화면으로 넘어간다.
 * onTab(tab)으로 다른 탭으로 이동한다.
 */
export default function RecoveryForm({ mode, onTab }) {
  const toast = useToast();
  const { findIdByNameEmail, hasIdEmail, resetPassword } = useAuth();
  const verifyCode = useVerifyCode();
  const [foundId, setFoundId] = useState('');
  const [foundEmail, setFoundEmail] = useState('');
  const isId = mode === 'id';
  const [first, setFirst] = useState('');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [sent, setSent] = useState(false); // 인증번호를 받았는지
  const [modal, setModal] = useState(false); // id: 결과 알림 창
  const [reset, setReset] = useState(false); // pw: 재설정 화면
  const [changed, setChanged] = useState(false); // pw: 변경 성공 알림 창
  const [pw, setPw] = useState('');
  const [pw2, setPw2] = useState('');

  const sendCode = () => {
    if (!email.includes('@')) { toast('이메일 형식을 확인하세요'); return; }
    setSent(true);
    setCode('');
    toast(`인증번호를 보냈습니다 (프로토타입: ${verifyCode.send()}) · 3분 안에 입력하세요`);
  };

  // 입력 검사 → 계정 확인 → 인증번호 확인 순서로 실패 이유를 안내한다.
  const verified = () => {
    if (!first.trim() || !email.trim() || !code.trim()) { toast('모든 항목을 입력하세요'); return false; }
    if (!email.includes('@')) { toast('이메일 형식을 확인하세요'); return false; }
    if (!sent) { toast('먼저 인증번호를 받아 주세요'); return false; }
    const r = verifyCode.verify(code);
    if (r === 'expired') { toast('인증번호가 만료되었습니다. 다시 받아 주세요'); return false; }
    if (r === 'wrong') { toast('인증번호가 일치하지 않습니다'); return false; }
    return true;
  };

  const submit = () => {
    if (!verified()) return;
    if (isId) {
      const account = findIdByNameEmail(first, email);
      if (!account) { toast('이름 또는 이메일이 가입 정보와 일치하지 않습니다'); return; }
      setFoundId(account.id);
      setFoundEmail(account.email);
      setModal(true);
    } else {
      if (!hasIdEmail(first, email)) { toast('아이디 또는 이메일이 가입 정보와 일치하지 않습니다'); return; }
      setReset(true);
    }
  };

  const changePassword = () => {
    if (!pw || !pw2) { toast('새 비밀번호를 입력하세요'); return; }
    if (pw !== pw2) { toast('비밀번호가 일치하지 않습니다'); return; }
    if (pw.length < 8) { toast('비밀번호는 8자 이상으로 입력하세요'); return; }
    resetPassword(first, pw);
    setChanged(true);
  };

  if (reset) {
    return (
      <div className="grid" style={{ gap: 12 }}>
        {changed && (
          <Modal title="비밀번호 변경 완료" onClose={() => onTab?.('login')}>
            <div className="grid" style={{ gap: 16, textAlign: 'center', justifyItems: 'center' }}>
              <div className="icon-box"><Icon name="check" size={24} /></div>
              <div>
                <h3 style={{ fontSize: 18, marginBottom: 6 }}>비밀번호를 변경했습니다</h3>
                <p className="small muted">새 비밀번호로 로그인해 주세요.</p>
              </div>
              <Button variant="primary" size="lg" block autoFocus onClick={() => onTab?.('login')}>로그인하기</Button>
            </div>
          </Modal>
        )}
        <div className="grid" style={{ gap: 8, textAlign: 'center', justifyItems: 'center', marginBottom: 4 }}>
          <div className="icon-box"><Icon name="check" size={24} /></div>
          <h3 style={{ fontSize: 18 }}>비밀번호 재설정</h3>
          <p className="small muted">이메일 인증이 완료되었습니다. {first.trim()} 계정의 새 비밀번호를 입력하세요.</p>
        </div>
        <TextField id="pw4" label="새 비밀번호" type="password" value={pw} onChange={(e) => setPw(e.target.value)} />
        <TextField id="pw5" label="새 비밀번호 확인" type="password" value={pw2} onChange={(e) => setPw2(e.target.value)} />
        <Button variant="primary" size="lg" onClick={changePassword}>비밀번호 변경</Button>
        <Button variant="link" onClick={() => setReset(false)}>이전으로</Button>
      </div>
    );
  }

  return (
    <>
      <div className="grid" style={{ gap: 12 }}>
        <TextField id={`${mode}1`} label={isId ? '이름' : '아이디'} value={first} onChange={(e) => setFirst(e.target.value)} />
        <div className="row">
          <TextField id={`${mode}2`} label="이메일" type="email" placeholder="name@example.com" value={email} onChange={(e) => setEmail(e.target.value)} />
          <Button onClick={sendCode}>{sent ? '다시 받기' : '인증번호 받기'}</Button>
        </div>
        <TextField id={`${mode}3`} label="인증번호" placeholder="6자리" value={code} onChange={(e) => setCode(e.target.value)} />
        {sent && (
          <p className="small" style={{ color: verifyCode.expired ? 'var(--danger)' : 'var(--text-muted)' }}>
            {verifyCode.expired ? '인증번호가 만료되었습니다. 다시 받아 주세요.' : `인증번호 유효시간 ${formatClock(verifyCode.secondsLeft)}`}
          </p>
        )}
        <Button variant="primary" size="lg" onClick={submit}>
          {isId ? '아이디 확인' : '인증 확인'}
        </Button>
      </div>
      {modal && (
        <Modal title="아이디 찾기 결과" onClose={() => setModal(false)}>
          <div className="grid" style={{ gap: 16, textAlign: 'center', justifyItems: 'center' }}>
            <div className="icon-box"><Icon name="check" size={24} /></div>
            <div>
              <h3 style={{ fontSize: 18, marginBottom: 6 }}>아이디를 찾았습니다</h3>
              <p className="small muted">{first.trim()}님의 가입 정보입니다.</p>
            </div>
            <div style={{ width: '100%', textAlign: 'left' }}>
              <KeyValue label="아이디" mono={false}>{maskId(foundId)}</KeyValue>
              <KeyValue label="가입 이메일" mono={false}>{maskEmail(foundEmail)}</KeyValue>
            </div>
            <div className="grid" style={{ gap: 8, width: '100%' }}>
              <Button variant="primary" size="lg" autoFocus onClick={() => onTab?.('login')}>로그인하기</Button>
              <Button size="lg" onClick={() => setModal(false)}>닫기</Button>
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}
