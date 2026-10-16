'use no memo';

import { useEffect, useRef, useState } from 'react';
import styled from 'styled-components';
import { getPasswordError } from '../../utils/passwordValidation';
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

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CODE_TTL = 300;
const VERIFIED_TTL = 600;
const clock = (seconds) => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;

function getBirthDateError(digits) {
  if (!digits) return '';
  if (digits.length !== 8) return '생년월일을 숫자 8자리로 입력해주세요. 예: 19980521';
  const year = Number(digits.slice(0, 4));
  const month = Number(digits.slice(4, 6));
  const day = Number(digits.slice(6, 8));
  // 윤년과 월별 일수를 확인해 2월 30일처럼 존재하지 않는 날짜는 보내지 않는다.
  const leapYear = year % 400 === 0 || (year % 4 === 0 && year % 100 !== 0);
  const days = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  return year < 1 || month < 1 || month > 12 || day < 1 || day > days[month - 1]
    ? '올바른 생년월일을 입력해주세요.'
    : '';
}

function getFieldError(key, values) {
  if (key === 'loginId' && !/^[a-z0-9_]{4,20}$/i.test(values.loginId.trim()))
    return '아이디는 영문·숫자·_ 4~20자로 입력하세요.';
  if (key === 'password') return getPasswordError(values.password);
  if (key === 'passwordConfirm' && (!values.passwordConfirm || values.password !== values.passwordConfirm))
    return '비밀번호가 일치하지 않습니다.';
  if (key === 'name' && !values.name.trim()) return '이름을 입력해주세요.';
  if (key === 'email' && !EMAIL_RE.test(values.email.trim())) return '이메일 형식을 확인하세요.';
  if (key === 'birthDate') return getBirthDateError(values.birthDate);
  if (key === 'nickname' && !values.nickname.trim()) return '닉네임을 입력해주세요.';
  return '';
}

