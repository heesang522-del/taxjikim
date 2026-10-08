<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="게시물 작성 페이지 / Post Creation Page">
  <title>새 게시물 / post | post</title>
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
    <a href="${pageContext.request.contextPath}/main-post" class="" aria-label="post"><i class="bi bi-grid-3x3-gap"></i></a>
    <a href="${pageContext.request.contextPath}/new-post" class="active" aria-label="new"><i class="bi bi-plus-square"></i></a>
  </nav>

  <div class="zt-layout">

    <jsp:include page="/WEB-INF/views/components/sidebar.jsp">
      <jsp:param name="activePage" value="new-post" />
    </jsp:include>

    <main class="zt-content">

      <header class="zt-page-header">
        <h1>새 게시물 만들기</h1>
        <p>사진 및 동영상을 업로드합니다.</p>
      </header>

      <section class="zt-panel zt-profile-card">
        <c:if test="${not empty errorMessage}">
          <div class="alert alert-danger" role="alert">
            <c:out value="${errorMessage}"/>
          </div>
        </c:if>

        <form class="row g-4"
              id="post-form"
              action="${pageContext.request.contextPath}/api/posts"
              method="post"
              enctype="multipart/form-data">

          <div class="col-lg-6">
            <div class="zt-post-image-uploader" data-post-image-uploader>
              <div id="post-main-preview" class="zt-post-main-preview position-relative" style="min-height: 250px; display: flex; align-items: center; justify-content: center; background: #f8f9fa; border-radius: 8px; overflow: hidden;">

                <!-- 빈 상태 메시지 -->
                <div id="post-image-empty" class="zt-post-image-empty text-center p-4">
                  <i class="bi bi-folder-plus display-5"></i>
                  <strong>파일을 선택하세요</strong>
                  <!-- 🌟 안내 문구 5개로 수정 -->
                  <p class="mb-0"><small>이미지 및 동영상은 최대 5개까지 선택할 수 있습니다.</small></p>
                </div>

                <!-- 통합 메인 미디어 뷰어 영역 (이미지, 비디오 렌더링) -->
                <div id="dynamic-media-view" style="width: 100%; height: 100%; display: none; align-items: center; justify-content: center;"></div>

                <!-- 슬라이더 버튼 -->
                <button type="button" id="post-prev-btn" class="zt-slider-btn zt-prev-btn" style="display: none; position: absolute; left: 10px; top: 50%; transform: translateY(-50%); z-index: 10;">〈</button>
                <button type="button" id="post-next-btn" class="zt-slider-btn zt-next-btn" style="display: none; position: absolute; right: 10px; top: 50%; transform: translateY(-50%); z-index: 10;">〉</button>
              </div>
              <div class="zt-post-thumbnail-row mt-2">
                <div id="post-thumbnail-list" class="zt-post-thumbnail-list"></div>

                <label class="zt-post-add-image" for="new-post-image">
                  <i class="bi bi-plus-lg"></i>
                  <span>파일</span>

                  <!-- 🌟 accept에서 audio/* 제거 (이미지, 비디오만 허용) -->
                  <input id="new-post-image"
                         name="files"
                         type="file"
                         accept="image/*,video/*"
                         multiple
                         class="d-none">

                </label>
              </div>

              <!-- 🌟 최대 개수 5개로 표기 수정 -->
              <p class="zt-post-image-count mt-2 text-center">
                <strong id="post-image-count">0</strong> / 5
              </p>

            </div>
          </div>

          <div class="col-lg-6">
            <div class="mb-3">
              <label class="form-label" for="post-title">제목</label>
              <input id="post-title" name="title" class="form-control" type="text" maxlength="60" placeholder="제목을 입력하세요" value="<c:out value='${post.title}'/>" required>
            </div>
            <div class="mb-3">
              <label class="form-label" for="post-place">장소 </label>
              <input id="post-place" name="place" class="form-control" type="text" placeholder="예: 서울 성수 " value="<c:out value='${post.place}'/>" required>
            </div>
            <div class="mb-3">
              <label class="form-label" for="post-content">내용</label>
              <textarea id="post-content"
                        name="content"
                        class="form-control"
                        rows="8"
                        placeholder="내용을 적어 주세요. (최소 10자 이상)"
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
                     onfocus="if (this.value === '0') { this.value = ''; }"
                     onblur="if (this.value === '') { this.value = '0'; }"
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
                     onfocus="if (this.value === '0') { this.value = ''; }"
                     onblur="if (this.value === '') { this.value = '0'; }"
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
                     onfocus="if (this.value === '0') { this.value = ''; }"
                     onblur="if (this.value === '') { this.value = '0'; }"
                     required>
            </div>

            <div class="mb-3">
              <label class="form-label" for="post-tags">태그</label>
              <input id="post-tags" class="form-control" type="text" placeholder="#여행 #가성비">
            </div>
            <div class="form-check form-switch mb-4">
              <input id="share-route" class="form-check-input" type="checkbox" checked>
              <label class="form-check-label" for="share-route"> 동선 공개</label>
            </div>
            <button class="btn btn-primary zt-primary-btn w-100 py-2" type="submit">작성 완료</button>
          </div>
        </form>
      </section>

    </main>

  </div>
</div>
<script src="${pageContext.request.contextPath}/assets/js/client.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/post-preview.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/common.js"></script>
</body>
</html>