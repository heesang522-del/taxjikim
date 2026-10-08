document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("[data-like-button]").forEach((button) => {
    button.addEventListener("click", () => {
      const icon = button.querySelector("i");
      const liked = icon.classList.contains("bi-heart-fill");

      icon.classList.toggle("bi-heart", liked);
      icon.classList.toggle("bi-heart-fill", !liked);
      button.classList.toggle("text-danger", !liked);

      const targetSelector = button.dataset.likeTarget;
      if (targetSelector) {
        const countElement = document.querySelector(targetSelector);
        if (countElement) {
          const current = Number(countElement.dataset.count || 0);
          const next = Math.max(0, current + (liked ? -1 : 1));
          countElement.dataset.count = String(next);
          countElement.textContent = `좋아요 ${next.toLocaleString()}개`;
        }
      }
    });
  });

  document.querySelectorAll("[data-demo-form]").forEach((form) => {
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      const message = form.dataset.message || "데모 화면이므로 실제 서버에는 저장되지 않습니다.";
      window.alert(message);

      const redirect = form.dataset.redirect;
      if (redirect) window.location.href = redirect;
    });
  });

  document.querySelectorAll("[data-comment-form]").forEach((form) => {
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      const input = form.querySelector("input");
      const value = input?.value.trim();
      if (!value) return;

      const comments = form.closest(".zt-post, .zt-panel")?.querySelector("[data-comment-list]");
      if (comments) {
        const row = document.createElement("p");
        row.className = "mb-1 small";
        row.innerHTML = `<strong>나</strong> ${escapeHtml(value)}`;
        comments.appendChild(row);
      }
      input.value = "";
    });
  });

  const uploadInput = document.querySelector("[data-upload-input]");
  const uploadPreview = document.querySelector("[data-upload-preview]");
  if (uploadInput && uploadPreview) {
    uploadInput.addEventListener("change", () => {
      const file = uploadInput.files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => {
        uploadPreview.innerHTML = `<img src="${reader.result}" alt="업로드 미리보기" class="w-100 h-100 object-fit-cover rounded-3">`;
      };
      reader.readAsDataURL(file);
    });
  }
});

function escapeHtml(value) {
  return value.replace(/[&<>"']/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;"
  }[character]));
}