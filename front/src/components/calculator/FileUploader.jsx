'use no memo';

import { useRef, useState } from 'react';

import { Button, Card, Icon } from '../common/index';

import { useTax, useTaxStore, parseTaxAmount, normalizeTaxKind, getRowTaxYear } from '../../store/useTaxStore';

import { useToast } from '../../store/useToastStore';
import { uploadTaxPdfApi } from '../../api/taxApi';

function fileKey(file) {
  return `${file.name}:${file.size}:${file.lastModified}`;
}

function normalizeDate(value) {
  if (!value) {
    return null;
  }

  let text;

  if (Array.isArray(value)) {
    const [year, month, day] = value;

    if (year == null || month == null || day == null) {
      return null;
    }

    text = [String(year).padStart(4, '0'), String(month).padStart(2, '0'), String(day).padStart(2, '0')].join('-');
  } else {
    text = String(value).trim();
  }

  const match = text.match(/^(\d{4})[-./](\d{1,2})[-./](\d{1,2})$/);

  if (!match) {
    return null;
  }

  const normalized = [match[1], match[2].padStart(2, '0'), match[3].padStart(2, '0')].join('-');

  const parsed = new Date(`${normalized}T00:00:00Z`);

  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== normalized) {
    return null;
  }

  return normalized;
}

function toFileRow(item, index, fileId) {
  const date = normalizeDate(item.transactionDate);

  // 원본의 거래 구분을 사용하고 임의로 분류하지 않는다.
  const kind = normalizeTaxKind(item.transactionType);

  const preTaxIncome = parseTaxAmount(item.preTaxIncome);

  const taxDeducted = parseTaxAmount(item.taxDeducted);

  const actualAmount = parseTaxAmount(item.actualAmount);

  const reportedAmount = parseTaxAmount(item.amount);

  let amount;
  let amountBasis;

  if (kind === 'in' && preTaxIncome !== null) {
    amount = preTaxIncome;
    amountBasis = 'GROSS';
  } else if (actualAmount !== null) {
    amount = actualAmount;
    amountBasis = kind === 'in' ? 'NET' : 'CASH';
  } else {
    amount = reportedAmount;
    amountBasis = 'UNSPECIFIED';
  }

  const available = amount !== null;

  return {
    sourceItemIndex: index,
    sourceFileId: fileId,
    source: 'transaction',

    kind,
    rawTransactionType: item.transactionType ?? null,

    // 원본 날짜를 보존하며 선택 연도로 덮어쓰지 않는다.
    date,
    month: null,
    year: null,
    m: date ? Number(date.slice(5, 7)) : null,

    cat: typeof item.category === 'string' ? item.category.trim() : '',

    memo: item.description ?? item.rawText ?? '',

    amt: amount ?? '',
    amountBasis,

    preTaxIncome,
    taxDeducted,
    actualAmount,
    reportedAmount,

    valueKind: 'transaction',

    // 날짜나 연도와 관계없이 유효 금액이 있으면 선택한다.
    selected: available,
    excluded: !available,

    persisted: false,
    autoImported: true,

    pageNumber: item.pageNumber ?? null,
    rawText: item.rawText ?? '',
    parseSuccess: item.parseSuccess ?? false,
  };
}

function canInclude(row) {
  return row.excluded !== true && parseTaxAmount(row.amt) !== null;
}

function isIncluded(row) {
  return canInclude(row) && row.selected !== false;
}

function formatMoney(value) {
  return `${Number(value ?? 0).toLocaleString('ko-KR')}원`;
}

function getErrorMessage(error) {
  const status = error?.status ?? error?.response?.status;

  if (status === 401) {
    return 'PDF 분석 요청이 인증 단계에서 거부되었습니다.';
  }

  if (status === 413) {
    return '업로드 가능한 파일 크기를 초과했습니다.';
  }

  if (status === 503) {
    return 'PDF 분석 서버 또는 OCR 서비스를 사용할 수 없습니다.';
  }

  const body = error?.response?.data;

  const detailMessage = typeof body?.detail === 'string' ? body.detail : body?.detail?.message;

  return body?.error?.message ?? detailMessage ?? error?.message ?? 'PDF 분석에 실패했습니다.';
}

