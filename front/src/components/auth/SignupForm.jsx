'use no memo';

import { useEffect, useState } from 'react';
import { Button, TextField } from '../common/index';
import { useToast } from '../../store/useToastStore';
import {
  checkLoginId,
  checkNickname,
  checkEmail,
  createUser,
  sendEmailVerification,
  verifyEmail,
} from '../../api/userApi.js';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CODE_TTL = 300;
const VERIFIED_TTL = 600;
const clock = (seconds) => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;

export function SignupForm({ onDone }) {
  const toast = useToast();
  const [form, setForm] = useState({
    name: '',
    nickname: '',
    loginId: '',
    birthDate: '',
    password: '',
    passwordConfirm: '',
    email: '',
    code: '',
  });
  const [profile, setProfile] = useState(null);
  const [pending, setPending] = useState('');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [sentAt, setSentAt] = useState(null);
  const [verifiedUntil, setVerifiedUntil] = useState(null);
  const [now, setNow] = useState(Date.now);
  const [checks, setChecks] = useState({});
  const busy = Boolean(pending);
  const verified = verifiedUntil != null && now < verifiedUntil;
  const secondsLeft = sentAt == null ? 0 : Math.max(0, CODE_TTL - Math.floor((now - sentAt) / 1000));

  useEffect(() => {
    if (sentAt == null && verifiedUntil == null) return undefined;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [sentAt, verifiedUntil]);

  function handleChange(key) {
    return (event) => {
      const value = event.target.value;
      setForm((previous) => ({ ...previous, [key]: value }));
      setError('');
      setFieldErrors({});
      // 검사한 값이 바뀌면 중복 확인과 해당 이메일의 인증 상태를 다시 확인해야 한다.
      setChecks((previous) => ({ ...previous, [key]: null }));
      if (key === 'email') {
        setSentAt(null);
        setVerifiedUntil(null);
        setForm((previous) => ({ ...previous, code: '' }));
      }
    };
  }

  async function handleDuplicateCheck(key) {
    const value = form[key].trim();
    if (!value || (key === 'loginId' && !/^[a-z0-9_]{4,20}$/i.test(value))) {
      setError(key === 'loginId' ? '아이디는 영문·숫자·_ 4~20자로 입력하세요.' : '닉네임을 입력하세요.');
      return;
    }
    setPending(key);
    setError('');
    setFieldErrors({});
    try {
      const result = await (key === 'loginId' ? checkLoginId(value) : checkNickname(value));
      setChecks((previous) => ({ ...previous, [key]: { value, duplicate: result.data } }));
      if (result.data) setError(`이미 사용 중인 ${key === 'loginId' ? '아이디' : '닉네임'}입니다.`);
      else toast('사용할 수 있습니다.');
    } catch (cause) {
      setError(cause.message);
      setFieldErrors(cause.fields ?? {});
    } finally {
      setPending('');
    }
  }

  async function handleSendCode() {
    const email = form.email.trim();
    if (!EMAIL_RE.test(email)) {
      setError('이메일 형식을 확인하세요.');
      return;
    }
    setPending('send');
    setError('');
    setFieldErrors({});
    setVerifiedUntil(null);
    setSentAt(null);
    try {
      if ((await checkEmail(email)).data) {
        setError('이미 가입된 이메일입니다.');
        return;
      }
      await sendEmailVerification(email);
      // 발송 성공 응답 이후에만 서버의 5분 유효시간에 맞춰 안내를 시작한다.
      const timestamp = Date.now();
      setNow(timestamp);
      setSentAt(timestamp);
      setForm((previous) => ({ ...previous, code: '' }));
      toast('인증번호를 보냈습니다. 5분 안에 입력하세요.');
    } catch (cause) {
      setError(cause.message);
      setFieldErrors(cause.fields ?? {});
    } finally {
      setPending('');
    }
  }

  async function handleVerifyCode() {
    if (sentAt == null || secondsLeft === 0) {
      setError('인증번호를 다시 받아 주세요.');
      return;
    }
    if (!/^\d{6}$/.test(form.code.trim())) {
      setError('인증번호 6자리를 입력하세요.');
      return;
    }
    setPending('verify');
    setError('');
    setFieldErrors({});
    try {
      await verifyEmail(form.email.trim(), form.code.trim());
      const timestamp = Date.now();
      setNow(timestamp);
      setVerifiedUntil(timestamp + VERIFIED_TTL * 1000);
      toast('이메일 인증이 완료되었습니다. 10분 안에 가입을 완료해주세요.');
    } catch (cause) {
      setError(cause.message);
      setFieldErrors(cause.fields ?? {});
    } finally {
      setPending('');
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (busy) return;
    const user = {
      loginId: form.loginId.trim(),
      password: form.password,
      name: form.name.trim(),
      nickname: form.nickname.trim(),
      email: form.email.trim(),
      birthDate: form.birthDate || null,
    };
    if (!user.name || !user.nickname || !user.loginId || !user.password || !user.email) {
      setError('필수 항목을 입력하세요.');
      return;
    }
    if (!/^[a-z0-9_]{4,20}$/i.test(user.loginId)) {
      setError('아이디는 영문·숫자·_ 4~20자로 입력하세요.');
      return;
    }
    if (user.password.length < 8) {
      setError('비밀번호는 8자 이상으로 입력하세요.');
      return;
    }
    if (user.password !== form.passwordConfirm) {
      setError('비밀번호가 일치하지 않습니다.');
      return;
    }
    if (!EMAIL_RE.test(user.email)) {
      setError('이메일 형식을 확인하세요.');
      return;
    }
    if (!verified || Date.now() >= verifiedUntil) {
      setError('이메일 인증을 완료하거나 다시 인증해주세요.');
      return;
    }
    setPending('signup');
    setError('');
    setFieldErrors({});
    try {
      // 가입 직전에 서버에서 중복을 다시 검사하며, 최종 동시성 검사는 DB UNIQUE 제약이 담당한다.
      const results = await Promise.all([
        checkLoginId(user.loginId),
        checkNickname(user.nickname),
        checkEmail(user.email),
      ]);
      const duplicate = results.findIndex((result) => result.data);
      if (duplicate !== -1) {
        setError(`이미 사용 중인 ${['아이디', '닉네임', '이메일'][duplicate]}입니다.`);
        return;
      }
      await createUser(user, profile);
      onDone();
    } catch (cause) {
      if (cause.code === 'EMAIL_NOT_VERIFIED') setVerifiedUntil(null);
      setError(cause.message);
      setFieldErrors(cause.fields ?? {});
    } finally {
      setPending('');
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <fieldset className="grid" disabled={busy} style={{ gap: 12, border: 0, padding: 0, margin: 0, minWidth: 0 }}>
        <TextField
          id="su-profile"
          label="프로필 이미지 (선택)"
          type="file"
          accept="image/*"
          onChange={(event) => setProfile(event.target.files?.[0] ?? null)}
        />
        {profile && <p className="small muted">선택한 이미지: {profile.name}</p>}
        <TextField
          id="su-name"
          error={fieldErrors.name}
          label="이름"
          value={form.name}
          onChange={handleChange('name')}
          maxLength={50}
          required
        />
        <div className="row">
          <TextField
            id="su-nickname"
            error={fieldErrors.nickname}
            label="닉네임"
            value={form.nickname}
            onChange={handleChange('nickname')}
            maxLength={50}
            required
          />
          <Button onClick={() => handleDuplicateCheck('nickname')}>중복 확인</Button>
        </div>
        {checks.nickname && !checks.nickname.duplicate && <p className="small">사용 가능한 닉네임입니다.</p>}
        <div className="row">
          <TextField
            id="su-loginId"
            error={fieldErrors.loginId}
            label="아이디"
            value={form.loginId}
            onChange={handleChange('loginId')}
            autoComplete="username"
            maxLength={20}
            required
          />
          <Button onClick={() => handleDuplicateCheck('loginId')}>중복 확인</Button>
        </div>
        {checks.loginId && !checks.loginId.duplicate && <p className="small">사용 가능한 아이디입니다.</p>}
        <TextField
          id="su-birth"
          error={fieldErrors.birthDate}
          label="생년월일 (선택)"
          type="date"
          value={form.birthDate}
          onChange={handleChange('birthDate')}
        />
        <TextField
          id="su-password"
          error={fieldErrors.password}
          label="비밀번호 (8자 이상)"
          type="password"
          value={form.password}
          onChange={handleChange('password')}
          autoComplete="new-password"
          required
        />
        <TextField
          id="su-confirm"
          label="비밀번호 확인"
          type="password"
          value={form.passwordConfirm}
          onChange={handleChange('passwordConfirm')}
          autoComplete="new-password"
          required
        />
        <div className="row">
          <TextField
            id="su-email"
            error={fieldErrors.email}
            label="이메일"
            type="email"
            value={form.email}
            onChange={handleChange('email')}
            maxLength={255}
            required
          />
          <Button onClick={handleSendCode}>
            {pending === 'send' ? '발송 중...' : sentAt == null ? '인증번호 받기' : '다시 받기'}
          </Button>
        </div>
        <div className="row">
          <TextField
            id="su-code"
            error={fieldErrors.code}
            label="이메일 인증번호"
            value={form.code}
            onChange={handleChange('code')}
            inputMode="numeric"
            maxLength={6}
            disabled={verified}
          />
          <Button onClick={handleVerifyCode} disabled={verified}>
            {verified ? '인증 완료' : '인증 확인'}
          </Button>
        </div>
        {sentAt != null && !verified && (
          <p className="small muted">
            {secondsLeft > 0
              ? `인증번호 유효시간 ${clock(secondsLeft)}`
              : '인증번호가 만료되었습니다. 다시 받아 주세요.'}
          </p>
        )}
        {error && (
          <p className="small" role="alert" style={{ color: 'var(--danger)' }}>
            {error}
          </p>
        )}
        <Button type="submit" variant="primary" size="lg">
          {pending === 'signup' ? '가입 중...' : '가입하기'}
        </Button>
      </fieldset>
    </form>
  );
}
