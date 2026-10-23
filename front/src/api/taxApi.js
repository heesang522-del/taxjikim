import { client } from './client';

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

    return body;
  } catch (cause) {
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

function validateId(value, label) {
  if (!Number.isSafeInteger(value) || value <= 0) {
    throw new Error(`${label}를 확인해주세요.`);
  }
}

function validateTaxYear(taxYear) {
  if (!Number.isInteger(taxYear) || taxYear < 1000 || taxYear > 9999) {
    throw new Error('귀속 연도를 네 자리 숫자로 입력해주세요.');
  }
}

function inputPath(taxInputId) {
  validateId(taxInputId, '분석번호');

  return `/tax/inputs/${taxInputId}`;
}

function transactionPath(taxInputId, transactionId) {
  validateId(transactionId, '거래번호');

  return `${inputPath(taxInputId)}/transactions/${transactionId}`;
}

export async function createTaxInput(input) {
  validateTaxYear(input?.taxYear);

  return request({
    url: '/tax/inputs/create',
    method: 'POST',
    data: input,
  });
}

export async function getSavedTaxInputs(taxYear = null) {
  if (taxYear !== null) {
    validateTaxYear(taxYear);
  }

  return request({
    url: '/tax/inputs/list',
    method: 'POST',
    params: taxYear === null ? {} : { taxYear },
  });
}

export async function getTaxInput(taxInputId) {
  return request({
    url: `${inputPath(taxInputId)}/detail`,
    method: 'POST',
  });
}

export async function updateTaxInput(taxInputId, input) {
  validateTaxYear(input?.taxYear);

  return request({
    url: `${inputPath(taxInputId)}/update`,
    method: 'POST',
    data: input,
  });
}

export async function deleteTaxInput(taxInputId) {
  return request({
    url: `${inputPath(taxInputId)}/delete`,
    method: 'POST',
  });
}

/*
 * 전체 거래 목록을 교체한다.
 * 호출 후 응답 data의 거래번호를 화면 상태에 다시 반영해야 한다.
 */
export async function saveTransactions(taxInputId, rows) {
  if (!Array.isArray(rows)) {
    throw new Error('저장할 거래 목록이 필요합니다.');
  }

  return request({
    url: `${inputPath(taxInputId)}/transactions/save`,
    method: 'POST',
    data: rows,
  });
}

export async function getTransactions(taxInputId) {
  return request({
    url: `${inputPath(taxInputId)}/transactions/list`,
    method: 'POST',
  });
}

export async function updateTransaction(taxInputId, transactionId, category) {
  if (typeof category !== 'string' || !/^[A-Z][A-Z0-9_]{0,49}$/.test(category)) {
    throw new Error('카테고리 코드를 확인해주세요.');
  }

  return request({
    url: `${transactionPath(taxInputId, transactionId)}/category/update`,
    method: 'POST',
    data: { category },
  });
}

export async function deleteTransaction(taxInputId, transactionId) {
  return request({
    url: `${transactionPath(taxInputId, transactionId)}/delete`,
    method: 'POST',
  });
}

export async function getTaxResult(taxInputId) {
  return request({
    url: `${inputPath(taxInputId)}/results/detail`,
    method: 'POST',
  });
}

export async function calculateTaxInput(taxInputId) {
  // 서버 계산 엔진 연결 전에는 503 오류를 반환한다.
  return request({
    url: `${inputPath(taxInputId)}/calculate`,
    method: 'POST',
  });
}

export async function parsePdf(file, { signal, taxInputId = null } = {}) {
  const formData = new FormData();

  formData.append('file', file);

  if (taxInputId !== null) {
    validateId(taxInputId, '분석번호');
    formData.append('taxInputId', String(taxInputId));
  }

  return request({
    url: '/tax/parse',
    method: 'POST',
    data: formData,
    // OCR 시간도 포함하므로 Python 통신 제한 시간과는 별개다.
    timeout: 180000,
    signal,
  });
}

export async function uploadTaxPdfApi(file, { signal, taxInputId = null } = {}) {
  const response = await parsePdf(file, {
    signal,
    taxInputId,
  });

  const result = response?.data;

  if (!result || typeof result !== 'object' || Array.isArray(result)) {
    throw new Error('PDF 분석 결과가 올바르지 않습니다.');
  }

  const skippedCount = Number(result.skippedCount ?? 0);

  return {
    ...result,
    originalFileName: result.originalFileName ?? result.fileName ?? file.name,
    fileId: result.fileId ?? null,
    // 파싱 성공은 DB 거래 저장 완료를 의미하지 않는다.
    persisted: false,
    transactions: Array.isArray(result.transactions) ? result.transactions : [],
    skippedCount: Number.isFinite(skippedCount) ? skippedCount : 0,
    evidence: result.evidence ?? null,
  };
}
