'use no memo';

import {
  getPasswordError,
  PASSWORD_GUIDE,
  getEmailError,
  PASSWORD_MAX_LENGTH,
  USER_INPUT_LIMITS,
} from '../../utils/userValidation';

import { useEffect, useRef, useState } from 'react';
import styled from 'styled-components';
import { useNavigate } from 'react-router-dom';
import { Button, Icon, Modal, TextField } from '../common/index';
import { useAuth } from '../../stores/useAuthStore';
import { useToast } from '../../stores/useToastStore';
import { maskEmail } from '../../utils/format';
import {
  sendEmailVerification,
  verifyEmail,
  updateMyNickname,
  updateMyEmail,
  updateMyPassword,
  updateMyProfileImage,
  deleteMyAccount,
} from '../../api/userApi.js';

export function ProfileSettings() {
  const { user, refreshProfile, clearAuth } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [nicknameDraft, setNickname] = useState(null);
  const [emailDraft, setEmail] = useState(null);
  const nickname = nicknameDraft ?? user?.nickname ?? '';
  const email = emailDraft ?? user?.email ?? '';
  const [code, setCode] = useState('');
  const [verifiedEmail, setVerifiedEmail] = useState(null);
  const imageInput = useRef(null);
  const [editing, setEditing] = useState(null);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [deletePassword, setDeletePassword] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [deleteConfirmed, setDeleteConfirmed] = useState(false);
  // 진입 시 회원 정보 확인은 공통 Layout에서 처리하고, 여기서는 수정 요청 상태만 관리한다.
  const [pending, setPending] = useState('');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [resendAvailableAt, setResendAvailableAt] = useState(null);
  const [now, setNow] = useState(Date.now);
  const resendSecondsLeft = resendAvailableAt == null ? 0 : Math.max(0, Math.ceil((resendAvailableAt - now) / 1000));

  useEffect(() => {
    if (resendAvailableAt == null) return undefined;
    // 팝업을 닫아도 잠금을 유지하고, 1분이 지나면 카운트다운을 종료한다.
    const timer = setInterval(() => {
      const timestamp = Date.now();
      setNow(timestamp);
      if (timestamp >= resendAvailableAt) clearInterval(timer);
    }, 1000);
    return () => clearInterval(timer);
  }, [resendAvailableAt]);

  function handleEdit(section) {
    if (pending) return;
    // 팝업을 열고 닫을 때 이전 입력·오류·인증 상태가 다른 수정에 남지 않게 한다.
    setEditing(section);
    setNickname(null);
    setEmail(null);
    setCode('');
    setVerifiedEmail(null);
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setError('');
    setFieldErrors({});
  }

  async function handleAction(key, operation, refresh = false) {
    if (pending) return;
    setPending(key);
    setError('');
    setFieldErrors({});
    try {
      await operation();
      if (refresh) await refreshProfile();
      // 이메일 발송·번호 확인은 팝업을 유지하고 최종 저장 성공 후에만 닫는다.
      if (['nickname', 'email', 'password'].includes(key)) setEditing(null);
    } catch (cause) {
      setError(cause.message);
      setFieldErrors(cause.fields ?? {});
    } finally {
      setPending('');
    }
  }

  function handleNickname() {
    const nextNickname = nickname.trim();
    // 바뀌지 않은 값은 서버에 저장 요청을 보내기 전에 팝업의 공통 오류로 안내한다.
    const nicknameError = !nextNickname
      ? '닉네임을 입력하세요.'
      : nextNickname === user?.nickname
        ? '현재 사용 중인 닉네임입니다. 다른 닉네임을 입력해주세요.'
        : '';
    if (nicknameError) {
      setError(nicknameError);
      setFieldErrors({});
      return;
    }
    handleAction(
      'nickname',
      async () => {
        await updateMyNickname(nextNickname);
        toast('닉네임을 변경했습니다.');
      },
      true
    );
  }

  function handleSendCode() {
    if (pending || (resendAvailableAt != null && Date.now() < resendAvailableAt)) return;
    const emailError = getEmailError(email);
    if (emailError) {
      setFieldErrors((previous) => ({ ...previous, email: emailError }));
      return;
    }
    handleAction('send', async () => {
      await sendEmailVerification(email.trim());
      // 발송 성공 응답부터 60초간 잠그고, 성공한 경우에만 기존 인증 상태를 초기화한다.
      const timestamp = Date.now();
      setNow(timestamp);
      setResendAvailableAt(timestamp + 60_000);
      setVerifiedEmail(null);
      setCode('');
      toast('인증번호를 보냈습니다. 5분 안에 입력하세요.');
    });
  }

  function handleVerify() {
    const emailError = getEmailError(email);
    if (emailError) {
      setFieldErrors((previous) => ({ ...previous, email: emailError }));
      return;
    }
    handleAction('verify', async () => {
      await verifyEmail(email.trim(), code.trim());
      setVerifiedEmail(email.trim());
      toast('이메일 인증이 완료되었습니다.');
    });
  }

  function handleEmail() {
    const emailError = getEmailError(email);
    if (emailError) {
      setFieldErrors((previous) => ({ ...previous, email: emailError }));
      return;
    }
    if (verifiedEmail !== email.trim()) {
      setError('새 이메일 인증을 완료하세요.');
      return;
    }
    handleAction(
      'email',
      async () => {
        await updateMyEmail(email.trim());
        setVerifiedEmail(null);
        toast('이메일을 변경했습니다.');
      },
      true
    );
  }

  function handlePassword() {
    const passwordError = getPasswordError(newPassword);
    if (!currentPassword || passwordError || newPassword !== confirmPassword) {
      setError(
        !currentPassword
          ? '현재 비밀번호를 입력해주세요.'
          : passwordError || '새 비밀번호와 확인 값이 일치하지 않습니다.'
      );
      return;
    }
    handleAction('password', async () => {
      await updateMyPassword(currentPassword, newPassword);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      toast('비밀번호를 변경했습니다.');
    });
  }

  function handleImage(remove, file = null) {
    if (!remove && (!file || !file.type.startsWith('image/'))) {
      setError('이미지 파일을 선택해주세요.');
      return;
    }
    handleAction(
      'image',
      async () => {
        // 사진을 선택하면 별도 저장 버튼 없이 업로드하고 서버의 회원 정보를 갱신한다.
        await updateMyProfileImage(remove ? null : file, remove);
        toast(remove ? '프로필 이미지를 삭제했습니다.' : '프로필 이미지를 변경했습니다.');
      },
      true
    );
  }

  function handleDelete() {
    // 복구 불가 안내를 직접 확인한 경우에만 즉시 탈퇴 요청을 보낸다.
    if (!deleteConfirmed) {
      setError('탈퇴 안내를 확인하고 체크해주세요.');
      return;
    }
    if (!deletePassword) {
      setError('탈퇴 확인용 비밀번호를 입력하세요.');
      return;
    }
    handleAction('delete', async () => {
      // 서버에서 탈퇴가 성공한 경우에만 브라우저의 로그인 정보를 제거한다.
      await deleteMyAccount(deletePassword);
      clearAuth();
      navigate('/');
      toast('탈퇴가 완료되었습니다.');
    });
  }

  return (
    <SettingsCard>
      <header className="settings-heading">
        <span className="settings-icon">
          <Icon name="user" size={26} />
        </span>
        <h2>회원정보 설정</h2>
        <p className="small muted">프로필과 계정 정보를 관리하세요.</p>
      </header>
      <fieldset
        disabled={Boolean(pending)}
        className="grid"
        style={{ gap: 12, border: 0, padding: 0, margin: '0 auto', minWidth: 0, width: '100%', maxWidth: 520 }}
      >
        <div className="profile-preview">
          {user?.profileImageUrl ? (
            <img src={user.profileImageUrl} alt="프로필 사진" />
          ) : (
            <Icon name="user" size={36} />
          )}
        </div>
        <input
          ref={imageInput}
          type="file"
          accept="image/*"
          hidden
          aria-label="프로필 사진 선택"
          onChange={(event) => {
            const file = event.target.files?.[0];
            event.target.value = '';
            if (file) handleImage(false, file);
          }}
        />
        <div className="profile-actions">
          <Button variant="soft" onClick={() => imageInput.current?.click()}>
            사진 변경
          </Button>
          <Button variant="ghost" onClick={() => handleImage(true)} disabled={!user?.profileImageUrl}>
            기본 이미지로 변경
          </Button>
        </div>
        <SettingsSection
          title="닉네임"
          summary={user?.nickname || '등록된 닉네임이 없습니다.'}
          open={editing === 'nickname'}
          onOpen={() => handleEdit('nickname')}
          onClose={() => handleEdit(null)}
          pending={Boolean(pending)}
          error={error}
        >
          <TextField
            id="me-nickname"
            error={fieldErrors.nickname}
            label="닉네임"
            placeholder="사용할 닉네임을 입력해주세요"
            value={nickname}
            maxLength={USER_INPUT_LIMITS.nickname}
            onChange={(event) => {
              setNickname(event.target.value);
              setError('');
              setFieldErrors((previous) => ({ ...previous, nickname: '' }));
            }}
          />
          <Button variant="primary" size="lg" onClick={handleNickname}>
            저장
          </Button>
        </SettingsSection>
        <SettingsSection
          title="이메일"
          summary={maskEmail(user?.email || '')}
          open={editing === 'email'}
          onOpen={() => handleEdit('email')}
          onClose={() => handleEdit(null)}
          pending={Boolean(pending)}
          error={error}
        >
          <p className="small muted">현재 이메일: {maskEmail(user?.email || '')}</p>
          <InputActionRow>
            <TextField
              id="me-email"
              error={fieldErrors.email}
              label="새 이메일"
              type="email"
              value={email}
              maxLength={USER_INPUT_LIMITS.email}
              onChange={(event) => {
                setEmail(event.target.value);
                setVerifiedEmail(null);
                setCode('');
              }}
            />
            <Button onClick={handleSendCode} disabled={resendSecondsLeft > 0}>
              {resendSecondsLeft > 0 ? `${resendSecondsLeft}초 후 받기` : '인증번호 받기'}
            </Button>
          </InputActionRow>
          <InputActionRow>
            <TextField
              id="me-code"
              error={fieldErrors.code}
              label="이메일 인증번호"
              value={code}
              inputMode="numeric"
              placeholder="메일로 받은 숫자 6자리"
              maxLength={USER_INPUT_LIMITS.verificationCode}
              onChange={(event) => setCode(event.target.value)}
            />
            <Button onClick={handleVerify} disabled={verifiedEmail === email.trim()}>
              인증 확인
            </Button>
          </InputActionRow>
          {verifiedEmail === email.trim() && <p className="small">이메일 인증이 완료되었습니다.</p>}
          <Button variant="primary" size="lg" onClick={handleEmail} disabled={verifiedEmail !== email.trim()}>
            이메일 저장
          </Button>
        </SettingsSection>
        <SettingsSection
          title="비밀번호"
          summary="계정 보호를 위해 비밀번호를 관리하세요."
          open={editing === 'password'}
          onOpen={() => handleEdit('password')}
          onClose={() => handleEdit(null)}
          pending={Boolean(pending)}
          error={error}
        >
          <TextField
            id="me-current"
            error={fieldErrors.currentPassword}
            label="현재 비밀번호"
            type="password"
            autoComplete="current-password"
            value={currentPassword}
            maxLength={PASSWORD_MAX_LENGTH}
            onChange={(event) => setCurrentPassword(event.target.value)}
          />
          <TextField
            id="me-new"
            error={fieldErrors.newPassword}
            label="새 비밀번호 (8~20자)"
            type="password"
            placeholder="영문·숫자·특수문자 조합, 공백 없이"
            aria-describedby="profile-password-guide"
            autoComplete="new-password"
            value={newPassword}
            maxLength={PASSWORD_MAX_LENGTH}
            onChange={(event) => setNewPassword(event.target.value)}
          />
          <p id="profile-password-guide" className="small muted">
            {PASSWORD_GUIDE}
          </p>
          <TextField
            id="me-confirm"
            label="새 비밀번호 확인"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            maxLength={PASSWORD_MAX_LENGTH}
            onChange={(event) => setConfirmPassword(event.target.value)}
          />
          <Button variant="primary" size="lg" onClick={handlePassword}>
            비밀번호 변경
          </Button>
        </SettingsSection>
        <div className="delete-section">
          <p className="small muted">더 이상 서비스를 이용하지 않으시나요?</p>
          <Button
            variant="link"
            onClick={() => {
              setError('');
              setFieldErrors({});
              setDeleting(true);
            }}
          >
            회원 탈퇴
          </Button>
        </div>
      </fieldset>
      {error && !editing && !deleting && (
        <p role="alert" className="small" style={{ color: 'var(--danger)' }}>
          {error}
        </p>
      )}
      {deleting && (
        <Modal
          title="회원 탈퇴"
          onClose={() => {
            if (!pending) {
              setDeleting(false);
              setDeletePassword('');
              setDeleteConfirmed(false);
            }
          }}
        >
          <p>탈퇴하면 로그인할 수 없으며 계정을 복구할 수 없습니다. 계속하려면 비밀번호를 입력해주세요.</p>
          <label className="toggle" style={{ margin: '16px 0' }}>
            <input
              type="checkbox"
              checked={deleteConfirmed}
              disabled={Boolean(pending)}
              onChange={(event) => setDeleteConfirmed(event.target.checked)}
            />
            탈퇴 즉시 회원 정보가 삭제되고 계정을 복구할 수 없음을 확인했습니다.
          </label>
          <TextField
            id="me-delete"
            label="탈퇴 확인 비밀번호"
            type="password"
            autoComplete="current-password"
            value={deletePassword}
            maxLength={PASSWORD_MAX_LENGTH}
            onChange={(event) => setDeletePassword(event.target.value)}
            disabled={Boolean(pending)}
          />
          <Button
            variant="danger"
            onClick={handleDelete}
            disabled={Boolean(pending) || !deleteConfirmed || !deletePassword}
          >
            회원 삭제
          </Button>
          {error && <p role="alert">{error}</p>}
        </Modal>
      )}
    </SettingsCard>
  );
}

