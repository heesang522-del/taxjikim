// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { Button, Icon, KeyValue, Modal, TextField } from '../common/index';
import { maskEmail, maskId } from '../../utils/format';
import { useToast } from '../../store/useToastStore';
import {
  sendAccountLookupCode,
  sendPasswordResetCode,
  findLoginId,
  verifyEmail,
  resetPassword,
} from '../../api/userApi.js';
import { formatClock } from '../../utils/useVerifyCode';

/**
 * mode: 'id' (이름+이메일) | 'pw' (아이디+이메일). 이메일 인증을 통과하면
 * id는 결과를 알림 창으로 띄우고, pw는 비밀번호 재설정 화면으로 넘어간다.
 * onTab(tab)으로 다른 탭으로 이동한다.
 */
export function RecoveryForm({ mode, onTab }) {
  const toast = useToast();

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

  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [sentAt, setSentAt] = useState(null);
  const [now, setNow] = useState(Date.now);
  const secondsLeft = sentAt == null ? 0 : Math.max(0, 300 - Math.floor((now - sentAt) / 1000));
  const verifyCode = { expired: sentAt != null && secondsLeft === 0, secondsLeft };

  useEffect(() => {
    if (sentAt == null) return undefined;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [sentAt]);

  function handleIdentityChange(setValue) {
    return (event) => {
      setValue(event.target.value);
      // 이름·아이디·이메일이 바뀌면 이전 인증번호로 다음 단계에 진행하지 않는다.
      setSent(false);
      setSentAt(null);
      setCode('');
      setError('');
      setFieldErrors({});
    };
  }

  const handleSendCode = async () => {
    if (pending) return;
    if (!first.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setError('계정 정보와 이메일 형식을 확인하세요.');
      return;
    }
    setPending(true);
    setError('');
    setFieldErrors({});
    setSent(false);
    try {
      // 계정 소유 정보의 일치 여부는 localStorage가 아니라 서버에서 검사한다.
      if (isId) await sendAccountLookupCode(first.trim(), email.trim());
      else await sendPasswordResetCode(first.trim(), email.trim());
      const timestamp = Date.now();
      setNow(timestamp);
      setSentAt(timestamp);
      setSent(true);
      setCode('');
      toast('인증번호를 보냈습니다. 5분 안에 입력하세요.');
    } catch (cause) {
      setError(cause.message);
      setFieldErrors(cause.fields ?? {});
    } finally {
      setPending(false);
    }
  };

  const handleSubmit = async () => {
    if (pending) return;
    if (!first.trim() || !email.trim() || !/^\d{6}$/.test(code.trim())) {
      setError('계정 정보와 인증번호 6자리를 입력하세요.');
      return;
    }
    if (!sent || secondsLeft === 0) {
      setError('인증번호를 다시 받아 주세요.');
      return;
    }
    setPending(true);
    setError('');
    setFieldErrors({});
    try {
      if (isId) {
        const result = await findLoginId(first.trim(), email.trim(), code.trim());
        setFoundId(result.data);
        setFoundEmail(email.trim());
        setModal(true);
      } else {
        await verifyEmail(email.trim(), code.trim());
        setReset(true);
      }
    } catch (cause) {
      setError(cause.message);
      setFieldErrors(cause.fields ?? {});
    } finally {
      setPending(false);
    }
  };

  const handleChangePassword = async () => {
    if (pending) return;
    if (pw.length < 8 || pw !== pw2) {
      setError('새 비밀번호는 8자 이상이며 확인 값과 같아야 합니다.');
      return;
    }
    setPending(true);
    setError('');
    setFieldErrors({});
    try {
      // 서버가 인증 완료 기록과 계정 이메일을 다시 검사한 뒤 비밀번호를 변경한다.
      await resetPassword(first.trim(), email.trim(), pw);
      setChanged(true);
    } catch (cause) {
      setError(cause.message);
      setFieldErrors(cause.fields ?? {});
      if (cause.code === 'EMAIL_NOT_VERIFIED') {
        setReset(false);
        setSent(false);
      }
    } finally {
      setPending(false);
    }
  };
  if (reset) {
    return (
      <div className="grid" style={{ gap: 12 }}>
        {changed && (
          <Modal title="비밀번호 변경 완료" onClose={() => onTab?.('login')}>
            <div className="grid" style={{ gap: 16, textAlign: 'center', justifyItems: 'center' }}>
              <div className="icon-box">
                <Icon name="check" size={24} />
              </div>
              <div>
                <h3 style={{ fontSize: 18, marginBottom: 6 }}>비밀번호를 변경했습니다</h3>
                <p className="small muted">새 비밀번호로 로그인해 주세요.</p>
              </div>
              <Button variant="primary" size="lg" block autoFocus onClick={() => onTab?.('login')}>
                로그인하기
              </Button>
            </div>
          </Modal>
        )}
        <div className="grid" style={{ gap: 8, textAlign: 'center', justifyItems: 'center', marginBottom: 4 }}>
          <div className="icon-box">
            <Icon name="check" size={24} />
          </div>
          <h3 style={{ fontSize: 18 }}>비밀번호 재설정</h3>
          <p className="small muted">이메일 인증이 완료되었습니다. {first.trim()} 계정의 새 비밀번호를 입력하세요.</p>
        </div>
        <TextField
          id="pw4"
          error={fieldErrors.newPassword}
          label="새 비밀번호"
          type="password"
          value={pw}
          onChange={(e) => setPw(e.target.value)}
        />
        <TextField
          id="pw5"
          label="새 비밀번호 확인"
          type="password"
          value={pw2}
          onChange={(e) => setPw2(e.target.value)}
        />
        {error && (
          <p role="alert" className="small" style={{ color: 'var(--danger)' }}>
            {error}
          </p>
        )}
        <Button variant="primary" size="lg" onClick={handleChangePassword} disabled={pending}>
          비밀번호 변경
        </Button>
        <Button variant="link" onClick={() => setReset(false)}>
          이전으로
        </Button>
      </div>
    );
  }

  return (
    <>
      <div className="grid" style={{ gap: 12 }}>
        <TextField
          id={`${mode}1`}
          error={fieldErrors[isId ? 'name' : 'loginId']}
          label={isId ? '이름' : '아이디'}
          value={first}
          onChange={handleIdentityChange(setFirst)}
          disabled={pending}
        />
        <div className="row">
          <TextField
            id={`${mode}2`}
            error={fieldErrors.email}
            label="이메일"
            type="email"
            placeholder="name@example.com"
            value={email}
            onChange={handleIdentityChange(setEmail)}
            disabled={pending}
          />
          <Button onClick={handleSendCode} disabled={pending}>
            {sent ? '다시 받기' : '인증번호 받기'}
          </Button>
        </div>
        <TextField
          id={`${mode}3`}
          error={fieldErrors.code}
          label="인증번호"
          placeholder="6자리"
          value={code}
          onChange={(e) => setCode(e.target.value)}
        />
        {sent && (
          <p className="small" style={{ color: verifyCode.expired ? 'var(--danger)' : 'var(--text-muted)' }}>
            {verifyCode.expired
              ? '인증번호가 만료되었습니다. 다시 받아 주세요.'
              : `인증번호 유효시간 ${formatClock(verifyCode.secondsLeft)}`}
          </p>
        )}
        {error && (
          <p role="alert" className="small" style={{ color: 'var(--danger)' }}>
            {error}
          </p>
        )}
        <Button variant="primary" size="lg" onClick={handleSubmit} disabled={pending}>
          {pending ? '처리 중...' : isId ? '아이디 확인' : '인증 확인'}
        </Button>
      </div>
      {modal && (
        <Modal title="아이디 찾기 결과" onClose={() => setModal(false)}>
          <div className="grid" style={{ gap: 16, textAlign: 'center', justifyItems: 'center' }}>
            <div className="icon-box">
              <Icon name="check" size={24} />
            </div>
            <div>
              <h3 style={{ fontSize: 18, marginBottom: 6 }}>아이디를 찾았습니다</h3>
              <p className="small muted">{first.trim()}님의 가입 정보입니다.</p>
            </div>
            <div style={{ width: '100%', textAlign: 'left' }}>
              <KeyValue label="아이디" mono={false}>
                {maskId(foundId)}
              </KeyValue>
              <KeyValue label="가입 이메일" mono={false}>
                {maskEmail(foundEmail)}
              </KeyValue>
            </div>
            <div className="grid" style={{ gap: 8, width: '100%' }}>
              <Button variant="primary" size="lg" autoFocus onClick={() => onTab?.('login')}>
                로그인하기
              </Button>
              <Button size="lg" onClick={() => setModal(false)}>
                닫기
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}
