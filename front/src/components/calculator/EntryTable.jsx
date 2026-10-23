'use no memo';

import { Button, Card, DataTable, Icon, Pill } from '../common/index';

import { won } from '../../utils/format';

import { useTax, parseTaxAmount, normalizeTaxKind } from '../../store/useTaxStore';
import { getTaxInputSummary, getTransactionCashAmount } from '../../utils/taxInputSummary';
import { getTransactionCategoryLabel } from '../../data/transactionCategories';

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

function sortRows(rows) {
  return [...rows].sort((a, b) => {
    const dateA = a.date ?? a.month ?? a.year ?? '';

    const dateB = b.date ?? b.month ?? b.year ?? '';

    return String(dateB).localeCompare(String(dateA));
  });
}

export function EntryTable() {
  const { allRows, rows, removeRow } = useTax();
  const selectedTaxTotals = getTaxInputSummary(rows);

  const fileMode = allRows.some((row) => row.autoImported);

  const sortedRows = sortRows(allRows);

  const columns = [
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

      render: (row) => getTransactionCategoryLabel(row.cat),
    },

    {
      key: 'memo',
      header: '내용',

      render: (row) => row.memo || '내용 미기재',
    },

    {
      key: 'amt',
      header: '실제 입출금액',
      align: 'right',
      num: true,

      render: (row) => formatAmount(getTransactionCashAmount(row)),
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
    <Card title="입력한 거래 내역" aside={<span className="muted small">총 {allRows.length}건</span>}>
      {fileMode && (
        <p className="small muted" style={{ marginBottom: 12 }}>
          직접 입력한 거래와 PDF에서 불러온 거래를 함께 표시합니다. 카테고리가 없으면 미분류로 표시합니다.
        </p>
      )}

      {allRows.length === 0 ? (
        <p className="state-msg">{fileMode ? 'PDF에서 추출된 거래가 없습니다.' : '직접 입력한 거래가 없습니다.'}</p>
      ) : (
        <DataTable columns={columns} rows={sortedRows} rowKey={(row) => row.id} />
      )}

      {allRows.length > 0 && (
        <p className="small muted" style={{ marginTop: 12 }}>
          목록의 거래는 자동으로 계산에 포함됩니다. 금액 미인식 거래는 계산에서 제외됩니다.
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
