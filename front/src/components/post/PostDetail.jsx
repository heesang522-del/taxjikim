import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';

export default function PostDetail() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const postId = searchParams.get('postId');

    const [post, setPost] = useState(null);

    useEffect(() => {
        if (!postId) return;
        axios.get(`/api/posts/${postId}`) // 혹은 상세 단건 조회 API 경로
            .then((res) => {
                setPost(res.data.data || res.data);
            })
            .catch((err) => console.error('상세 조회 실패:', err));
    }, [postId]);

    const handleDelete = async () => {
        if (window.confirm('정말 삭제하시겠습니까?')) {
            try {
                await axios.delete(`/api/posts/${postId}`);
                alert('삭제되었습니다.');
                navigate('/main-post');
            } catch (err) {
                console.error('삭제 실패:', err);
                alert('삭제 중 오류가 발생했습니다.');
            }
        }
    };

    if (!post) return <div className="text-center mt-10">로딩 중...</div>;

    return (
        <div className="max-w-2xl mx-auto p-6 bg-white shadow-md rounded-lg mt-10">
            <div className="border-b pb-4 mb-4">
                <span className="text-xs bg-blue-100 text-blue-800 font-semibold px-2.5 py-0.5 rounded">
                    {post.boardType}
                </span>
                {post.jobCategory && (
                    <span className="ml-2 text-xs bg-gray-100 text-gray-800 px-2.5 py-0.5 rounded">
                        {post.jobCategory}
                    </span>
                )}
                <h1 className="text-2xl font-bold mt-2 text-gray-900">{post.title}</h1>
                <p className="text-sm text-gray-500 mt-1">작성일: {post.createdAt || post.createAt}</p>
            </div>

            <div className="mb-6 text-gray-700 whitespace-pre-wrap min-h-[150px]">
                {post.content}
            </div>

            {/* 통합 파일 메타데이터(post.files) 목록 렌더링 */}
            {post.files && post.files.length > 0 && (
                <div className="mb-6 border-t pt-4">
                    <h3 className="text-sm font-semibold text-gray-700 mb-2">첨부 파일</h3>
                    <ul className="space-y-1">
                        {post.files.map((file) => (
                            <li key={file.fileId} className="text-sm">
                                <a
                                    href={`/uploads/files/${file.storedFileName}`}
                                    download={file.originalFileName}
                                    className="text-blue-600 hover:underline flex items-center"
                                >
                                    📎 {file.originalFileName} ({Math.round(file.fileSize / 1024)} KB)
                                </a>
                            </li>
                        ))}
                    </ul>
                </div>
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