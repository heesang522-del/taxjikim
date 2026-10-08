document.addEventListener("DOMContentLoaded", () => {
    // ==========================================
    // 1. [상세 보기 페이지 전용 로직]
    // ==========================================
    const mediaContainer = document.querySelector("#post-media-container");
    if (mediaContainer) {
        const files = window.postFiles || [];
        if (files.length > 0) {
            mediaContainer.innerHTML = "";

            files.forEach((file) => {
                const fileNameLower = file.savedFileName.toLowerCase();

                const isVideo = fileNameLower.endsWith(".webm") || fileNameLower.endsWith(".mp4") || fileNameLower.endsWith(".mov");
                const isImage = fileNameLower.endsWith(".jpg") || fileNameLower.endsWith(".jpeg") || fileNameLower.endsWith(".png") || fileNameLower.endsWith(".gif") || fileNameLower.endsWith(".webp");
                const isAudio = fileNameLower.endsWith(".mp3") || fileNameLower.endsWith(".wav");

                const safeFileName = encodeURIComponent(file.savedFileName);
                const fileUrl = `/uploads/${safeFileName}`;

                const itemDiv = document.createElement("div");
                itemDiv.className = "zt-detail-media-item mb-3 text-center";

                if (isImage) {
                    itemDiv.innerHTML = `
                        <img src="${fileUrl}" 
                             alt="${file.originalName || '게시글 이미지'}" 
                             style="max-width: 100%; max-height: 500px; width: auto; height: auto; object-fit: contain; border-radius: 8px;">
                    `;
                } else if (isVideo) {
                    // 🛡️ [보안 적용] 상세 보기 동영상 다운로드 방지 및 우클릭 차단 속성 추가 완료
                    itemDiv.innerHTML = `
                        <video src="${fileUrl}" 
                               controls 
                               preload="metadata" 
                               controlsList="nodownload" 
                               oncontextmenu="return false;" 
                               disablePictureInPicture="true"
                               style="max-width: 100%; max-height: 500px; width: 100%; height: auto; object-fit: contain; border-radius: 8px; background: #000;"></video>
                    `;
                } else if (isAudio) {
                    itemDiv.innerHTML = `
                        <div class="p-3 border rounded bg-light">
                            <i class="bi bi-file-earmark-music display-4 mb-2"></i>
                            <p class="mb-2">${file.originalName || '오디오 파일'}</p>
                            <audio src="${fileUrl}" controls class="w-100" controlsList="nodownload"></audio>
                        </div>
                    `;
                } else {
                    itemDiv.innerHTML = `
                        <div class="p-3 border rounded bg-light">
                            <i class="bi bi-file-earmark-arrow-down display-4 mb-2"></i>
                            <p class="mb-2">${file.originalName || '첨부 파일'}</p>
                            <a href="${fileUrl}" download="${file.originalName}" class="btn btn-sm btn-outline-primary">파일 다운로드</a>
                        </div>
                    `;
                }

                mediaContainer.appendChild(itemDiv);
            });
        }
    }

    // ==========================================
    // 2. [글 작성 페이지 폼 제출 및 청크 업로드 연동 로직]
    // ==========================================
    const postForm = document.querySelector("#post-form");
    if (postForm) {
        postForm.addEventListener("submit", async (e) => {
            e.preventDefault(); // 1. 기본 폼 제출을 즉시 차단합니다.

            const submitBtn = postForm.querySelector('button[type="submit"]');
            if (submitBtn) submitBtn.disabled = true; // 중복 클릭 방지

            const fileInput = document.querySelector("#new-post-image");
            const files = fileInput ? fileInput.files : [];
            const savedFileNames = [];

            try {
                // 2. 선택된 파일이 있다면 모든 파일의 청크 업로드와 병합이 100% 끝날 때까지 기다립니다 (await).
                if (files.length > 0) {
                    console.log("파일 청크 업로드 시작...");

                    for (let i = 0; i < files.length; i++) {
                        const file = files[i];
                        const uploadResult = await uploadFileWithChunkAndMerge(file);

                        if (!uploadResult || !uploadResult.savedFileName) {
                            alert("파일 업로드 중 오류가 발생했습니다.");
                            if (submitBtn) submitBtn.disabled = false;
                            return;
                        }

                        // 성공한 파일명을 배열에 안전하게 누적합니다.
                        savedFileNames.push(uploadResult.savedFileName);
                    }
                }

                // 3. 폼 안에 있는 모든 입력값(제목, 내용 등)을 FormData로 수집합니다.
                const formData = new FormData(postForm);

                // 기존 파일 input 객체는 제거하고, 서버 업로드가 완료된 파일명 리스트를 각각 추가합니다.
                formData.delete("files");
                savedFileNames.forEach(fileName => {
                    formData.append("savedFileNames", fileName);
                });

                console.log("서버로 최종 폼 데이터 전송 시작...", savedFileNames);

                // 4. 모든 데이터가 완벽하게 준비된 상태에서 fetch 전송을 수행합니다.
                const response = await fetch(postForm.action, {
                    method: 'POST',
                    body: formData
                });

                if (response.ok) {
                    // 성공 시 메인 페이지로 안전하게 이동
                    window.location.href = '/main-post';
                } else {
                    alert("게시글 등록에 실패했습니다.");
                    if (submitBtn) submitBtn.disabled = false;
                }
            } catch (error) {
                console.error("서버 전송 에러:", error);
                alert("서버 통신 중 오류가 발생했습니다.");
                if (submitBtn) submitBtn.disabled = false;
            }
        });
    }
});

// ==========================================
// 3. [청크 단위 파일 업로드 및 병합 헬퍼 함수]
// ==========================================
async function uploadFileWithChunkAndMerge(file) {
    const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB 단위 설정
    const totalChunks = Math.ceil(file.size / CHUNK_SIZE);

    const uploadId = 'upload_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
    const fileName = file.name;

    console.log(`[업로드 시작] 파일명: ${fileName}, 총 청크 수: ${totalChunks}`);

    let finalSavedFileName = null;

    try {
        for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
            const start = chunkIndex * CHUNK_SIZE;
            const end = Math.min(start + CHUNK_SIZE, file.size);
            const chunk = file.slice(start, end);

            const formData = new FormData();
            formData.append("file", chunk);
            formData.append("uploadId", uploadId);
            formData.append("chunkIndex", chunkIndex);
            formData.append("totalChunks", totalChunks);
            formData.append("originalName", fileName);

            const response = await fetch('/api/posts/upload-chunk', {
                method: 'POST',
                body: formData
            });

            if (!response.ok) {
                throw new Error(`${chunkIndex + 1}번째 청크 업로드 중 오류가 발생했습니다.`);
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
            console.error("파일 병합은 되었으나 최종 파일명이 반환되지 않았습니다. 서버 응답을 확인하세요.");
            throw new Error("최종 파일명을 가져오지 못했습니다.");
        }

        console.log("[파일 병합 최종 완료]:", finalSavedFileName);

        return {
            savedFileName: finalSavedFileName,
            originalName: fileName
        };

    } catch (error) {
        console.error("[업로드 실패]:", error);
        throw error;
    }
}