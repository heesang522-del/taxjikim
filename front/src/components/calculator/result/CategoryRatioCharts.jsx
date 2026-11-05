import { useState } from 'react';
import styled from 'styled-components';
import { getTransactionCategoryLabel } from '../../../data/transactionCategories';

const COLORS = [
  '#5144e8',
  '#168376',
  '#bd4b72',
  '#986018',
  '#326cba',
  '#7946a4',
  '#438043',
  '#b94732',
  '#537d8d',
  '#697286',
];

const Charts = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 20px;

  @media (max-width: 420px) {
    grid-template-columns: 1fr;
  }
`;

const Pie = styled.div`
  width: min(108px, 100%);
  aspect-ratio: 1;
  border-radius: 50%;
  margin: 10px auto;
  background: ${({ $gradient }) => $gradient};
`;

const PieArea = styled.div`
  position: relative;
`;

const Tooltip = styled.div`
  position: absolute;
  z-index: 1;
  top: 50%;
  left: 50%;
  transform: translateX(-50%);
  padding: 6px 10px;
  border-radius: 8px;
  background: #172033;
  color: #fff;
  font-size: 12px;
  white-space: nowrap;
  pointer-events: none;
`;

const Legend = styled.ul`
  list-style: none;
  padding: 0;
  margin: 0;
  font-size: 12px;

  li {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    gap: 4px 12px;
    padding: 3px 0;
  }

  i {
    display: inline-block;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    margin-right: 6px;
  }
`;

const formatNumber = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 });

function CategoryPie({ title, basis, categories }) {
  const [hoveredIndex, setHoveredIndex] = useState(-1);
  const hovered = categories[hoveredIndex];
  const total = categories.reduce((sum, category) => sum + Number(category.amount), 0);
  let end = 0;
  // 화면에 표시하는 반올림 비율과 별개로, 실제 금액을 기준으로 조각을 그린다.
  const segments = categories.map(({ amount }, index) => {
    const start = end;
    end = index === categories.length - 1 ? 100 : end + (Number(amount) / total) * 100;
    return { end, colorStop: `${COLORS[index % COLORS.length]} ${start}% ${end}%` };
  });

  // 원의 위쪽을 시작점으로 마우스 각도를 구해 해당 조각을 찾는다.
  function handlePointerMove(event) {
    const bounds = event.currentTarget.getBoundingClientRect();
    const x = event.clientX - bounds.left - bounds.width / 2;
    const y = event.clientY - bounds.top - bounds.height / 2;
    if (Math.hypot(x, y) > bounds.width / 2) {
      setHoveredIndex(-1);
      return;
    }

    const position = ((Math.atan2(x, -y) / (2 * Math.PI) + 1) % 1) * 100;
    setHoveredIndex(segments.findIndex((segment) => position < segment.end));
  }

  return (
    <section aria-label={title}>
      <h4>{title}</h4>
      <p className="small muted">{basis}</p>
      {total > 0 ? (
        <>
          <PieArea>
            <Pie
              aria-hidden="true"
              $gradient={`conic-gradient(${segments.map((segment) => segment.colorStop).join(', ')})`}
              onPointerMove={handlePointerMove}
              onPointerLeave={() => setHoveredIndex(-1)}
            />
            {hovered && (
              <Tooltip aria-hidden="true">
                {getTransactionCategoryLabel(hovered.category)} · {formatNumber.format(hovered.amount)}원 ·{' '}
                {formatNumber.format(hovered.percentage)}%
              </Tooltip>
            )}
          </PieArea>
          <Legend aria-label={title}>
            {categories.map(({ category, amount, percentage }, index) => (
              <li key={category}>
                <span>
                  <i aria-hidden="true" style={{ backgroundColor: COLORS[index % COLORS.length] }} />
                  {getTransactionCategoryLabel(category)}
                </span>
                <span>
                  {formatNumber.format(amount)}원 · {formatNumber.format(percentage)}%
                </span>
              </li>
            ))}
          </Legend>
        </>
      ) : (
        <p className="small muted">표시할 실제 거래 금액이 없습니다.</p>
      )}
    </section>
  );
}

export function CategoryRatioCharts({ incomeCategories, expenseCategories }) {
  return (
    <Charts>
      <CategoryPie title="수입 카테고리 비율" basis="실제 거래의 세전 수입 기준" categories={incomeCategories} />
      <CategoryPie title="지출 카테고리 비율" basis="실제 거래의 지출 금액 기준" categories={expenseCategories} />
    </Charts>
  );
}
