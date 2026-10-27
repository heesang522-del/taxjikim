'use no memo';

import { useRef, useState } from 'react';
import styled from 'styled-components';

import { Button } from '../common/index';
import { uploadTaxPdfApi } from '../../api/taxApi';
import { normalizeTaxKind, parseTaxAmount, useTaxStore } from '../../store/useTaxStore';

const Container = styled.div`
  min-width: 0;
  max-width: 100%;
`;

const StatusArea = styled.div`
  height: 54px;
  padding-top: 8px;
  overflow: auto;
  scrollbar-gutter: stable;
  font-size: 13px;
  line-height: 1.6;
  overflow-wrap: anywhere;
`;

const FileSection = styled.section`
  margin-top: 12px;
  padding: 14px;
  border: 1px solid #dce3ed;
  border-radius: 12px;
  background: #fff;
  min-width: 0;
`;

const FileHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  min-width: 0;
`;

const FileName = styled.p`
  margin: 0;
  min-width: 0;
  flex: 1 1 160px;
  font-weight: 600;
  overflow-wrap: anywhere;
`;

const Notice = styled.p`
  margin: 8px 0 0;
  color: #536476;
  font-size: 13px;
  line-height: 1.6;
  overflow-wrap: anywhere;
`;

const ErrorMessage = styled.div`
  padding: 8px 10px;
  border-radius: 8px;
  background: #fff4f3;
  color: #a82020;
`;

const ReviewList = styled.ul`
  margin: 8px 0 0;
  padding-left: 20px;
  max-height: 220px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.6;
  overflow-wrap: anywhere;
