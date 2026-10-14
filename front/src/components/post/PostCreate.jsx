import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

export default function PostCreate() {
    const navigate = useNavigate();

    const [title, setTitle] = useState('');
    const [content, setContent] = useState('');
    const [boardType, setBoardType] = useState('INFO'); // DB 스키마: INFO / TIP / NOTICE / FAQ / QNA
    const [jobCategory, setJobCategory] = useState('');
    const [isPublic, setIsPublic] = useState(true); // QNA 전용 공개 여부
    const [files, setFiles] = useState([]); // { file, previewUrl } 형태의 파일 리스트 (최대 5개)

    // 파일 선택 핸들러 (최대 5개 제한)
    const handleFileChange = (e) => {
        const selectedFiles = Array.from(e.target.files);
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

    // 등록 제출 (POSTS 및 FILE_META 연동 - 토큰 인증 포함)
    const handleSubmit = async (e) => {
        e.preventDefault();

        // 🌟 로컬 스토리지에서 실제 로그인 토큰 가져오기 
        // (로그인 시 저장했던 키 이름과 일치해야 합니다. 예: 'token' 또는 'accessToken')
        const token = localStorage.getItem('token') || localStorage.getItem('accessToken');

        // 토큰이 없으면 사전에 차단
        if (!token) {
            alert('로그인 후 이용 가능한 서비스입니다. 로그인 페이지로 이동합니다.');
            navigate('/login');
            return;
        }

        const formData = new FormData();
        formData.append('title', title);
        formData.append('content', content);
        formData.append('boardType', boardType);
        formData.append('jobCategory', jobCategory);
        formData.append('isPublic', isPublic);

        // FILE_META 테이블과 매핑될 파일들 전송
        files.forEach((item) => {
            formData.append('files', item.file);
        });

        try {
            await axios.post('/api/posts', formData, {
                headers: { 
                    'Content-Type': 'multipart/form-data',
                    'Authorization': `Bearer ${token}` // 🌟 실제 토큰 동봉
                },
            });
            alert('게시글이 성공적으로 등록되었습니다.');
            navigate('/main-post');
        } catch (error) {
            console.error('게시글 등록 실패:', error);
            
            // 401 Unauthorized 에러 처리
            if (error.response && error.response.status === 401) {
                alert('세션이 만료되었거나 로그인되지 않았습니다. 다시 로그인해 주세요.');
                localStorage.removeItem('token');
                localStorage.removeItem('accessToken');
                navigate('/login');
            } else {
                alert('게시글 등록 중 오류가 발생했습니다. 서버 설정을 확인해주세요.');
            }
        }
    };

    return (
        <div className="max-w-2xl mx-auto p-6 bg-white shadow-md rounded-lg mt-10">
            <h2 className="text-2xl font-bold mb-6 text-gray-800">새 게시글 작성</h2>
            <form onSubmit={handleSubmit} className="space-y-4">
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
                            비공개 문의로 설정 (본인과 관리자만 조회 가능)
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
                    type="submit"
                    className="w-full bg-blue-600 text-white py-2 px-4 rounded-md hover:bg-blue-700 font-medium"
                >
                    등록하기
                </button>
            </form>
        </div>
    );
}