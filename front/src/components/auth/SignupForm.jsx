'use no memo';

import { useEffect, useRef, useState } from 'react';
import styled from 'styled-components';
import { getPasswordError } from '../../utils/passwordValidation';
import { getEmailError, getLoginIdError } from '../../utils/userValidation';
import { Button, TextField } from '../common/index';
import { SignupProfileImage } from './SignupProfileImage';
import { useToast } from '../../store/useToastStore';
import {
  checkLoginId,
  checkNickname,
  checkEmail,
  createUser,
  sendEmailVerification,
  verifyEmail,
} from '../../api/userApi.js';

const CODE_TTL = 300;
const VERIFIED_TTL = 600;
const clock = (seconds) => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;

function getFieldError(key, values) {
  if (key === 'loginId') return getLoginIdError(values.loginId);
  if (key === 'password') return getPasswordError(values.password);
  if (key === 'passwordConfirm' && (!values.passwordConfirm || values.password !== values.passwordConfirm))
    return '비밀번호가 일치하지 않습니다.';
  if (key === 'name' && !values.name.trim()) return '이름을 입력해주세요.';
  if (key === 'email') return getEmailError(values.email);
  if (key === 'nickname' && !values.nickname.trim()) return '닉네임을 입력해주세요.';
  return '';
}

