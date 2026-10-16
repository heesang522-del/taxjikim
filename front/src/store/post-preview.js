// 게시글 미디어 미리보기와 청크 업로드에서 공유하는 로직이다.

// 기본 우클릭·드래그 저장 동작을 제한한다. 서버 접근 권한 검사나 완전한 복제 방지는 아니다.
export function preventMediaSave(event) {
  event.preventDefault();
}

export const imagePreviewProps = {
  onContextMenu: preventMediaSave,
  onDragStart: preventMediaSave,
  draggable: false,
};

export const videoPreviewProps = {
  ...imagePreviewProps,
  controlsList: 'nodownload',
};

export function filterBlockedFiles(files) {
  // 사진·동영상만 허용해 PDF, 압축파일, 스크립트, 실행파일을 모두 제외한다.
  const validFiles = files.filter((file) => /\.(jpe?g|png|gif|webp|mp4|webm|mov)$/i.test(file.name));
  return validFiles;
}

export async function uploadFileChunks(file) {
  if (!file.size) throw new Error('빈 파일은 업로드할 수 없습니다.');
  if (!filterBlockedFiles([file]).length)
    throw new Error('사진·동영상만 업로드할 수 있습니다. PDF·ZIP·실행파일은 차단됩니다.');
  const CHUNK_SIZE = 1024 * 1024 * 2; // 2MB
  const uploadId = 'upload_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
  let finalFileName = null;

  for (let i = 0; i < totalChunks; i++) {
    const start = i * CHUNK_SIZE;
    const end = Math.min(start + CHUNK_SIZE, file.size);
    const chunk = file.slice(start, end);

    const formData = new FormData();
    formData.append('file', chunk);
    formData.append('uploadId', uploadId);
    formData.append('chunkIndex', i);
    formData.append('totalChunks', totalChunks);
    formData.append('originalName', file.name);

    const response = await fetch('/api/posts/upload-chunk', {
      method: 'POST',
      // 같은 출처 요청의 인증 쿠키만 사용하며 토큰을 JavaScript에 보관하지 않는다.
      credentials: 'same-origin',
      body: formData,
    });

    const result = await response.json();
    if (!response.ok || !result.success) {
      throw new Error(result.error?.message || result.message || '파일 업로드 오류');
    }

    if (result.data.completed) {
      finalFileName = result.data.savedFileName;
    }
  }
  if (!finalFileName) throw new Error('파일 병합 완료 응답을 받지 못했습니다.');
  return finalFileName;
}
