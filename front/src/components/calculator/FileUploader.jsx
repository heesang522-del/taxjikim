'use no memo';

import { useRef, useState } from 'react';

import { Button, Card, Icon } from '../common/index';

import { useTax, parseTaxAmount, normalizeTaxKind } from '../../store/useTaxStore';

import { useToast } from '../../store/useToastStore';
import { uploadTaxPdfApi } from '../../api/taxApi';

function fileKey(file) {
  return `${file.name}:${file.size}:${file.lastModified}`;
}

function normalizeDate(value) {
  if (!value) return null;

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

  if (!match) return null;

  const normalized = [match[1], match[2].padStart(2, '0'), match[3].padStart(2, '0')].join('-');

  const parsed = new Date(`${normalized}T00:00:00Z`);

  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== normalized) {
    return null;
  }

  return normalized;
}

function toFileRow(item, index, fileId) {
  const date = normalizeDate(item.transactionDate);

  /*
   * 원본의 거래 구분만 사용한다.
   * 설명이나 금액을 보고 수입·지출을 추측하지 않는다.
   */
  const kind = normalizeTaxKind(item.transactionType);

  const preTaxIncome = parseTaxAmount(item.preTaxIncome);

  const taxDeducted = parseTaxAmount(item.taxDeducted);

  const actualAmount = parseTaxAmount(item.actualAmount);

  const reportedAmount = parseTaxAmount(item.amount);

  let amount;
  let amountBasis;

  if (kind === 'in' && preTaxIncome !== null) {
    // 수입에 세전 금액이 명시되어 있으면 그대로 보존한다.
    amount = preTaxIncome;
    amountBasis = 'GROSS';
  } else if (actualAmount !== null) {
    amount = actualAmount;

    amountBasis = kind === 'in' ? 'NET' : 'CASH';
  } else {
    amount = reportedAmount;
    amountBasis = 'UNSPECIFIED';
  }

  // 구분이 미확인이어도 금액이 읽히면 자동 체크한다.
  const available = amount !== null;

  return {
    sourceItemIndex: index,
    sourceFileId: fileId,
    source: 'transaction',

    kind,

    // 원본 구분도 보존한다.
    rawTransactionType: item.transactionType ?? null,

    date,
    month: null,
    year: null,

    m: date ? Number(date.slice(5, 7)) : null,

    // 원본에 없는 세부 카테고리는 임의로 채우지 않는다.
    cat: typeof item.category === 'string' ? item.category.trim() : '',

    memo: item.description ?? item.rawText ?? '',

    amt: amount ?? '',
    amountBasis,

    preTaxIncome,
    taxDeducted,
    actualAmount,
    reportedAmount,

    valueKind: 'transaction',

    selected: available,
    excluded: !available,

    persisted: false,
    autoImported: true,

    pageNumber: item.pageNumber ?? null,
    rawText: item.rawText ?? '',

    parseSuccess: item.parseSuccess ?? false,
  };
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

export default function FileUploader({ onUploadSuccess }) {
  const inputRef = useRef(null);
  const uploadingRef = useRef(false);

  const [loading, setLoading] = useState(false);
  const [currentFile, setCurrentFile] = useState('');

  const { uploadedFiles, fileRows, addFileResult, setFileSelected, removeUploadedFile } = useTax();

  const toast = useToast();

  const selectedRows = fileRows.filter(
    (row) => row.selected !== false && row.excluded !== true && parseTaxAmount(row.amt) !== null
  );

  const totals = selectedRows.reduce(
    (result, row) => {
      const amount = parseTaxAmount(row.amt);

      if (amount === null) return result;

      const kind = normalizeTaxKind(row.kind);

      if (kind === 'in') {
        result.income += amount;
        result.incomeCount += 1;
      } else if (kind === 'out') {
        result.expense += amount;
        result.expenseCount += 1;
      } else {
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

  const upload = async (fileList) => {
    if (uploadingRef.current) return;

    const files = Array.from(fileList ?? []);

    if (!files.length) return;

    uploadingRef.current = true;
    setLoading(true);

    try {
      const existingKeys = new Set(uploadedFiles.map((file) => file.key));

      for (const file of files) {
        const key = fileKey(file);

        if (!/\.pdf$/i.test(file.name)) {
          toast('PDF 파일만 업로드할 수 있습니다.');
          continue;
        }

        if (existingKeys.has(key)) {
          toast('이미 업로드한 파일입니다.');
          continue;
        }

        setCurrentFile(file.name);

        try {
          const response = await uploadTaxPdfApi(file);

          if (response?.success === false) {
            throw new Error(response.error?.message ?? response.message ?? 'PDF 분석에 실패했습니다.');
          }

          const data = response?.data ?? response;

          if (!data || !Array.isArray(data.transactions)) {
            throw new Error('PDF 분석 응답 형식이 올바르지 않습니다.');
          }

          const rows = data.transactions.map((item, index) => toFileRow(item, index, data.fileId ?? null));

          const availableCount = rows.filter((row) => row.selected).length;

          addFileResult(rows, {
            key,
            name: file.name,
            fileId: data.fileId ?? null,
            count: rows.length,
          });

          setFileSelected(key, true);

          existingKeys.add(key);

          toast(`${file.name}: ${rows.length}건 추출 · ` + `${availableCount}건 자동 반영`);

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
  };

  return (
    <Card title="파일로 불러오기" icon="uploadCloud">
      <div
        className="drop"
        onDragOver={(event) => {
          event.preventDefault();
        }}
        onDrop={(event) => {
          event.preventDefault();
          upload(event.dataTransfer.files);
        }}
      >
        <Icon name="uploadCloud" size={36} />

        <p style={{ fontSize: 17 }}>PDF 거래정보 자동 분석</p>

        <p className="small muted">원본의 수입·지출 구분을 유지하고 금액이 읽힌 거래를 자동 체크합니다.</p>

        <Button size="sm" disabled={loading} onClick={() => inputRef.current?.click()}>
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

            upload(files);
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
          border: '1px solid #e2e8f0',
          borderRadius: 10,
        }}
      >
        <p style={{ fontWeight: 700 }}>현재 반영된 PDF 거래</p>

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

        <p className="small muted">세부 카테고리가 없는 경우에는 미기재로 표시합니다.</p>
      </section>

      {uploadedFiles.map((file) => {
        const rows = fileRows.filter((row) => row.sourceFileKey === file.key);

        const availableRows = rows.filter((row) => row.excluded !== true && parseTaxAmount(row.amt) !== null);

        const selectedCount = availableRows.filter((row) => row.selected === true).length;

        const allSelected = availableRows.length > 0 && selectedCount === availableRows.length;

        return (
          <section
            key={file.key}
            style={{
              padding: 14,
              marginTop: 12,
              border: '1px solid #e2e8f0',
              borderRadius: 10,
            }}
          >
            <p style={{ fontWeight: 600 }}>{file.name}</p>

            <p className="small muted">
              {rows.length}건 추출 · {selectedCount}건 반영
            </p>

            <label>
              <input
                type="checkbox"
                checked={allSelected}
                disabled={loading || !availableRows.length}
                onChange={(event) => {
                  setFileSelected(file.key, event.target.checked);
                }}
              />{' '}
              파일 거래 전체 반영
            </label>

            <div style={{ marginTop: 10 }}>
              <Button
                size="sm"
                variant="ghost"
                disabled={loading}
                onClick={() => {
                  removeUploadedFile(file.key);
                }}
              >
                파일 제거
              </Button>
            </div>
          </section>
        );
      })}
    </Card>
  );
}
