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
  position: relative;
  display: grid;
  place-items: center;
  width: min(108px, 100%);
  aspect-ratio: 1;
  border-radius: 50%;
  margin: 10px auto;
  background: ${({ $gradient }) => $gradient};
`;

const PieCenter = styled.div`
  display: grid;
  place-content: center;
  width: 72%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: var(--surface, #fff);
  text-align: center;
  font-size: 10px;
  line-height: 1.35;
  pointer-events: none;

  strong {
    font-size: 12px;
  }
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
const compactNumber = new Intl.NumberFormat('ko-KR', { notation: 'compact', maximumFractionDigits: 1 });

function CategoryPie({ title, basis, categories }) {
  const [hoveredIndex, setHoveredIndex] = useState(-1);
  const hovered = categories[hoveredIndex];
  const total = categories.reduce((sum, category) => sum + Number(category.amount), 0);
  let end = 0;
  // 화면에 표시하는 반올림 비율과 별개로, 실제 금액을 기준으로 조각을 그린다.
  const segments = categories.map(({ amount }, index) => {
    const start = end;
    end = index === categories.length - 1 ? 100 : end + (Number(amount) / total) * 100;
    const gap = categories.length > 1 ? Math.min(0.45, (end - start) / 4) : 0;
    const color = COLORS[index % COLORS.length];
    return {
      end,
      colorStop: gap
        ? `transparent ${start}% ${start + gap}%, ${color} ${start + gap}% ${end - gap}%, transparent ${end - gap}% ${end}%`
        : `${color} ${start}% ${end}%`,
    };
  });

  // 원의 위쪽을 시작점으로 마우스 각도를 구해 해당 조각을 찾는다.
  function handlePointerMove(event) {
    const bounds = event.currentTarget.getBoundingClientRect();
    const x = event.clientX - bounds.left - bounds.width / 2;
    const y = event.clientY - bounds.top - bounds.height / 2;
    const distance = Math.hypot(x, y);
    if (distance > bounds.width / 2 || distance < bounds.width * 0.36) {
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
              $gradient={`conic-gradient(${segments.map((segment) => segment.colorStop).join(', ')})`}
              onPointerMove={handlePointerMove}
              onPointerLeave={() => setHoveredIndex(-1)}
            >
              <PieCenter>
                <span>실제 합계</span>
                <strong title={`${formatNumber.format(total)}원`}>{compactNumber.format(total)}원</strong>
              </PieCenter>
            </Pie>
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
