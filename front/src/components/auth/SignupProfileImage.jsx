import { useEffect, useRef, useState } from 'react';
import styled from 'styled-components';
import { Button, Icon } from '../common/index';

export function SignupProfileImage({ profile, onChange }) {
  const inputRef = useRef(null);
  const imageRef = useRef(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!profile) return undefined;
    // 가입 전에는 서버 업로드 없이 선택한 파일을 미리 보고, 교체·화면 종료 시 임시 주소를 해제한다.
    const previewUrl = URL.createObjectURL(profile);
    imageRef.current.src = previewUrl;
    return () => URL.revokeObjectURL(previewUrl);
  }, [profile]);

  function handleSelect(event) {
    const file = event.target.files?.[0];
    // 같은 사진을 다시 선택해도 change 이벤트가 발생하도록 파일 입력을 비운다.
    event.target.value = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      setError('이미지 파일을 선택해주세요.');
      return;
    }
    setError('');
    onChange(file);
  }

  function handleReset() {
    // 선택을 취소하면 가입 요청에 프로필 파일을 포함하지 않는다.
    setError('');
    onChange(null);
  }

  function handleImageError() {
    setError('사진을 불러올 수 없습니다. 다른 이미지 파일을 선택해주세요.');
    onChange(null);
  }

  return (
    <ProfileSection aria-labelledby="signup-profile-label">
      <p id="signup-profile-label" className="small">
        프로필 이미지 <span className="muted">(선택)</span>
      </p>
      <Preview>
        {profile ? (
          <img ref={imageRef} alt="선택한 프로필 이미지 미리보기" onError={handleImageError} />
        ) : (
          <span role="img" aria-label="기본 프로필 이미지">
            <Icon name="user" size={56} />
          </span>
        )}
      </Preview>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        aria-label="프로필 이미지 선택"
        hidden
        onChange={handleSelect}
      />
      <Actions>
        <Button onClick={() => inputRef.current?.click()}>
          <Icon name="camera" size={16} />
          사진 선택
        </Button>
        <Button variant="ghost" onClick={handleReset} disabled={!profile}>
          기본 이미지로 변경
        </Button>
      </Actions>
      <p className="small muted">사진은 선택 사항이며, 나중에 마이페이지에서도 변경할 수 있어요.</p>
      {error && (
        <p className="small" role="alert" style={{ color: 'var(--danger)' }}>
          {error}
        </p>
      )}
    </ProfileSection>
  );
}

const ProfileSection = styled.section`
  display: grid;
  justify-items: center;
  gap: 14px;
  padding: 12px 0 24px;
  text-align: center;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
`;

const Preview = styled.div`
  width: 128px;
  height: 128px;
  border-radius: 50%;
  overflow: hidden;
  display: grid;
  place-items: center;
  color: ${({ theme }) => theme.colors.muted};
  background: ${({ theme }) => theme.colors.background};
  border: 1px solid ${({ theme }) => theme.colors.border};

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  span {
    display: flex;
  }
`;

const Actions = styled.div`
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
`;
