// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, SelectField, TextAreaField, TextField } from '../common/index';
import { BOARD_WRITE_TABS } from '../../data/content';
import { useAuth } from '../../store/useAuthStore';
import { useToast } from '../../store/useToastStore';
import { uploadFileChunks, filterBlockedFiles, imagePreviewProps, videoPreviewProps } from '../../store/post-preview.js';
import { createPost, updatePost } from '../../api/post.js';
import { ROUTES } from '../../utils/routes';

export default function PostForm({ heading, initial, submitLabel, onCancel, postId, onSubmit }) {
  const toast = useToast();
  const navigate = useNavigate();
  const { isAdmin, accessToken } = useAuth();
  const [tab, setTab] = useState(initial.tab);
  const [title, setTitle] = useState(initial.title);
  const [body, setBody] = useState(initial.body);

  const [files, setFiles] = useState(
    (initial.files ?? []).map((f) => (typeof f === 'string' ? { name: f, previewUrl: null } : f))
  );
  const [isUploading, setIsUploading] = useState(false);
  const previewUrls = useRef(new Set());
  // 새로 선택한 로컬 미리보기 URL은 화면을 벗어날 때 해제한다. 서버 URL은 유지한다.
  useEffect(() => () => {
    previewUrls.current.forEach((url) => URL.revokeObjectURL(url));
    previewUrls.current.clear();
  }, []);

  const tabOptions = isAdmin ? BOARD_WRITE_TABS : BOARD_WRITE_TABS.filter((t) => t !== '공지');

  const handleFileChange = (e) => {
    const pickedFiles = Array.from(e.target.files);
    const selectedFiles = filterBlockedFiles(pickedFiles);

    if (selectedFiles.length !== pickedFiles.length) {
      toast('사진·동영상만 첨부할 수 있습니다. PDF·ZIP·실행파일은 차단됩니다.');
    }
    e.target.value = '';

    if (files.length + selectedFiles.length > 5) {
      toast('첨부파일은 최대 5개까지 등록할 수 있습니다.');
      return;
    }

    const newFilesWithPreview = selectedFiles.map((file) => {
      const previewUrl = URL.createObjectURL(file);
      previewUrls.current.add(previewUrl);
      return {
        file, name: file.name, previewUrl,
        mediaType: /\.(mp4|webm|mov)$/i.test(file.name) ? 'video' : 'image',
      };
    });

    setFiles((prev) => [...prev, ...newFilesWithPreview]);
  };

  const handleRemoveFile = (index) => {
    setFiles((prev) => {
      const target = prev[index];
      if (previewUrls.current.has(target.previewUrl)) {
        URL.revokeObjectURL(target.previewUrl);
        previewUrls.current.delete(target.previewUrl);
      }
      return prev.filter((_, i) => i !== index);
    });
  };

  // 💡 2. 폼 제출 시 청크 업로드 후 백엔드 DB로 전송하는 핵심 로직
  const submit = async (e) => {
    e.preventDefault();
    if (!title.trim() || !body.trim()) {
      toast('제목과 내용을 입력하세요');
      return;
    }
    if (isUploading) return;
    // 기존 브라우저 예시 글의 편집은 서버 게시글 생성으로 바꾸지 않는다.
    if (onSubmit && !postId && heading === '글 수정') {
      onSubmit({ tab, title, body, files: files.map((file) => file.name) });
      return;
    }
    // 인증 정보가 없으면 파일부터 업로드하지 않고 재로그인을 안내한다.
    if (!accessToken) {
      toast('로그인이 필요합니다. 다시 로그인해 주세요.');
      return;
    }

    try {
      setIsUploading(true);
      const uploadedFiles = [];

      // 파일 청크 업로드 처리
      for (const item of files) {
        if (item.file) {
          const storedFileName = await uploadFileChunks(item.file, { accessToken });
          uploadedFiles.push({
            fileId: crypto.randomUUID(),
            newUpload: true,
            originalFileName: item.file.name,
            storedFileName,
            fileSize: item.file.size,
            fileType: item.file.type,
            filePath: `/uploads/files/${encodeURIComponent(storedFileName)}`,
          });
        } else {
          uploadedFiles.push(item);
        }
      }

      // 서버로 보낼 FormData 구성
      const formData = new FormData();
      formData.append('title', title);
      formData.append('content', body);
      formData.append('boardType', tab);
      formData.append('isPublic', true);

      if (uploadedFiles.length > 0) {
        // 서버는 병합된 파일명 목록으로 게시글과 첨부파일을 연결한다.
        formData.append('savedFileNamesJson', JSON.stringify(uploadedFiles.filter((file) => file.newUpload).map((file) => file.storedFileName)));
      }

      // 💡 3. 실제 백엔드 API를 호출하여 DB에 적재!
      // 수정에서도 서버가 토큰의 회원 번호로 작성자 권한을 검사한다.
      if (postId) await updatePost(postId, formData, accessToken);
      else await createPost(formData, accessToken);

      toast(postId ? '게시글이 수정되었습니다.' : '게시글이 성공적으로 등록되었습니다.');
      navigate(ROUTES.board); // 등록 후 실제 게시판 목록을 다시 조회한다.
    } catch (error) {
      console.error('게시글 등록 실패:', error);
      toast(error.message || '게시글 등록 중 오류가 발생했습니다.');
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <Card title={heading} icon="fileText">
      <p role="status" className="small muted" style={{ marginBottom: 8 }}>
        {isUploading ? '🔄 청크 업로드 및 DB 저장 중...' : ''}
      </p>

      <form className="grid" style={{ gap: 12 }} onSubmit={submit}>
        <fieldset disabled={isUploading} style={{ border: 0, padding: 0, margin: 0, display: 'contents' }}>
          <div className="row">
            <SelectField
              id="pCat"
              label="분류"
              options={tabOptions}
              value={tab}
              onChange={(e) => setTab(e.target.value)}
            />
          </div>
          <TextField
            id="pTitle"
            label="제목"
            placeholder="제목을 입력하세요"
            maxLength={60}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          <TextAreaField
            id="pBody"
            label="내용"
            rows={10}
            placeholder="내용을 입력하세요"
            maxLength={3000}
            value={body}
            onChange={(e) => setBody(e.target.value)}
          />

          <div className="field">
            <label htmlFor="pFiles">파일 첨부 (최대 5개)</label>
            <input
              id="pFiles"
              className="in"
              type="file"
              multiple
              accept=".jpg,.jpeg,.png,.gif,.webp,.mp4,.webm,.mov"
              onChange={handleFileChange}
            />

            {files.length > 0 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 8 }}>
                {files.map((item, index) => (
                  <div
                    key={index}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: 10,
                      background: 'rgba(248, 250, 252, 0.5)',
                      padding: '8px 12px',
                      borderRadius: 8,
                      border: '1px solid var(--border, #e2e8f0)',
                    }}
                  >
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 10, minWidth: 0, flex: 1 }}>
                      {item.previewUrl ? (
                        item.mediaType === 'video' ? <video {...videoPreviewProps} src={item.previewUrl} controls preload="metadata" style={{ width: '100%', maxWidth: 560, maxHeight: 360, borderRadius: 6 }} /> : <img
                          {...imagePreviewProps}
                          src={item.previewUrl}
                          alt="미리보기"
                          style={{ display: 'block', width: '100%', maxWidth: 560, maxHeight: 360, objectFit: 'contain', borderRadius: 6 }}
                        />
                      ) : (
                        <div
                          style={{
                            width: 48,
                            height: 48,
                            background: '#e2e8f0',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            borderRadius: 6,
                            fontSize: 11,
                            flexShrink: 0,
                          }}
                        >
                          파일
                        </div>
                      )}
                      <span
                        className="small"
                        style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                      >
                        {item.file ? item.file.name : item.name}
                      </span>
                    </div>
                    <button
                      type="button"
                      onClick={() => handleRemoveFile(index)}
                      style={{
                        color: '#ef4444',
                        border: 'none',
                        background: 'none',
                        cursor: 'pointer',
                        fontWeight: 'bold',
                      }}
                    >
                      ✕
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="row" style={{ justifyContent: 'flex-end', marginTop: 12 }}>
            <Button onClick={onCancel} disabled={isUploading}>
              취소
            </Button>
            <Button type="submit" variant="primary" disabled={isUploading}>
              {submitLabel}
            </Button>
          </div>
        </fieldset>
      </form>
    </Card>
  );
}
