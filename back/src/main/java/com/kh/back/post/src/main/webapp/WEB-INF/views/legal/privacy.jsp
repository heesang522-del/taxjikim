<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>개인정보처리방침 | post</title>
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
        <h1>개인정보처리방침</h1>
        <p class="mb-0">개인정보 처리방침</p>
      </header>

      <section class="zt-panel p-4">
        <p class="zt-muted small">최종 업데이트: 2026-08-08</p>
        <h2 class="h6 fw-bold mt-3">1. 수집하는 개인정보</h2>
        <p class="small">서비스는 회원가입 및 이용 과정에서 아이디, 비밀번호, 닉네임, 프로필 정보, 서비스 이용 기록(게시글·댓글·미션 진행 등)을 수집할 수 있습니다.</p>

        <h2 class="h6 fw-bold mt-3">2. 이용 목적</h2>
        <p class="small">수집한 정보는 회원 식별, 서비스 제공, 미션·포인트 운영, 부정 이용 방지, 고객 문의 대응을 위해 사용됩니다.</p>

        <h2 class="h6 fw-bold mt-3">3. 보관 및 파기</h2>
        <p class="small">관련 법령이 정한 기간 또는 서비스 운영에 필요한 기간 동안 보관하며, 목적 달성 후 지체 없이 파기하는 것을 원칙으로 합니다. (더미 안내)</p>

        <h2 class="h6 fw-bold mt-3">4. 제3자 제공</h2>
        <p class="small">법령에 근거하거나 회원의 동의가 있는 경우를 제외하고 개인정보를 외부에 제공하지 않습니다.</p>

        <h2 class="h6 fw-bold mt-3">5. 이용자 권리</h2>
        <p class="small">회원은 언제든지 자신의 개인정보 열람·수정·삭제를 요청할 수 있으며, 계정 설정 또는 관리자 문의를 통해 처리할 수 있습니다.</p>

        <h2 class="h6 fw-bold mt-3">6. 문의</h2>
        <p class="small mb-0">개인정보 관련 문의는 서비스 관리자에게 연락해 주세요. 본 문서는 개발·시연용 더미입니다.</p>
      </section>
    </main>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
