import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
// 익명 관련 API 대신 일반 게시글 API로 변경하거나 프로젝트에 맞게 수정하세요
import { getPost, deletePost } from '../../api/posts';

// 미디어 타입 판별 함수
const getMediaType = (file) => {
  if (file.fileType?.startsWith('image/')) return 'image';
  if (file.fileType?.startsWith('video/')) return 'video';
  const name = file.originalFileName || '';
  if (/\.(png|jpe?g|gif|webp|avif|bmp)$/i.test(name)) return 'image';
  if (/\.(mp4|webm|ogv|mov|m4v)$/i.test(name)) return 'video';
  return null;
};

export default function PostDetail() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const postId = searchParams.get('postId');

  const [post, setPost] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [errorMessage, setErrorMessage] = useState('');

  // 게시글 상세 데이터 불러오기
  useEffect(() => {
    let active = true;
    setPost(null);
    setSelectedFile(null);
    setErrorMessage('');

    getPost(postId)
      .then((data) => {
        if (active) setPost(data);
      })
      .catch((error) => {
        if (active) setErrorMessage(error.message || '게시글을 불러오지 못했습니다.');
      });

    return () => {
      active = false;
    };
  }, [postId]);

  // 게시글 삭제 핸들러
  const handleDelete = async () => {
    if (window.confirm('정말 삭제하시겠습니까?')) {
      try {
        await deletePost(postId);
        alert('삭제되었습니다.');
        navigate('/main-post');
      } catch (err) {
        console.error('삭제 실패:', err);
        alert('삭제 중 오류가 발생했습니다.');
      }
    }
  };

  if (errorMessage) {
    return (
      <div role="alert" className="p-6 text-center">
        <p className="text-red-500 mb-4">{errorMessage}</p>
        <button onClick={() => navigate('/main-post')} className="bg-gray-500 text-white px-4 py-2 rounded">
          목록으로
        </button>
      </div>
    );
  }

  if (!post) return <div className="text-center mt-10">로딩 중...</div>;

  return (
    <div className="max-w-2xl mx-auto p-6 bg-white shadow-md rounded-lg mt-10">
      {/* 게시글 헤더 영역 */}
      <div className="border-b pb-4 mb-4">
        <span className="text-xs bg-blue-100 text-blue-800 font-semibold px-2.5 py-0.5 rounded">{post.boardType}</span>
        {post.jobCategory && (
          <span className="ml-2 text-xs bg-gray-100 text-gray-800 px-2.5 py-0.5 rounded">{post.jobCategory}</span>
        )}
        <h1 className="text-2xl font-bold mt-2 text-gray-900">{post.title}</h1>
        <p className="text-sm text-gray-500 mt-1">작성일: {post.createdAt || post.createAt}</p>
      </div>

      {/* 본문 내용 */}
      <div className="mb-6 text-gray-700 whitespace-pre-wrap min-h-[150px]">{post.content}</div>

      {/* 첨부 파일 목록 및 미리보기 트리거 */}
      {post.files && post.files.length > 0 && (
        <div className="mb-6 border-t pt-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-2">첨부 파일</h3>
          <ul className="space-y-2">
            {post.files.map((file) => {
              const mediaType = getMediaType(file);
              return (
                <li
                  key={file.fileId || file.originalFileName}
                  className="text-sm flex items-center justify-between bg-gray-50 p-2 rounded"
                >
                  <span>📎 {file.originalFileName}</span>
                  {mediaType ? (
                    <button
                      type="button"
                      onClick={() => setSelectedFile(file)}
                      className="text-blue-600 hover:underline font-medium text-xs bg-blue-50 px-2 py-1 rounded border border-blue-200"
                    >
                      {mediaType === 'image' ? '🖼️ 사진 보기' : '▶️ 동영상 보기'}
                    </button>
                  ) : (
                    <span className="text-gray-400 text-xs">(미리보기 미지원)</span>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      )}

      {/* 선택한 사진·동영상 미리보기 섹션 */}
      {selectedFile && (
        <section aria-label="첨부파일 미리보기" className="mb-6 p-4 border rounded-lg bg-gray-50">
          <div className="flex justify-between items-center mb-2">
            <span className="text-sm font-semibold text-gray-700">미리보기: {selectedFile.originalFileName}</span>
            <button
              type="button"
              onClick={() => setSelectedFile(null)}
              className="text-red-500 hover:text-red-700 text-xs font-bold px-2 py-1 bg-white border rounded"
            >
              ✕ 닫기
            </button>
          </div>

          <div className="flex justify-center bg-black rounded overflow-hidden">
            {getMediaType(selectedFile) === 'image' ? (
              <img
                src={selectedFile.filePath}
                alt={selectedFile.originalFileName}
                draggable={false}
                onContextMenu={(event) => event.preventDefault()}
                className="max-h-[70vh] object-contain"
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
                className="max-h-[70vh] w-full"
              >
                브라우저가 동영상 재생을 지원하지 않습니다.
              </video>
            )}
          </div>
        </section>
      )}

      {/* 하단 버튼 그룹 */}
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
