/**
 * DOMContentLoaded 및 폼 이벤트 바인딩 관리 함수
 * (React 내부 컴포넌트에서 직접 DOM을 제어하지 않고 유틸리티 형태로 분리할 때 사용합니다)
 */
export function initPostEditForm() {
    const postForm = document.querySelector("#post-form");
    if (!postForm) return;

    let selectedFilesArray = [];

    const fileInput = document.querySelector('#new-post-image');
    const mainPreview = document.querySelector('#post-main-preview');
    const emptyMessage = document.querySelector('#post-image-empty');
    const imageCount = document.querySelector('#post-image-count');
    const prevBtn = document.querySelector('#post-prev-btn');
    const nextBtn = document.querySelector('#post-next-btn');
    const thumbnailList = document.querySelector('#post-thumbnail-list');

    let currentIndex = 0;
    let objectUrls = [];

    window.postImageManager = {
        getSelectedFiles: () => selectedFilesArray
    };

    if (fileInput) {
        fileInput.addEventListener("change", (e) => {
            const files = Array.from(e.target.files);
            if (files.length === 0) return;

            if (selectedFilesArray.length + files.length > 5) {
                alert("파일은 최대 5개까지 업로드할 수 있습니다.");
                return;
            }

            objectUrls.forEach(url => URL.revokeObjectURL(url));
            objectUrls = [];

            selectedFilesArray = files;
            currentIndex = 0;

            updatePreviewUI();
        });
    }

    function updatePreviewUI() {
        if (!mainPreview || !emptyMessage) return;

        if (selectedFilesArray.length === 0) {
            mainPreview.innerHTML = "";
            emptyMessage.style.display = "flex";
            if (imageCount) imageCount.textContent = "0";
            if (prevBtn) prevBtn.style.display = "none";
            if (nextBtn) nextBtn.style.display = "none";
            if (thumbnailList) thumbnailList.innerHTML = "";
            return;
        }

        emptyMessage.style.display = "none";
        const file = selectedFilesArray[currentIndex];
        const previewUrl = URL.createObjectURL(file);
        objectUrls.push(previewUrl);

        renderMediaPreview(mainPreview, file, previewUrl, currentIndex, selectedFilesArray, prevBtn, nextBtn, imageCount);
    }

    if (prevBtn) {
        prevBtn.addEventListener("click", () => {
            if (currentIndex > 0) {
                currentIndex--;
                updatePreviewUI();
            }
        });
    }

    if (nextBtn) {
        nextBtn.addEventListener("click", () => {
            if (currentIndex < selectedFilesArray.length - 1) {
                currentIndex++;
                updatePreviewUI();
            }
        });
    }

    postForm.addEventListener("submit", async (e) => {
        e.preventDefault();

        const submitBtn = postForm.querySelector('button[type="submit"]');
        if (submitBtn) submitBtn.disabled = true;

        try {
            const savedFileNames = [];

            if (selectedFilesArray.length > 0) {
                console.log("파일 청크 업로드 시작...");

                for (const file of selectedFilesArray) {
                    const uploadResult = await uploadFileWithChunkAndMerge(file);
                    if (!uploadResult || !uploadResult.savedFileName) {
                        throw new Error("파일 업로드 중 오류가 발생했습니다.");
                    }
                    savedFileNames.push(uploadResult.savedFileName);
                }
            }

            const finalPayload = new FormData();
            const postIdInput = postForm.querySelector('input[name="postId"]');
            const postId = postIdInput ? postIdInput.value : null;

            finalPayload.append("title", postForm.querySelector('#post-title')?.value || "");
            finalPayload.append("place", postForm.querySelector('#post-place')?.value || "");
            finalPayload.append("content", postForm.querySelector('#post-content')?.value || "");
            finalPayload.append("transportCost", postForm.querySelector('#transport-cost')?.value || 0);
            finalPayload.append("foodCost", postForm.querySelector('#food-cost')?.value || 0);
            finalPayload.append("otherCost", postForm.querySelector('#other-cost')?.value || 0);

            if (savedFileNames.length > 0) {
                finalPayload.append("savedFileNamesJson", JSON.stringify(savedFileNames));
            }

            const url = postId ? `/api/posts/${postId}/update` : '/api/posts';

            const checkedDeletes = postForm.querySelectorAll('input[name="deleteImageIds"]:checked');
            checkedDeletes.forEach(chk => {
                finalPayload.append("deleteImageIds", chk.value);
            });

            const response = await fetch(url, {
                method: 'POST',
                body: finalPayload
            });

            const result = await response.json();

            if (response.ok && result.success) {
                alert(postId ? "게시글이 성공적으로 수정되었습니다!" : "게시글이 성공적으로 등록되었습니다!");
                window.location.href = postId ? `/detail?postId=${postId}` : "/main-post";
            } else {
                alert("처리에 실패했습니다: " + (result.message || ""));
                if (submitBtn) submitBtn.disabled = false;
            }

        } catch (error) {
            console.error("서버 전송 에러:", error);
            alert("오류가 발생했습니다: " + error.message);
            if (submitBtn) submitBtn.disabled = false;
        }
    });
}

