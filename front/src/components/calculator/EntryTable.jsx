'use no memo';

import { Button, Card, DataTable, Icon, Pill } from '../common/index';

import { won } from '../../utils/format';

import { useTax, parseTaxAmount, normalizeTaxKind } from '../../store/useTaxStore';

function formatAmount(value) {
  const amount = parseTaxAmount(value);

  return amount === null ? '금액 확인 불가' : won(amount);
}

function formatDate(row) {
  if (row.date) return row.date;
  if (row.month) return `${row.month} · 월별`;
  if (row.year) return `${row.year}년 · 연간`;

  return '날짜 미확인';
}

function canSelect(row) {
  return row.excluded !== true && parseTaxAmount(row.amt) !== null;
}

function isSelected(row) {
  return canSelect(row) && row.selected === true;
}

function getStatus(row) {
  if (row.excluded) {
    return '계산 제외';
  }

  if (!canSelect(row)) {
    return '금액 확인 불가';
  }

  if (!row.selected) {
    return '선택 해제';
  }

  if (normalizeTaxKind(row.kind) === 'unknown') {
    return '미분류 합계 포함';
  }

  return '계산 포함';
}

function sortRows(rows) {
  return [...rows].sort((a, b) => {
    const dateA = a.date ?? a.month ?? a.year ?? '';
    const dateB = b.date ?? b.month ?? b.year ?? '';

    return String(dateB).localeCompare(String(dateA));
  });
}

export default function EntryTable() {
  const {
    rows,
    allRows,
    inputMethod,
    removeRow,
    setRowSelected,
    setAllRowsSelected,
    uploadedFiles,
    removeUploadedFile,
    selectedTaxTotals,
  } = useTax();

  const fileMode = inputMethod === 'file';
  const sortedRows = sortRows(allRows);

  const selectedRows = rows.filter(isSelected);
  const availableRows = allRows.filter(canSelect);

  const columns = [
    {
      key: 'selected',
      header: '계산 포함',

      render: (row) => (
        <input
          type="checkbox"
          checked={isSelected(row)}
          disabled={!canSelect(row)}
          aria-label={`${row.memo || '거래'} 계산에 포함`}
          onChange={(event) => setRowSelected(row.id, event.target.checked)}
        />
      ),
    },

    {
      key: 'kind',
      header: '구분',

      render: (row) => {
        const kind = normalizeTaxKind(row.kind);

        return (
          <Pill tone={kind === 'in' ? 'primary' : 'neutral'}>
            {kind === 'in' ? '수입' : kind === 'out' ? '지출' : '미분류'}
          </Pill>
        );
      },
    },

    {
      key: 'date',
      header: '날짜',
      num: true,
      render: formatDate,
    },

    {
      key: 'cat',
      header: '카테고리',
      render: (row) => row.cat || '미분류',
    },

    {
      key: 'memo',
      header: '내용',
      render: (row) => row.memo || '거래 내용 없음',
    },

    {
      key: 'amt',
      header: '금액',
      align: 'right',
      num: true,
      render: (row) => formatAmount(row.amt),
    },

    {
      key: 'status',
      header: '상태',
      render: getStatus,
    },

    {
      key: 'del',
      header: '',

      render: (row) => (
        <Button size="sm" variant="danger" className="icon" aria-label="삭제" onClick={() => removeRow(row.id)}>
          <Icon name="trash" size={16} />
        </Button>
      ),
    },
  ];

  return (
    <>
      <Card
        title={fileMode ? 'PDF 자동 파싱 거래 내역' : '직접 입력한 거래 내역'}
        aside={
          <span className="muted small">
            {selectedRows.length}/{allRows.length}건 선택
          </span>
        }
      >
        {fileMode && (
          <p className="small muted" style={{ marginBottom: 12 }}>
            PDF에서 금액을 추출한 거래는 자동 선택됩니다. 수입·지출 구분이 없는 거래도 미분류로 집계됩니다.
          </p>
        )}

        {allRows.length > 0 && (
          <div className="row" style={{ marginBottom: 12 }}>
            <Button size="sm" variant="ghost" onClick={() => setAllRowsSelected(true)}>
              전체 선택
            </Button>

            <Button size="sm" variant="ghost" onClick={() => setAllRowsSelected(false)}>
              전체 해제
            </Button>
          </div>
        )}

        {!allRows.length && (
          <p className="state-msg">{fileMode ? 'PDF에서 추출된 거래가 없습니다.' : '직접 입력한 거래가 없습니다.'}</p>
        )}

        <DataTable columns={columns} rows={sortedRows} rowKey={(row) => row.id} />

        {fileMode && allRows.length > 0 && (
          <p className="small muted" style={{ marginTop: 12 }}>
            금액이 있는 거래 {availableRows.length}건 중 {selectedRows.length}건 선택
          </p>
        )}

        {fileMode && (
          <div
            style={{
              marginTop: 14,
              padding: 12,
              border: '1px solid #e2e8f0',
              borderRadius: 8,
            }}
          >
            <p>수입 합계: {won(selectedTaxTotals.income)}</p>

            <p>지출 합계: {won(selectedTaxTotals.expense)}</p>

            <p>미분류 합계: {won(selectedTaxTotals.unknown)}</p>

            <p className="small muted">미분류 금액은 별도 집계하며, 수입·지출에 임의로 더하지 않습니다.</p>
          </div>
        )}
      </Card>

      {fileMode && uploadedFiles.length > 0 && (
        <Card title="업로드한 PDF" aside={<span className="small muted">{uploadedFiles.length}개 파일</span>}>
          {uploadedFiles.map((file) => {
            const relatedRows = allRows.filter((row) => row.sourceFileKey === file.key);

            const selectedCount = relatedRows.filter(isSelected).length;

            return (
              <div
                key={file.key}
                className="row"
                style={{
                  marginBottom: 10,
                  justifyContent: 'space-between',
                }}
              >
                <div>
                  <p style={{ fontWeight: 600 }}>{file.name}</p>

                  <p className="small muted">
                    {relatedRows.length}건 추출 · {selectedCount}건 선택
                  </p>
                </div>

                <Button size="sm" variant="ghost" onClick={() => removeUploadedFile(file.key)}>
                  목록에서 제거
                </Button>
              </div>
            );
          })}
        </Card>
      )}
    </>
  );
}
