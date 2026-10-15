import { client } from './client.js';

export const EVIDENCE_TYPES = { CREDIT_CARD: '신용카드', DEBIT_CARD: '직불·체크카드', MEDICAL: '의료비', UNKNOWN: '종류 확인 필요' };
export const EVIDENCE_STATUSES = { NEEDS_REVIEW: '원본 확인 필요', UNSUPPORTED_FORMAT: '추출 가능한 금액 없음', YEAR_MISMATCH: '귀속연도 불일치' };

export function normalizeTaxPdfResult(result) {
  const kind = result?.documentKind || 'LEDGER';
  if (kind === 'YEAR_END_EVIDENCE') {
    if (!result.evidence || !Array.isArray(result.evidence.items)) throw new Error('증빙 분석 응답을 확인할 수 없습니다.');
    // 증빙 응답에 거래가 섞여 와도 계산용 거래로 사용하지 않는다.
    return { ...result, documentKind: kind, transactions: [] };
  }
  if (kind !== 'LEDGER' || !Array.isArray(result?.transactions)) throw new Error('PDF 분석 응답의 문서 종류를 확인할 수 없습니다.');
  return { ...result, documentKind: kind, evidence: result.evidence || null };
}

export async function uploadTaxPdfApi(file, { accessToken, taxYear } = {}) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('taxYear', String(taxYear));
  try {
    const response = await client.post('/tax/upload-pdf', formData, {
      // PDF 분석은 일반 API보다 오래 걸릴 수 있어 요청 대기 시간을 별도로 설정한다.
      timeout: 120000,
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
    });
    const result = response.data;
    if (!result?.success) {
      throw new Error(result?.error?.message || 'PDF 분석 응답을 확인할 수 없습니다.');
    }
    return normalizeTaxPdfResult(result.data);
  } catch (error) {
    throw new Error(error.response?.data?.error?.message ||
      (error.response?.status === 413 ? '서버가 허용하는 업로드 크기를 초과했습니다.' : error.message) || 'PDF 업로드에 실패했습니다.');
  }
}

// 저장 성공 응답을 받은 뒤에만 계산 내역에 반영한다.
export async function saveReviewedTransactionApi(payload, accessToken) {
  try {
    const response = await client.post('/tax/reviewed-transactions', payload, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (!response.data?.success || !response.data.data?.transactionId) throw new Error('거래 저장 응답을 확인할 수 없습니다.');
    return response.data.data;
  } catch (error) {
    throw new Error(error.response?.data?.error?.message || error.message || '거래 저장에 실패했습니다. 다시 시도해 주세요.');
  }
}