function SettingsSection({ title, summary, open, onOpen, onClose, pending, error, children }) {
  return (
    <SettingsRow>
      <div className="setting-header">
        <div className="setting-summary">
          <h3>{title}</h3>
          <p className="small muted">{summary}</p>
        </div>
        <Button variant="soft" onClick={onOpen}>
          변경
        </Button>
      </div>
      {open && (
        <Modal title={`${title} 변경`} onClose={onClose}>
          <h3 style={{ marginBottom: 20 }}>{title} 변경</h3>
          <fieldset
            disabled={pending}
            className="grid"
            style={{ gap: 12, border: 0, padding: 0, margin: 0, minWidth: 0 }}
          >
            {children}
            {error && (
              <p className="small" role="alert" style={{ color: 'var(--danger)' }}>
                {error}
              </p>
            )}
            <Button onClick={onClose}>취소</Button>
          </fieldset>
        </Modal>
      )}
    </SettingsRow>
  );
}

const SettingsRow = styled.section`
  padding: 20px 0;
  .setting-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
  }
  .setting-header > .btn {
    flex-shrink: 0;
  }
  border-bottom: 1px solid var(--border);

  .setting-summary {
    min-width: 0;
    overflow-wrap: anywhere;
  }

  h3 {
    font-size: 17px;
    margin-bottom: 8px;
  }
`;

const SettingsCard = styled.div`
  max-width: 680px;
  width: 100%;
  box-sizing: border-box;
  margin: 0 auto;

  .settings-heading {
    display: grid;
    justify-items: center;
    gap: 12px;
    text-align: center;
    padding: 8px 0 28px;
  }

  .settings-heading h2 {
    font-size: 24px;
  }

  .settings-icon {
    display: grid;
    place-items: center;
    width: 52px;
    height: 52px;
    border-radius: 16px;
    background: var(--primary-bg);
    color: var(--primary);
  }

  .profile-actions {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 8px;
  }

  .profile-preview {
    width: 80px;
    height: 80px;
    margin: 0 auto;
    display: grid;
    place-items: center;
    overflow: hidden;
    border-radius: 50%;
    background: var(--primary-bg);
    color: var(--primary);
  }

  .profile-preview img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .delete-section {
    display: grid;
    justify-items: center;
    gap: 12px;
    padding: 16px 0 8px;
  }
`;

// 회원가입과 같이 오류 문구는 별도 행에 두고, 버튼은 입력칸 옆에 정렬한다.
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
