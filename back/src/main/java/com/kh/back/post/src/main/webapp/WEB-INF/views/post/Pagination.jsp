<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!-- totalPage -> totalPages 로 수정 -->
<c:if test="${not empty paging and paging.totalPages > 1}">
  <nav aria-label="Page navigation" class="mt-4 mb-4">
    <ul class="pagination justify-content-center">

      <!-- 이전 블록 버튼 -->
      <li class="page-item ${!paging.prev ? 'disabled' : ''}">
        <a class="page-link" href="?page=${paging.startPage - 1}&sort=${sort}&keyword=${keyword}" aria-label="Previous">
          <span aria-hidden="true">&laquo;</span>
        </a>
      </li>

      <!-- 페이지 번호 반복 출력 -->
      <c:forEach var="p" begin="${paging.startPage}" end="${paging.endPage}">
        <!-- currentPage -> page 로 수정 -->
        <li class="page-item ${p == paging.page ? 'active' : ''}">
          <a class="page-link" href="?page=${p}&sort=${sort}&keyword=${keyword}">${p}</a>
        </li>
      </c:forEach>

      <!-- 다음 블록 버튼 -->
      <li class="page-item ${!paging.next ? 'disabled' : ''}">
        <a class="page-link" href="?page=${paging.endPage + 1}&sort=${sort}&keyword=${keyword}" aria-label="Next">
          <span aria-hidden="true">&raquo;</span>
        </a>
      </li>

    </ul>
  </nav>
</c:if>