export function SignupForm({ onDone }) {
  const toast = useToast();
  const formRef = useRef(null);
  const focusTarget = useRef(null);
  const [step, setStep] = useState(1);
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
      // 생년월일은 숫자만 보관하고 점은 화면에서만 자동으로 표시한다.
      const value = key === 'birthDate' ? event.target.value.replace(/\D/g, '').slice(0, 8) : event.target.value;
      const nextForm = { ...form, [key]: value };
      if (key === 'email') nextForm.code = '';
      setForm(nextForm);
      setError('');
      // 한 번 표시한 오류는 수정한 항목만 다시 검사하고, 다른 항목의 안내는 유지한다.
      setFieldErrors((previous) => {
        const next = { ...previous };
        if (Object.hasOwn(previous, key)) {
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
        setSentAt(null);
        setVerifiedUntil(null);
      }
    };
  }

  async function handleDuplicateCheck(key, nextInputId) {
    const value = form[key].trim();
    if (!value || (key === 'loginId' && !/^[a-z0-9_]{4,20}$/i.test(value))) {
      setError(key === 'loginId' ? '아이디는 영문·숫자·_ 4~20자로 입력하세요.' : '닉네임을 입력하세요.');
      return;
    }
    setPending(key);
    setError('');
    setFieldErrors((previous) => ({ ...previous, [key]: '' }));
    try {
      const result = await (key === 'loginId' ? checkLoginId(value) : checkNickname(value));
      setChecks((previous) => ({ ...previous, [key]: { value, duplicate: result.data } }));
      if (result.data) setError(`이미 사용 중인 ${key === 'loginId' ? '아이디' : '닉네임'}입니다.`);
      else {
        toast('사용할 수 있습니다.');
        if (nextInputId) focusTarget.current = nextInputId;
      }
      return !result.data;
    } catch (cause) {
      setError(cause.message);
      setFieldErrors((previous) => ({ ...previous, ...cause.fields }));
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
    setFieldErrors((previous) => ({ ...previous, code: '' }));
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
      focusTarget.current = 'su-code';
      toast('인증번호를 보냈습니다. 5분 안에 입력하세요.');
    } catch (cause) {
      setError(cause.message);
      setFieldErrors((previous) => ({ ...previous, ...cause.fields }));
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
    setFieldErrors((previous) => ({ ...previous, code: '' }));
    try {
      await verifyEmail(form.email.trim(), form.code.trim());
      const timestamp = Date.now();
      setNow(timestamp);
      setVerifiedUntil(timestamp + VERIFIED_TTL * 1000);
      setFieldErrors((previous) => ({ ...previous, email: '', code: '' }));
      focusTarget.current = 'su-next';
      toast('이메일 인증이 완료되었습니다. 10분 안에 가입을 완료해주세요.');
    } catch (cause) {
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

  async function handleNextStep() {
    if (busy || !validateMemberInfo()) return;
    const checked = checks.loginId;
    if (
      (!checked || checked.value !== form.loginId.trim() || checked.duplicate) &&
      !(await handleDuplicateCheck('loginId'))
    )
      return;
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
      if (checks.loginId?.value === form.loginId.trim() && !checks.loginId.duplicate)
        formRef.current?.querySelector('#su-password')?.focus();
      else void handleDuplicateCheck('loginId', 'su-password');
    } else if (key === 'email') {
      if (verified) void handleNextStep();
      else void handleSendCode();
    } else if (key === 'code') void handleVerifyCode();
    else if (key === 'birthDate') formRef.current?.requestSubmit();
    else if (key === 'nickname') {
      if (checks.nickname?.value === form.nickname.trim() && !checks.nickname.duplicate)
        formRef.current?.querySelector('#su-birthDate')?.focus();
      else void handleDuplicateCheck('nickname', 'su-birthDate');
    } else {
      const next = { password: 'passwordConfirm', passwordConfirm: 'name', name: 'email' }[key];
      if (next) formRef.current?.querySelector(`#su-${next}`)?.focus();
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (busy) return;
    if (step === 1) {
      await handleNextStep();
      return;
    }
    if (!validateMemberInfo()) return;
    const birthDateError = getBirthDateError(form.birthDate);
    if (birthDateError) {
      setFieldErrors((previous) => ({ ...previous, birthDate: birthDateError }));
      formRef.current?.querySelector('#su-birthDate')?.focus();
      return;
    }
    const user = {
      loginId: form.loginId.trim(),
      password: form.password,
      name: form.name.trim(),
      nickname: form.nickname.trim(),
      email: form.email.trim(),
      // API에는 기존 날짜 계약인 YYYY-MM-DD를 유지하며 선택하지 않으면 null을 보낸다.
      birthDate: form.birthDate
        ? `${form.birthDate.slice(0, 4)}-${form.birthDate.slice(4, 6)}-${form.birthDate.slice(6, 8)}`
        : null,
    };
    const nicknameError = getFieldError('nickname', form);
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
      } else if (cause.fields?.birthDate) {
        focusTarget.current = 'su-birthDate';
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
                autoComplete="username"
                placeholder="영문·숫자·_ 4~20자"
                maxLength={20}
                required
              />
              <Button onClick={() => handleDuplicateCheck('loginId')}>중복 확인</Button>
            </InputActionRow>
            {checks.loginId && !checks.loginId.duplicate && <p className="small">사용 가능한 아이디입니다.</p>}
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
                onChange={handleChange('email')}
                maxLength={255}
                autoComplete="email"
                placeholder="예: creator@example.com (최대 255자)"
                required
              />
              <Button onClick={handleSendCode}>
                {pending === 'send' ? '발송 중...' : sentAt == null ? '인증번호 받기' : '다시 받기'}
              </Button>
            </InputActionRow>
            {sentAt != null && (
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
              <Button onClick={() => handleDuplicateCheck('nickname')}>중복 확인</Button>
            </InputActionRow>
            {checks.nickname && !checks.nickname.duplicate && <p className="small">사용 가능한 닉네임입니다.</p>}
            <TextField
              id="su-birthDate"
              name="birthDate"
              error={fieldErrors.birthDate}
              label="생년월일 (선택 · 8자리)"
              type="text"
              inputMode="numeric"
              autoComplete="bday"
              placeholder="예: 19980521"
              maxLength={10}
              value={form.birthDate.replace(/^(\d{4})(\d)/, '$1.$2').replace(/^(\d{4}\.\d{2})(\d)/, '$1.$2')}
              onChange={handleChange('birthDate')}
              onBlur={() =>
                setFieldErrors((previous) => ({ ...previous, birthDate: getBirthDateError(form.birthDate) }))
              }
            />
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
            <Button type="submit" variant="primary" size="lg">
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