`;

function normalizeDate(value) {
  if (Array.isArray(value) && value.length === 3) {
    return value.map((part, index) => String(part).padStart(index === 0 ? 4 : 2, '0')).join('-');
  }

  return typeof value === 'string' ? value : '';
}

function normalizeCategory(value) {
  const category = String(value ?? '').trim();

  return /^[A-Z][A-Z0-9_]{0,49}$/.test(category) ? category : 'UNCLASSIFIED';
}

function toTransactionRow(item, index, fileKey) {
  const date = normalizeDate(item.transactionDate);
  const actualAmount = parseTaxAmount(item.actualAmount ?? item.amount);

  const needsReview = item.needsReview === true || item.parseSuccess !== true;

  const excluded = item.excluded === true || needsReview;

  return {
    id: `pdf-${crypto.randomUUID()}`,
    sourceFileKey: fileKey,
    sourceFileId: null,
    sourceItemIndex: index,
    autoImported: true,

    transactionId: null,
    taxInputId: null,
    persisted: false,

    kind: normalizeTaxKind(item.transactionType),
    date,
    month: null,
    year: null,
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
    needsReview,
    failureReason: needsReview ? item.failureReason || '거래 내용을 확인해주세요.' : null,

    selected: !excluded,
    excluded,
  };
}

function fileKey(file) {
  return [file.name, file.size, file.lastModified].join(':');
}

export function FileUploader({ compact = false, onUploadSuccess }) {
  const inputRef = useRef(null);
  const uploadingRef = useRef(false);

  const saving = useTaxStore((state) => state.taxSaveBusy === true);
  const uploadedFiles = useTaxStore((state) => state.uploadedFiles) ?? [];
  const fileRows = useTaxStore((state) => state.fileRows) ?? [];

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const busy = loading || saving;

  async function handleUpload(file) {
    if (!file || uploadingRef.current || useTaxStore.getState().taxSaveBusy) {
      return;
    }

    setError('');
    setMessage('');

    if (!/\.pdf$/i.test(file.name)) {
      setError('PDF 파일을 선택해주세요.');
      return;
    }

    if (file.size === 0 || file.size > 6 * 1024 * 1024) {
      setError('비어 있지 않은 6MB 이하 PDF를 선택해주세요.');
      return;
    }

    const key = fileKey(file);

    if ((useTaxStore.getState().uploadedFiles ?? []).some((entry) => entry.key === key)) {
      setError('이미 불러온 파일입니다.');
      return;
    }

    uploadingRef.current = true;
    setLoading(true);
    useTaxStore.setState({ taxSaveBusy: true });

    let resultInfo = null;

    try {
      const result = await uploadTaxPdfApi(file);

      const transactionRows = result.transactions.map((item, index) => toTransactionRow(item, index, key));

      useTaxStore.setState((state) => ({
        uploadedFiles: [
          ...(state.uploadedFiles ?? []),
          {
            key,
            name: file.name,
            fileId: result.fileId,
            count: transactionRows.length,
          },
        ],
        fileRows: [...(state.fileRows ?? []), ...transactionRows],
        taxLastSavedSignature: null,
        calculated: false,
        inputMethod: 'file',
      }));

      setMessage(
        `분석 ${transactionRows.length}건` +
          ` · 저장 대기 ${transactionRows.length - result.reviewCount}건` +
          ` · 검토 ${result.reviewCount}건`
      );

      resultInfo = {
        transactionCount: transactionRows.length,
        savedCount: 0,
        reviewCount: result.reviewCount,
        evidenceCount: 0,
        persisted: false,
        taxInputIdsByYear: {},
      };
    } catch (cause) {
      setError((cause.message ?? 'PDF를 불러오지 못했습니다.') + (cause.code ? ` [${cause.code}]` : ''));
    } finally {
      uploadingRef.current = false;
      setLoading(false);
      useTaxStore.setState({ taxSaveBusy: false });
    }

    if (resultInfo) {
      try {
        await onUploadSuccess?.(resultInfo);
      } catch {
        setError('분석 결과는 반영됐지만 후속 화면 처리에 실패했습니다. 현재 목록을 확인해주세요.');
      }
    }
  }

  function handleRemoveFile(key) {
    if (busy || useTaxStore.getState().taxSaveBusy) return;

    useTaxStore.setState((state) => ({
      uploadedFiles: (state.uploadedFiles ?? []).filter((entry) => entry.key !== key),
      fileRows: (state.fileRows ?? []).filter((row) => row.sourceFileKey !== key),
      taxLastSavedSignature: null,
      calculated: false,
    }));

    setError('');
    setMessage('화면에서 파일 내역을 제외했습니다. 저장된 DB 거래를 삭제하려면 내역 확인·수정에서 삭제해주세요.');
  }

  return (
    <Container style={compact ? undefined : { padding: 16 }}>
      <Button type="button" size="sm" disabled={busy} onClick={() => inputRef.current?.click()}>
        {loading ? 'PDF 분석 중…' : 'PDF 불러오기'}
      </Button>

      <input
        ref={inputRef}
        hidden
        type="file"
        accept=".pdf,application/pdf"
        disabled={busy}
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = '';
          void handleUpload(file);
        }}
      />

      <StatusArea aria-live="polite">
        {error ? (
          <ErrorMessage role="alert">{error}</ErrorMessage>
        ) : (
          <span>{loading ? 'PDF를 분석하고 있습니다.' : message}</span>
        )}
      </StatusArea>

      {uploadedFiles.map((file) => {
        const rows = fileRows.filter((row) => row.sourceFileKey === file.key);

        const reviewRows = rows.filter((row) => row.needsReview === true || row.excluded === true);

        const savedCount = rows.filter((row) => row.persisted === true).length;

        const pendingCount = rows.filter(
          (row) => row.persisted !== true && row.needsReview !== true && row.excluded !== true
        ).length;

        return (
          <FileSection key={file.key}>
            <FileHeader>
              <FileName>{file.name}</FileName>

              <Button type="button" size="sm" disabled={busy} onClick={() => handleRemoveFile(file.key)}>
                파일 내역 제외
              </Button>
            </FileHeader>

            <Notice>
              전체 {rows.length}건 · DB 저장 {savedCount}건{' · '}저장 대기 {pendingCount}건{' · '}검토{' '}
              {reviewRows.length}건
            </Notice>

            <Notice>PDF 불러오기는 분석만 수행합니다. 내역 확인·수정에서 결정하면 정상 거래를 저장합니다.</Notice>

            {reviewRows.length > 0 && (
              <details>
                <summary>검토 사유 {reviewRows.length}건 확인</summary>

                <ReviewList>
                  {reviewRows.map((row) => (
                    <li key={row.id}>
                      {row.pageNumber ? `${row.pageNumber}페이지 · ` : ''}
                      {row.date || '날짜 확인 필요'}
                      {' · '}
                      {row.memo || '설명 확인 필요'}
                      {' — '}
                      {row.failureReason || '거래 내용을 확인해주세요.'}
                    </li>
                  ))}
                </ReviewList>
              </details>
            )}
          </FileSection>
        );
      })}
    </Container>
  );
}

export default FileUploader;