export function SignupForm({ onDone }) {
  const toast = useToast();
  const formRef = useRef(null);
  const focusTarget = useRef(null);
  const duplicateInputVersions = useRef({ loginId: 0, nickname: 0 });
  const duplicateRequestId = useRef(0);
  const emailInputVersion = useRef(0);
  const [step, setStep] = useState(1);
  const [form, setForm] = useState({
    name: '',
    nickname: '',
    loginId: '',
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
  const [verifiedEmail, setVerifiedEmail] = useState(null);
  const [sentEmail, setSentEmail] = useState(null);
  const [now, setNow] = useState(Date.now);
  const [checks, setChecks] = useState({});
  const busy = Boolean(pending);
  // 이메일 잠금은 인증 만료와 별개로 유지하고, 인증 유효성은 주소와 만료 시각을 함께 확인한다.
  const emailLocked = verifiedEmail != null;
  const verified = verifiedEmail === form.email.trim() && verifiedUntil != null && now < verifiedUntil;
  const secondsLeft = sentAt == null ? 0 : Math.max(0, CODE_TTL - Math.floor((now - sentAt) / 1000));

  useEffect(() => {
    // 단계 전환이나 요청 완료 후 입력칸이 화면에 나타나고 활성화된 다음 포커스를 옮긴다.
    if (!pending && focusTarget.current) {
      formRef.current?.querySelector(`#${focusTarget.current}`)?.focus();
      focusTarget.current = null;
    }
  }, [step, pending]);

  useEffect(() => {
    if (sentAt == null && verifiedUntil == null) return undefined;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [sentAt, verifiedUntil]);

  function handleChange(key) {
    return (event) => {
      // 조합 입력·붙여넣기도 허용 문자만 남기고, 화면 입력값과 React 상태를 함께 맞춘다.
      const value =
        key === 'loginId' ? event.target.value.replace(/[^a-zA-Z0-9_]/g, '').slice(0, 20) : event.target.value;
      if (key === 'loginId') {
        event.target.value = value;
        // 막힌 문자만 입력했다면 실제 아이디는 그대로이므로 기존 중복 확인을 유지한다.
        if (value === form.loginId) return;
      }
      if (key === 'email' && emailLocked) return;
      const needsDuplicateCheck = key === 'loginId' || key === 'nickname';
      // 원래 값으로 되돌려도 이전 응답을 재사용하지 않도록 입력 변경 횟수를 기록한다.
      if (needsDuplicateCheck) duplicateInputVersions.current[key] += 1;
      const nextForm = { ...form, [key]: value };
      if (key === 'email') nextForm.code = '';
      setForm(nextForm);
      setError('');
      // 한 번 표시한 오류는 수정한 항목만 다시 검사하고, 다른 항목의 안내는 유지한다.
      setFieldErrors((previous) => {
        const next = { ...previous };
        if (needsDuplicateCheck) {
          next[key] =
            getFieldError(key, nextForm) || `${key === 'loginId' ? '아이디' : '닉네임'} 중복 확인이 필요합니다.`;
        } else if (Object.hasOwn(previous, key)) {
          next[key] = getFieldError(key, nextForm);
          if (key === 'email' && !next[key]) next[key] = '이메일 인증을 완료해주세요.';
        }
        if (key === 'password' && Object.hasOwn(previous, 'passwordConfirm'))
          next.passwordConfirm = getFieldError('passwordConfirm', nextForm);
        if (key === 'email') delete next.code;
        return next;
      });
      // 검사한 값이 바뀌면 중복 확인과 해당 이메일의 인증 상태를 다시 확인해야 한다.
      setChecks((previous) => ({ ...previous, [key]: null }));
      if (key === 'email') {
        emailInputVersion.current += 1;
        setSentAt(null);
        setSentEmail(null);
        setVerifiedUntil(null);
      }
    };
  }

  function handleLoginIdBeforeInput(event) {
    // 입력 전에 차단 가능한 문자는 미리 막고, 취소할 수 없는 IME 입력은 onChange에서 정리한다.
    const text = event.data ?? event.nativeEvent.data;
    if (text && /[^a-zA-Z0-9_]/.test(text)) event.preventDefault();
  }

  function handleChangeEmail() {
    if (busy || !window.confirm('이메일을 변경하면 다시 인증해야 합니다. 변경할까요?')) return;
    // 명시적으로 변경을 확인한 경우에만 잠금을 풀고 이전 인증 정보를 모두 버린다.
    emailInputVersion.current += 1;
    setVerifiedEmail(null);
    setVerifiedUntil(null);
    setSentEmail(null);
    setSentAt(null);
    setForm((previous) => ({ ...previous, code: '' }));
    setFieldErrors((previous) => ({ ...previous, email: '이메일 인증을 완료해주세요.', code: '' }));
    setError('');
    formRef.current?.querySelector('#su-email')?.focus();
  }

  function isDuplicateConfirmed(key) {
    const checked = checks[key];
    return checked?.value === form[key].trim() && checked.duplicate === false;
  }

  async function handleDuplicateCheck(key, nextInputId) {
    if (busy) return;
    const value = form[key].trim();
    const validationError = getFieldError(key, form);
    if (validationError) {
      setFieldErrors((previous) => ({ ...previous, [key]: validationError }));
      return;
    }
    const inputVersion = duplicateInputVersions.current[key];
    const requestId = ++duplicateRequestId.current;
    // 요청 시작 후 값이 한 번이라도 변경되면 성공·실패 안내와 포커스 이동을 모두 무시한다.
    const isCurrentRequest = () =>
      requestId === duplicateRequestId.current && inputVersion === duplicateInputVersions.current[key];
    setPending(key);
    setChecks((previous) => ({ ...previous, [key]: null }));
    setError('');
    setFieldErrors((previous) => ({ ...previous, [key]: '' }));
    try {
      const result = await (key === 'loginId' ? checkLoginId(value) : checkNickname(value));
      if (!isCurrentRequest()) return;
      setChecks((previous) => ({ ...previous, [key]: { value, duplicate: result.data } }));
      if (result.data) {
        setFieldErrors((previous) => ({
          ...previous,
          [key]: `이미 사용 중인 ${key === 'loginId' ? '아이디' : '닉네임'}입니다.`,
        }));
      } else {
        toast('사용할 수 있습니다.');
        if (nextInputId) focusTarget.current = nextInputId;
      }
      return !result.data;
    } catch (cause) {
      if (!isCurrentRequest()) return;
      setError(cause.message);
      setFieldErrors((previous) => ({ ...previous, ...cause.fields }));
    } finally {
      if (requestId === duplicateRequestId.current) setPending('');
    }
  }

  async function handleSendCode() {
    if (busy || verified) return;
    const email = form.email.trim();
    const inputVersion = emailInputVersion.current;
    const emailError = getEmailError(email);
    if (emailError) {
      setFieldErrors((previous) => ({ ...previous, email: emailError }));
      return;
    }
    setPending('send');
    setError('');
    setFieldErrors((previous) => ({ ...previous, code: '' }));
    setVerifiedUntil(null);
    setSentAt(null);
    try {
      const result = await checkEmail(email);
      if (inputVersion !== emailInputVersion.current) return;
      if (result.data) {
        setError('이미 가입된 이메일입니다.');
        return;
      }
      await sendEmailVerification(email);
      if (inputVersion !== emailInputVersion.current) return;
      // 발송 성공 응답 이후에만 서버의 5분 유효시간에 맞춰 안내를 시작한다.
      const timestamp = Date.now();
      setNow(timestamp);
      setSentAt(timestamp);
      setSentEmail(email);
      setForm((previous) => ({ ...previous, code: '' }));
      focusTarget.current = 'su-code';
      toast('인증번호를 보냈습니다. 5분 안에 입력하세요.');
    } catch (cause) {
      if (inputVersion !== emailInputVersion.current) return;
      setError(cause.message);
      setFieldErrors((previous) => ({ ...previous, ...cause.fields }));
    } finally {
      setPending('');
    }
  }

  async function handleVerifyCode() {
    if (busy || verified) return;
    const emailError = getEmailError(form.email);
    if (emailError) {
      setFieldErrors((previous) => ({ ...previous, email: emailError }));
      return;
    }
    if (sentAt == null || secondsLeft === 0 || sentEmail !== form.email.trim()) {
      setError('인증번호를 다시 받아 주세요.');
      return;
    }
    if (!/^\d{6}$/.test(form.code.trim())) {
      setError('인증번호 6자리를 입력하세요.');
      return;
    }
    setPending('verify');
    const email = form.email.trim();
    const inputVersion = emailInputVersion.current;
    setError('');
    setFieldErrors((previous) => ({ ...previous, code: '' }));
    try {
      await verifyEmail(email, form.code.trim());
      if (inputVersion !== emailInputVersion.current) return;
      const timestamp = Date.now();
      setNow(timestamp);
      setVerifiedUntil(timestamp + VERIFIED_TTL * 1000);
      setVerifiedEmail(email);
      setSentAt(null);
      setSentEmail(null);
      setForm((previous) => ({ ...previous, code: '' }));
      setFieldErrors((previous) => ({ ...previous, email: '', code: '' }));
      focusTarget.current = 'su-next';
      toast('이메일 인증이 완료되었습니다. 10분 안에 가입을 완료해주세요.');
    } catch (cause) {
      if (inputVersion !== emailInputVersion.current) return;
      setError(cause.message);
      setFieldErrors((previous) => ({ ...previous, ...cause.fields }));
    } finally {
      setPending('');
    }
  }

  function validateMemberInfo() {
    const keys = ['loginId', 'password', 'passwordConfirm', 'name', 'email'];
    const errors = Object.fromEntries(
      keys.map((key) => [key, getFieldError(key, form)]).filter(([, message]) => message)
    );
    // 다음 단계와 최종 가입 모두 사용자가 현재 아이디를 직접 확인했는지 검사한다.
    if (!isDuplicateConfirmed('loginId')) errors.loginId ||= '아이디 중복 확인이 필요합니다.';
    if (!verified || Date.now() >= verifiedUntil) errors.email ||= '이메일 인증을 완료하거나 다시 인증해주세요.';
    setFieldErrors((previous) => ({ ...previous, ...Object.fromEntries(keys.map((key) => [key, errors[key] ?? ''])) }));
    setError('');
    const first = keys.find((key) => errors[key]);
    if (first) {
      // 숨겨진 항목에 오류가 있으면 1단계로 돌아가 바로 수정할 수 있게 한다.
      if (step !== 1) {
        focusTarget.current = `su-${first}`;
        setStep(1);
      } else formRef.current?.querySelector(`#su-${first}`)?.focus();
      return false;
    }
    return true;
  }

  function handleNextStep() {
    if (busy || !validateMemberInfo()) return;
    // 입력 정보와 이메일 인증은 유지하고 표시하는 단계만 변경한다.
    focusTarget.current = 'su-nickname';
    setStep(2);
  }

  function handlePreviousStep() {
    setError('');
    focusTarget.current = 'su-loginId';
    setStep(1);
  }

  function handleKeyDown(event) {
    // 한글 조합 중 Enter와 버튼·파일 선택기의 기본 동작은 가로채지 않는다.
    if (
      event.key !== 'Enter' ||
      event.nativeEvent.isComposing ||
      event.keyCode === 229 ||
      event.target.tagName !== 'INPUT' ||
      event.target.type === 'file'
    )
      return;
    event.preventDefault();
    if (busy || event.repeat) return;
    const key = event.target.name;
    const message = getFieldError(key, form);
    if (message) {
      setFieldErrors((previous) => ({ ...previous, [key]: message }));
      return;
    }
    if (key === 'loginId') {
      if (isDuplicateConfirmed('loginId')) formRef.current?.querySelector('#su-password')?.focus();
      else void handleDuplicateCheck('loginId', 'su-password');
    } else if (key === 'email') {
      if (verified) void handleNextStep();
      else void handleSendCode();
    } else if (key === 'code') void handleVerifyCode();
    else if (key === 'nickname') {
      // 마지막 입력인 닉네임 확인 후 가입 버튼으로 이동해 사용자가 최종 제출한다.
      if (isDuplicateConfirmed('nickname')) formRef.current?.querySelector('#su-submit')?.focus();
      else void handleDuplicateCheck('nickname', 'su-submit');
    } else {
      const next = { password: 'passwordConfirm', passwordConfirm: 'name', name: 'email' }[key];
      if (next) formRef.current?.querySelector(`#su-${next}`)?.focus();
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (busy) return;
    if (step === 1) {
      handleNextStep();
      return;
    }
    if (!validateMemberInfo()) return;
    const user = {
      loginId: form.loginId.trim(),
      password: form.password,
      name: form.name.trim(),
      nickname: form.nickname.trim(),
      email: form.email.trim(),
    };
    const nicknameError =
      getFieldError('nickname', form) || (!isDuplicateConfirmed('nickname') ? '닉네임 중복 확인이 필요합니다.' : '');
    if (nicknameError) {
      setFieldErrors((previous) => ({ ...previous, nickname: nicknameError }));
      formRef.current?.querySelector('#su-nickname')?.focus();
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
        // 최종 서버 검사에서 중복이면 화면의 이전 확인 성공도 취소한다.
        if (duplicate < 2) {
          const key = duplicate === 0 ? 'loginId' : 'nickname';
          setChecks((previous) => ({ ...previous, [key]: null }));
          setFieldErrors((previous) => ({
            ...previous,
            [key]: `${key === 'loginId' ? '아이디' : '닉네임'} 중복 확인이 필요합니다.`,
          }));
        }
        setError(`이미 사용 중인 ${['아이디', '닉네임', '이메일'][duplicate]}입니다.`);
        if (duplicate !== 1) {
          focusTarget.current = duplicate === 0 ? 'su-loginId' : 'su-email';
          setStep(1);
        }
        return;
      }
      await createUser(user, profile);
      onDone();
    } catch (cause) {
      const memberField = ['loginId', 'password', 'name', 'email'].find((key) => cause.fields?.[key]);
      if (cause.code === 'EMAIL_NOT_VERIFIED' || memberField) {
        if (cause.code === 'EMAIL_NOT_VERIFIED') setVerifiedUntil(null);
        focusTarget.current = `su-${memberField ?? 'email'}`;
        setStep(1);
      }
      setError(cause.message);
      setFieldErrors((previous) => ({ ...previous, ...cause.fields }));
    } finally {
      setPending('');
    }
  }

  return (
    <form ref={formRef} onSubmit={handleSubmit} onKeyDown={handleKeyDown} noValidate>
      <div className="row" aria-label="가입 진행 단계" style={{ marginBottom: 16 }}>
        <span aria-current={step === 1 ? 'step' : undefined} style={{ fontWeight: step === 1 ? 700 : 400 }}>
          1. 회원 정보
        </span>
        <span aria-hidden="true">→</span>
        <span aria-current={step === 2 ? 'step' : undefined} style={{ fontWeight: step === 2 ? 700 : 400 }}>
          2. 프로필 설정
        </span>
        <span className="small muted">{step} / 2</span>
      </div>
      <h3 style={{ marginBottom: 8 }}>{step === 1 ? '회원 정보' : '프로필 설정'}</h3>
      <p className="small muted" style={{ marginBottom: 20 }}>
        {step === 1
          ? '로그인과 계정 찾기에 사용할 정보를 입력해주세요.'
          : '게시판에서 사용할 닉네임과 프로필을 설정해주세요.'}
      </p>
      <fieldset className="grid" disabled={busy} style={{ gap: 16, border: 0, padding: 0, margin: 0, minWidth: 0 }}>
        {step === 1 ? (
          <>
            <InputActionRow>
              <TextField
                id="su-loginId"
                name="loginId"
                error={fieldErrors.loginId}
                label="아이디"
                value={form.loginId}
                onChange={handleChange('loginId')}
                onBeforeInput={handleLoginIdBeforeInput}
                onCompositionEnd={handleChange('loginId')}
                autoComplete="username"
                placeholder="영문·숫자·_ 4~20자"
                maxLength={20}
                required
              />
              <Button onClick={() => handleDuplicateCheck('loginId')} disabled={isDuplicateConfirmed('loginId')}>
                중복 확인
              </Button>
            </InputActionRow>
            {isDuplicateConfirmed('loginId') && <p className="small">사용 가능한 아이디입니다.</p>}
            <TextField
              id="su-password"
              name="password"
              error={fieldErrors.password}
              label="비밀번호 (8~20자)"
              type="password"
              value={form.password}
              onChange={handleChange('password')}
              autoComplete="new-password"
              placeholder="영문·숫자·특수문자 조합, 공백 없이"
              aria-describedby="su-password-guide"
              required
            />
            <p id="su-password-guide" className="small muted">
              8~20자, 영문·숫자·특수문자(!@#$%^&*)를 각각 포함해주세요. 공백은 사용할 수 없어요.
            </p>
            <TextField
              id="su-passwordConfirm"
              name="passwordConfirm"
              error={fieldErrors.passwordConfirm}
              label="비밀번호 확인"
              type="password"
              value={form.passwordConfirm}
              onChange={handleChange('passwordConfirm')}
              autoComplete="new-password"
              placeholder="위 비밀번호를 다시 입력해주세요"
              required
            />
            <TextField
              id="su-name"
              name="name"
              error={fieldErrors.name}
              label="이름"
              value={form.name}
              onChange={handleChange('name')}
              maxLength={50}
              autoComplete="name"
              placeholder="이름을 입력해주세요"
              required
            />
            <InputActionRow>
              <TextField
                id="su-email"
                name="email"
                error={fieldErrors.email}
                label="이메일"
                type="email"
                value={form.email}
                readOnly={emailLocked}
                aria-describedby={emailLocked ? 'su-email-status' : undefined}
                onChange={handleChange('email')}
                maxLength={255}
                autoComplete="email"
                placeholder="예: creator@example.com (최대 255자)"
                required
              />
              <Button onClick={handleSendCode} disabled={verified}>
                {verified
                  ? '인증 완료'
                  : pending === 'send'
                    ? '발송 중...'
                    : emailLocked && sentAt == null
                      ? '다시 인증받기'
                      : sentAt == null
                        ? '인증번호 받기'
                        : '다시 받기'}
              </Button>
            </InputActionRow>
            {emailLocked && (
              <div className="row" style={{ justifyContent: 'space-between', gap: 8 }}>
                <p id="su-email-status" className="small muted" role="status">
                  {verified ? '인증된 이메일입니다.' : '인증이 만료되었습니다. 다시 인증해주세요.'}
                </p>
                <Button size="sm" variant="ghost" onClick={handleChangeEmail}>
                  이메일 변경
                </Button>
              </div>
            )}
            {sentAt != null && !verified && (
              <>
                <InputActionRow>
                  <TextField
                    id="su-code"
                    name="code"
                    error={fieldErrors.code}
                    label="이메일 인증번호"
                    value={form.code}
                    onChange={handleChange('code')}
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    placeholder="메일로 받은 숫자 6자리"
                    maxLength={6}
                    disabled={verified}
                  />
                  <Button onClick={handleVerifyCode} disabled={verified}>
                    {verified ? '인증 완료' : '인증 확인'}
                  </Button>
                </InputActionRow>
                {!verified && (
                  <p className="small muted">
                    {secondsLeft > 0
                      ? '인증번호 유효시간 ' + clock(secondsLeft)
                      : '인증번호가 만료되었습니다. 다시 받아 주세요.'}
                  </p>
                )}
              </>
            )}
          </>
        ) : (
          <>
            <SignupProfileImage profile={profile} onChange={setProfile} />
            <InputActionRow>
              <TextField
                id="su-nickname"
                name="nickname"
                error={fieldErrors.nickname}
                label="닉네임"
                placeholder="게시판에서 사용할 닉네임 (최대 50자)"
                value={form.nickname}
                onChange={handleChange('nickname')}
                maxLength={50}
                required
              />
              <Button onClick={() => handleDuplicateCheck('nickname')} disabled={isDuplicateConfirmed('nickname')}>
                중복 확인
              </Button>
            </InputActionRow>
            {isDuplicateConfirmed('nickname') && <p className="small">사용 가능한 닉네임입니다.</p>}
          </>
        )}
        {error && (
          <p className="small" role="alert" style={{ color: 'var(--danger)' }}>
            {error}
          </p>
        )}
        {step === 1 ? (
          <Button id="su-next" type="submit" variant="primary" size="lg">
            다음
          </Button>
        ) : (
          <div className="row">
            <Button onClick={handlePreviousStep}>이전</Button>
            <Button id="su-submit" type="submit" variant="primary" size="lg">
              {pending === 'signup' ? '가입 중...' : '가입 완료'}
            </Button>
          </div>
        )}
      </fieldset>
    </form>
  );
}

// 제목·입력·오류를 별도 행에 배치해 오류 문구가 길어져도 버튼은 입력칸 옆에 유지한다.
const InputActionRow = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 12px;
  align-items: center;

  > .field {
    display: contents;
  }
  > .field > label {
    grid-column: 1;
    grid-row: 1;
  }
  > .field > input {
    grid-column: 1;
    grid-row: 2;
  }
  > .field > p {
    grid-column: 1 / -1;
    grid-row: 3;
  }
  > button {
    grid-column: 2;
    grid-row: 2;
    align-self: stretch;
    white-space: nowrap;
  }
`;
