'use no memo';

import { useRef, useState } from 'react';
import styled from 'styled-components';

import { Button } from '../common/index';
import { uploadTaxPdfApi } from '../../api/taxApi';

import {
  normalizeTaxKind,
  parseTaxAmount,
  useTaxStore,
} from '../../store/useTaxStore';

const FileSection = styled.section`
  margin-top: 12px;
  padding: 14px;
  border: 1px solid #dce3ed;
  border-radius: 12px;
  background: #fff;
`;

const FileHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
`;

const FileName = styled.p`
  margin: 0;
  font-weight: 600;
  overflow-wrap: anywhere;
`;

const Notice = styled.p`
  margin: 8px 0 0;
  color: #536476;
  font-size: 13px;
  line-height: 1.6;
`;

const ErrorMessage = styled.p`
  margin: 10px 0 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff4f3;
  color: #a82020;
  font-size: 13px;
  line-height: 1.6;
`;

function normalizeDate(value) {
  if (Array.isArray(value) && value.length === 3) {
    return value.map((part, index) => (
        index === 0
            ? String(part).padStart(4, '0')
            : String(part).padStart(2, '0')
    )).join('-');
  }

  return typeof value === 'string' ? value : '';
}

function normalizeCategory(value) {
  const category = String(value ?? '').trim();

  return /^[A-Z][A-Z0-9_]{0,49}$/.test(category)
      ? category
      : 'UNCLASSIFIED';
}

function isEvidence(item) {
  return item.amountBasis === 'DEDUCTION_ELIGIBLE';
}

function toTransactionRow(item, index, fileKey) {
  const date = normalizeDate(item.transactionDate);

  const actualAmount = parseTaxAmount(
      item.actualAmount ?? item.amount,
  );

  return {
    id: `pdf-${crypto.randomUUID()}`,
    sourceFileKey: fileKey,
    sourceFileId: null,
    sourceItemIndex: index,
    autoImported: true,

    // 서버가 발급한 DB 식별자를 유지한다.
    transactionId: item.transactionId,
    taxInputId: item.taxInputId,
    persisted: true,

    kind: normalizeTaxKind(item.transactionType),
    date,
    month: item.transactionMonth ?? null,
    year: date || item.transactionMonth
        ? null
        : item.documentYear == null
            ? null
            : String(item.documentYear),
    documentYear: item.documentYear ?? null,

    memo: item.description ?? '',
    cat: normalizeCategory(item.category),

    amt: actualAmount ?? '',
    actualAmount,
    preTaxIncome: parseTaxAmount(item.preTaxIncome),
    taxDeducted: parseTaxAmount(item.taxDeducted),
    deductedTaxRate: item.deductedTaxRate ?? null,

    amountBasis: item.amountBasis ?? 'UNSPECIFIED',
    valueKind: item.valueKind ?? 'DETAIL',

    pageNumber: item.pageNumber ?? null,
    rawText: item.rawText ?? '',

    parseSuccess: item.parseSuccess === true,
    needsReview: item.needsReview === true,
    failureReason: item.failureReason ?? null,

    selected: true,
    excluded: false,
  };
}

function toEvidenceRow(item, index, fileKey) {
  return {
    id: `${fileKey}:evidence:${index}`,
    sourceFileKey: fileKey,

    transactionId: item.transactionId,
    taxInputId: item.taxInputId,
    persisted: true,

    transactionDate: normalizeDate(item.transactionDate),
    transactionMonth: item.transactionMonth ?? null,
    documentYear: item.documentYear ?? null,

    description: item.description ?? '',
    category: normalizeCategory(item.category),
    amount: parseTaxAmount(item.amount),

    amountBasis: 'DEDUCTION_ELIGIBLE',
    valueKind: item.valueKind ?? 'MONTHLY_AGGREGATE',

    pageNumber: item.pageNumber ?? null,
    rawText: item.rawText ?? '',

    needsReview: item.needsReview === true,
    failureReason: item.failureReason ?? null,
  };
}

function fileKey(file) {
  return [file.name, file.size, file.lastModified].join(':');
}

export function FileUploader({
                               compact = false,
                               onUploadSuccess,
                             }) {
  const inputRef = useRef(null);
  const uploadingRef = useRef(false);

  const saving = useTaxStore(
      (state) => state.taxSaveBusy === true,
  );

  const uploadedFiles = useTaxStore(
      (state) => state.uploadedFiles,
  );

  const fileRows = useTaxStore(
      (state) => state.fileRows,
  );

  const evidenceDocuments = useTaxStore(
      (state) => state.evidenceDocuments,
  ) ?? [];

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const busy = loading || saving;

  async function handleUpload(file) {
    if (
        !file
        || uploadingRef.current
        || useTaxStore.getState().taxSaveBusy
    ) {
      return;
    }

    setError('');
    setMessage('');

    if (!/\.pdf$/i.test(file.name)) {
      setError('PDF 파일을 선택해주세요.');
      return;
    }

    if (file.size > 6 * 1024 * 1024) {
      setError('PDF 파일은 6MB 이하여야 합니다.');
      return;
    }

    const key = fileKey(file);
    const current = useTaxStore.getState();

    if ((current.uploadedFiles ?? []).some(
        (entry) => entry.key === key,
    )) {
      setError('이미 불러온 파일입니다.');
      return;
    }

    uploadingRef.current = true;
    setLoading(true);

    // 업로드 중 팝업 결정 등 다른 저장 요청이 겹치지 않도록 막는다.
    useTaxStore.setState({ taxSaveBusy: true });

    let resultInfo = null;

    try {
      const result = await uploadTaxPdfApi(file);

      const invalidItem = result.transactions.find((item) => (
          item.parseSuccess !== true
          || item.needsReview === true
          || parseTaxAmount(item.amount) === null
      ));

      if (invalidItem) {
        throw new Error(
            'DB 저장 응답에 확인이 필요한 거래가 있습니다. '
            + '재업로드하기 전에 저장 결과를 확인해주세요.',
        );
      }

      const normalItems = result.transactions.filter(
          (item) => !isEvidence(item),
      );

      const evidenceItems = result.transactions.filter(isEvidence);

      const transactionRows = normalItems.map(
          (item, index) => toTransactionRow(item, index, key),
      );

      const evidenceRows = evidenceItems.map(
          (item, index) => toEvidenceRow(item, index, key),
      );

      /*
       * 서버에서 DB 저장과 재조회까지 완료한 결과를 한 번에 반영한다.
       * 저장된 거래번호를 화면용 임시 번호로 대체하지 않는다.
       */
      useTaxStore.setState((state) => {
        const previousSnapshots = state.taxSavedSnapshots ?? [];

        const newInputIds = new Set(
            result.snapshots.map((snapshot) => snapshot.taxInputId),
        );

        return {
          uploadedFiles: [
            ...(state.uploadedFiles ?? []).filter(
                (entry) => entry.key !== key,
            ),
            {
              key,
              name: file.name,
              fileId: result.fileId,
              count: transactionRows.length,
              savedCount: result.savedCount,
              persisted: true,
              taxInputIdsByYear: result.taxInputIdsByYear,
            },
          ],

          fileRows: [
            ...(state.fileRows ?? []).filter(
                (row) => row.sourceFileKey !== key,
            ),
            ...transactionRows,
          ],

          evidenceDocuments: [
            ...(state.evidenceDocuments ?? []).filter(
                (document) => document.key !== key,
            ),
            ...(evidenceRows.length > 0
                ? [{
                  key,
                  name: file.name,
                  rows: evidenceRows,
                  persisted: true,
                }]
                : []),
          ],

          taxInputIdsByYear: {
            ...(state.taxInputIdsByYear ?? {}),
            ...result.taxInputIdsByYear,
          },

          taxSavedSnapshots: [
            ...previousSnapshots.filter(
                (snapshot) => !newInputIds.has(snapshot.taxInputId),
            ),
            ...result.snapshots,
          ],

          /*
           * 새 파일이 추가되면 이전 결정 결과는 최신 입력과 다르다.
           * 팝업의 결정에서 전체 입력과 프로필을 다시 저장한다.
           */
          taxLastSavedSignature: null,
          taxPendingSnapshot: null,

          inputMethod: 'file',
          calculated: false,
        };
      });

      setMessage(
          `DB에 ${result.savedCount}건을 저장했습니다. `
          + `일반 거래 ${transactionRows.length}건 · `
          + `월별 증빙 ${evidenceRows.length}건`,
      );

      resultInfo = {
        transactionCount: transactionRows.length,
        evidenceCount: evidenceRows.length,
        persisted: true,
        taxInputIdsByYear: result.taxInputIdsByYear,
      };
    } catch (cause) {
      setError(
          `${cause.message ?? 'PDF 내역을 불러오지 못했습니다.'}`
          + (cause.code ? ` [${cause.code}]` : ''),
      );
    } finally {
      uploadingRef.current = false;
      setLoading(false);

      useTaxStore.setState({ taxSaveBusy: false });
    }

    if (resultInfo) {
      try {
        await onUploadSuccess?.(resultInfo);
      } catch {
        setError(
            'DB 저장과 목록 반영은 완료됐지만 후속 화면 처리에 실패했습니다.',
        );
      }
    }
  }

  function handleRemoveFile(key) {
    if (busy || useTaxStore.getState().taxSaveBusy) return;

    /*
     * 원본 스냅샷은 보존한다.
     * 화면에서 제거한 결과는 팝업 결정 시 새 수정본에 반영한다.
     */
    useTaxStore.setState((state) => ({
      uploadedFiles: (state.uploadedFiles ?? []).filter(
          (entry) => entry.key !== key,
      ),
      fileRows: (state.fileRows ?? []).filter(
          (row) => row.sourceFileKey !== key,
      ),
      evidenceDocuments: (state.evidenceDocuments ?? []).filter(
          (document) => document.key !== key,
      ),
      taxLastSavedSignature: null,
      taxPendingSnapshot: null,
      calculated: false,
    }));

    setError('');
    setMessage(
        '파일을 현재 목록에서 제외했습니다. '
        + '원본은 DB에 보존되며, 팝업 결정 시 제외된 목록을 새로 저장합니다.',
    );
  }

  return (
      <div style={compact ? undefined : { padding: 16 }}>
        <Button
            type="button"
            size="sm"
            disabled={busy}
            onClick={() => inputRef.current?.click()}
        >
          {loading ? 'PDF 분석·DB 저장 중…' : 'PDF 불러오기'}
        </Button>

        <input
            ref={inputRef}
            hidden
            type="file"
            accept=".pdf,application/pdf"
            disabled={busy}
            onChange={(event) => {
              const file = event.target.files?.[0];

              // 같은 파일을 다시 선택할 수 있도록 입력값만 비운다.
              event.target.value = '';

              handleUpload(file);
            }}
        />

        {message && <Notice role="status">{message}</Notice>}

        {error && (
            <ErrorMessage role="alert">
              {error}
            </ErrorMessage>
        )}

        {(uploadedFiles ?? []).map((file) => {
          const transactionCount = (fileRows ?? []).filter(
              (row) => row.sourceFileKey === file.key,
          ).length;

          const document = evidenceDocuments.find(
              (entry) => entry.key === file.key,
          );

          const evidenceCount = document?.rows?.length ?? 0;

          return (
              <FileSection key={file.key}>
                <FileHeader>
                  <FileName>{file.name}</FileName>

                  <Button
                      type="button"
                      size="sm"
                      disabled={busy}
                      onClick={() => handleRemoveFile(file.key)}
                  >
                    파일 내역 제외
                  </Button>
                </FileHeader>

                <Notice>
                  일반 거래 {transactionCount}건
                  {' · '}
                  월별 증빙 {evidenceCount}건
                </Notice>

                <Notice>
                  카테고리 수정과 선택 삭제는 수입·지출 내역 확인
                </Notice>

                {evidenceCount > 0 && (
                    <Notice>
                      공제대상금액은 실제 출금액이나 최종 세금 공제액이 아니며,
                      수입·지출 합계에 포함하지 않습니다.
                    </Notice>
                )}
              </FileSection>
          );
        })}
      </div>
  );
}

// 기존 default import를 사용하는 파일과의 호환을 유지한다.
export default FileUploader;