/**
 * 미디어 미리보기 및 보안/다운로드 방지 속성이 적용된 렌더링 함수
 */
export function renderMediaPreview(mediaElement, file, currentPreviewUrl, currentImageIndex, selectedFiles, prevBtn, nextBtn, imageCount) {
    if (!mediaElement) return;

    const isVideo = file.type && file.type.startsWith("video/");
    const isAudio = file.type && file.type.startsWith("audio/");

    mediaElement.innerHTML = "";

    if (isVideo) {
        const videoEl = document.createElement("video");
        videoEl.src = currentPreviewUrl;
        videoEl.controls = true;
        videoEl.controlsList = "nodownload";
        videoEl.disablePictureInPicture = true;
        videoEl.setAttribute("oncontextmenu", "return false;");
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

    if (imageCount) {
        imageCount.textContent = `${currentImageIndex + 1} / ${selectedFiles.length}`;
    }

    if (prevBtn && nextBtn) {
        const hasMultiple = selectedFiles.length > 1;
        prevBtn.style.display = hasMultiple ? "block" : "none";
        nextBtn.style.display = hasMultiple ? "block" : "none";
    }
}

/**
 * 청크 단위 파일 업로드 및 병합 헬퍼 함수
 */
export async function uploadFileWithChunkAndMerge(file) {
    const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB
    const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
    const fileUid = (typeof crypto !== 'undefined' && crypto.randomUUID) ? crypto.randomUUID() : 'file-' + Date.now();
    let finalSavedFileName = null;

    for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
        const start = chunkIndex * CHUNK_SIZE;
        const end = Math.min(start + CHUNK_SIZE, file.size);
        const chunk = file.slice(start, end);

        const formData = new FormData();
        formData.append('file', chunk);
        formData.append('fileUid', fileUid);
        formData.append('originalName', file.name);
        formData.append("chunkIndex", chunkIndex);
        formData.append("totalChunks", totalChunks);

      const response = await fetch('/api/v1/posts/community/upload-chunk', {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            throw new Error(`${chunkIndex + 1}번째 청크 업로드 실패`);
        }

        const result = await response.json();
        if (!result.success) {
            throw new Error(result.message || "청크 업로드 실패");
        }

        const targetData = result.data !== undefined ? result.data : result;
        if (targetData) {
            if (typeof targetData === 'string') {
                finalSavedFileName = targetData;
            } else if (targetData.savedFileName) {
                finalSavedFileName = targetData.savedFileName;
            }
        }
        if (!finalSavedFileName && result.savedFileName) {
            finalSavedFileName = result.savedFileName;
        }
    }

    if (!finalSavedFileName) {
        throw new Error("최종 파일명을 가져오지 못했습니다.");
    }

    return {
        savedFileName: finalSavedFileName,
        originalName: file.name
    };
}