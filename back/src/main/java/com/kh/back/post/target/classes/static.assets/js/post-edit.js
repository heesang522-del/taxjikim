document.addEventListener("DOMContentLoaded", () => {
    const maxFileCount = 10; // 최대 파일 제한 개수

    const imageInput = document.querySelector("#edit-post-images");
    const mainPreview = document.querySelector("#post-main-preview");
    const emptyMessage = document.querySelector("#post-image-empty");
    const thumbnailListContainer = document.querySelector(".zt-post-image-uploader");

    if (!imageInput) return;

    let newSelectedFiles = []; // 새로 추가한 파일 배열

    imageInput.addEventListener("change", () => {
        const rawNewFiles = Array.from(imageInput.files);
        imageInput.value = ""; // 입력 초기화

        // 🚫 1. 보안상 차단할 확장자 검사 (.exe, .zip 등)
        const blockedExtensions = [".exe", ".zip", ".bat", ".cmd", ".sh", ".jar", ".msi", ".iso", ".dmg"];
        const validFiles = [];
        let hasBlockedFile = false;

        rawNewFiles.forEach(file => {
            const fileNameLower = file.name.toLowerCase();
            const isBlocked = blockedExtensions.some(ext => fileNameLower.endsWith(ext));
            if (isBlocked) {
                hasBlockedFile = true;
            } else {
                validFiles.push(file);
            }
        });

        if (hasBlockedFile) {
            alert("보안상 `.exe`, `.zip` 등의 실행 파일 및 압축 파일은 업로드할 수 없습니다.");
        }

        if (validFiles.length === 0) return;

        // 2. 기존 DB에 등록된 파일 개수와 현재까지 새로 추가한 파일 개수 합산
        const existingThumbnails = document.querySelectorAll('input[name="deleteImageIds"]');
        let deletedCount = 0;
        existingThumbnails.forEach(checkbox => {
            if (checkbox.checked) deletedCount++;
        });

        // 남아있는 기존 파일 수 + 현재 새 파일들 수
        const currentRemainingExisting = existingThumbnails.length - deletedCount;
        const totalCurrentCount = currentRemainingExisting + newSelectedFiles.length;
        const remainingAllowed = maxFileCount - totalCurrentCount;

        if (validFiles.length > remainingAllowed) {
            alert(`기존 파일과 새 파일을 합쳐 최대 ${maxFileCount}개까지만 등록할 수 있습니다.`);
        }

        const filesToAdd = validFiles.slice(0, remainingAllowed);

        if (filesToAdd.length > 0) {
            newSelectedFiles = [...newSelectedFiles, ...filesToAdd];
            updateNewFilesInput();
            renderNewFilesPreview();
        }
    });

    function updateNewFilesInput() {
        const dataTransfer = new DataTransfer();
        newSelectedFiles.forEach(file => {
            dataTransfer.items.add(file);
        });
        imageInput.files = dataTransfer.files;
    }

    function renderNewFilesPreview() {
        // 기존에 동적으로 생성된 새 파일 미리보기 영역이 있다면 제거 후 재생성
        let newPreviewContainer = document.querySelector("#dynamic-new-files-preview");
        if (!newPreviewContainer) {
            newPreviewContainer = document.createElement("div");
            newPreviewContainer.id = "dynamic-new-files-preview";
            newPreviewContainer.className = "mt-3";
            // 기존 썸네일 영역 아래에 삽입
            const thumbnailSection = document.querySelector('input[name="deleteImageIds"]').closest('.mt-3').parentElement;
            thumbnailSection.appendChild(newPreviewContainer);
        }

        if (newSelectedFiles.length === 0) {
            newPreviewContainer.innerHTML = "";
            return;
        }

        let html = `<p class="mb-2 text-muted small"><i class="bi bi-plus-circle"></i> 새로 추가된 파일 목록:</p>`;
        html += `<div class="d-flex flex-wrap gap-2">`;

        newSelectedFiles.forEach((file, index) => {
            const fileUrl = URL.createObjectURL(file);
            const fileNameLower = file.name.toLowerCase();
            const isImage = file.type.startsWith("image/") || /\.(jpg|jpeg|png|gif|webp)$/.test(fileNameLower);

            html += `<div class="position-relative border p-1 rounded text-center" style="width: 70px; background: #fff;">`;
            if (isImage) {
                html += `<img src="${fileUrl}" alt="${file.name}" style="width: 60px; height: 60px; object-fit: cover; border-radius: 4px;">`;
            } else {
                html += `<div class="bg-dark text-white d-flex align-items-center justify-content-center" style="width: 60px; height: 60px; border-radius: 4px;"><i class="bi bi-file-earmark-play-fill fs-4"></i></div>`;
            }
            html += `<button type="button" class="btn btn-danger btn-sm position-absolute top-0 end-0 p-0 px-1 remove-new-file" data-index="${index}" style="font-size: 10px;">×</button>`;
            html += `</div>`;
        });

        html += `</div>`;
        newPreviewContainer.innerHTML = html;

        // 새로 추가한 파일 개별 삭제 버튼 이벤트 바인딩
        newPreviewContainer.querySelectorAll(".remove-new-file").forEach(btn => {
            btn.addEventListener("click", (e) => {
                const idx = parseInt(e.target.getAttribute("data-index"));
                newSelectedFiles.splice(idx, 1);
                updateNewFilesInput();
                renderNewFilesPreview();
            });
        });
    }
});