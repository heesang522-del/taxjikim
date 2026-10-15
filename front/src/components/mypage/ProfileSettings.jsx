'use no memo';

import { getPasswordError, PASSWORD_GUIDE } from '../../utils/passwordValidation';

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Modal, TextField } from '../common/index';
import { useAuth } from '../../store/useAuthStore';
import { useToast } from '../../store/useToastStore';
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
  const [profile, setProfile] = useState(null);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [deletePassword, setDeletePassword] = useState('');
  const [deleting, setDeleting] = useState(false);
  // 진입 시 회원 정보 확인은 공통 Layout에서 처리하고, 여기서는 수정 요청 상태만 관리한다.
  const [pending, setPending] = useState('');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});

  async function handleAction(key, operation, refresh = false) {
    if (pending) return;
    setPending(key);
    setError('');
    setFieldErrors({});
    try {
      await operation();
      if (refresh) await refreshProfile();
    } catch (cause) {
      setError(cause.message);
      setFieldErrors(cause.fields ?? {});
    } finally {
      setPending('');
    }
  }

  function handleNickname() {
    if (!nickname.trim()) {
      setError('닉네임을 입력하세요.');
      return;
    }
    handleAction(
      'nickname',
      async () => {
        await updateMyNickname(nickname.trim());
        toast('닉네임을 변경했습니다.');
      },
      true
    );
  }

  function handleSendCode() {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setError('이메일 형식을 확인하세요.');
      return;
    }
    setVerifiedEmail(null);
    handleAction('send', async () => {
      await sendEmailVerification(email.trim());
      setCode('');
      toast('인증번호를 보냈습니다. 5분 안에 입력하세요.');
    });
  }

  function handleVerify() {
    handleAction('verify', async () => {
      await verifyEmail(email.trim(), code.trim());
      setVerifiedEmail(email.trim());
      toast('이메일 인증이 완료되었습니다.');
    });
  }

  function handleEmail() {
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

  function handleImage(remove) {
    if (!remove && !profile) {
      setError('이미지를 선택하세요.');
      return;
    }
    handleAction(
      'image',
      async () => {
        await updateMyProfileImage(remove ? null : profile, remove);
        setProfile(null);
        toast(remove ? '프로필 이미지를 삭제했습니다.' : '프로필 이미지를 변경했습니다.');
      },
      true
    );
  }

  function handleDelete() {
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
    <Card as="aside" title="회원 정보">
      <fieldset
        disabled={Boolean(pending)}
        className="grid"
        style={{ gap: 12, border: 0, padding: 0, margin: 0, minWidth: 0 }}
      >
        {pending === 'load' && <p role="status">회원 정보를 불러오는 중...</p>}
        <p>
          <b>{user?.name || user?.loginId}</b>
        </p>
        <p className="small muted">{user?.email}</p>
        {user?.profileImageUrl && (
          <img
            src={user.profileImageUrl}
            alt="프로필"
            width="80"
            height="80"
            style={{ objectFit: 'cover', borderRadius: '50%' }}
          />
        )}
        <TextField
          id="me-image"
          label="프로필 이미지"
          type="file"
          accept="image/*"
          onChange={(event) => setProfile(event.target.files?.[0] ?? null)}
        />
        <div className="row">
          <Button onClick={() => handleImage(false)}>이미지 저장</Button>
          <Button onClick={() => handleImage(true)}>이미지 삭제</Button>
        </div>
        <TextField
          id="me-nickname"
          error={fieldErrors.nickname}
          label="닉네임"
          value={nickname}
          maxLength={50}
          onChange={(event) => setNickname(event.target.value)}
        />
        <Button onClick={handleNickname}>닉네임 저장</Button>
        <TextField
          id="me-email"
          error={fieldErrors.email}
          label="새 이메일"
          type="email"
          value={email}
          maxLength={255}
          onChange={(event) => {
            setEmail(event.target.value);
            setVerifiedEmail(null);
            setCode('');
          }}
        />
        <Button onClick={handleSendCode}>인증번호 받기</Button>
        <TextField
          id="me-code"
          error={fieldErrors.code}
          label="이메일 인증번호"
          value={code}
          inputMode="numeric"
          maxLength={6}
          onChange={(event) => setCode(event.target.value)}
        />
        <Button onClick={handleVerify}>인증 확인</Button>
        <Button onClick={handleEmail} disabled={verifiedEmail !== email.trim()}>
          이메일 저장
        </Button>
        <TextField
          id="me-current"
          error={fieldErrors.currentPassword}
          label="현재 비밀번호"
          type="password"
          autoComplete="current-password"
          value={currentPassword}
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
          onChange={(event) => setConfirmPassword(event.target.value)}
        />
        <Button onClick={handlePassword}>비밀번호 변경</Button>
        <Button
          variant="danger"
          onClick={() => {
            setError('');
            setFieldErrors({});
            setDeleting(true);
          }}
        >
          회원 탈퇴
        </Button>
      </fieldset>
      {error && (
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
            }
          }}
        >
          <p>탈퇴하면 로그인할 수 없으며 계정을 복구할 수 없습니다. 계속하려면 비밀번호를 입력해주세요.</p>
          <TextField
            id="me-delete"
            label="탈퇴 확인 비밀번호"
            type="password"
            autoComplete="current-password"
            value={deletePassword}
            onChange={(event) => setDeletePassword(event.target.value)}
            disabled={Boolean(pending)}
          />
          <Button variant="danger" onClick={handleDelete} disabled={Boolean(pending)}>
            탈퇴 확정
          </Button>
          {error && <p role="alert">{error}</p>}
        </Modal>
      )}
    </Card>
  );
}
