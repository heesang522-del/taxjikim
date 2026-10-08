document.addEventListener("DOMContentLoaded", () => {
    const maxFileCount = 5;
    const uploader = document.querySelector("[data-post-image-uploader]");
    const imageInput = document.querySelector("#new-post-image");
    const emptyMessage = document.querySelector("#post-image-empty");
    const mediaElement = document.querySelector("#dynamic-media-view");
    const thumbnailList = document.querySelector("#post-thumbnail-list");
    const imageCount = document.querySelector("#post-image-count");

    const prevBtn = document.querySelector("#post-prev-btn");
    const nextBtn = document.querySelector("#post-next-btn");

    if (!uploader || !imageInput || !emptyMessage || !mediaElement || !thumbnailList || !imageCount) {
        return;
    }

    let selectedFiles = [];
    let currentImageIndex = 0;
    let currentPreviewUrl = null;

    function renderMainPreview(index) {
        mediaElement.innerHTML = "";

        if (selectedFiles.length === 0) {
            if (currentPreviewUrl) {
                URL.revokeObjectURL(currentPreviewUrl);
                currentPreviewUrl = null;
            }
            emptyMessage.style.display = "flex";
            const mainPreviewEl = document.querySelector("#post-main-preview");
            if (mainPreviewEl) mainPreviewEl.hidden = false;

            mediaElement.style.display = "none";
            if (prevBtn) prevBtn.style.display = "none";
            if (nextBtn) nextBtn.style.display = "none";
            imageCount.textContent = "0";
            return;
        }

        if (index >= selectedFiles.length) index = selectedFiles.length - 1;
        if (index < 0) index = 0;
        currentImageIndex = index;

        const file = selectedFiles[index];
        if (currentPreviewUrl) {
            URL.revokeObjectURL(currentPreviewUrl);
        }

        currentPreviewUrl = URL.createObjectURL(file);
        emptyMessage.style.display = "none";
        mediaElement.style.display = "flex";

        const fileNameLower = file.name.toLowerCase();
        const isVideo = file.type.startsWith("video/") || fileNameLower.endsWith(".webm") || fileNameLower.endsWith(".mp4") || fileNameLower.endsWith(".mov");
        const isImage = file.type.startsWith("image/") || fileNameLower.endsWith(".jpg") || fileNameLower.endsWith(".jpeg") || fileNameLower.endsWith(".png") || fileNameLower.endsWith(".gif") || fileNameLower.endsWith(".webp");
        const isAudio = file.type.startsWith("audio/") || fileNameLower.endsWith(".mp3") || fileNameLower.endsWith(".wav");

        if (isImage) {
            mediaElement.innerHTML = `<img src="${currentPreviewUrl}" alt="${file.name}" style="max-width: 100%; max-height: 250px; width: auto; height: auto; object-fit: contain; border-radius: 8px;">`;
        } else if (isVideo) {

            // 🛡️ 동영상 미리보기 및 보안/다운로드 방지 속성 적용
            const videoEl = document.createElement("video");
            videoEl.src = currentPreviewUrl;
            videoEl.controls = true;
            videoEl.controlsList = "nodownload"; // 브라우저 기본 다운로드 버튼 숨기기
            videoEl.disablePictureInPicture = true; // 팝업(PIP) 모드 차단
            videoEl.setAttribute("oncontextmenu", "return false;"); // 우클릭 메뉴 차단
            videoEl.style.cssText = "max-width: 100%; max-height: 250px; width: auto; height: auto; border-radius: 8px;";

            mediaElement.appendChild(videoEl);
        } else if (isAudio) {
            mediaElement.innerHTML = `
                <div class="text-center p-4">
                    <i class="bi bi-file-earmark-music display-4 mb-2 text-primary"></i>
                    <p class="mb-1">${file.name}</p>
                    <audio src="${currentPreviewUrl}" controls class="w-100 mt-2" controlsList="nodownload"></audio>
                </div>
            `;
        } else {
            mediaElement.innerHTML = `
                <div class="text-center p-4">
                    <i class="bi bi-file-earmark-fill display-4 mb-2"></i>
                    <p class="mb-1">${file.name}</p>
                    <small class="text-muted">일반 파일이 선택되었습니다.</small>
                </div>
            `;
        }

        imageCount.textContent = `${currentImageIndex + 1} / ${selectedFiles.length}`;

        if (prevBtn && nextBtn) {
            const hasMultiple = selectedFiles.length > 1;
            prevBtn.style.display = hasMultiple ? "block" : "none";
            nextBtn.style.display = hasMultiple ? "block" : "none";
        }
    }

    function renderThumbnails() {
        thumbnailList.innerHTML = "";
        selectedFiles.forEach((file, index) => {
            const item = document.createElement("div");
            item.className = "zt-post-thumbnail-item me-2 d-inline-block position-relative";

            const button = document.createElement("button");
            button.type = "button";
            button.className = "zt-post-thumbnail btn p-0 border-0";
            if (index === currentImageIndex) button.classList.add("active");

            const thumbContent = document.createElement("div");
            thumbContent.style.cssText = "width: 60px; height: 60px; border-radius: 4px; overflow: hidden; display: flex; align-items: center; justify-content: center; background: #f1f3f5; position: relative;";

            const thumbnailUrl = URL.createObjectURL(file);
            const fileNameLower = file.name.toLowerCase();
            const isImage = file.type.startsWith("image/") || fileNameLower.endsWith(".jpg") || fileNameLower.endsWith(".jpeg") || fileNameLower.endsWith(".png") || fileNameLower.endsWith(".gif") || fileNameLower.endsWith(".webp");

            if (isImage) {
                thumbContent.innerHTML = `<img src="${thumbnailUrl}" alt="${file.name}" style="width: 100%; height: 100%; object-fit: cover;">`;
            } else {
                thumbContent.innerHTML = `<i class="bi bi-file-earmark-fill text-dark" style="font-size: 1.5rem;"></i>`;
            }

            button.appendChild(thumbContent);
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
                currentImageIndex = Math.max(0, Math.min(currentImageIndex, selectedFiles.length - 1));
                renderMainPreview(currentImageIndex);
                renderThumbnails();
            });

            item.append(button, removeButton);
            thumbnailList.append(item);
        });
    }

    if (prevBtn) {
        prevBtn.onclick = (e) => {
            e.preventDefault();
            if (selectedFiles.length <= 1) return;
            currentImageIndex = (currentImageIndex - 1 + selectedFiles.length) % selectedFiles.length;
            renderMainPreview(currentImageIndex);
            renderThumbnails();
        };
    }

    if (nextBtn) {
        nextBtn.onclick = (e) => {
            e.preventDefault();
            if (selectedFiles.length <= 1) return;
            currentImageIndex = (currentImageIndex + 1) % selectedFiles.length;
            renderMainPreview(currentImageIndex);
            renderThumbnails();
        };
    }

    imageInput.addEventListener("change", () => {
        const newFiles = Array.from(imageInput.files);
        imageInput.value = "";

        const blockedExtensions = [".exe", ".zip", ".bat", ".cmd", ".sh", ".jar", ".msi", ".iso", ".dmg"];
        const validFiles = newFiles.filter(file => !blockedExtensions.some(ext => file.name.toLowerCase().endsWith(ext)));

        if (validFiles.length < newFiles.length) alert("보안상 실행 및 압축 파일은 업로드할 수 없습니다.");
        if (validFiles.length === 0) return;

        const remainingCount = maxFileCount - selectedFiles.length;
        if (validFiles.length > remainingCount) {
            alert(`파일은 최대 ${maxFileCount}개까지만 선택할 수 있습니다.`);
        }

        const filesToAdd = validFiles.slice(0, remainingCount);
        if (filesToAdd.length > 0) {
            selectedFiles = [...selectedFiles, ...filesToAdd];
            currentImageIndex = 0;
            renderMainPreview(currentImageIndex);
            renderThumbnails();
        }
    });

    // 🌟 폼 제출 시 선택된 파일들을 청크로 나누어 업로드하고 데이터 전송
    const postForm = document.querySelector("#post-form");
    if (postForm) {
        postForm.addEventListener("submit", async (e) => {
            e.preventDefault();

            const submitBtn = postForm.querySelector("button[type='submit']") || postForm.querySelector("#submit-btn");
            if (submitBtn) submitBtn.disabled = true;

            try {
                const savedFileNames = [];
                const CHUNK_SIZE = 1024 * 1024 * 2; // 2MB 단위 청크

                // 1. 파일이 있는 경우 청크 업로드 진행
                if (selectedFiles.length > 0) {
                    for (const file of selectedFiles) {
                        const uploadId = "upload_" + Date.now() + "_" + Math.random().toString(36).substring(2, 9);
                        const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
                        let finalFileName = null;

                        for (let i = 0; i < totalChunks; i++) {
                            const start = i * CHUNK_SIZE;
                            const end = Math.min(start + CHUNK_SIZE, file.size);
                            const chunk = file.slice(start, end);

                            const formData = new FormData();
                            formData.append("file", chunk);
                            formData.append("uploadId", uploadId);
                            formData.append("chunkIndex", i);
                            formData.append("totalChunks", totalChunks);
                            formData.append("originalName", file.name);

                            const response = await fetch(`${window.location.origin}/api/posts/upload-chunk`, {
                                method: "POST",
                                body: formData
                            });

                            const result = await response.json();
                            if (!result.success) {
                                throw new Error(result.message || "파일 업로드 중 오류가 발생했습니다.");
                            }

                            if (result.data.completed) {
                                finalFileName = result.data.savedFileName;
                            }
                        }

                        if (finalFileName) {
                            savedFileNames.push(finalFileName);
                        }
                    }
                }

                // 2. 폼 데이터를 기반으로 FormData 생성
                const finalFormData = new FormData(postForm);

                // 업로드된 파일명 JSON 문자열을 FormData에 직접 추가
                finalFormData.set("savedFileNamesJson", JSON.stringify(savedFileNames));

                // 3. fetch를 통해 /api/posts로 최종 데이터 전송
                const finalResponse = await fetch(postForm.action, {
                    method: "POST",
                    body: finalFormData
                });

                const finalResult = await finalResponse.json();

                if (finalResult.success) {
                    window.location.href = `${window.location.origin}/main-post`;
                } else {
                    throw new Error(finalResult.message || "게시글 등록에 실패했습니다.");
                }

            } catch (error) {
                console.error("등록 에러:", error);
                alert("오류 발생: " + error.message);
                if (submitBtn) submitBtn.disabled = false;
            }
        });
    }
});