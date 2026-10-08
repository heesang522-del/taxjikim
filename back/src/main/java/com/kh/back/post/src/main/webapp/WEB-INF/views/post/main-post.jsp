<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="ko">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="나만의 게시물 메인">
    <title>나만의 게시물 | post</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/post-detail.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
</head>
<body>
<div class="zt-app">

    <!-- 상단 모바일 헤더 -->
    <header class="zt-mobile-header d-flex justify-content-between align-items-center px-3 py-2 bg-white border-bottom">
        <a class="zt-brand fw-bold text-decoration-none text-dark fs-5" href="${pageContext.request.contextPath}/main-post">
            <span>post</span>
        </a>
        <!-- 상단 우측: 바로 글쓰기 버튼 -->
        <a href="${pageContext.request.contextPath}/new-post" class="btn btn-primary btn-sm d-flex align-items-center gap-1">
            <i class="bi bi-plus-lg"></i> 글쓰기
        </a>
    </header>

    <nav class="zt-mobile-nav" aria-label="메인 메뉴">
        <a href="${pageContext.request.contextPath}/main-post" class="active" aria-label="홈">
            <i class="bi bi-house"></i>
        </a>
        <a href="${pageContext.request.contextPath}/new-post" aria-label="새글 작성">
            <i class="bi bi-plus-square"></i>
        </a>
    </nav>

    <div class="zt-layout d-flex">

        <!-- 메인 컨텐츠 영역 -->
        <main class="zt-content flex-grow-1 p-4">

            <!-- 상단 타이틀 영역 -->
            <div class="text-center mb-4">
                <h2 class="fw-bold">post</h2>
                <p class="text-muted">나만의 게시물 </p>
            </div>

            <!-- 검색 및 정렬 바 -->
            <form action="${pageContext.request.contextPath}/main-post" method="get" class="input-group mb-4 shadow-sm">
                <input type="text" name="keyword" class="form-control" placeholder="제목 또는 내용을 검색하세요" value="${param.keyword}">
                <button class="btn btn-primary" type="submit">
                    <i class="bi bi-search"></i> 검색
                </button>
                <select name="sort" class="form-select" style="max-width: 130px;" onchange="this.form.submit()">
                    <option value="latest" ${param.sort eq 'latest' ? 'selected' : ''}>최신순</option>
                    <option value="oldest" ${param.sort eq 'oldest' ? 'selected' : ''}>오래된순</option>
                </select>
            </form>

            <!-- 게시글 목록 또는 비어있을 때의 화면 -->
            <c:choose>
                <%-- 1. 게시글이 존재하는 경우 --%>
                <c:when test="${not empty posts}">
                    <div class="row row-cols-1 row-cols-md-2 g-4">
                        <c:forEach var="post" items="${posts}">
                            <div class="col">
                                <div class="card h-100 shadow-sm">

                                    <c:choose>
                                        <c:when test="${not empty post.thumbnailPath}">
                                            <%-- 🌟 수정: ${pageContext.request.contextPath} ti inayon tapno umiso ti pannakaidalan ti URL --%>
                                            <img src="${pageContext.request.contextPath}/uploads/${post.thumbnailPath}" class="card-img-top" alt="썸네일" style="height: 200px; object-fit: cover;">
                                        </c:when>
                                        <c:otherwise>
                                            <div class="d-flex align-items-center justify-content-center bg-light text-muted" style="height: 200px;">
                                                <i class="bi bi-image fs-1"></i>
                                            </div>
                                        </c:otherwise>
                                    </c:choose>

                                    <div class="card-body">
                                        <h5 class="card-title fw-bold">
                                            <a href="${pageContext.request.contextPath}/detail?postId=${post.postId}" class="text-decoration-none text-dark">
                                                    ${post.title}
                                            </a>
                                        </h5>
                                        <p class="card-text text-muted text-truncate">${post.content}</p>
                                    </div>

                                    <div class="card-footer bg-white d-flex justify-content-between align-items-center text-muted small">
                                        <span>${post.writer}</span>
                                        <span>${post.formattedCreateAt}</span>
                                    </div>

                                </div>
                            </div>
                        </c:forEach>
                    </div>
                </c:when>

                <%-- 2. 게시글이 없는 경우 --%>
                <c:otherwise>
                    <div class="text-center p-5 bg-white rounded shadow-sm">
                        <i class="bi bi-journal-x fs-1 text-muted"></i>
                        <p class="text-muted mt-3 mb-3">작성된 게시글이 없습니다.</p>
                        <a href="${pageContext.request.contextPath}/new-post" class="btn btn-primary">
                            <i class="bi bi-plus-lg"></i> 첫 게시글 쓰기
                        </a>
                    </div>
                </c:otherwise>
            </c:choose>
        </main>
    </div>
</div>
<script src="${pageContext.request.contextPath}/assets/js/client.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/post-detail.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/common.js"></script>
</body>
</html>