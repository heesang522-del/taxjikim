const commentToggleButton = document.querySelector(
    "[data-comment-toggle]"
);
const commentList = document.querySelector(
    "[data-comment-list]"
);

if (commentToggleButton && commentList) {
    commentToggleButton.addEventListener("click", () => {
        const isExpanded =
            commentToggleButton.getAttribute("aria-expanded") === "true";

        commentList.hidden = isExpanded;

        commentToggleButton.setAttribute(
            "aria-expanded",
            String(!isExpanded)
        );

        commentToggleButton.setAttribute(
            "aria-label",
            isExpanded ? "댓글 보기" : "댓글 숨기기"
        );

        commentToggleButton.classList.toggle(
            "is-active",
            !isExpanded
        );
    });
}

const loginPromptButtons = document.querySelectorAll(
    "[data-login-prompt]"
);

loginPromptButtons.forEach((loginPromptButton) => {
    loginPromptButton.addEventListener("click", () => {
        const message =
            loginPromptButton.dataset.loginMessage;
        const loginUrl =
            loginPromptButton.dataset.loginUrl;

        const wantsToLogin = confirm(message);

        if (!wantsToLogin) {
            return;
        }

        window.location.href = loginUrl;
    });
});