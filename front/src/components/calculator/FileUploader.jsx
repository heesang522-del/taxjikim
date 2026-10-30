'use no memo';

import { useRef, useState } from 'react';
import styled from 'styled-components';
import { Button } from '../common/index';
import { uploadTaxPdfApi, saveInitialTaxRows } from '../../api/taxApi';
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

  return typeof value === 'string' ? value.trim() : '';
}

function normalizeCategory(value) {
  const category = String(value ?? '').trim();

  return /^[A-Z][A-Z0-9_]{0,49}$/.test(category) ? category : 'UNCLASSIFIED';
}

function isSavedRow(row) {
  return (
    Number.isSafeInteger(row.transactionId) &&
    row.transactionId > 0 &&
    Number.isSafeInteger(row.taxInputId) &&
    row.taxInputId > 0
  );
}

function isReviewRow(row) {
  return row.needsReview === true || row.excluded === true;
}

function isPendingRow(row) {
  return !isSavedRow(row) && !isReviewRow(row) && row.selected !== false;
}

// 파싱 결과를 저장 전 임시 거래 구조로 변환한다.
function toTransactionRow(item, index, key) {
  const date = normalizeDate(item.transactionDate);
  const actualAmount = parseTaxAmount(item.actualAmount ?? item.amount);
  const needsReview = item.needsReview === true || item.parseSuccess !== true;
  const excluded = item.excluded === true || needsReview;

  return {
    id: `pdf-${crypto.randomUUID()}`,
    sourceFileKey: key,
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

function getFileRecord(key) {
  const record = (useTaxStore.getState().uploadedFiles ?? []).find((file) => file.key === key);

  if (!record) {
    throw new Error('처리할 파일 정보를 찾지 못했습니다.');
  }

  return record;
}

// 파일별 분석번호를 기록하여 저장 재시도에 사용한다.
function rememberPdfInput(key, { taxYear, taxInputId }) {
  useTaxStore.setState((current) => ({
    uploadedFiles: (current.uploadedFiles ?? []).map((file) => {
      if (file.key !== key) return file;

      return {
        ...file,
        pendingInputIdsByYear: {
          ...(file.pendingInputIdsByYear ?? {}),
          [taxYear]: taxInputId,
        },
      };
    }),
  }));
}

// 저장 응답의 거래번호를 임시 거래에 연결한다.
// 모든 정상 거래가 저장되기 전에는 편집 목록에 공개하지 않는다.
function commitPdfGroup(key, { taxYear, taxInputId, rows: savedRows, bindings }) {
  const savedById = new Map(
    bindings.filter((binding) => binding.source === 'file').map((binding) => [binding.rowId, binding.saved])
  );

  useTaxStore.setState((current) => ({
    uploadedFiles: (current.uploadedFiles ?? []).map((file) => {
      if (file.key !== key) return file;

      const pending = {
        ...(file.pendingInputIdsByYear ?? {}),
      };

      if (pending[taxYear] === taxInputId) {
        delete pending[taxYear];
      }

      return {
        ...file,
        pendingInputIdsByYear: pending,
        stagedRows: (file.stagedRows ?? []).map((row) => {
          const saved = savedById.get(row.id);

          if (!saved) return row;

          return {
            ...row,
            taxInputId: saved.taxInputId,
            transactionId: saved.transactionId,
            cat: saved.category,
            persisted: true,
            needsReview: false,
            excluded: false,
            selected: true,
            failureReason: null,
          };
        }),
        savedGroups: [
          ...(file.savedGroups ?? []).filter((group) => group.taxInputId !== taxInputId),
          {
            taxYear,
            taxInputId,
            rows: savedRows,
          },
        ],
      };
    }),
  }));
}

// 저장된 거래를 화면과 저장 스냅샷에 공개한다.
function publishPdfRows(key) {
  const record = getFileRecord(key);
  const stagedRows = record.stagedRows ?? [];

  if (stagedRows.some(isPendingRow)) {
    throw new Error('저장되지 않은 거래가 남아 있습니다. 저장 재시도를 눌러주세요.');
  }

  const savedGroups = record.savedGroups ?? [];

  useTaxStore.setState((current) => {
    const idsByYear = {
      ...(current.taxInputIdsByYear ?? {}),
    };
    const groupIds = new Set(savedGroups.map((group) => group.taxInputId));

    for (const group of savedGroups) {
      idsByYear[group.taxYear] = group.taxInputId;
    }

    return {
      // 다른 파일의 기존 거래는 유지한다.
      fileRows: [...(current.fileRows ?? []).filter((row) => row.sourceFileKey !== key), ...stagedRows],
      uploadedFiles: (current.uploadedFiles ?? []).map((file) =>
        file.key === key
          ? {
              ...file,
              published: true,
              stagedRows: undefined,
              savedGroups: undefined,
              pendingInputIdsByYear: {},
            }
          : file
      ),
      taxInputIdsByYear: idsByYear,
      taxSavedSnapshots: [
        ...(current.taxSavedSnapshots ?? []).filter((snapshot) => !groupIds.has(snapshot.taxInputId)),
        ...savedGroups,
      ],
      taxLastSavedSignature: null,
      calculated: false,
      inputMethod: 'file',
    };
  });

  const savedCount = stagedRows.filter(isSavedRow).length;
  const reviewCount = stagedRows.filter(isReviewRow).length;
  const taxInputIdsByYear = {};

  for (const group of savedGroups) {
    taxInputIdsByYear[group.taxYear] = group.taxInputId;
  }

  return {
    transactionCount: stagedRows.length,
    savedCount,
    reviewCount,
    evidenceCount: 0,
    persisted: savedCount > 0,
    taxInputIdsByYear,
  };
}

// 현재 파일의 정상 거래 전체를 DB에 저장한다.
// 이미 저장된 거래는 거래번호가 있으므로 재저장 대상에서 제외된다.
async function saveParsedPdf(key) {
  const current = useTaxStore.getState();
  const record = getFileRecord(key);

  if (record.published === true) {
    throw new Error('이미 저장이 완료된 파일입니다.');
  }

  const scopedState = {
    ...current,
    // 다른 파일이나 수동 입력 거래가 함께 저장되지 않도록 제한한다.
    manualRows: [],
    fileRows: record.stagedRows ?? [],
    evidenceDocuments: [],
    taxPendingInputIdsByYear: {
      ...(record.pendingInputIdsByYear ?? {}),
    },
  };

  await saveInitialTaxRows(scopedState, {
    onInputCreated: (input) => rememberPdfInput(key, input),
    onGroupSaved: (group) => commitPdfGroup(key, group),
  });

  return publishPdfRows(key);
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

  async function notifySuccess(resultInfo) {
    try {
      await onUploadSuccess?.(resultInfo);
    } catch {
      setError('거래는 저장됐지만 후속 화면 처리에 실패했습니다. 현재 목록을 확인해주세요.');
    }
  }

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
    const alreadyUploaded = (useTaxStore.getState().uploadedFiles ?? []).some((entry) => entry.key === key);

    if (alreadyUploaded) {
      setError('이미 불러온 파일입니다. 저장 실패 상태라면 해당 파일의 저장 재시도를 눌러주세요.');
      return;
    }

    uploadingRef.current = true;
    setLoading(true);
    setMessage('PDF를 분석하고 있습니다.');
    useTaxStore.setState({
      taxSaveBusy: true,
    });

    let resultInfo = null;

    try {
      const result = await uploadTaxPdfApi(file);
      const transactionRows = result.transactions.map((item, index) => toTransactionRow(item, index, key));

      // 저장 전에는 파싱 결과를 임시로 보관한다.
      useTaxStore.setState((current) => ({
        uploadedFiles: [
          ...(current.uploadedFiles ?? []),
          {
            key,
            name: file.name,
            fileId: result.fileId,
            count: transactionRows.length,
            published: false,
            stagedRows: transactionRows,
            savedGroups: [],
            pendingInputIdsByYear: {},
          },
        ],
      }));

      setMessage('분석한 정상 거래를 DB에 저장하고 있습니다.');

      resultInfo = await saveParsedPdf(key);

      setMessage(
        `분석 ${resultInfo.transactionCount}건` +
          ` · DB 저장 ${resultInfo.savedCount}건` +
          ` · 검토 ${resultInfo.reviewCount}건`
      );
    } catch (cause) {
      setError((cause.message ?? 'PDF 분석 또는 거래 저장에 실패했습니다.') + (cause.code ? ` [${cause.code}]` : ''));
    } finally {
      uploadingRef.current = false;
      setLoading(false);

      // 저장 결과를 화면에 연결한 뒤 잠금을 해제한다.
      // 기존 내역 컴포넌트가 새 거래를 감지하여 팝업을 연다.
      useTaxStore.setState({
        taxSaveBusy: false,
      });
    }

    if (resultInfo) {
      await notifySuccess(resultInfo);
    }
  }

  async function handleRetrySave(key) {
    if (uploadingRef.current || useTaxStore.getState().taxSaveBusy) {
      return;
    }

    uploadingRef.current = true;
    setLoading(true);
    setError('');
    setMessage('저장되지 않은 거래를 다시 저장하고 있습니다.');
    useTaxStore.setState({
      taxSaveBusy: true,
    });

    let resultInfo = null;

    try {
      resultInfo = await saveParsedPdf(key);

      setMessage(`DB 저장 ${resultInfo.savedCount}건` + ` · 검토 ${resultInfo.reviewCount}건`);
    } catch (cause) {
      setError((cause.message ?? '거래 저장 재시도에 실패했습니다.') + (cause.code ? ` [${cause.code}]` : ''));
    } finally {
      uploadingRef.current = false;
      setLoading(false);
      useTaxStore.setState({
        taxSaveBusy: false,
      });
    }

    if (resultInfo) {
      await notifySuccess(resultInfo);
    }
  }

  function handleRemoveFile(key) {
    const current = useTaxStore.getState();

    if (busy || uploadingRef.current || current.taxSaveBusy) {
      return;
    }

    setError('');
    setMessage('');

    const record = (current.uploadedFiles ?? []).find((file) => file.key === key);

    if (!record) {
      setError('이미 제외된 파일입니다.');
      return;
    }

    const rows =
      record.published === false
        ? (record.stagedRows ?? [])
        : (current.fileRows ?? []).filter((row) => row.sourceFileKey === key);

    const hasSavedRows = rows.some(isSavedRow);
    const hasUnconfirmedSave = Object.keys(record.pendingInputIdsByYear ?? {}).length > 0;

    // 저장된 거래를 화면에서만 제거하여 DB와 불일치하지 않도록 한다.
    if (hasSavedRows || hasUnconfirmedSave) {
      setError(
        hasUnconfirmedSave
          ? '저장 결과 확인이 필요한 파일입니다. 저장 재시도를 먼저 진행해주세요.'
          : 'DB에 저장된 거래가 있습니다. 내역 확인·수정에서 거래를 삭제 반영한 뒤 파일을 제외해주세요.'
      );
      return;
    }

    useTaxStore.setState((state) => ({
      uploadedFiles: (state.uploadedFiles ?? []).filter((entry) => entry.key !== key),
      fileRows: (state.fileRows ?? []).filter((row) => row.sourceFileKey !== key),
      taxLastSavedSignature: null,
      calculated: false,
    }));

    setMessage('미저장 파일 내역을 화면에서 제외했습니다.');
  }

  return (
    <Container style={compact ? undefined : { padding: 16 }}>
      <Button type="button" size="sm" disabled={busy} onClick={() => inputRef.current?.click()}>
        {loading ? 'PDF 처리 중…' : 'PDF 불러오기'}
      </Button>

      <input
        ref={inputRef}
        hidden
        type="file"
        accept=".pdf,application/pdf"
        disabled={busy}
        onChange={(event) => {
          const file = event.target.files?.[0];

          // 같은 파일도 다시 선택할 수 있도록 입력값을 비운다.
          event.target.value = '';
          void handleUpload(file);
        }}
      />

      <StatusArea aria-live="polite">
        {error ? <ErrorMessage role="alert">{error}</ErrorMessage> : <span>{message}</span>}
      </StatusArea>

      {uploadedFiles.map((file) => {
        const unpublished = file.published === false;
        const rows = unpublished ? (file.stagedRows ?? []) : fileRows.filter((row) => row.sourceFileKey === file.key);
        const reviewRows = rows.filter(isReviewRow);
        const savedCount = rows.filter(isSavedRow).length;
        const pendingCount = rows.filter(isPendingRow).length;
        const hasUnconfirmedSave = Object.keys(file.pendingInputIdsByYear ?? {}).length > 0;
        const cannotRemove = savedCount > 0 || hasUnconfirmedSave;

        return (
          <FileSection key={file.key}>
            <FileHeader>
              <FileName>{file.name}</FileName>

              {unpublished && (
                <Button type="button" size="sm" disabled={busy} onClick={() => void handleRetrySave(file.key)}>
                  저장 재시도
                </Button>
              )}

              <Button
                type="button"
                size="sm"
                disabled={busy || cannotRemove}
                onClick={() => handleRemoveFile(file.key)}
              >
                파일 내역 제외
              </Button>
            </FileHeader>

            <Notice>
              전체 {rows.length}건{' · '}
              DB 저장 {savedCount}건{' · '}
              저장 대기 {pendingCount}건{' · '}
              검토 {reviewRows.length}건
            </Notice>

            <Notice>PDF 분석 후 정상 거래를 DB에 저장합니다. 저장된 거래는 팝업에서 수정할 수 있습니다.</Notice>

            {unpublished && (
              <Notice>저장이 완료되지 않았습니다. 저장 재시도를 눌러 남은 거래의 저장을 진행해주세요.</Notice>
            )}

            {cannotRemove && <Notice>저장된 거래 또는 저장 확인이 필요한 거래가 있어 파일 제외가 제한됩니다.</Notice>}

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
