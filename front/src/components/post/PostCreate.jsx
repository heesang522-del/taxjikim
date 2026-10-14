import { uploadFileChunks, filterBlockedFiles } from '../../api/post-preview';
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { saveAnonymousPost } from '../../api/anonymousPosts';

export default function PostCreate() {
  const navigate = useNavigate();
  const [isUploading, setIsUploading] = useState(false);

  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [boardType, setBoardType] = useState('INFO'); // DB 스키마: INFO / TIP / NOTICE / FAQ / QNA
  const [jobCategory, setJobCategory] = useState('');
  const [isPublic, setIsPublic] = useState(true); // QNA 전용 공개 여부
  const [files, setFiles] = useState([]); // { file, previewUrl } 형태의 파일 리스트 (최대 5개)

  // 파일 선택 핸들러 (최대 5개 제한)
  const handleFileChange = (e) => {
    const pickedFiles = Array.from(e.target.files);
    const selectedFiles = filterBlockedFiles(pickedFiles);
    if (selectedFiles.length !== pickedFiles.length) {
      alert('사진·동영상만 첨부할 수 있습니다. PDF·ZIP·실행파일은 차단됩니다.');
    }
    e.target.value = '';

    if (files.length + selectedFiles.length > 5) {
      alert('첨부파일은 최대 5개까지 등록할 수 있습니다.');
      return;
    }

    const newFilesWithPreview = selectedFiles.map((file) => ({
      file: file,
      previewUrl: file.type.startsWith('image/') ? URL.createObjectURL(file) : null,
    }));

    setFiles((prev) => [...prev, ...newFilesWithPreview]);
  };

  // 개별 파일 삭제
  const handleRemoveFile = (index) => {
    setFiles((prev) => {
      const target = prev[index];
      if (target.previewUrl) {
        URL.revokeObjectURL(target.previewUrl);
      }
      return prev.filter((_, i) => i !== index);
    });
  };

  // 폼 제출 핸들러 (청크 업로드 수행 후 서버로 데이터 전송)
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (isUploading) return;

    const formData = new FormData();
    formData.append('title', title);
    formData.append('content', content);
    formData.append('boardType', boardType);
    formData.append('jobCategory', jobCategory);
    formData.append('isPublic', isPublic);

    try {
      setIsUploading(true);
      const uploadedFiles = [];
      
      // 1. 선택한 파일들을 청크 단위로 서버에 업로드
      for (const file of files.map((item) => item.file)) {
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
      
      // 2. 업로드된 파일의 메타데이터를 JSON 문자열로 변환하여 FormData에 추가
      formData.append('uploadedFiles', JSON.stringify(uploadedFiles));
      
      // 3. 익명 게시글은 브라우저에 저장하고 파일은 서버의 청크 병합 결과를 연결한다.
      await saveAnonymousPost(formData);
      
      alert('게시글이 성공적으로 등록되었습니다.');
      navigate('/main-post');
    } catch (error) {
      console.error('게시글 등록 실패:', error);
      alert(error.message || '게시글 등록에 실패했습니다.');
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <div className="max-w-2xl mx-auto p-6 bg-white shadow-md rounded-lg mt-10">
      <h2 className="text-2xl font-bold mb-6 text-gray-800">새 게시글 작성</h2>
      <p className="text-sm text-gray-500 mb-4">첨부파일은 청크 방식으로 서버에 업로드됩니다.</p>
      <p role="status" className="text-blue-600 font-medium mb-2">{isUploading ? '청크 업로드 및 병합 중...' : ''}</p>
      
      <form onSubmit={handleSubmit} className="space-y-4">
        <fieldset disabled={isUploading} style={{ border: 0, padding: 0 }}>
          {/* 1. 게시판 유형 (DB: POSTS.BOARD_TYPE) */}
          <div>
            <label className="block text-sm font-medium text-gray-700">게시판 유형</label>
            <select
              value={boardType}
              onChange={(e) => setBoardType(e.target.value)}
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm p-2 border"
            >
              <option value="INFO">정보 공유 (INFO)</option>
              <option value="TIP">팁 게시판 (TIP)</option>
              <option value="QNA">문의하기 (QNA)</option>
            </select>
          </div>

          {/* QNA 선택 시 비공개 여부 설정 (DB: POSTS.IS_PUBLIC) */}
          {boardType === 'QNA' && (
            <div className="flex items-center space-x-2 bg-gray-50 p-3 rounded border">
              <input
                type="checkbox"
                checked={!isPublic}
                onChange={(e) => setIsPublic(!e.target.checked)}
                id="isPrivate"
                className="rounded border-gray-300"
              />
              <label htmlFor="isPrivate" className="text-sm font-medium text-gray-700">
                비공개로 설정하기
              </label>
            </div>
          )}

          {/* 2. 직군 태그 (DB: POSTS.JOB_CATEGORY) */}
          <div>
            <label className="block text-sm font-medium text-gray-700">직군 태그</label>
            <input
              type="text"
              value={jobCategory}
              onChange={(e) => setJobCategory(e.target.value)}
              placeholder="예: 개발자, 프리랜서 등"
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm p-2 border"
            />
          </div>

          {/* 3. 제목 (DB: POSTS.TITLE) */}
          <div>
            <label className="block text-sm font-medium text-gray-700">제목</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
              placeholder="제목을 입력하세요"
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm p-2 border"
            />
          </div>

          {/* 4. 내용 (DB: POSTS.CONTENT) */}
          <div>
            <label className="block text-sm font-medium text-gray-700">내용</label>
            <textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              rows="5"
              required
              placeholder="내용을 입력하세요"
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm p-2 border"
            />
          </div>

          {/* 5. 첨부파일 (DB: FILE_META 통합 관리, 최대 5개) */}
          <div>
            <label className="block text-sm font-medium text-gray-700">첨부파일 (최대 5개)</label>
            <input
              type="file"
              multiple
              accept="image/*,.pdf,.hwp,.txt"
              onChange={handleFileChange}
              className="mt-1 block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
            />

            {/* 선택된 파일 리스트 및 미리보기 */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-3">
              {files.map((item, idx) => (
                <div key={idx} className="flex items-center justify-between bg-gray-50 p-2 rounded border">
                  <div className="flex items-center space-x-2 overflow-hidden">
                    {item.previewUrl ? (
                      <img
                        src={item.previewUrl}
                        alt="미리보기"
                        className="w-16 h-16 object-cover rounded flex-shrink-0 border"
                      />
                    ) : (
                      <div className="w-16 h-16 bg-gray-200 flex items-center justify-center rounded text-xs text-gray-500 flex-shrink-0">
                        파일
                      </div>
                    )}
                    <span className="text-sm text-gray-700 truncate">{item.file.name}</span>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleRemoveFile(idx)}
                    className="text-red-500 hover:text-red-700 text-sm px-2 font-bold"
                  >
                    ✕
                  </button>
                </div>
              ))}
            </div>
          </div>

          {/* 제출 버튼 */}
          <button
            disabled={isUploading}
            type="submit"
            className="w-full bg-blue-600 text-white py-2 px-4 rounded-md hover:bg-blue-700 font-medium mt-4"
          >
            등록하기
          </button>
        </fieldset>
      </form>
    </div>
  );
}
