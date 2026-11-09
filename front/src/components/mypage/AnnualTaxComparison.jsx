import { useState } from 'react';
import styled from 'styled-components';
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Button, Card, DataTable } from '../common/index';

const METRICS = [
  { key: 'totalIncome', label: '총수입' },
  { key: 'totalExpense', label: '총지출' },
  { key: 'totalTax', label: '총세금' },
];

// 저장 금액은 원 단위를 유지하고 표와 축의 표시 단위만 만원으로 바꾼다.
const formatManwon = (value) =>
  value == null ? '—' : (value / 10000).toLocaleString('ko-KR', { maximumFractionDigits: 2 });

const ChartGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  h4 {
    margin: 0 0 12px;
    font-size: 14px;
  }
  h4 span {
    margin-left: 6px;
    color: var(--text-muted);
    font-size: 11px;
    font-weight: 400;
  }
`;

const TooltipCard = styled.div`
  min-width: 140px;
  padding: 12px 16px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.08);
  pointer-events: none;
  p {
    margin: 0 0 8px;
    color: var(--text-muted);
    font-size: 12px;
  }
  div {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 16px;
  }
  span {
    color: var(--text-muted);
    font-size: 12px;
  }
  strong {
    color: var(--text-h);
    font-size: 16px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }
  small {
    margin-left: 4px;
    color: var(--text-muted);
    font-size: 11px;
    font-weight: 400;
  }
`;

function ComparisonTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  const entry = payload[0];
  // 차트·표와 같은 만원 단위로 표시하며 원본 금액은 변경하지 않는다.
  return (
    <TooltipCard>
      <p>{label}년</p>
      <div>
        <span>{entry.name}</span>
        <strong>
          {formatManwon(entry.value)}
          {entry.value != null && <small>만원</small>}
        </strong>
      </div>
    </TooltipCard>
  );
}

export function AnnualTaxComparison({ savedResults, isLoading, errorMessage }) {
  const [showGraph, setShowGraph] = useState(false);
  // 기존 저장 결과 목록의 순서는 유지하고 비교 영역만 귀속연도 오름차순으로 배치한다.
  const rows = [...savedResults].sort((a, b) => a.taxYear - b.taxYear);
  const columns = [
    { key: 'taxYear', header: '귀속연도', render: (row) => `${row.taxYear}년` },
    ...METRICS.map(({ key, label }) => ({
      key,
      header: label,
      align: 'right',
      render: (row) => (row[key] == null ? '—' : `${formatManwon(row[key])}만원`),
    })),
  ];

  function handleToggleView() {
    // 보기 방식만 전환하며 서버 조회나 저장 결과 계산은 다시 하지 않는다.
    setShowGraph((previous) => !previous);
  }

  return (
    <Card
      title="연도별 수입·지출·세금 비교"
      icon="barChart"
      aside={
        <Button
          size="sm"
          onClick={handleToggleView}
          aria-pressed={showGraph}
          disabled={isLoading || !!errorMessage || rows.length === 0}
        >
          {showGraph ? '표로 보기' : '그래프로 보기'}
        </Button>
      }
    >
      <p className="muted" style={{ margin: '0 0 16px', fontSize: 12 }}>
        저장된 분석 기준 · 단위: 만원
      </p>
      {isLoading ? (
        <p className="state-msg">비교할 분석 결과를 불러오고 있습니다.</p>
      ) : errorMessage ? (
        <p role="alert">{errorMessage}</p>
      ) : rows.length === 0 ? (
        <p className="state-msg">저장한 분석 결과가 있으면 연도별로 비교할 수 있습니다.</p>
      ) : showGraph ? (
        <ChartGrid>
          {METRICS.map(({ key, label }) => (
            <section key={key} aria-label={`${label} 연도별 그래프`}>
              <h4>
                {label}
                <span>단위: 만원</span>
              </h4>
              <ResponsiveContainer width="100%" height={210}>
                <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                  <XAxis dataKey="taxYear" tickFormatter={(year) => `${year}년`} tick={{ fontSize: 12 }} />
                  <YAxis tickFormatter={formatManwon} tick={{ fontSize: 11 }} width={58} />
                  <Tooltip content={<ComparisonTooltip />} cursor={{ fill: 'var(--primary-bg)', radius: 6 }} />
                  <Bar dataKey={key} name={label} fill="var(--primary)" radius={[4, 4, 0, 0]} maxBarSize={40} />
                </BarChart>
              </ResponsiveContainer>
            </section>
          ))}
        </ChartGrid>
      ) : (
        <DataTable columns={columns} rows={rows} rowKey={(row) => row.taxInputId} />
      )}
    </Card>
  );
}
