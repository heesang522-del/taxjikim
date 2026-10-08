<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="서버 오류가 발생했습니다">
  <title>서버 오류 | post</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css">
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.13.1/font/bootstrap-icons.min.css">
</head>
<body>
<div class="zt-app">
  <div class="zt-auth-wrap">
    <section class="zt-panel zt-auth-card zt-panel-shadow text-center">
      <a class="zt-brand d-inline-flex justify-content-center mb-3" href="${pageContext.request.contextPath}/home">
        <span>FBI</span>
      </a>
      <p class="display-6 fw-bold mb-2">500</p>
      <h1 class="h4 mb-3">서버 오류가 발생했습니다</h1>
      <p class="zt-muted mb-4">일시적인 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.</p>
      <a class="btn btn-primary zt-primary-btn" href="${pageContext.request.contextPath}/home">홈으로 돌아가기</a>
    </section>
  </div>
</div>
</body>
</html>
