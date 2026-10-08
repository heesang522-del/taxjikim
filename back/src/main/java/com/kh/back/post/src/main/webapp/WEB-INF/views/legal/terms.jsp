<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>이용약관 | post</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css">
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.13.1/font/bootstrap-icons.min.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
</head>
<body>
<div class="zt-app">
  <header class="zt-mobile-header">
    <a class="zt-brand" href="${pageContext.request.contextPath}/home"><span>post</span></a>
  </header>

  <div class="zt-layout">
    <jsp:include page="/WEB-INF/views/components/sidebar.jsp">
      <jsp:param name="activePage" value=""/>
    </jsp:include>

    <main class="zt-content">
      <header class="zt-page-header">
        <h1>이용약관</h1>
        <p class="mb-0">서비스 이용약관</p>
      </header>

      <section class="zt-panel p-4">
        <p class="zt-muted small">최종 업데이트: 2026-08-08</p>
        <h2 class="h6 fw-bold mt-3">제1조 (목적)</h2>
        <p class="small">본 약관은 post(이하 “서비스”)가 제공하는 여행 기록·미션·채팅 등 관련 서비스의 이용 조건과 절차를 규정합니다.</p>

        <h2 class="h6 fw-bold mt-3">제2조 (정의)</h2>
        <p class="small">“회원”이란 서비스에 가입하여 본 약관에 따라 서비스를 이용하는 자를 말합니다. “콘텐츠”란 회원이 서비스에 게시하는 글, 이미지, 댓글 등을 말합니다.</p>

        <h2 class="h6 fw-bold mt-3">제3조 (약관의 효력)</h2>
        <p class="small">회원은 서비스 가입 시 본 약관에 동의한 것으로 보며, 서비스 이용 중에도 약관이 적용됩니다. 본 문서는 개발·시연용 더미이며 실제 법적 효력을 담보하지 않습니다.</p>

        <h2 class="h6 fw-bold mt-3">제4조 (서비스 내용)</h2>
        <p class="small">서비스는 여행 게시물 작성, 미션 수행 및 포인트 적립, 오픈 채팅 등 기능을 제공할 수 있으며, 운영상 필요에 따라 일부 기능이 변경·중단될 수 있습니다.</p>

        <h2 class="h6 fw-bold mt-3">제5조 (회원의 의무)</h2>
        <p class="small">회원은 관련 법령과 서비스 운영 정책을 준수해야 하며, 타인의 권리를 침해하거나 서비스 운영을 방해하는 행위를 해서는 안 됩니다.</p>

        <h2 class="h6 fw-bold mt-3">제6조 (문의)</h2>
        <p class="small mb-0">약관 관련 문의는 서비스 내 문의 채널 또는 관리자에게 연락해 주세요. (더미)</p>
      </section>
    </main>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
