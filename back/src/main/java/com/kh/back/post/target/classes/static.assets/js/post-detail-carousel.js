document.addEventListener("DOMContentLoaded", () => {
    const maxImageCount = 10;

    const uploader = document.querySelector("[data-post-image-uploader]");
    const imageInput = document.querySelector("#new-post-image");
    const emptyMessage = document.querySelector("#post-image-empty");
    const mainPreview = document.querySelector("#post-main-preview");
    const thumbnailList = document.querySelector("#post-thumbnail-list");
    const imageCount = document.querySelector("#post-image-count");

    const prevBtn = document.querySelector("#post-prev-btn");
    const nextBtn = document.querySelector("#post-next-btn");

    if (
        !uploader
        || !imageInput
        || !emptyMessage
        || !mainPreview
        || !thumbnailList
        || !imageCount
    ) {
        return;
    }

    let selectedFiles = [];
    let currentImageIndex = 0;

    function renderMainPreview(index) {
        if (selectedFiles.length === 0) {
            emptyMessage.style.display = "flex";
            mainPreview.hidden = true;
            mainPreview.removeAttribute("src");
            if (prevBtn) prevBtn.style.display = "none";
            if (nextBtn) nextBtn.style.display = "none";
            imageCount.textContent = "0";
            return;
        }

        if (index >= selectedFiles.length) {
            index = selectedFiles.length - 1;
        }
        currentImageIndex = index;

        const file = selectedFiles[index];
        const previewUrl = URL.createObjectURL(file);

        mainPreview.src = previewUrl;
        mainPreview.hidden = false;
        emptyMessage.style.display = "none";

        // 현재 인덱스 및 총 파일 수 카운터 갱신 (예: 1 / 3)
        imageCount.textContent = `${currentImageIndex + 1} / ${selectedFiles.length}`;

        // 이미지가 2장 이상일 때만 좌우 버튼 표시
        if (prevBtn && nextBtn) {
            const hasMultiple = selectedFiles.length > 1;
            prevBtn.style.display = hasMultiple ? "block" : "none";
            nextBtn.style.display = hasMultiple ? "block" : "none";
        }

        mainPreview.onload = () => {
            URL.revokeObjectURL(previewUrl);
        };
    }

    function renderThumbnails() {
        thumbnailList.innerHTML = "";

        selectedFiles.forEach((file, index) => {
            const item = document.createElement("div");
            item.className = "zt-post-thumbnail-item me-2 d-inline-block position-relative";

            const button = document.createElement("button");
            button.type = "button";
            button.className = "zt-post-thumbnail btn p-0 border-0";

            if (index === currentImageIndex) {
                button.classList.add("active");
            }

            const image = document.createElement("img");
            const thumbnailUrl = URL.createObjectURL(file);
            image.src = thumbnailUrl;
            image.alt = file.name;
            image.style.width = "60px";
            image.style.height = "60px";
            image.style.objectFit = "cover";
            image.style.borderRadius = "4px";

            button.addEventListener("click", () => {
                renderMainPreview(index);
                renderThumbnails();
            });

            const removeButton = document.createElement("button");
            removeButton.type = "button";
            removeButton.className = "zt-post-thumbnail-remove btn btn-danger btn-sm position-absolute top-0 end-0 p-0 px-1";
            removeButton.style.fontSize = "10px";
            removeButton.textContent = "×";

            removeButton.addEventListener("click", (e) => {
                e.stopPropagation();
                selectedFiles.splice(index, 1);

                if (currentImageIndex >= selectedFiles.length) {
                    currentImageIndex = Math.max(0, selectedFiles.length - 1);
                } else if (index < currentImageIndex) {
                    currentImageIndex--;
                }

                updateImageInput();
                renderMainPreview(currentImageIndex);
                renderThumbnails();
            });

            button.append(image);
            item.append(button);
            item.append(removeButton);
            thumbnailList.append(item);
        });
    }

    function updateImageInput() {
        const dataTransfer = new DataTransfer();
        selectedFiles.forEach((file) => {
            dataTransfer.items.add(file);
        });
        imageInput.files = dataTransfer.files;
    }

    // 왼쪽 화살표 클릭 (이전 사진)
    if (prevBtn) {
        prevBtn.addEventListener("click", () => {
            if (selectedFiles.length <= 1) return;
            currentImageIndex = (currentImageIndex - 1 + selectedFiles.length) % selectedFiles.length;
            renderMainPreview(currentImageIndex);
            renderThumbnails();
        });
    }

    // 오른쪽 화살표 클릭 (다음 사진)
    if (nextBtn) {
        nextBtn.addEventListener("click", () => {
            if (selectedFiles.length <= 1) return;
            currentImageIndex = (currentImageIndex + 1) % selectedFiles.length;
            renderMainPreview(currentImageIndex);
            renderThumbnails();
        });
    }

    // 이미지 파일 선택 변경 이벤트
    imageInput.addEventListener("change", () => {
        const newFiles = Array.from(imageInput.files);
        imageInput.value = "";

        const imageFiles = newFiles.filter((file) => {
            return file.type === "image/jpeg" || file.type === "image/png";
        });

        if (imageFiles.length !== newFiles.length) {
            alert("JPG 또는 PNG 이미지만 선택할 수 있습니다.");
        }

        const remainingCount = maxImageCount - selectedFiles.length;

        if (imageFiles.length > remainingCount) {
            alert(`이미지는 최대 ${maxImageCount}장까지 선택할 수 있습니다.`);
        }

        const filesToAdd = imageFiles.slice(0, remainingCount);

        if (filesToAdd.length > 0) {
            const newIndex = selectedFiles.length; // 새로 추가된 첫 번째 이미지 인덱스
            selectedFiles = [...selectedFiles, ...filesToAdd];
            updateImageInput();

            renderMainPreview(newIndex);
            renderThumbnails();
        }
    });
});