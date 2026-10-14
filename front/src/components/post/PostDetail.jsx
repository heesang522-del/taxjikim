import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

function getMediaType(file) {
  if (file.fileType?.startsWith('image/')) return 'image';
  if (file.fileType?.startsWith('video/')) return 'video';
  const name = file.originalFileName || '';
  if (/\.(png|jpe?g|gif|webp|avif|bmp)$/i.test(name)) return 'image';
  if (/\.(mp4|webm|ogv|mov|m4v)$/i.test(name)) return 'video';
  return null;
}

export default function PostDetail() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const postId = searchParams.get('postId');

  const [post, setPost] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);

  const [errorMessage, setErrorMessage] = useState('');
  useEffect(() => {
    let active = true;
    setPost(null);
    setSelectedFile(null);
    setErrorMessage('');
    getAnonymousPost(postId)
      .then((data) => {
        if (active) setPost(data);
      })
      .catch((error) => {
        if (active) setErrorMessage(error.message);
      });
    return () => {
      active = false;
    };
  }, [postId]);

  const handleDelete = async () => {
    if (window.confirm('정말 삭제하시겠습니까?')) {
      try {
        await deleteAnonymousPost(postId);
        alert('삭제되었습니다.');
        navigate('/main-post');
      } catch (err) {
        console.error('삭제 실패:', err);
        alert('삭제 중 오류가 발생했습니다.');
      }
    }
  };

  if (errorMessage)
    return (
      <div role="alert">
        {errorMessage} <button onClick={() => navigate('/main-post')}>목록으로</button>
      </div>
    );
  if (!post) return <div className="text-center mt-10">로딩 중...</div>;

  return (
    <div className="max-w-2xl mx-auto p-6 bg-white shadow-md rounded-lg mt-10">
      <p>익명 테스트 · 작성자: 익명 · 이 브라우저에만 저장됩니다.</p>
      <div className="border-b pb-4 mb-4">
        <span className="text-xs bg-blue-100 text-blue-800 font-semibold px-2.5 py-0.5 rounded">{post.boardType}</span>
        {post.jobCategory && (
          <span className="ml-2 text-xs bg-gray-100 text-gray-800 px-2.5 py-0.5 rounded">{post.jobCategory}</span>
        )}
        <h1 className="text-2xl font-bold mt-2 text-gray-900">{post.title}</h1>
        <p className="text-sm text-gray-500 mt-1">작성일: {post.createdAt || post.createAt}</p>
      </div>

      <div className="mb-6 text-gray-700 whitespace-pre-wrap min-h-[150px]">{post.content}</div>

      {/* 통합 파일 메타데이터(post.files) 목록 렌더링 */}
      {post.files && post.files.length > 0 && (
        <div className="mb-6 border-t pt-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-2">첨부 파일</h3>
          <ul className="space-y-1">
            {post.files.map((file) => (
              <li key={file.fileId} className="text-sm">
                {getMediaType(file) ? (
                  <button type="button" onClick={() => setSelectedFile(file)}>
                    {getMediaType(file) === 'image' ? '사진 보기' : '동영상 보기'}: {file.originalFileName}
                  </button>
                ) : (
                  <span>{file.originalFileName} (미리보기 미지원)</span>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* 파일 링크 대신 선택한 사진·동영상을 페이지 안에서만 미리보기로 표시한다. */}
      {selectedFile && (
        <section aria-label="첨부파일 미리보기" style={{ margin: '16px 0', padding: 16, border: '1px solid #ddd' }}>
          <p>{selectedFile.originalFileName}</p>
          <button type="button" onClick={() => setSelectedFile(null)}>
            미리보기 닫기
          </button>
          {getMediaType(selectedFile) === 'image' ? (
            <img
              src={selectedFile.filePath}
              alt={selectedFile.originalFileName}
              draggable={false}
              onContextMenu={(event) => event.preventDefault()}
              style={{ display: 'block', maxWidth: '100%', maxHeight: '70vh', marginTop: 12 }}
            />
          ) : (
            <video
              key={selectedFile.fileId}
              src={selectedFile.filePath}
              controls
              controlsList="nodownload"
              disablePictureInPicture
              preload="metadata"
              onContextMenu={(event) => event.preventDefault()}
              style={{ display: 'block', maxWidth: '100%', maxHeight: '70vh', marginTop: 12 }}
            />
          )}
        </section>
      )}

      <div className="flex justify-end space-x-2 border-t pt-4">
        <button
          onClick={() => navigate(`/edit-post?postId=${postId}`)}
          className="bg-yellow-500 text-white px-4 py-2 rounded hover:bg-yellow-600 text-sm font-medium"
        >
          수정
        </button>
        <button
          onClick={handleDelete}
          className="bg-red-600 text-white px-4 py-2 rounded hover:bg-red-700 text-sm font-medium"
        >
          삭제
        </button>
        <button
          onClick={() => navigate('/main-post')}
          className="bg-gray-500 text-white px-4 py-2 rounded hover:bg-gray-600 text-sm font-medium"
        >
          목록으로
        </button>
      </div>
    </div>
  );
}
