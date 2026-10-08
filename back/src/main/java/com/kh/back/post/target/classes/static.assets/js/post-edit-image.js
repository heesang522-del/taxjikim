document.addEventListener("DOMContentLoaded", () => {
    const maxImageCount = 5;

    const imageInput = document.querySelector(
        "#edit-post-images"
    );

    const preview = document.querySelector(
        "#edit-new-image-preview"
    );

    const imageCount = document.querySelector(
        "#edit-new-image-count"
    );

    const deleteCheckboxes = document.querySelectorAll(
        `input[name="deleteImageIds"]`
    );

    if (!imageInput || !preview || !imageCount) {
        return;
    }

    let selectedFiles = [];

    function getRemainingExistingCount() {
        let remainingCount = 0;

        deleteCheckboxes.forEach((checkbox) => {
            if (!checkbox.checked) {
                remainingCount++;
            }
        });
        return remainingCount;
    }

    function updateImageInput() {
        const dataTransfer = new DataTransfer();

        selectedFiles.forEach((file) => {
            dataTransfer.items.add(file);
        });

        imageInput.files = dataTransfer.files;
    }

    function removeSelectedImage(index) {
        selectedFiles.splice(index, 1);

        updateImageInput();
        renderPreview();
    }

    function renderPreview() {
        preview.innerHTML = "";

        selectedFiles.forEach((file, index) => {
            const item = document.createElement("div");
            item.className = "zt-edit-new-image-item";

            const image = document.createElement("img");
            const imageUrl = URL.createObjectURL(file);

            image.src = imageUrl;
            image.alt = file.name;

            image.onload = () => {
                URL.revokeObjectURL(imageUrl);
            };

            const removeButton =
                document.createElement("button");

            removeButton.type = "button";
            removeButton.className =
                "zt-edit-new-image-remove";
            removeButton.textContent = "\u00D7";
            removeButton.setAttribute(
                "aria-label",
                "새 사진 선택 취소"
            );

            removeButton.addEventListener("click", () => {
                removeSelectedImage(index);
            });

            item.append(image);
            item.append(removeButton);
            preview.append(item);
        });

        imageCount.textContent =
            String(selectedFiles.length);
    }

    imageInput.addEventListener("change", () => {
        const newFiles = Array.from(imageInput.files);

        const validImageFiles = newFiles.filter((file) => {
            return file.type === "image/jpeg"
                || file.type === "image/png";
        });

        if (validImageFiles.length !== newFiles.length) {
            alert(
                "JPG 또는 PNG 이미지만 선택할 수 있습니다."
            );
        }

        const existingImageCount =
            getRemainingExistingCount();

        const availableCount =
            maxImageCount
            - existingImageCount
            - selectedFiles.length;

        if (validImageFiles.length > availableCount) {
            alert(
                "기존 사진과 새 사진을 합쳐 최대 5장까지 등록할 수 있습니다"
            );
        }

        const filesToAdd = validImageFiles.slice(
            0,
            Math.max(0, availableCount)
        );

        selectedFiles = [
            ...selectedFiles,
            ...filesToAdd
        ];

        updateImageInput();
        renderPreview();
    });

    deleteCheckboxes.forEach((checkbox) => {
        checkbox.addEventListener("change", () => {
            const totalImageCount =
                getRemainingExistingCount()
                + selectedFiles.length;

            if (totalImageCount > maxImageCount) {
                checkbox.checked = true;

                alert(
                    "사진은 최대 5장까지 유지할 수 있습니다."
                );
            }
        });
    });

    
});