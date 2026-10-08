document.addEventListener("DOMContentLoaded", () => {
  // 데모 폼 처리 (필요시 유지)
  document.querySelectorAll("[data-demo-form]").forEach((form) => {
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      const message = form.dataset.message || "데모 화면이므로 실제 서버에는 저장되지 않습니다.";
      window.alert(message);

      const redirect = form.dataset.redirect;
      if (redirect) window.location.href = redirect;
    });
  });
});

// HTML 특수문자 이스케이프 유틸 함수
function escapeHtml(value) {
  if (!value) return '';
  return value.replace(/[&<>"']/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;"
  }[character]));
}

// ==========================================
// 상세 페이지 이미지 슬라이더 이동 전역 함수
// 'var'를 사용하여 이미 선언된 경우 중복 에러 방지
// ==========================================
if (typeof currentSlideIndex === 'undefined') {
  var currentSlideIndex = 0;
}

function moveSlide(direction) {
  const slides = document.querySelectorAll('.slide-item');
  if (slides.length === 0) return;

  if (slides[currentSlideIndex]) {
    slides[currentSlideIndex].style.display = 'none';
  }

  currentSlideIndex = (currentSlideIndex + direction + slides.length) % slides.length;

  if (slides[currentSlideIndex]) {
    slides[currentSlideIndex].style.display = 'flex';
  }

  const currentIndexSpan = document.getElementById('current-index');
  if (currentIndexSpan) {
    currentIndexSpan.textContent = currentSlideIndex + 1;
  }
}