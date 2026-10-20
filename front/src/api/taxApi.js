import axios from 'axios';

const api = axios.create({
  baseURL: '/api/v1/tax',
  timeout: 120000,
});

/**
 * 기존 JWT 인증 토큰 첨부
 *
 * 실제 로그인 시스템이 accessToken을
 * localStorage에 저장하는 경우에만 유효하다.
 */
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

/**
 * 증빙 분석 상태 표시
 *
 * 백엔드 상태 코드와 일치해야 한다.
 */
export const EVIDENCE_STATUSES = {
  PENDING: '검토 대기',
  REVIEW_REQUIRED: '검토 필요',
  CONFIRMED: '확인 완료',
  REJECTED: '제외',
};

/**
 * 공통 응답 처리
 */
async function request(config) {
  try {
    const response = await api.request(config);

    const body = response.data;

    if (body && typeof body === 'object' && 'success' in body) {
      if (body.success === false) {
        const error = new Error(body.error?.message ?? '요청 처리에 실패했습니다.');

        error.code = body.error?.code;

        throw error;
      }

      return body.data ?? body;
    }

    return body;
  } catch (cause) {
    if (cause.code === 'ERR_CANCELED' || cause.name === 'CanceledError') {
      throw cause;
    }

    const detail = cause.response?.data?.error;

    const error = new Error(detail?.message ?? cause.message ?? '서버 요청에 실패했습니다.');

    error.status = cause.response?.status ?? cause.status;

    error.code = detail?.code ?? cause.code;

    throw error;
  }
}

/**
 * PDF 자동 분석
 */
export async function parsePdf(file, { taxYear, signal } = {}) {
  const formData = new FormData();

  formData.append('file', file);

  if (taxYear != null) {
    formData.append('taxYear', String(taxYear));
  }

  return request({
    url: '/parse',
    method: 'POST',
    data: formData,
    signal,
  });
}

/**
 * FileUploader 전용 API
 *
 * 백엔드 응답을 기존 컴포넌트가
 * 사용하는 형태로 정규화한다.
 */
export async function uploadTaxPdfApi(file, { taxYear, signal } = {}) {
  const result = await parsePdf(file, {
    taxYear,
    signal,
  });

  if (!result || typeof result !== 'object') {
    throw new Error('PDF 분석 결과가 올바르지 않습니다.');
  }

  const transactions = Array.isArray(result.transactions) ? result.transactions : [];

  const skippedCount = Number(result.skippedCount ?? 0);

  return {
    ...result,

    originalFileName: result.originalFileName ?? result.fileName ?? file.name,

    fileId: result.fileId ?? null,

    persisted: result.persisted === true,

    transactions,

    skippedCount: Number.isFinite(skippedCount) ? skippedCount : 0,

    evidence: result.evidence ?? null,
  };
}

/**
 * 선택한 거래를 DB에 저장
 *
 * 사용자 ID는 프론트에서 보내지 않고
 * 백엔드 인증 정보로 결정한다.
 */
export async function saveTransactions(rows) {
  return request({
    url: '/transactions',
    method: 'POST',
    data: rows,
  });
}

/**
 * 로그인 사용자 거래 조회
 */
export async function getTransactions() {
  return request({
    url: '/transactions',
    method: 'GET',
  });
}