export default function FileUploader({ onUploadSuccess, compact = false }) {
  const inputRef = useRef(null);
  const uploadingRef = useRef(false);

  const [loading, setLoading] = useState(false);
  const [currentFile, setCurrentFile] = useState('');

  const { uploadedFiles, fileRows, setFileSelected, removeUploadedFile } = useTax();

  const toast = useToast();

  // 모든 연도의 유효한 선택 거래를 합산한다.
  const selectedRows = fileRows.filter(isIncluded);

  const totals = selectedRows.reduce(
    (result, row) => {
      const amount = parseTaxAmount(row.amt);
      const kind = normalizeTaxKind(row.kind);

      if (kind === 'in') {
        result.income += amount;
        result.incomeCount += 1;
      } else if (kind === 'out') {
        result.expense += amount;
        result.expenseCount += 1;
      } else {
        // 미분류 금액은 별도 합계로 표시한다.
        result.unknown += amount;
        result.unknownCount += 1;
      }

      result.count += 1;

      return result;
    },
    {
      income: 0,
      expense: 0,
      unknown: 0,
      incomeCount: 0,
      expenseCount: 0,
      unknownCount: 0,
      count: 0,
    }
  );

  async function handleUpload(fileList) {
    if (uploadingRef.current) {
      return;
    }

    const files = Array.from(fileList ?? []);

    if (!files.length) {
      return;
    }

    uploadingRef.current = true;
    setLoading(true);

    try {
      for (const file of files) {
        const key = fileKey(file);

        if (!/\.pdf$/i.test(file.name)) {
          toast('PDF 파일만 업로드할 수 있습니다.');
          continue;
        }

        const alreadyUploaded = useTaxStore.getState().uploadedFiles.some((entry) => entry.key === key);

        if (alreadyUploaded) {
          toast('이미 업로드한 파일입니다.');
          continue;
        }

        setCurrentFile(file.name);

        try {
          // 업로드와 분석은 귀속 연도 선택 없이 수행한다.
          const response = await uploadTaxPdfApi(file);

          if (response?.success === false) {
            throw new Error(response.error?.message ?? response.message ?? 'PDF 분석에 실패했습니다.');
          }

          const data = response?.data ?? response;

          if (!data || !Array.isArray(data.transactions)) {
            throw new Error('PDF 분석 응답 형식이 올바르지 않습니다.');
          }

          const rows = data.transactions.map((item, index) => toFileRow(item, index, data.fileId ?? null));

          useTaxStore.getState().addFileResult(rows, {
            key,
            name: file.name,
            fileId: data.fileId ?? null,
            count: rows.length,
          });

          // 스토어의 실제 선택 결과로 안내 건수를 계산한다.
          const savedRows = useTaxStore.getState().fileRows.filter((row) => row.sourceFileKey === key);

          const includedCount = savedRows.filter(isIncluded).length;

          const unknownYearCount = savedRows.filter((row) => getRowTaxYear(row) === null).length;

          toast(
            `${file.name}: ${rows.length}건 추출 · ` +
              `전체 연도 ${includedCount}건 선택 · ` +
              `날짜 확인 필요 ${unknownYearCount}건`
          );

          onUploadSuccess?.(data);
        } catch (error) {
          toast(getErrorMessage(error));
        }
      }
    } finally {
      uploadingRef.current = false;
      setLoading(false);
      setCurrentFile('');
    }
  }

  if (compact) {
    return (
      <span>
        <Button type="button" size="sm" variant="soft" disabled={loading} onClick={() => inputRef.current?.click()}>
          <Icon name="uploadCloud" size={14} /> {loading ? 'PDF 읽는 중…' : 'PDF 불러오기'}
        </Button>
        <input
          ref={inputRef}
          hidden
          multiple
          type="file"
          accept=".pdf,application/pdf"
          disabled={loading}
          aria-label="PDF 거래 파일 선택"
          onChange={(event) => {
            const files = Array.from(event.target.files ?? []);
            event.target.value = '';
            upload(files);
          }}
        />
        {loading && (
          <span className="small muted" role="status">
            {' '}
            {currentFile} 분석 중
          </span>
        )}
      </span>
    );
  }

  return (
    <Card title="파일로 불러오기" icon="uploadCloud">
      <p className="small muted" style={{ marginBottom: 12 }}>
        연도와 관계없이 선택한 모든 거래를 합계에 반영합니다.
      </p>

      <div
        className="drop"
        onDragOver={(event) => {
          event.preventDefault();
        }}
        onDrop={(event) => {
          event.preventDefault();
          handleUpload(event.dataTransfer.files);
        }}
      >
        <Icon name="uploadCloud" size={36} />

        <p style={{ fontSize: 17 }}>PDF 거래정보 자동 분석</p>

        <p className="small muted">모든 연도 거래를 보존하고 금액이 있는 거래를 선택합니다.</p>

        <Button
          size="sm"
          disabled={loading}
          onClick={() => {
            inputRef.current?.click();
          }}
        >
          {loading ? '분석 중...' : 'PDF 선택'}
        </Button>

        <input
          ref={inputRef}
          hidden
          multiple
          type="file"
          accept=".pdf,application/pdf"
          disabled={loading}
          onChange={(event) => {
            const files = Array.from(event.target.files ?? []);

            event.target.value = '';

            handleUpload(files);
          }}
        />
      </div>

      {loading && (
        <p className="small muted" role="status">
          {currentFile} 분석 중...
        </p>
      )}

      <section
        style={{
          padding: 14,
          marginTop: 16,
          border: '1px solid var(--border)',
          borderRadius: 10,
        }}
      >
        <p style={{ fontWeight: 700 }}>전체 연도 PDF 거래 합계</p>

        <p>전체: {totals.count}건</p>

        <p>
          수입: {totals.incomeCount}건 · {formatMoney(totals.income)}
        </p>

        <p>
          지출: {totals.expenseCount}건 · {formatMoney(totals.expense)}
        </p>

        <p>
          구분 미확인: {totals.unknownCount}건 · {formatMoney(totals.unknown)}
        </p>

        <p className="small muted">
          날짜 미확인 거래도 금액이 있으면 합계에 포함합니다. 이 값은 전체 거래 합계이며 특정 연도의 세액 결과가
          아닙니다.
        </p>
      </section>

      {uploadedFiles.map((file) => {
        const rows = fileRows.filter((row) => row.sourceFileKey === file.key);

        const availableRows = rows.filter(canInclude);

        const selectedCount = availableRows.filter((row) => row.selected !== false).length;

        const unknownYearCount = rows.filter((row) => getRowTaxYear(row) === null).length;

        const allSelected = availableRows.length > 0 && selectedCount === availableRows.length;

        return (
          <section
            key={file.key}
            style={{
              padding: 14,
              marginTop: 12,
              border: '1px solid var(--border)',
              borderRadius: 10,
            }}
          >
            <p style={{ fontWeight: 600 }}>{file.name}</p>

            <p className="small muted">전체 {rows.length}건 추출</p>

            <p className="small muted">전체 연도 거래 중 {selectedCount}건 선택</p>

            <p className="small muted">날짜 확인 필요 {unknownYearCount}건</p>

            <label>
              <input
                type="checkbox"
                checked={allSelected}
                disabled={loading || !availableRows.length}
                onChange={(event) => {
                  setFileSelected(file.key, event.target.checked);
                }}
              />{' '}
              전체 연도 거래 선택
            </label>

            {unknownYearCount > 0 && (
              <p className="small muted">
                날짜 미확인 거래도 전체 합계에는 포함됩니다. 귀속 연도별 세액 계산이나 서버 저장 전에는 실제 날짜를
                확인해주세요.
              </p>
            )}

            <div style={{ marginTop: 10 }}>
              <Button
                size="sm"
                variant="ghost"
                disabled={loading}
                onClick={() => {
                  removeUploadedFile(file.key);
                }}
              >
                파일 전체 제거
              </Button>
            </div>

            <p className="small muted">파일을 제거하면 이 파일에서 추출한 모든 연도의 브라우저 내역이 제거됩니다.</p>
          </section>
        );
      })}
    </Card>
  );
}
