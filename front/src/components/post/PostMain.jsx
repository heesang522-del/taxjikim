import React, { useState } from 'react';


const PostMain = ({ posts = [], onSearch, onSortChange }) => {
    const [keyword, setKeyword] = useState('');

    // 검색 폼 제출 핸들러
    const handleSearchSubmit = (e) => {
        e.preventDefault();
        if (onSearch) onSearch(keyword);
    };

    return (
        <div className="zt-app">
            {/* 상단 모바일 헤더 */}
            <header className="zt-mobile-header d-flex justify-content-between align-items-center px-3 py-2 bg-white border-bottom">
                <a className="zt-brand fw-bold text-decoration-none text-dark fs-5" href="/main-post">
                    <span>post</span>
                </a>
                <a href="/new-post" className="btn btn-primary btn-sm d-flex align-items-center gap-1">
                    <i className="bi bi-plus-lg"></i> 글쓰기
                </a>
            </header>

            <nav className="zt-mobile-nav" aria-label="메인 메뉴">
                <a href="/main-post" className="active" aria-label="홈">
                    <i className="bi bi-house"></i>
                </a>
                <a href="/new-post" aria-label="새글 작성">
                    <i className="bi bi-plus-square"></i>
                </a>
            </nav>

            <div className="zt-layout d-flex">
                {/* 메인 컨텐츠 영역 */}
                <main className="zt-content flex-grow-1 p-4">
                    {/* 상단 타이틀 영역 */}
                    <div className="text-center mb-4">
                        <h2 className="fw-bold">post</h2>
                        <p className="text-muted">나만의 게시물</p>
                    </div>

                    {/* 검색 및 정렬 바 */}
                    <form onSubmit={handleSearchSubmit} className="input-group mb-4 shadow-sm">
                        <input 
                            type="text" 
                            name="keyword" 
                            className="form-control" 
                            placeholder="제목 또는 내용을 검색하세요" 
                            value={keyword}
                            onChange={(e) => setKeyword(e.target.value)}
                        />
                        <button className="btn btn-primary" type="submit">
                            <i className="bi bi-search"></i> 검색
                        </button>
                        <select 
                            name="sort" 
                            className="form-select" 
                            style={{ maxWidth: '130px' }} 
                            onChange={(e) => onSortChange && onSortChange(e.target.value)}
                        >
                            <option value="latest">최신순</option>
                            <option value="oldest">오래된순</option>
                        </select>
                    </form>

                    {/* 게시글 목록 또는 비어있을 때의 화면 */}
                    {posts && posts.length > 0 ? (
                        <div className="row row-cols-1 row-cols-md-2 g-4">
                            {posts.map((post) => (
                                <div className="col" key={post.postId}>
                                    <div className="card h-100 shadow-sm">
                                        {post.thumbnailPath ? (
                                            <img 
                                                src={`/uploads/${post.thumbnailPath}`} 
                                                className="card-img-top" 
                                                alt="썸네일" 
                                                style={{ height: '200px', objectFit: 'cover' }} 
                                            />
                                        ) : (
                                            <div className="d-flex align-items-center justify-content-center bg-light text-muted" style={{ height: '200px' }}>
                                                <i className="bi bi-image fs-1"></i>
                                            </div>
                                        )}

                                        <div className="card-body">
                                            <h5 className="card-title fw-bold">
                                                <a href={`/detail?postId=${post.postId}`} className="text-decoration-none text-dark">
                                                    {post.title}
                                                </a>
                                            </h5>
                                            <p className="card-text text-muted text-truncate">{post.content}</p>
                                        </div>

                                        <div className="card-footer bg-white d-flex justify-content-between align-items-center text-muted small">
                                            <span>{post.writer}</span>
                                            <span>{post.formattedCreateAt}</span>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <div className="text-center p-5 bg-white rounded shadow-sm">
                            <i className="bi bi-journal-x fs-1 text-muted"></i>
                            <p className="text-muted mt-3 mb-3">작성된 게시글이 없습니다.</p>
                            <a href="/new-post" className="btn btn-primary">
                                <i className="bi bi-plus-lg"></i> 첫 게시글 쓰기
                            </a>
                        </div>
                    )}
                </main>
            </div>
        </div>
    );
};

export default PostMainView;