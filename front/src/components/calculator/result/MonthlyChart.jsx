// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useState } from 'react';
import { getMonthlyIncomes, getMonthlyExpenses } from '../../../api/taxApi';
import { Card } from '../../common/index';

const W = 640,
  H = 220,
  PAD = { l: 44, r: 8, t: 10, b: 26 };

// 최고 금액에 맞춰 4칸짜리 Y축의 눈금 간격과 표시 단위를 정한다.
function getYAxisScale(highestAmount) {
  const targetStep = highestAmount / 4;
  let magnitude = 1;
  // 예: 간격 후보가 30만 원이면 자릿값은 10만 원이다.
  while (targetStep >= magnitude * 10) {
    magnitude *= 10;
  }

  // 자릿값의 1·2·5·10배 중 금액을 담을 수 있는 간격으로 올림한다.
  const ratio = targetStep / magnitude;
  let step;
  if (ratio <= 1) {
    step = magnitude;
  } else if (ratio <= 2) {
    step = magnitude * 2;
  } else if (ratio <= 5) {
    step = magnitude * 5;
  } else {
    step = magnitude * 10;
  }

  const max = step * 4;
  // 막대 높이는 원 단위로 계산하고 눈금 글자만 원·만원·억원으로 바꾼다.
  let unit = '원';
  let divisor = 1;
  if (max >= 100000000) {
    unit = '억원';
    divisor = 100000000;
  } else if (max >= 10000) {
    unit = '만원';
    divisor = 10000;
  }
  return { max, step, unit, divisor };
}

// 분석번호로 월별 금액을 조회하고 기존 SVG 막대그래프로 표시한다.
export function MonthlyChart({ taxInputId }) {
  const [monthlyData, setMonthlyData] = useState({ incomes: [], expenses: [] });
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');

  // 분석번호가 바뀌면 이전 결과를 비우고 해당 분석의 수입·지출을 다시 조회한다.
  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();

    async function loadMonthlyData() {
      setIsLoading(true);
      setErrorMessage('');
      setMonthlyData({ incomes: [], expenses: [] });

      try {
        const [incomeResult, expenseResult] = await Promise.all([
          getMonthlyIncomes(taxInputId, { signal: controller.signal }),
          getMonthlyExpenses(taxInputId, { signal: controller.signal }),
        ]);
        if (!cancelled) {
          setMonthlyData({ incomes: incomeResult.data, expenses: expenseResult.data });
        }
      } catch (error) {
        if (!cancelled) setErrorMessage(error.message || '월별 데이터를 불러오지 못했습니다.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    loadMonthlyData();
    // 화면을 떠나거나 분석번호가 바뀐 뒤 이전 응답이 화면을 덮어쓰지 않게 한다.
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [taxInputId]);

  const { incomes, expenses } = monthlyData;
  const monthly = { inc: Array(12).fill(0), exp: Array(12).fill(0) };
  const incomePredicted = Array(12).fill(false);
  const expensePredicted = Array(12).fill(false);

  // 금액을 재계산하지 않고 해당 월의 막대에 배치한다.
  for (const income of incomes) {
    monthly.inc[income.month - 1] = income.amount;
    incomePredicted[income.month - 1] = income.predicted;
  }
  for (const expense of expenses) {
    monthly.exp[expense.month - 1] = expense.amount;
    expensePredicted[expense.month - 1] = expense.predicted;
  }

  // 고정된 10월 대신 predicted가 처음 나타나는 월부터 기존 예측 배경을 표시한다.
  const forecastIndex = incomePredicted.findIndex((predicted, index) => predicted || expensePredicted[index]);
  const forecastLabel = forecastIndex === -1 ? '예상 금액 없음' : `점선 = ${forecastIndex + 1}~12월 예측`;
  if (isLoading || errorMessage || (incomes.length === 0 && expenses.length === 0)) {
    return (
      <Card title="월별 수입·지출 추이" icon="barChart">
        <p role={errorMessage ? 'alert' : 'status'}>
          {isLoading ? '월별 데이터를 불러오는 중입니다.' : errorMessage || '표시할 월별 데이터가 없습니다.'}
        </p>
      </Card>
    );
  }

  const iw = W - PAD.l - PAD.r,
    ih = H - PAD.t - PAD.b;
  const highestAmount = Math.max(...monthly.inc, ...monthly.exp);
  const { max, step, unit, divisor } = getYAxisScale(highestAmount);
  const bw = iw / 12;
  const fx = PAD.l + (forecastIndex === -1 ? 12 : forecastIndex) * bw;
  const barStyle = (forecast, color) =>
    forecast ? { fill: 'none', stroke: color, strokeWidth: 1.5, strokeDasharray: '3 2' } : { fill: color };

  return (
    <Card title="월별 수입·지출 추이" icon="barChart">
      <div className="legend">
        <span>
          <i style={{ background: 'var(--income)' }} />
          수입
        </span>
        <span>
          <i style={{ background: 'var(--expense)' }} />
          지출
        </span>
        <span>{forecastLabel}</span>
      </div>
      <svg
        viewBox={`0 0 ${W} ${H}`}
        width="100%"
        role="img"
        aria-label={`월별 수입과 지출 막대그래프. ${forecastLabel}`}
      >
        {[0, 1, 2, 3, 4].map((i) => {
          const y = PAD.t + ih - (ih * i) / 4;
          return (
            <g key={i}>
              <line x1={PAD.l} x2={W - PAD.r} y1={y} y2={y} stroke="var(--border)" />
              <text x={PAD.l - 6} y={y + 4} textAnchor="end">
                {((step * i) / divisor).toLocaleString('ko-KR', { maximumFractionDigits: 4 })}
              </text>
            </g>
          );
        })}
        <rect x={fx} y={PAD.t} width={W - PAD.r - fx} height={ih} fill="var(--primary-bg)" opacity=".7" />
        {monthly.inc.map((_, i) => {
          const x = PAD.l + i * bw,
            b = bw * 0.34;
          const hi = (monthly.inc[i] / max) * ih,
            he = (monthly.exp[i] / max) * ih;
          return (
            <g key={i}>
              <rect
                x={x + bw * 0.12}
                y={PAD.t + ih - hi}
                width={b}
                height={hi}
                rx="3"
                {...barStyle(incomePredicted[i], 'var(--income)')}
              />
              <rect
                x={x + bw * 0.12 + b + 2}
                y={PAD.t + ih - he}
                width={b}
                height={he}
                rx="3"
                {...barStyle(expensePredicted[i], 'var(--expense)')}
              />
              <text x={x + bw / 2} y={H - 8} textAnchor="middle">
                {i + 1}월
              </text>
            </g>
          );
        })}
        {forecastIndex !== -1 && (
          <text x={fx + 6} y={PAD.t + 12} style={{ fill: 'var(--primary)' }}>
            예측
          </text>
        )}
        <text x={PAD.l} y={PAD.t - 1} style={{ fontSize: 10 }}>
          ({unit})
        </text>
      </svg>
      <p className="small muted" style={{ marginTop: 8 }}>
        예측은 자료 기간의 월평균으로 계산합니다. 월 수입 편차가 큰 경우 실제 결과와 차이가 날 수 있습니다.
      </p>
    </Card>
  );
}
