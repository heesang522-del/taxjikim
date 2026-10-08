async function uploadFile(file) {
    // 1단계: 스프링 서버에 Pre-signed URL 요청 (파일 자체를 보내지 않음!)
    const response = await axios.get(`/api/files/presigned-url?fileName=${encodeURIComponent(file.name)}`);
    const { presignedUrl, storedFileName } = response.data;

    // 2단계: 스프링 서버를 거치지 않고, S3/MinIO 스토리지로 직접 바이너리 파일 전송 (PUT 요청)
    await axios.put(presignedUrl, file, {
        headers: {
            'Content-Type': file.type
        }
    });

    // 3단계: 업로드가 완료되면 스프링 서버에 메타데이터 저장 통지
    await axios.post('/api/files/metadata', {
        originalFileName: file.name,
        storedFileName: storedFileName,
        fileSize: file.size
    });

    alert('대용량 파일 업로드 성공!');
}