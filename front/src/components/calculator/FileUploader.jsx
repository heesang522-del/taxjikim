'use no memo';

import { useRef, useState } from 'react';

import { Button, Card, Icon } from '../common/index';
import { useTax, parseTaxAmount, normalizeTaxKind } from '../../store/useTaxStore';
import { useToast } from '../../store/useToastStore';
import { uploadTaxPdfApi } from '../../api/taxApi';
import { YEAR } from '../../data/taxConstants';

function fileKey(file) {
  return `${file.name}:${file.size}:${file.lastModified}`;
}

function normalizeDate(value) {
  if (!value) return '';

  if (Array.isArray(value)) {
    const [year, month, day] = value;

    if (year && month && day) {
      return [year, String(month).padStart(2, '0'), String(day).padStart(2, '0')].join('-');
    }

    return '';
  }

  const text = String(value).trim();

  const match = text.match(/^(\d{4})[-./년\s]+(\d{1,2})[-./월\s]+(\d{1,2})/);

  return match ? `${match[1]}-${match[2].padStart(2, '0')}-${match[3].padStart(2, '0')}` : '';
}

function normalizeMonth(value) {
  if (!value) return null;

  const match = String(value).match(/^(\d{4})[-./년\s]+(0?[1-9]|1[0-2])/);

  return match ? `${match[1]}-${match[2].padStart(2, '0')}` : null;
}

function toFileRow(item, index, fileId, source) {
  const date = normalizeDate(item.transactionDate ?? item.date);

  const month = date ? null : normalizeMonth(item.transactionMonth ?? item.month ?? item.period);

  const rawYear = item.documentYear ?? item.year ?? null;

  const year = !date && !month && rawYear != null ? String(rawYear) : null;

  const amount = parseTaxAmount(item.amount ?? item.reportedAmount);

  const kind = normalizeTaxKind(item.transactionType ?? item.type ?? item.kind);

  return {
    sourceItemIndex: index,
    sourceFileId: fileId,
    source,

    kind,
    date: date || null,
    month,
    year,

    m: date || month ? Number((date || month).slice(5, 7)) : null,

    cat: String(item.category ?? ''),
    memo: String(item.description ?? item.label ?? item.rawText ?? ''),

    amt: amount ?? '',
    valueKind: item.valueKind ?? source,

    excluded: item.excluded === true,

    selected: amount !== null && item.excluded !== true,

    persisted: false,
    autoImported: true,

    pageNumber: item.pageNumber ?? null,
    rawText: item.rawText ?? '',
  };
}

function formatMoney(value) {
  return `${Number(value ?? 0).toLocaleString('ko-KR')}원`;
}

function errorMessage(error) {
  const status = error?.response?.status;

  if (status === 401) {
    return 'PDF 분석 요청이 인증 단계에서 거부되었습니다.';
  }

  if (status === 500) {
    return '서버에서 PDF 분석 중 오류가 발생했습니다.';
  }

  return (
    error?.response?.data?.message ?? error?.response?.data?.detail ?? error?.message ?? 'PDF 분석에 실패했습니다.'
  );
}

export default function FileUploader({ onUploadSuccess }) {
  const inputRef = useRef(null);
  const uploadingRef = useRef(false);

  const [loading, setLoading] = useState(false);
  const [currentFile, setCurrentFile] = useState('');

  const { uploadedFiles, fileRows, selectedTaxTotals, addFileResult, setFileSelected, removeUploadedFile } = useTax();

  const toast = useToast();

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
          const response = await uploadTaxPdfApi(file, {
            taxYear: YEAR,
          });

          if (response?.success === false) {
            throw new Error(response.message ?? 'PDF 분석 실패');
          }

          const data = response?.data ?? response;

          const transactions = Array.isArray(data?.transactions) ? data.transactions : [];

          const summaries = Array.isArray(data?.summaryItems) ? data.summaryItems : [];

          const items = [
            ...transactions.map((item) => ({
              item,
              source: 'transaction',
            })),
            ...summaries.map((item) => ({
              item,
              source: 'summary',
            })),
          ];

          const rows = items.map(({ item, source }, index) => toFileRow(item, index, data.fileId ?? null, source));

          const fileInfo = {
            key,
            name: file.name,
            fileId: data.fileId ?? null,
            count: rows.length,
          };

          addFileResult(rows, fileInfo);
          existingKeys.add(key);

          const selectedRows = rows.filter((row) => row.selected && row.amt !== '');

          const incomeRows = selectedRows.filter((row) => row.kind === 'in');

          const expenseRows = selectedRows.filter((row) => row.kind === 'out');

          const unknownRows = selectedRows.filter((row) => row.kind === 'unknown');

          toast(
            `${file.name}: ${rows.length}건 추출, ` +
              `${selectedRows.length}건 자동 선택, ` +
              `수입 ${incomeRows.length}건, ` +
              `지출 ${expenseRows.length}건, ` +
              `미분류 ${unknownRows.length}건`
          );

          onUploadSuccess?.(data);
        } catch (error) {
          toast(errorMessage(error));
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
        onDragOver={(event) => event.preventDefault()}
        onDrop={(event) => {
          event.preventDefault();
          upload(event.dataTransfer.files);
        }}
      >
        <Icon name="uploadCloud" size={36} />

        <p style={{ fontSize: 17 }}>PDF 거래정보 자동 분석</p>

        <p className="small muted">비회원도 업로드 및 계산 가능 · 미분류 거래는 별도로 합산</p>

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

      <div
        style={{
          padding: 14,
          marginTop: 16,
          border: '1px solid #e2e8f0',
          borderRadius: 10,
        }}
      >
        <p style={{ fontWeight: 700 }}>현재 선택된 PDF 거래 합계</p>

        <p>선택 거래: {selectedTaxTotals.count}건</p>

        <p>수입: {formatMoney(selectedTaxTotals.income)}</p>

        <p>지출: {formatMoney(selectedTaxTotals.expense)}</p>

        <p>미분류: {formatMoney(selectedTaxTotals.unknown)}</p>

        {selectedTaxTotals.unknownCount > 0 && (
          <p className="small muted">
            미분류 {selectedTaxTotals.unknownCount}건은 별도 집계되며 세액 계산의 수입·경비로 임의 배분되지 않습니다.
          </p>
        )}
      </div>

      {uploadedFiles.map((file) => {
        const rows = fileRows.filter((row) => row.sourceFileKey === file.key);

        const selectable = rows.filter((row) => row.amt !== '' && !row.excluded);

        const selectedCount = selectable.filter((row) => row.selected === true).length;

        const allSelected = selectable.length > 0 && selectedCount === selectable.length;

        return (
          <div
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
              {rows.length}건 추출 · {selectedCount}건 선택
            </p>

            <label>
              <input
                type="checkbox"
                checked={allSelected}
                disabled={!selectable.length || loading}
                onChange={(event) => setFileSelected(file.key, event.target.checked)}
              />{' '}
              파일 거래 전체 선택
            </label>

            <div style={{ marginTop: 10 }}>
              <Button size="sm" variant="ghost" disabled={loading} onClick={() => removeUploadedFile(file.key)}>
                파일 제거
              </Button>
            </div>
          </div>
        );
      })}
    </Card>
  );
}
