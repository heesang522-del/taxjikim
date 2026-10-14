import { uploadFileChunks, filterBlockedFiles } from '../../api/post-preview';
import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { getAnonymousPost, saveAnonymousPost } from '../../api/anonymousPosts';

export default function PostEdit() {
  const navigate = useNavigate();
  const [isUploading, setIsUploading] = useState(false);
  const [searchParams] = useSearchParams();
  const postId = searchParams.get('postId');

  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [boardType, setBoardType] = useState('INFO');
  const [jobCategory, setJobCategory] = useState('');
  const [isPublic, setIsPublic] = useState(true);

  const [existingFiles, setExistingFiles] = useState([]); // 기존 서버에 등록된 파일 메타 리스트 (FILE_META)
  const [deleteFileIds, setDeleteFileIds] = useState([]); // 삭제할 파일 ID 목록
  const [newFiles, setNewFiles] = useState([]); // 새로 추가할 파일 리스트

  const [errorMessage, setErrorMessage] = useState('');
  const [isLoaded, setIsLoaded] = useState(false);
  useEffect(() => {
    let active = true;
    setIsLoaded(false);
    setErrorMessage('');
    getAnonymousPost(postId)
      .then((data) => {
        if (!active) return;
        setIsLoaded(true);
        setTitle(data.title || '');
        setContent(data.content || '');
        setBoardType(data.boardType || 'INFO');
        setJobCategory(data.jobCategory || '');
        setIsPublic(data.isPublic ?? true);
        setExistingFiles(data.files || []); // 🌟 통합 FILE_META 목록 바인딩
      })
      .catch((err) => {
        if (active) setErrorMessage(err.message);
      });
    return () => {
      active = false;
    };
  }, [postId]);

  // 기존 파일 삭제 대기열 추가
  const handleRemoveExistingFile = (fileId) => {
    setDeleteFileIds((prev) => [...prev, fileId]);
    setExistingFiles((prev) => prev.filter((file) => file.fileId !== fileId));
  };

  // 새 파일 추가 (최대 개수 5개 제한: 기존 잔여 파일 + 신규 파일)
  const handleNewFileChange = (e) => {
    const pickedFiles = Array.from(e.target.files);
    const selectedFiles = filterBlockedFiles(pickedFiles);
    if (selectedFiles.length !== pickedFiles.length) {
      alert('사진·동영상만 첨부할 수 있습니다. PDF·ZIP·실행파일은 차단됩니다.');
    }
    e.target.value = '';

    const totalCount = existingFiles.length + newFiles.length + selectedFiles.length;
    if (totalCount > 5) {
      alert('파일은 최대 5개까지 등록할 수 있습니다.');
      return;
    }
    setNewFiles((prev) => [...prev, ...selectedFiles]);
  };

  const handleRemoveNewFile = (index) => {
    setNewFiles((prev) => prev.filter((_, i) => i !== index));
  };

  // 수정 제출
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (isUploading) return;

    const formData = new FormData();
    formData.append('title', title);
    formData.append('content', content);
    formData.append('boardType', boardType);
    formData.append('jobCategory', jobCategory);
    formData.append('isPublic', isPublic);

    // 삭제할 파일 ID 배열 전송 (백엔드의 deleteFileIds와 일치)
    deleteFileIds.forEach((id) => {
      formData.append('deleteFileIds', id);
    });

    // 새로 추가된 파일 전송
    newFiles.forEach((file) => {
      formData.append('files', file);
    });

    try {
      setIsUploading(true);
      const uploadedFiles = [];
      // 기존 청크 함수를 순서대로 호출하고, 서버 병합 성공 후에만 게시글에 연결한다.
      for (const file of newFiles) {
        const storedFileName = await uploadFileChunks(file, { anonymous: true });
        uploadedFiles.push({
          fileId: crypto.randomUUID(),
          originalFileName: file.name,
          storedFileName,
          fileSize: file.size,
          fileType: file.type,
          filePath: `/uploads/files/${encodeURIComponent(storedFileName)}`,
        });
      }
      formData.set('uploadedFiles', JSON.stringify(uploadedFiles));
      await saveAnonymousPost(formData, postId);
      alert('수정 완료되었습니다.');
      navigate(`/detail?postId=${postId}`);
    } catch (error) {
      console.error('수정 실패:', error);
      alert(error.message || '수정 중 오류가 발생했습니다.');
    } finally {
      setIsUploading(false);
    }
  };

  if (errorMessage)
    return (
      <p role="alert">
        {errorMessage} <button onClick={() => navigate('/main-post')}>목록으로</button>
      </p>
    );
  if (!isLoaded) return <p>불러오는 중...</p>;
  return (
    <div className="max-w-2xl mx-auto p-6 bg-white shadow-md rounded-lg mt-10">
      <h2 className="text-2xl font-bold mb-6 text-gray-800">게시글 수정</h2>
      <p>익명 테스트 · 작성자: 익명 · 첨부파일은 기존 청크 방식으로 서버에 업로드합니다.</p>
      <p role="status">{isUploading ? '청크 업로드 및 병합 중...' : ''}</p>
      <form onSubmit={handleSubmit} className="space-y-4">
        <fieldset disabled={isUploading} style={{ border: 0, padding: 0 }}>
          <div>
            <label className="block text-sm font-medium text-gray-700">제목</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm p-2 border"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700">내용</label>
            <textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              rows="5"
              required
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm p-2 border"
            />
          </div>

          {/* 기존 첨부파일 목록 */}
          <div>
            <label className="block text-sm font-medium text-gray-700">기존 첨부파일</label>
            <ul className="mt-2 space-y-1">
              {existingFiles.map((file) => (
                <li key={file.fileId} className="flex justify-between items-center text-sm bg-gray-100 p-2 rounded">
                  <span>{file.originalFileName}</span>
                  <button
                    type="button"
                    onClick={() => handleRemoveExistingFile(file.fileId)}
                    className="text-red-500 hover:text-red-700 font-semibold"
                  >
                    X 삭제
                  </button>
                </li>
              ))}
            </ul>
          </div>

          {/* 새 파일 추가 */}
          <div>
            <label className="block text-sm font-medium text-gray-700">새 파일 첨부 (최대 5개 제한)</label>
            <input
              type="file"
              accept=".jpg,.jpeg,.png,.gif,.webp,.mp4,.webm,.mov"
              multiple
              onChange={handleNewFileChange}
              className="mt-1 block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-sm file:font-semibold file:bg-green-50 file:text-green-700 hover:file:bg-green-100"
            />
            <ul className="mt-2 space-y-1">
              {newFiles.map((file, idx) => (
                <li key={idx} className="flex justify-between items-center text-sm bg-gray-50 p-2 rounded">
                  <span>{file.name} (신규)</span>
                  <button
                    type="button"
                    onClick={() => handleRemoveNewFile(idx)}
                    className="text-red-500 hover:text-red-700"
                  >
                    취소
                  </button>
                </li>
              ))}
            </ul>
          </div>

          <button
            disabled={isUploading}
            type="submit"
            className="w-full bg-green-600 text-white py-2 px-4 rounded-md hover:bg-green-700 font-medium"
          >
            수정 완료
          </button>
        </fieldset>
      </form>
    </div>
  );
}
