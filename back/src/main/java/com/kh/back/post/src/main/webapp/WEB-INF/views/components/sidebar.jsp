<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="ko">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="post 사이드바">
    <title>post</title>
</head>
<body>
<aside class="zt-sidebar">
  <a class="zt-brand" href="${pageContext.request.contextPath}/index">
    <span class="fs-4 fw-bold">post</span>
  </a>

  <nav class="zt-nav" aria-label="주요 메뉴">
    <a class="zt-nav-link ${param.activePage eq 'main-post' ? 'active' : ''}"
       href="${pageContext.request.contextPath}/main-post"
       aria-current="${param.activePage eq 'main-post' ? 'page' : 'false'}">
      <i class="bi bi-grid-3x3-gap"></i><span>메인 피드</span>
    </a>

    <a class="zt-nav-link ${param.activePage eq 'new-post' ? 'active' : ''}"
       href="${pageContext.request.contextPath}/new-post"
       aria-current="${param.activePage eq 'new-post' ? 'page' : 'false'}">
      <i class="bi bi-plus-square"></i>
      <span>게시물 만들기</span>
    </a>
  </nav>

  <div class="zt-sidebar-footer">
    <a href="${pageContext.request.contextPath}/terms">이용약관</a><br>
    <a href="${pageContext.request.contextPath}/privacy">개인정보처리방침</a><br>
    <span>&copy; 2026 post</span>
  </div>
</aside>
</body>
</html>