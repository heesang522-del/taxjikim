'use no memo';

import { useRef, useState } from 'react';
import { Button } from '../common/index';
import { saveInitialTaxRows, saveTaxReview, uploadTaxPdfApi } from '../../api/taxApi';
import { normalizeTaxKind, parseTaxAmount, useTaxStore } from '../../stores/useTaxStore';
import { parseDeductedTaxRate, parseWon } from '../../utils/transactionAmount';

function normalizeDate(value) {
  if (Array.isArray(value) && value.length === 3) {
    return value.map((part, index) => String(part).padStart(index === 0 ? 4 : 2, '0')).join('-');
  }

  return value ?? '';
}

function isSavedRow(row) {
  return row.persisted === true && row.transactionId != null && row.taxInputId != null;
}

function isReviewRow(row) {
  return row.needsReview === true || row.excluded === true;
}

function isPendingRow(row) {
  return !isSavedRow(row) && !isReviewRow(row) && row.selected !== false;
}

// 분석 API의 결과를 화면과 저장에서 사용하는 거래 구조로 변환한다.
function toTransactionRow(item, index, key) {
  const actualAmount = parseWon(item.actualAmount ?? item.amount);
  const deductedTaxRate = parseDeductedTaxRate(item.deductedTaxRate);
  // 저장 범위를 벗어난 행만 검토 대상으로 보내고 정상 행의 자동 저장은 진행한다.
  let numberError = null;
  if (actualAmount === null) {
    numberError = '실제 입출금액을 확인해주세요.';
  } else if (item.deductedTaxRate != null && deductedTaxRate === null) {
    numberError = '차감 세율의 범위와 소수 자릿수를 확인해주세요.';
  }
  const needsReview = item.needsReview === true || numberError !== null;
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
    date: normalizeDate(item.transactionDate),
    month: null,
    year: null,
    documentYear: item.documentYear ?? null,
    memo: item.description ?? '',
    cat: item.category || 'UNCLASSIFIED',
    amt: actualAmount ?? '',
    actualAmount,
    preTaxIncome: parseTaxAmount(item.preTaxIncome),
    taxDeducted: parseTaxAmount(item.taxDeducted),
    deductedTaxRate,
    amountBasis: item.amountBasis ?? 'UNSPECIFIED',
    valueKind: item.valueKind ?? 'DETAIL',
    pageNumber: item.pageNumber ?? null,
    rawText: item.rawText ?? '',
    parseSuccess: item.parseSuccess === true,
    needsReview,
    failureReason: needsReview ? item.failureReason || numberError || '거래 내용을 확인해주세요.' : null,
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

function getFileRows(file, fileRows) {
  if (file.published === false) {
    return file.stagedRows ?? [];
  }

  return fileRows.filter((row) => row.sourceFileKey === file.key);
}

function requiresSaveCheck(file) {
  return file.saveOutcomeUnknown === true;
}

// DB 거래번호를 화면에 표시할 행에 연결한다.
function bindPdfRow(row, saved) {
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
}

// 저장 성공 응답의 거래번호를 임시 거래에 연결한다.
function commitPdfGroup(key, { taxYear, taxInputId, rows: savedRows, bindings }) {
  const savedById = new Map(
    bindings.filter((binding) => binding.source === 'file').map((binding) => [binding.rowId, binding.saved])
  );

  useTaxStore.setState((current) => ({
    uploadedFiles: (current.uploadedFiles ?? []).map((file) => {
      if (file.key !== key) return file;

      return {
        ...file,
        stagedRows: (file.stagedRows ?? []).map((row) => {
          const saved = savedById.get(row.id);
          return saved ? bindPdfRow(row, saved) : row;
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

function publishPdfRows(key, saveError = null) {
  const record = getFileRecord(key);
  const stagedRows = record.stagedRows ?? [];
  const pendingCount = stagedRows.filter(isPendingRow).length;

  if (!saveError && pendingCount > 0) {
    throw new Error('저장 완료를 확인하지 못한 거래가 있습니다.');
  }

  const unconfirmedCount = saveError ? pendingCount : 0;
  const status = saveError?.status;

  // 응답이 끊기면 DB 반영 여부를 알 수 없어 같은 파일의 재업로드를 제한한다.
  const saveOutcomeUnknown = unconfirmedCount > 0 && (status == null || status === 408 || status >= 500);

  // 저장이 중단되면 성공 응답을 받은 거래와 원래 검토 대상만 목록에 표시한다.
  const visibleRows = saveError ? stagedRows.filter((row) => isSavedRow(row) || isReviewRow(row)) : stagedRows;

  const savedGroups = record.savedGroups ?? [];

  useTaxStore.setState((current) => {
    const idsByYear = { ...(current.taxInputIdsByYear ?? {}) };
    const groupIds = new Set(savedGroups.map((group) => group.taxInputId));

    for (const group of savedGroups) {
      idsByYear[group.taxYear] = group.taxInputId;
    }

    return {
      fileRows: [...(current.fileRows ?? []).filter((row) => row.sourceFileKey !== key), ...visibleRows],
      uploadedFiles: (current.uploadedFiles ?? []).map((file) =>
        file.key === key
          ? {
              ...file,
              published: true,
              stagedRows: undefined,
              savedGroups: undefined,
              unconfirmedCount,
              saveOutcomeUnknown,
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

  const savedCount = visibleRows.filter(isSavedRow).length;
  const reviewCount = visibleRows.filter(isReviewRow).length;
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

async function saveParsedPdf(key) {
  const current = useTaxStore.getState();
  const record = getFileRecord(key);

  if (record.published === true) {
    throw new Error('이미 처리가 완료된 파일입니다.');
  }

  const scopedState = {
    ...current,
    // 이 PDF에서 가져온 거래만 자동 저장 대상으로 전달한다.
    manualRows: [],
    fileRows: record.stagedRows ?? [],
    evidenceDocuments: [],
  };

  await saveInitialTaxRows(scopedState, {
    saveInputWithTransactions: true,
    onGroupSaved: (group) => commitPdfGroup(key, group),
  });

  return publishPdfRows(key);
}

// DB 삭제가 성공한 거래를 화면과 저장 스냅샷에서 제거한다.
function removeSavedPdfRow(key, { rowId, taxInputId, transactionId }) {
  useTaxStore.setState((current) => ({
    fileRows: (current.fileRows ?? []).filter((row) => row.sourceFileKey !== key || row.id !== rowId),
    taxSavedSnapshots: (current.taxSavedSnapshots ?? []).map((snapshot) => {
      if (snapshot.taxInputId !== taxInputId) {
        return snapshot;
      }

      return {
        ...snapshot,
        rows: (snapshot.rows ?? []).filter((row) => row.transactionId !== transactionId),
      };
    }),
    taxLastSavedSignature: null,
    calculated: false,
  }));
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

    const key = fileKey(file);
    const existingFile = (useTaxStore.getState().uploadedFiles ?? []).find((entry) => entry.key === key);

    if (existingFile) {
      if (existingFile.saveOutcomeUnknown) {
        setError('이 PDF의 일부 거래는 저장 여부를 확인할 수 없습니다.');
      } else {
        setMessage('이미 불러온 PDF입니다. 아래 파일 내역을 확인해주세요.');
      }

      return;
    }

    uploadingRef.current = true;
    setLoading(true);
    setMessage('PDF를 분석하고 있습니다.');
    useTaxStore.setState({ taxSaveBusy: true });

    let resultInfo = null;
    let analysisCompleted = false;

    try {
      const result = await uploadTaxPdfApi(file);
      const transactionRows = result.transactions.map((item, index) => toTransactionRow(item, index, key));

      // 분석 응답을 보관한 뒤 정상 거래의 자동 저장을 시작한다.
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
          },
        ],
      }));

      analysisCompleted = true;
      setMessage('분석한 정상 거래를 저장하고 있습니다.');

      resultInfo = await saveParsedPdf(key);

      setMessage(
        `분석 ${resultInfo.transactionCount}건` +
          ` · DB 저장 ${resultInfo.savedCount}건` +
          ` · 검토 ${resultInfo.reviewCount}건`
      );
    } catch (cause) {
      const detail = cause?.message ?? 'PDF 처리 중 오류가 발생했습니다.';
      const code = cause?.code ? ` [${cause.code}]` : '';

      setMessage('');

      if (analysisCompleted) {
        const partial = publishPdfRows(key, cause ?? new Error(detail));

        setError(
          `거래 자동 저장을 완료하지 못했습니다. ${detail}${code}` +
            (partial.savedCount > 0 ? ` 저장이 확인된 ${partial.savedCount}건은 목록에 표시했습니다.` : '')
        );
      } else {
        setError(`PDF를 분석하지 못했습니다. ${detail}${code}`);
      }
    } finally {
      uploadingRef.current = false;
      setLoading(false);

      // 확인된 거래를 화면에 반영한 뒤 처리 잠금을 해제한다.
      useTaxStore.setState({ taxSaveBusy: false });
    }

    if (resultInfo) {
      await notifySuccess(resultInfo);
    }
  }

  async function handleRemoveFile(key) {
    const current = useTaxStore.getState();

    if (uploadingRef.current || current.taxSaveBusy) {
      return;
    }

    setError('');
    setMessage('');

    const record = (current.uploadedFiles ?? []).find((file) => file.key === key);

    if (!record) {
      setError('이미 제외된 파일입니다.');
      return;
    }

    const rows = getFileRows(record, current.fileRows ?? []);

    if (requiresSaveCheck(record)) {
      setError('일부 거래의 DB 저장 여부를 확인할 수 없어 파일 내역을 제외할 수 없습니다.');
      return;
    }

    const savedRows = rows.filter(isSavedRow);

    if (
      savedRows.length > 0 &&
      !window.confirm(`"${record.name}"에서 저장한 거래 ${savedRows.length}건을 삭제할까요?`)
    ) {
      return;
    }

    useTaxStore.setState({ taxSaveBusy: true });

    try {
      if (savedRows.length > 0) {
        setMessage('이 파일에서 저장한 거래를 삭제하고 있습니다.');

        // 기존 일괄 삭제 API에 이 PDF의 저장 거래만 전달한다.
        await saveTaxReview(
          { fileRows: [] },
          {
            originalState: { fileRows: savedRows },
            onOperationSuccess: (operation) => removeSavedPdfRow(key, operation),
          }
        );
      }

      // DB 삭제가 끝나면 파일 표시와 남아 있는 미저장 거래를 제거한다.
      useTaxStore.setState((state) => ({
        uploadedFiles: (state.uploadedFiles ?? []).filter((entry) => entry.key !== key),
        fileRows: (state.fileRows ?? []).filter((row) => row.sourceFileKey !== key),
        taxLastSavedSignature: null,
        calculated: false,
      }));

      setMessage(
        savedRows.length > 0
          ? `DB 거래 ${savedRows.length}건을 삭제하고 파일 내역을 제외했습니다.`
          : '파일 내역을 화면에서 제외했습니다.'
      );
    } catch (cause) {
      const deletedCount = cause.completedOperations?.length ?? 0;

      setMessage('');
      setError(
        (cause.message ?? '파일 내역을 제외하지 못했습니다.') +
          (deletedCount > 0 ? ` 삭제된 ${deletedCount}건은 화면에 반영했습니다. 남은 거래를 다시 시도해주세요.` : '')
      );
    } finally {
      useTaxStore.setState({ taxSaveBusy: false });
    }
  }

  return (
    <div className={compact ? 'tax-upload tax-upload--compact' : 'tax-upload'}>
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

          // PDF 분석 자체가 실패하면 같은 파일을 다시 선택할 수 있게 한다.
          event.target.value = '';
          void handleUpload(file);
        }}
      />

      <div className="tax-upload__status" aria-live="polite">
        {error ? (
          <div className="tax-upload__error" role="alert">
            {error}
          </div>
        ) : (
          <span>{message}</span>
        )}
      </div>

      {uploadedFiles.map((file) => {
        const rows = getFileRows(file, fileRows);
        const reviewRows = rows.filter(isReviewRow);
        const savedCount = rows.filter(isSavedRow).length;
        const pendingCount = rows.filter(isPendingRow).length;
        const unconfirmedCount = file.unconfirmedCount ?? 0;
        const saveCheckRequired = requiresSaveCheck(file);

        return (
          <section className="tax-upload__file" key={file.key}>
            <div className="tax-upload__file-header">
              <p className="tax-upload__file-name">{file.name}</p>

              <Button
                type="button"
                size="sm"
                variant={savedCount > 0 ? 'danger' : undefined}
                disabled={busy || saveCheckRequired}
                onClick={() => void handleRemoveFile(file.key)}
              >
                파일 내역 제외
              </Button>
            </div>

            <p className="tax-upload__notice">
              전체 {rows.length + unconfirmedCount}건{' · '}
              DB 저장 {savedCount}건{' · '}
              검토 {reviewRows.length}건{pendingCount > 0 && ` · 저장 대기 ${pendingCount}건`}
              {unconfirmedCount > 0 &&
                (file.saveOutcomeUnknown
                  ? ` · 저장 미확인 ${unconfirmedCount}건`
                  : ` · 저장 실패 ${unconfirmedCount}건`)}
            </p>

            {saveCheckRequired ? (
              <p className="tax-upload__notice">
                저장 여부를 확인할 수 없는 거래가 있어 파일 제외가 제한됩니다. 저장이 확인된 거래는 내역 확인·수정에서
                편집할 수 있습니다.
              </p>
            ) : (
              <p className="tax-upload__notice">
                저장된 거래는 내역 확인·수정에서 편집할 수 있습니다. 파일 내역을 제외하면 이 PDF의 저장 거래도
                삭제됩니다.
              </p>
            )}

            {reviewRows.length > 0 && (
              <details>
                <summary>검토 사유 {reviewRows.length}건 확인</summary>

                <ul className="tax-upload__review-list">
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
                </ul>
              </details>
            )}
          </section>
        );
      })}
    </div>
  );
}

export default FileUploader;
