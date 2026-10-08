<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="게시물 상세 페이지">
  <title>피드 상세 | post</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/post-detail.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/common.css">
</head>
<body>
<div class="app">

  <header class="mobile-header">
    <a class="brand" href="${pageContext.request.contextPath}/home">
      <span>post</span>
    </a>
  </header>
  <nav class="mobile-nav" aria-label="모바일 메뉴">
    <a href="${pageContext.request.contextPath}/home" class="" aria-label="home"><i class="bi bi-house"></i></a>
    <a href="${pageContext.request.contextPath}/main-post" class="active" aria-label="이야기"><i class="bi bi-grid-3x3-gap"></i></a>
    <a href="${pageContext.request.contextPath}/new-post" class="" aria-label="new"><i class="bi bi-plus-square"></i></a>
  </nav>

  <div class="layout">

    <jsp:include page="/WEB-INF/views/components/sidebar.jsp">
      <jsp:param name="activePage" value="new-post" />
    </jsp:include>

    <main class="content">
      <article class="panel overflow-hidden p-4">

        <div class="detail-title-area mb-3">
          <span class="detail-category badge bg-primary mb-2">이야기</span>
          <h1 class="detail-title">
            <c:out value="${post.title}"/>
          </h1>
        </div>

        <div class="user-meta d-flex justify-content-between align-items-center mb-3 pb-2 border-bottom">
          <div class="detail-author-main">
            <strong>
              <c:out value="${empty post.writer ? '익명' : post.writer}"/>
            </strong>
          </div>
          <div class="detail-author-sub text-muted small">
            <span class="me-3"><c:out value="${post.formattedCreateAt}"/></span>
            <span>조회수 <c:out value="${post.viewCount}" default="0"/></span>
          </div>
        </div>

        <div class="detail-owner-actions mb-4">
          <a class="btn btn-outline-secondary btn-sm" href="${pageContext.request.contextPath}/edit-post?postId=${post.postId}">수정</a>
          <form action="${pageContext.request.contextPath}/delete-post" method="post" onsubmit="return confirm('정말 삭제하시겠습니까?');" style="display:inline;">
            <input type="hidden" name="postId" value="${post.postId}">
            <button type="submit" class="btn btn-outline-danger btn-sm">삭제</button>
          </form>
        </div>

        <c:choose>
          <c:when test="${not empty post.images}">
            <div class="zt-detail-carousel" style="position: relative !important; width: 100%; height: 400px; background: #000; border-radius: 8px; overflow: hidden; margin-bottom: 1.5rem;">

              <div id="image-slider-container" style="position: relative; width: 100%; height: 100%;">
                <c:forEach var="image" items="${post.images}" varStatus="status">
                  <div class="slide-item"
                       style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; display: ${status.first ? 'flex' : 'none'}; align-items: center; justify-content: center; background: #000;"
                       data-index="${status.index}">

                    <c:set var="resolvedPath" value="${pageContext.request.contextPath}/uploads/${image.uploadPath}" />
                    <c:set var="lowerPath" value="${fn:toLowerCase(image.uploadPath)}" />

                    <%-- 🌟 이미지와 동영상만 허용하도록 판별식 정의 (오디오 및 기타 파일 제외) --%>
                    <c:set var="isImage" value="${fn:contains(lowerPath, '.jpg') || fn:contains(lowerPath, '.jpeg') || fn:contains(lowerPath, '.png') || fn:contains(lowerPath, '.gif') || fn:contains(lowerPath, '.webp')}" />
                    <c:set var="isVideo" value="${fn:contains(lowerPath, '.mp4') || fn:contains(lowerPath, '.webm') || fn:contains(lowerPath, '.mov') || fn:contains(lowerPath, '.avi')}" />

                    <c:choose>
                      <c:when test="${isImage}">
                        <img src="${resolvedPath}" alt="${image.originName}" style="width: 100%; height: 100%; object-fit: contain;">
                      </c:when>

                      <c:when test="${isVideo}">
                        <%-- 🛡️ 동영상 보안 속성 유지 --%>
                        <video src="${resolvedPath}"
                               controls
                               preload="metadata"
                               controlsList="nodownload"
                               oncontextmenu="return false;"
                               disablePictureInPicture="true"
                               style="width: 100%; height: 100%; object-fit: contain;"></video>
                      </c:when>

                      <c:otherwise>
                        <%-- 혹시라도 예외 상황이나 잘못 들어온 파일일 경우 방어 로직 --%>
                        <div class="text-center text-white">
                          <p class="mb-0">지원하지 않는 형식의 미디어입니다.</p>
                        </div>
                      </c:otherwise>
                    </c:choose>

                  </div>
                </c:forEach>
              </div>

              <c:if test="${post.images.size() > 1}">
                <button type="button" onclick="moveSlide(-1)" aria-label="이전 파일"
                        style="position: absolute !important; left: 15px !important; top: 50% !important; transform: translateY(-50%) !important; z-index: 100 !important; width: 40px !important; height: 40px !important; border-radius: 50% !important; background: rgba(0, 0, 0, 0.6) !important; color: white !important; border: none !important; display: flex !important; align-items: center !important; justify-content: center !important; cursor: pointer !important; padding: 0 !important;">
                  <i class="bi bi-chevron-left" style="font-size: 1.2rem;"></i>
                </button>

                <button type="button" onclick="moveSlide(1)" aria-label="다음 파일"
                        style="position: absolute !important; right: 15px !important; top: 50% !important; transform: translateY(-50%) !important; z-index: 100 !important; width: 40px !important; height: 40px !important; border-radius: 50% !important; background: rgba(0, 0, 0, 0.6) !important; color: white !important; border: none !important; display: flex !important; align-items: center !important; justify-content: center !important; cursor: pointer !important; padding: 0 !important;">
                  <i class="bi bi-chevron-right" style="font-size: 1.2rem;"></i>
                </button>

                <div style="position: absolute !important; bottom: 15px !important; right: 15px !important; background: rgba(0, 0, 0, 0.6) !important; color: white !important; padding: 4px 12px !important; border-radius: 12px !important; font-size: 0.85rem !important; z-index: 100 !important;">
                  <span id="current-index">1</span> / <span>${post.images.size()}</span>
                </div>
              </c:if>

            </div>
          </c:when>
          <c:otherwise>
            <div class="text-center text-muted p-4 bg-light rounded mb-4">
              <p class="mb-0">등록된 파일이 없습니다.</p>
            </div>
          </c:otherwise>
        </c:choose>

        <c:if test="${not empty post.place}">
          <div class="mb-3 text-muted">
            <i class="bi bi-geo-alt-fill text-danger"></i> <strong>장소:</strong> <c:out value="${post.place}"/>
          </div>
        </c:if>

        <div class="detail-content mb-4" style="white-space: pre-wrap; line-height: 1.6;">
          <c:out value="${post.content}"/>
        </div>

        <div class="card bg-light border-0 p-3 mb-3">
          <h6 class="fw-bold mb-2"><i class="bi bi-receipt"></i> 지출 비용 정보</h6>
          <div class="row text-secondary small">
            <div class="col-4">교통비: <span class="fw-bold text-dark"><fmt:formatNumber value="${empty post.transportCost ? 0 : post.transportCost}" pattern="#,###"/>원</span></div>
            <div class="col-4">식비: <span class="fw-bold text-dark"><fmt:formatNumber value="${empty post.foodCost ? 0 : post.foodCost}" pattern="#,###"/>원</span></div>
            <div class="col-4">기타 비용: <span class="fw-bold text-dark"><fmt:formatNumber value="${empty post.otherCost ? 0 : post.otherCost}" pattern="#,###"/>원</span></div>
          </div>
        </div>

      </article>
    </main>

  </div>
</div>

<script src="${pageContext.request.contextPath}/assets/js/client.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/common.js"></script>
</body>
</html>