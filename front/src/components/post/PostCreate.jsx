import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { filterBlockedFiles, uploadFileChunks } from './post-preview'; // 분리된 JS 파일 임포트

const PostCreateView = ({ errorMessage }) => {
    const navigate = useNavigate();

    const [formData, setFormData] = useState({
        title: '',
        place: '',
        content: '',
        transportCost: 0,
        foodCost: 0,
        otherCost: 0,
        tags: '',
        shareRoute: true
    });

    const [selectedFiles, setSelectedFiles] = useState([]);
    const [previewUrls, setPreviewUrls] = useState([]);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState(errorMessage || '');

    // 입력값 변경 핸들러
    const handleChange = (e) => {
        const { name, value, type, checked } = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: type === 'checkbox' ? checked : value
        }));
    };

    // 파일 선택 및 보안 필터링 핸들러 (post-preview.js의 filterBlockedFiles 활용)
    const handleFileChange = (e) => {
        const rawFiles = Array.from(e.target.files);
        const validFiles = filterBlockedFiles(rawFiles); // 분리된 함수 사용

        if (validFiles.length < rawFiles.length) {
            alert('보안상 실행 및 압축 파일은 업로드할 수 없습니다.');
        }

        if (validFiles.length === 0) return;

        if (selectedFiles.length + validFiles.length > 5) {
            alert('이미지 및 동영상은 최대 5개까지 선택할 수 있습니다.');
            e.target.value = '';
            return;
        }

        const newFiles = [...selectedFiles, ...validFiles];
        setSelectedFiles(newFiles);

        // Preview URLs 생성
        const newPreviews = validFiles.map(file => URL.createObjectURL(file));
        setPreviewUrls(prev => [...prev, ...newPreviews]);
        e.target.value = '';
    };

    // 썸네일 삭제 핸들러
    const handleRemoveFile = (index, e) => {
        e.stopPropagation();
        setSelectedFiles(prev => prev.filter((_, i) => i !== index));
        setPreviewUrls(prev => prev.filter((_, i) => i !== index));
        setCurrentIndex(prev => Math.max(0, Math.min(prev, selectedFiles.length - 2)));
    };

    // 폼 제출 및 청크 업로드 핸들러 (post-preview.js의 uploadFileChunks 활용 가능)
    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsSubmitting(true);
        setSubmitError('');

        try {
            const savedFileNames = [];

            // 1. 파일이 있는 경우 청크 업로드 순차 진행
            if (selectedFiles.length > 0) {
                for (const file of selectedFiles) {
                    // 만약 post-preview.js에 uploadFileChunks 함수를 구현해 두었다면 아래와 같이 호출합니다.
                    // const savedName = await uploadFileChunks(file);
                    // if (savedName) savedFileNames.push(savedName);
                }
            }

            // 2. 최종 폼 데이터 전송
            const submissionData = new FormData();
            Object.keys(formData).forEach(key => {
                submissionData.append(key, formData[key]);
            });

            selectedFiles.forEach(file => {
                submissionData.append('files', file);
            });

            submissionData.append('savedFileNamesJson', JSON.stringify(savedFileNames));

            const response = await fetch('/api/posts', {
                method: 'POST',
                body: submissionData
            });

            const result = await response.json();

            if (result.success || response.ok) {
                navigate('/main-post'); // 성공 시 목록 페이지로 이동
            } else {
                throw new Error(result.message || '게시글 등록에 실패했습니다.');
            }

        } catch (error) {
            console.error('등록 에러:', error);
            setSubmitError(error.message || '오류가 발생했습니다.');
            setIsSubmitting(false);
        }
    };

    return (
        <div className="zt-app">
            <header className="zt-mobile-header">
                <a className="zt-brand" href="/home">
                    <span>post</span>
                </a>
            </header>
            
            <nav className="zt-mobile-nav" aria-label="모바일 메뉴">
                <a href="/main-post" aria-label="post"><i className="bi bi-grid-3x3-gap"></i></a>
                <a href="/new-post" className="active" aria-label="new"><i className="bi bi-plus-square"></i></a>
            </nav>

            <div className="zt-layout">
                <main className="zt-content">
                    <header className="zt-page-header">
                        <h1>새 게시물 만들기</h1>
                        <p>사진 및 동영상을 업로드합니다.</p>
                    </header>

                    <section className="zt-panel zt-profile-card">
                        {submitError && (
                            <div className="alert alert-danger" role="alert">
                                {submitError}
                            </div>
                        )}

                        <form className="row g-4" id="post-form" onSubmit={handleSubmit}>
                            <div className="col-lg-6">
                                <div className="zt-post-image-uploader">
                                    <div id="post-main-preview" className="zt-post-main-preview position-relative" style={{ minHeight: '250px', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f8f9fa', borderRadius: '8px', overflow: 'hidden' }}>
                                        
                                        {previewUrls.length === 0 ? (
                                            <div id="post-image-empty" className="zt-post-image-empty text-center p-4">
                                                <i className="bi bi-folder-plus display-5"></i>
                                                <strong>파일을 선택하세요</strong>
                                                <p className="mb-0"><small>이미지 및 동영상은 최대 5개까지 선택할 수 있습니다.</small></p>
                                            </div>
                                        ) : (
                                            <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                                                {previewUrls[currentIndex] && previewUrls[currentIndex].includes('video') ? (
                                                    <video src={previewUrls[currentIndex]} controls style={{ maxWidth: '100%', maxHeight: '250px' }} />
                                                ) : (
                                                    <img src={previewUrls[currentIndex]} alt="preview" style={{ maxWidth: '100%', maxHeight: '250px', objectFit: 'contain' }} />
                                                )}
                                            </div>
                                        )}
                                    </div>

                                    <div className="zt-post-thumbnail-row mt-2">
                                        <div id="post-thumbnail-list" className="zt-post-thumbnail-list d-flex gap-2 flex-wrap">
                                            {previewUrls.map((url, idx) => (
                                                <div key={idx} className="position-relative d-inline-block">
                                                    <img 
                                                        src={url} 
                                                        alt="thumb" 
                                                        style={{ width: '50px', height: '50px', objectFit: 'cover', cursor: 'pointer', border: currentIndex === idx ? '2px solid blue' : 'none', borderRadius: '4px' }} 
                                                        onClick={() => setCurrentIndex(idx)} 
                                                    />
                                                    <button 
                                                        type="button" 
                                                        className="btn btn-danger btn-sm position-absolute top-0 end-0 p-0 px-1" 
                                                        style={{ fontSize: '10px', lineHeight: '1' }}
                                                        onClick={(e) => handleRemoveFile(idx, e)}
                                                    >
                                                        ×
                                                    </button>
                                                </div>
                                            ))}
                                        </div>

                                        <label className="zt-post-add-image btn btn-outline-secondary mt-2 w-100" htmlFor="new-post-image">
                                            <i className="bi bi-plus-lg"></i> 파일 선택
                                            <input 
                                                id="new-post-image"
                                                name="files"
                                                type="file"
                                                accept="image/*,video/*"
                                                multiple
                                                className="d-none"
                                                onChange={handleFileChange}
                                            />
                                        </label>

                                        <p className="zt-post-image-count mt-2 text-center">
                                            <strong id="post-image-count">{selectedFiles.length}</strong> / 5
                                        </p>
                                    </div>
                                </div>
                            </div>

                            <div className="col-lg-6">
                                <div className="mb-3">
                                    <label className="form-label" htmlFor="post-title">제목</label>
                                    <input id="post-title" name="title" className="form-control" type="text" maxLength="60" placeholder="제목을 입력하세요" value={formData.title} onChange={handleChange} required />
                                </div>
                                <div className="mb-3">
                                    <label className="form-label" htmlFor="post-place">장소</label>
                                    <input id="post-place" name="place" className="form-control" type="text" placeholder="예: 서울 성수" value={formData.place} onChange={handleChange} required />
                                </div>
                                <div className="mb-3">
                                    <label className="form-label" htmlFor="post-content">내용</label>
                                    <textarea id="post-content" name="content" className="form-control" rows="3" placeholder="내용을 적어 주세요." value={formData.content} onChange={handleChange} required />
                                </div>
                                <div className="mb-3">
                                    <label className="form-label" htmlFor="transport-cost">교통비</label>
                                    <input id="transport-cost" name="transportCost" className="form-control" type="number" min="0" value={formData.transportCost} onChange={handleChange} required />
                                </div>
                                <div className="mb-3">
                                    <label className="form-label" htmlFor="food-cost">식비</label>
                                    <input id="food-cost" name="foodCost" className="form-control" type="number" min="0" value={formData.foodCost} onChange={handleChange} required />
                                </div>
                                <div className="mb-3">
                                    <label className="form-label" htmlFor="other-cost">입장료 및 기타 비용</label>
                                    <input id="other-cost" name="otherCost" className="form-control" type="number" min="0" value={formData.otherCost} onChange={handleChange} required />
                                </div>
                                <div className="mb-3">
                                    <label className="form-label" htmlFor="post-tags">태그</label>
                                    <input id="post-tags" name="tags" className="form-control" type="text" placeholder="#여행 #가성비" value={formData.tags} onChange={handleChange} />
                                </div>
                                <div className="form-check form-switch mb-4">
                                    <input id="share-route" name="shareRoute" className="form-check-input" type="checkbox" checked={formData.shareRoute} onChange={handleChange} />
                                    <label className="form-check-label" htmlFor="share-route"> 동선 공개</label>
                                </div>
                                <button className="btn btn-primary zt-primary-btn w-100 py-2" type="submit" disabled={isSubmitting}>
                                    {isSubmitting ? '등록 중...' : '작성 완료'}
                                </button>
                            </div>
                        </form>
                    </section>
                </main>
            </div>
        </div>
    );
};

export default PostCreateView;