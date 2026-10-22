import { client } from './client';

/**
 * 공통 client를 사용하고 ApiResponse 본문을 반환한다.
 * 인증은 기존 쿠키 인증 흐름을 따른다.
 */
async function request(config) {
  try {
    const response = await client.request(config);
    const body = response.data;

    if (body?.success === false) {
      const error = new Error(body.error?.message ?? '요청 처리에 실패했습니다.');

      error.code = body.error?.code;
      error.fields = body.error?.fields;
      error.status = response.status;

      throw error;
    }

    // 호출부에서 data와 meta를 사용할 수 있도록 공통 응답을 유지한다.
    return body;
  } catch (cause) {
    // 업로드 취소는 일반 요청 실패로 변환하지 않는다.
    if (cause.code === 'ERR_CANCELED' || cause.name === 'CanceledError') {
      throw cause;
    }

    const detail = cause.response?.data?.error;

    const error = new Error(detail?.message ?? cause.message ?? '서버 요청에 실패했습니다.');

    error.status = cause.response?.status ?? cause.status;
    error.code = detail?.code ?? cause.code;
    error.fields = detail?.fields ?? cause.fields;

    throw error;
  }
}

/**
 * PDF 자동 분석
 */
export async function parsePdf(file, { signal } = {}) {
  const formData = new FormData();

  formData.append('file', file);

  return request({
    url: '/tax/parse',
    method: 'POST',
    data: formData,
    // OCR 처리 시간을 고려해 PDF 분석 요청에만 적용한다.
    timeout: 120000,
    signal,
  });
}

/**
 * FileUploader에서 사용하는 형태로 PDF 분석 결과를 정규화한다.
 */
export async function uploadTaxPdfApi(file, { signal } = {}) {
  const response = await parsePdf(file, { signal });

  // 백엔드 ApiResponse의 data에서 실제 분석 결과를 꺼낸다.
  const result = response?.data;

  if (!result || typeof result !== 'object' || Array.isArray(result)) {
    throw new Error('PDF 분석 결과가 올바르지 않습니다.');
  }

  const transactions = Array.isArray(result.transactions) ? result.transactions : [];

  const skippedCount = Number(result.skippedCount ?? 0);

  return {
    // 페이지별 결과와 분석 건수 등 원래 응답 필드를 보존한다.
    ...result,

    originalFileName: result.originalFileName ?? result.fileName ?? file.name,

    fileId: result.fileId ?? null,
    persisted: result.persisted === true,

    // 거래 객체의 description, rawText, pageNumber를 그대로 유지한다.
    transactions,

    skippedCount: Number.isFinite(skippedCount) ? skippedCount : 0,
    evidence: result.evidence ?? null,
  };
}

/**
 * 후속 작업: 분류·확인한 거래를 DB에 저장하는 화면에서 연결한다.
 * 현재 PDF 파싱 화면에서는 호출하지 않는다.
 * 사용자 ID는 백엔드 인증 정보로 결정한다.
 */
export async function saveTransactions(rows) {
  return request({
    url: '/tax/transactions',
    method: 'POST',
    data: rows,
  });
}

/**
 * 후속 작업: 저장된 거래를 조회하는 화면에서 연결한다.
 * 현재 PDF 파싱 화면에서는 호출하지 않는다.
 * 반환된 ApiResponse의 data에 거래 목록이 들어 있다.
 */
export async function getTransactions() {
  return request({
    url: '/tax/transactions',
    method: 'GET',
  });
}
