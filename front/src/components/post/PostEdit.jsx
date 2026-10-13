import React, { useState } from 'react';


const PostEditView = ({ post = {}, onUpdatePost, errorMessage }) => {
    // 폼 입력 필드 상태 초기화 (기존 post 데이터 반영)
    const [formData, setFormData] = useState({
        postId: post.postId || '',
        title: post.title || '',
        place: post.place || '',
        content: post.content || '',
        transportCost: post.transportCost || 0,
        foodCost: post.foodCost || 0,
        otherCost: post.otherCost || 0,
    });

    // 삭제할 기존 이미지 ID 목록 상태
    const [deleteImageIds, setDeleteImageIds] = useState([]);

    // 새로 추가할 파일 상태 및 미리보기
    const [newFiles, setNewFiles] = useState([]);
    const [previewUrls, setPreviewUrls] = useState([]);
    const [currentIndex, setCurrentIndex] = useState(0);

    // 입력값 변경 핸들러
    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: value
        }));
    };

    // 기존 이미지 삭제 체크박스 핸들러
    const handleCheckboxChange = (uploadId) => {
        setDeleteImageIds(prev => 
            prev.includes(uploadId) 
                ? prev.filter(id => id !== uploadId) 
                : [...prev, uploadId]
        );
    };

    // 새 파일 추가 핸들러 (최대 5개 제한)
    const handleFileChange = (e) => {
        const files = Array.from(e.target.files);
        if (newFiles.length + files.length > 5) {
            alert('이미지 및 동영상은 최대 5개까지 업로드 가능합니다.');
            return;
        }

        const updatedFiles = [...newFiles, ...files];
        setNewFiles(updatedFiles);

        // 미리보기 URL 생성
        const newPreviews = files.map(file => URL.createObjectURL(file));
        setPreviewUrls(prev => [...prev, ...newPreviews]);
    };

    // 슬라이드 이동 함수
    const moveSlide = (direction) => {
        if (previewUrls.length === 0) return;
        let newIndex = currentIndex + direction;
        if (newIndex < 0) {
            newIndex = previewUrls.length - 1;
        } else if (newIndex >= previewUrls.length) {
            newIndex = 0;
        }
        setCurrentIndex(newIndex);
    };

    // 폼 제출 핸들러
    const handleSubmit = (e) => {
        e.preventDefault();
        const submissionData = new FormData();
        
        // 기본 텍스트 필드 추가
        Object.keys(formData).forEach(key => {
            submissionData.append(key, formData[key]);
        });

        // 삭제할 기존 이미지 ID 추가
        deleteImageIds.forEach(id => {
            submissionData.append('deleteImageIds', id);
        });

        // 새로 첨부한 파일들 추가
        newFiles.forEach(file => {
            submissionData.append('files', file);
        });

        if (onUpdatePost) {
            onUpdatePost(submissionData);
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
                <a href="/home" aria-label="home"><i className="bi bi-house"></i></a>
                <a href="/main-post" aria-label="post"><i className="bi bi-grid-3x3-gap"></i></a>
                <a href="/new-post" aria-label="new"><i className="bi bi-plus-square"></i></a>
            </nav>

            <div className="zt-layout">
                <main className="zt-content">
                    <header className="zt-page-header">
                        <h1>게시물 수정하기</h1>
                        <p>사진 및 동영상, 게시물 정보를 수정합니다.</p>
                    </header>

                    <section className="zt-panel zt-profile-card">
                        {errorMessage && (
                            <div className="alert alert-danger" role="alert">
                                {errorMessage}
                            </div>
                        )}

                        <form className="row g-4" id="post-form" onSubmit={handleSubmit}>
                            <input type="hidden" name="postId" value={formData.postId} />

                            <div className="col-lg-6">
                                <div className="zt-post-image-uploader">
                                    
                                    {/* 새로 선택한 파일 프리뷰 및 슬라이드 UI */}
                                    <div className="mb-4">
                                        <label className="form-label fw-bold">새로 첨부할 파일 미리보기</label>
                                        <div className="position-relative d-flex align-items-center justify-content-center border rounded bg-dark overflow-hidden" style={{ height: '250px' }}>
                                            
                                            {previewUrls.length > 0 && (
                                                <button type="button" onClick={() => moveSlide(-1)} className="btn btn-light btn-sm position-absolute start-0 ms-2" style={{ zIndex: 10, opacity: 0.8 }}>
                                                    <i className="bi bi-chevron-left"></i>
                                                </button>
                                            )}

                                            <div id="post-main-preview" className="text-white text-center w-100 h-100 d-flex align-items-center justify-content-center">
                                                {previewUrls.length === 0 ? (
                                                    <div className="text-muted d-flex flex-column align-items-center justify-content-center">
                                                        <i className="bi bi-image fs-1 mb-1"></i>
                                                        <small>선택된 파일이 없습니다.</small>
                                                    </div>
                                                ) : (
                                                    previewUrls[currentIndex].includes('video') ? (
                                                        <video src={previewUrls[currentIndex]} controls style={{ maxWidth: '100%', maxHeight: '100%' }} />
                                                    ) : (
                                                        <img src={previewUrls[currentIndex]} alt="preview" style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
                                                    )
                                                )}
                                            </div>

                                            {previewUrls.length > 0 && (
                                                <button type="button" onClick={() => moveSlide(1)} className="btn btn-light btn-sm position-absolute end-0 me-2" style={{ zIndex: 10, opacity: 0.8 }}>
                                                    <i className="bi bi-chevron-right"></i>
                                                </button>
                                            )}

                                            <span id="post-image-count" className="position-absolute bottom-0 end-0 badge bg-secondary m-2" style={{ fontSize: '11px' }}>
                                                {previewUrls.length > 0 ? `${currentIndex + 1} / ${previewUrls.length}` : '0'}
                                            </span>
                                        </div>
                                    </div>

                                    {/* 기존 업로드된 파일 및 삭제 체크박스 영역 */}
                                    <div className="mb-3">
                                        <label className="form-label fw-bold">기존 첨부파일 (삭제할 항목을 체크하세요)</label>
                                        <div className="d-flex flex-wrap gap-2">
                                            {post.images && post.images.length > 0 ? (
                                                post.images.map((image) => (
                                                    <div key={image.uploadId} className="border p-2 rounded text-center bg-light" style={{ width: '110px' }}>
                                                        <span className="d-block text-truncate small mb-1" style={{ maxWidth: '100px' }}>{image.originName}</span>
                                                        <div className="form-check d-flex justify-content-center align-items-center gap-1">
                                                            <input 
                                                                className="form-check-input" 
                                                                type="checkbox" 
                                                                value={image.uploadId} 
                                                                id={`del-${image.uploadId}`}
                                                                checked={deleteImageIds.includes(image.uploadId)}
                                                                onChange={() => handleCheckboxChange(image.uploadId)}
                                                            />
                                                            <label className="form-check-label small text-danger fw-bold" htmlFor={`del-${image.uploadId}`}>삭제</label>
                                                        </div>
                                                    </div>
                                                ))
                                            ) : (
                                                <p className="text-muted small">등록된 기존 파일이 없습니다.</p>
                                            )}
                                        </div>
                                    </div>

                                    {/* 새 파일 추가 인풋 */}
                                    <div className="mb-3 mt-4">
                                        <label className="form-label fw-bold" htmlFor="new-post-image">새 파일 추가</label>
                                        <input 
                                            id="new-post-image"
                                            name="files"
                                            type="file"
                                            accept="image/*,video/*"
                                            multiple
                                            className="form-control"
                                            onChange={handleFileChange}
                                        />
                                        <small className="text-muted">이미지 및 동영상은 최대 5개까지 업로드 가능합니다.</small>
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
                                    <input id="post-place" name="place" className="form-control" type="text" placeholder="예: 서울 성수" value={formData.place} onChange={handleChange} />
                                </div>
                                <div className="mb-3">
                                    <label className="form-label" htmlFor="post-content">내용</label>
                                    <textarea id="post-content" name="content" className="form-control" rows="6" placeholder="내용을 적어 주세요." value={formData.content} onChange={handleChange} required />
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
                                <button className="btn btn-primary w-100 py-2 mt-3" type="submit">수정 완료</button>
                            </div>
                        </form>
                    </section>
                </main>
            </div>
        </div>
    );
};

export default PostEditView;