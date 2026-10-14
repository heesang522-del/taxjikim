// src/components/post/post-preview.js
// DOM 조작을 제거하고 순수 로직만 함수로 분리합니다.

export function filterBlockedFiles(files) {
    const blockedExtensions = [".exe", ".zip", ".bat", ".cmd", ".sh", ".jar", ".msi", ".iso", ".dmg"];
    const validFiles = files.filter(file => 
        !blockedExtensions.some(ext => file.name.toLowerCase().endsWith(ext))
    );
    return validFiles;
}

export async function uploadFileChunks(file) {
    const CHUNK_SIZE = 1024 * 1024 * 2; // 2MB
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

        const response = await fetch('/api/posts/upload-chunk', {
            method: "POST",
            body: formData
        });

        const result = await response.json();
        if (!result.success) {
            throw new Error(result.message || "파일 업로드 오류");
        }

        if (result.data.completed) {
            finalFileName = result.data.savedFileName;
        }
    }
    return finalFileName;
}