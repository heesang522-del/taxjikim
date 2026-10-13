import React, { useState } from 'react';


const PostDetailView = ({ post, onEdit, onDelete }) => {
    const [currentIndex, setCurrentIndex] = useState(0);

    if (!post) {
        return <div className="text-center p-5">Matalek a data wenno post ket saan a masarakan.</div>;
    }

    // Carousel Slider navigation
    const moveSlide = (direction) => {
        if (!post.images || post.images.length === 0) return;
        let newIndex = currentIndex + direction;
        if (newIndex < 0) {
            newIndex = post.images.length - 1;
        } else if (newIndex >= post.images.length) {
            newIndex = 0;
        }
        setCurrentIndex(newIndex);
    };

    return (
        <div className="app">
            <header className="mobile-header">
                <a className="brand" href="/home">
                    <span>post</span>
                </a>
            </header>
            
            <nav className="mobile-nav" aria-label="모바일 메뉴">
                <a href="/home" aria-label="home"><i className="bi bi-house"></i></a>
                <a href="/main-post" className="active" aria-label="이야기"><i className="bi bi-grid-3x3-gap"></i></a>
                <a href="/new-post" aria-label="new"><i className="bi bi-plus-square"></i></a>
            </nav>

            <div className="layout">
                <main className="content">
                    <article className="panel overflow-hidden p-4">

                        <div className="detail-title-area mb-3">
                            <span className="detail-category badge bg-primary mb-2">이야기</span>
                            <h1 className="detail-title">{post.title}</h1>
                        </div>

                        <div className="user-meta d-flex justify-content-between align-items-center mb-3 pb-2 border-bottom">
                            <div className="detail-author-main">
                                <strong>{post.writer || '익명'}</strong>
                            </div>
                            <div className="detail-author-sub text-muted small">
                                <span className="me-3">{post.formattedCreateAt}</span>
                                <span>조회수 {post.viewCount || 0}</span>
                            </div>
                        </div>

                        <div className="detail-owner-actions mb-4">
                            <button className="btn btn-outline-secondary btn-sm me-2" onClick={() => onEdit(post.postId)}>수정</button>
                            <button className="btn btn-outline-danger btn-sm" onClick={() => {
                                if (window.confirm('정말 삭제하시겠습니까?')) {
                                    onDelete(post.postId);
                                }
                            }}>삭제</button>
                        </div>

                        {post.images && post.images.length > 0 ? (
                            <div className="zt-detail-carousel" style={{ position: 'relative', width: '100%', height: '400px', background: '#000', borderRadius: '8px', overflow: 'hidden', marginBottom: '1.5rem' }}>
                                <div id="image-slider-container" style={{ position: 'relative', width: '100%', height: '100%' }}>
                                    {post.images.map((image, index) => {
                                        const lowerPath = image.uploadPath.toLowerCase();
                                        const isImage = ['.jpg', '.jpeg', '.png', '.gif', '.webp'].some(ext => lowerPath.includes(ext));
                                        const isVideo = ['.mp4', '.webm', '.mov', '.avi'].some(ext => lowerPath.includes(ext));

                                        return (
                                            <div 
                                                key={index}
                                                className="slide-item"
                                                style={{
                                                    position: 'absolute', top: 0, left: 0, width: '100%', height: '100%',
                                                    display: index === currentIndex ? 'flex' : 'none',
                                                    alignItems: 'center', justifyContent: 'center', background: '#000'
                                                }}
                                            >
                                                {isImage && (
                                                    <img src={`/uploads/${image.uploadPath}`} alt={image.originName} style={{ width: '100%', height: '100%', objectFit: 'contain' }} />
                                                )}
                                                {isVideo && (
                                                    <video 
                                                        src={`/uploads/${image.uploadPath}`} 
                                                        controls 
                                                        preload="metadata" 
                                                        controlsList="nodownload" 
                                                        onContextMenu={(e) => e.preventDefault()} 
                                                        disablePictureInPicture={true} 
                                                        style={{ width: '100%', height: '100%', objectFit: 'contain' }} 
                                                    />
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>

                                {post.images.length > 1 && (
                                    <>
                                        <button type="button" onClick={() => moveSlide(-1)} style={{ position: 'absolute', left: '15px', top: '50%', transform: 'translateY(-50%)', zIndex: 100, width: '40px', height: '40px', borderRadius: '50%', background: 'rgba(0, 0, 0, 0.6)', color: 'white', border: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}>
                                            <i className="bi bi-chevron-left" style={{ fontSize: '1.2rem' }}></i>
                                        </button>
                                        <button type="button" onClick={() => moveSlide(1)} style={{ position: 'absolute', right: '15px', top: '50%', transform: 'translateY(-50%)', zIndex: 100, width: '40px', height: '40px', borderRadius: '50%', background: 'rgba(0, 0, 0, 0.6)', color: 'white', border: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}>
                                            <i className="bi bi-chevron-right" style={{ fontSize: '1.2rem' }}></i>
                                        </button>
                                        <div style={{ position: 'absolute', bottom: '15px', right: '15px', background: 'rgba(0, 0, 0, 0.6)', color: 'white', padding: '4px 12px', borderRadius: '12px', fontSize: '0.85rem', zIndex: 100 }}>
                                            <span>{currentIndex + 1}</span> / <span>{post.images.length}</span>
                                        </div>
                                    </>
                                )}
                            </div>
                        ) : (
                            <div className="text-center text-muted p-4 bg-light rounded mb-4">
                                <p className="mb-0">등록된 파일이 없습니다.</p>
                            </div>
                        )}

                        {post.place && (
                            <div className="mb-3 text-muted">
                                <i className="bi bi-geo-alt-fill text-danger"></i> <strong>장소:</strong> {post.place}
                            </div>
                        )}

                        <div className="detail-content mb-4" style={{ whiteSpace: 'pre-wrap', lineHeight: '1.6' }}>
                            {post.content}
                        </div>

                        <div className="card bg-light border-0 p-3 mb-3">
                            <h6 className="fw-bold mb-2"><i className="bi bi-receipt"></i> 지출 비용 정보</h6>
                            <div className="row text-secondary small">
                                <div className="col-4">교통비: <span className="fw-bold text-dark">{(post.transportCost || 0).toLocaleString()}원</span></div>
                                <div className="col-4">식비: <span className="fw-bold text-dark">{(post.foodCost || 0).toLocaleString()}원</span></div>
                                <div className="col-4">기타 비용: <span className="fw-bold text-dark">{(post.otherCost || 0).toLocaleString()}원</span></div>
                            </div>
                        </div>

                    </article>
                </main>
            </div>
        </div>
    );
};

PostDetailView.defaultProps = {
    post: {
        images: []
    }
};

export default PostDetailView;