'use no memo';

import { Button, Card, DataTable, Icon, Pill } from '../common/index';

import { won } from '../../utils/format';

import { useTax, parseTaxAmount, normalizeTaxKind } from '../../store/useTaxStore';

function formatAmount(value) {
  const amount = parseTaxAmount(value);

  return amount === null ? '금액 미인식' : won(amount);
}

function formatDate(row) {
  if (row.date) return row.date;
  if (row.month) return `${row.month} · 월별`;
  if (row.year) return `${row.year}년 · 연간`;

  return '미기재';
}

function canSelect(row) {
  return row.excluded !== true && parseTaxAmount(row.amt) !== null;
}

function isSelected(row) {
  return canSelect(row) && row.selected === true;
}

function getStatus(row) {
  if (!canSelect(row)) {
    return '금액 미인식';
  }

  if (row.selected !== true) {
    return '선택 해제';
  }

  if (normalizeTaxKind(row.kind) === 'unknown') {
    return '별도 합계 반영';
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
  const { allRows, inputMethod, removeRow, setRowSelected, setAllRowsSelected, selectedTaxTotals } = useTax();

  const fileMode = inputMethod === 'file';

  const sortedRows = sortRows(allRows);

  const selectedCount = allRows.filter(isSelected).length;

  const availableCount = allRows.filter(canSelect).length;

  const columns = [
    {
      key: 'selected',
      header: '반영',

      render: (row) => (
        <input
          type="checkbox"
          checked={isSelected(row)}
          disabled={!canSelect(row)}
          aria-label={`${row.memo || '거래'} 반영`}
          onChange={(event) => {
            setRowSelected(row.id, event.target.checked);
          }}
        />
      ),
    },

    {
      key: 'kind',
      header: '수입·지출 구분',

      render: (row) => {
        const kind = normalizeTaxKind(row.kind);

        let label = '구분 미확인';

        if (kind === 'in') {
          label = '수입';
        } else if (kind === 'out') {
          label = '지출';
        }

        return <Pill tone={kind === 'in' ? 'primary' : 'neutral'}>{label}</Pill>;
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
      header: '세부 카테고리',

      render: (row) => {
        const category = typeof row.cat === 'string' ? row.cat.trim() : '';

        return category || '미기재';
      },
    },

    {
      key: 'memo',
      header: '내용',

      render: (row) => row.memo || '내용 미기재',
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
      header: '반영 상태',
      render: getStatus,
    },

    {
      key: 'del',
      header: '',

      render: (row) => (
        <Button size="sm" variant="danger" className="icon" aria-label="거래 제거" onClick={() => removeRow(row.id)}>
          <Icon name="trash" size={16} />
        </Button>
      ),
    },
  ];

  return (
    <Card
      title={fileMode ? 'PDF 자동 파싱 거래 내역' : '직접 입력한 거래 내역'}
      aside={
        <span className="muted small">
          {selectedCount}/{allRows.length}건 반영
        </span>
      }
    >
      {fileMode && (
        <p className="small muted" style={{ marginBottom: 12 }}>
          수입·지출은 원본의 구분을 사용합니다. 카테고리가 없으면 미기재로 표시합니다.
        </p>
      )}

      {allRows.length > 0 && (
        <div className="row" style={{ marginBottom: 12 }}>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => {
              setAllRowsSelected(true);
            }}
          >
            전체 선택
          </Button>

          <Button
            size="sm"
            variant="ghost"
            onClick={() => {
              setAllRowsSelected(false);
            }}
          >
            전체 해제
          </Button>
        </div>
      )}

      {allRows.length === 0 ? (
        <p className="state-msg">{fileMode ? 'PDF에서 추출된 거래가 없습니다.' : '직접 입력한 거래가 없습니다.'}</p>
      ) : (
        <DataTable columns={columns} rows={sortedRows} rowKey={(row) => row.id} />
      )}

      {allRows.length > 0 && (
        <p className="small muted" style={{ marginTop: 12 }}>
          금액이 있는 거래 {availableCount}건 중 {selectedCount}건 반영
        </p>
      )}

      {fileMode && (
        <section
          style={{
            marginTop: 14,
            padding: 12,
            border: '1px solid #e2e8f0',
            borderRadius: 8,
          }}
        >
          <p>수입 합계: {won(selectedTaxTotals.income)}</p>

          <p>지출 합계: {won(selectedTaxTotals.expense)}</p>

          <p>구분 미확인 합계: {won(selectedTaxTotals.unknown)}</p>

          <p className="small muted">구분 미확인 금액은 수입이나 지출로 임의 배정하지 않습니다.</p>
        </section>
      )}
    </Card>
  );
}
