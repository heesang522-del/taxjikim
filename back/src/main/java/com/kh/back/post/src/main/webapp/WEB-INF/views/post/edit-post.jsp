<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="게시물 수정 페이지">
  <title>게시물 수정 | post</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
</head>
<body>
<div class="zt-app">

  <header class="zt-mobile-header">
    <a class="zt-brand" href="${pageContext.request.contextPath}/home">
      <span>post</span>
    </a>
  </header>
  <nav class="zt-mobile-nav" aria-label="모바일 메뉴">
    <a href="${pageContext.request.contextPath}/home" aria-label="home"><i class="bi bi-house"></i></a>
    <a href="${pageContext.request.contextPath}/main-post" aria-label="post"><i class="bi bi-grid-3x3-gap"></i></a>
    <a href="${pageContext.request.contextPath}/new-post" aria-label="new"><i class="bi bi-plus-square"></i></a>
  </nav>

  <div class="zt-layout">

    <jsp:include page="/WEB-INF/views/components/sidebar.jsp">
      <jsp:param name="activePage" value="new-post" />
    </jsp:include>

    <main class="zt-content">

      <header class="zt-page-header">
        <h1>게시물 수정하기</h1>
        <p>사진 및 동영상, 게시물 정보를 수정합니다.</p>
      </header>

      <section class="zt-panel zt-profile-card">
        <c:if test="${not empty errorMessage}">
          <div class="alert alert-danger" role="alert">
            <c:out value="${errorMessage}"/>
          </div>
        </c:if>

        <%-- 폼 태그 (enctype 및 id 설정) --%>
        <form class="row g-4"
              id="post-form"
              method="post"
              enctype="multipart/form-data">

          <%-- 🔑 핵심: 자바스크립트가 postId를 읽어갈 수 있도록 하는 hidden 태그 --%>
          <input type="hidden" name="postId" value="${post.postId}">

          <div class="col-lg-6">
            <div class="zt-post-image-uploader" data-post-image-uploader>

              <%-- 🌟 새로 선택한 파일 프리뷰 및 슬라이드 UI 영역 --%>
              <div class="mb-4">
                <label class="form-label fw-bold">새로 첨부할 파일 미리보기</label>
                <div class="position-relative d-flex align-items-center justify-content-center border rounded bg-dark overflow-hidden" style="height: 250px;">

                  <!-- 이전 슬라이드 버튼 -->
                  <button type="button" id="post-prev-btn" class="btn btn-light btn-sm position-absolute start-0 ms-2" style="z-index: 10; display: none; opacity: 0.8;">
                    <i class="bi bi-chevron-left"></i>
                  </button>

                  <!-- 메인 프리뷰 표시 영역 -->
                  <div id="post-main-preview" class="text-white text-center"></div>

                  <!-- 빈 상태 안내 메시지 -->
                  <div id="post-image-empty" class="text-muted d-flex flex-column align-items-center justify-content-center" style="width: 100%; height: 100%;">
                    <i class="bi bi-image fs-1 mb-1"></i>
                    <small>선택된 파일이 없습니다.</small>
                  </div>

                  <!-- 다음 슬라이드 버튼 -->
                  <button type="button" id="post-next-btn" class="btn btn-light btn-sm position-absolute end-0 me-2" style="z-index: 10; display: none; opacity: 0.8;">
                    <i class="bi bi-chevron-right"></i>
                  </button>

                  <!-- 현재 인덱스 / 총 개수 카운트 -->
                  <span id="post-image-count" class="position-absolute bottom-0 end-0 badge bg-secondary m-2" style="font-size: 11px;">0</span>
                </div>

                <!-- 썸네일 리스트 영역 -->
                <div id="post-thumbnail-list" class="mt-2 overflow-auto py-1" style="white-space: nowrap;"></div>
              </div>

              <%-- 기존 업로드된 파일 및 삭제 체크박스 영역 --%>
              <div class="mb-3">
                <label class="form-label fw-bold">기존 첨부파일 (삭제할 항목을 체크하세요)</label>
                <div class="d-flex flex-wrap gap-2">
                  <c:choose>
                    <c:when test="${not empty post.images}">
                      <c:forEach var="image" items="${post.images}">
                        <div class="border p-2 rounded text-center bg-light" style="width: 110px;">
                          <span class="d-block text-truncate small mb-1" style="max-width: 100px;"><c:out value="${image.originName}"/></span>
                          <div class="form-check d-flex justify-content-center align-items-center gap-1">
                            <input class="form-check-input" type="checkbox" name="deleteImageIds" value="${image.uploadId}" id="del-${image.uploadId}">
                            <label class="form-check-label small text-danger fw-bold" for="del-${image.uploadId}">삭제</label>
                          </div>
                        </div>
                      </c:forEach>
                    </c:when>
                    <c:otherwise>
                      <p class="text-muted small">등록된 기존 파일이 없습니다.</p>
                    </c:otherwise>
                  </c:choose>
                </div>
              </div>

              <%-- 새 파일 추가 인풋 (오디오 제거, 최대 5개 제한 반영) --%>
              <div class="mb-3 mt-4">
                <label class="form-label fw-bold" for="new-post-image">새 파일 추가</label>
                <input id="new-post-image"
                       name="files"
                       type="file"
                       accept="image/*,video/*"
                       multiple
                       class="form-control">
                <small class="text-muted">이미지 및 동영상은 최대 5개까지 업로드 가능합니다.</small>
              </div>

            </div>
          </div>

          <div class="col-lg-6">
            <div class="mb-3">
              <label class="form-label" for="post-title">제목</label>
              <input id="post-title" name="title" class="form-control" type="text" maxlength="60" placeholder="제목을 입력하세요" value="<c:out value='${post.title}'/>" required>
            </div>
            <div class="mb-3">
              <label class="form-label" for="post-place">장소</label>
              <input id="post-place" name="place" class="form-control" type="text" placeholder="예: 서울 성수" value="<c:out value='${post.place}'/>">
            </div>
            <div class="mb-3">
              <label class="form-label" for="post-content">내용</label>
              <textarea id="post-content"
                        name="content"
                        class="form-control"
                        rows="6"
                        placeholder="내용을 적어 주세요."
                        required><c:out value="${post.content}"/></textarea>
            </div>

            <div class="mb-3">
              <label class="form-label" for="transport-cost">교통비</label>
              <input id="transport-cost"
                     name="transportCost"
                     class="form-control"
                     type="number"
                     min="0"
                     value="${empty post.transportCost ? 0 : post.transportCost}"
                     required>
            </div>

            <div class="mb-3">
              <label class="form-label" for="food-cost">식비</label>
              <input id="food-cost"
                     name="foodCost"
                     class="form-control"
                     type="number"
                     min="0"
                     value="${empty post.foodCost ? 0 : post.foodCost}"
                     required>
            </div>

            <div class="mb-3">
              <label class="form-label" for="other-cost">입장료 및 기타 비용</label>
              <input id="other-cost"
                     name="otherCost"
                     class="form-control"
                     type="number"
                     min="0"
                     value="${empty post.otherCost ? 0 : post.otherCost}"
                     required>
            </div>

            <button class="btn btn-primary w-100 py-2 mt-3" type="submit">수정 완료</button>
          </div>
        </form>
      </section>

    </main>

  </div>
</div>
<script src="${pageContext.request.contextPath}/assets/js/post-edit.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/post-detail.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/common.js"></script>
</body>
</html>