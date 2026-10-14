// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Card } from '../common/index';
import { MONTH_NOW } from '../../data/taxConstants';

const W = 640, H = 220, PAD = { l: 44, r: 8, t: 10, b: 26 };

export default function MonthlyChart({ monthly }) {
  const iw = W - PAD.l - PAD.r, ih = H - PAD.t - PAD.b;
  const max = Math.ceil(Math.max(...monthly.inc, ...monthly.exp) / 1e6) * 1e6 || 1e6;
  const bw = iw / 12;
  const fx = PAD.l + MONTH_NOW * bw;
  const barStyle = (forecast, color) => (forecast ? { fill: 'none', stroke: color, strokeWidth: 1.5, strokeDasharray: '3 2' } : { fill: color });

  return (
    <Card title="월별 수입·지출 추이" icon="barChart">
      <div className="legend">
        <span><i style={{ background: 'var(--income)' }} />수입</span>
        <span><i style={{ background: 'var(--expense)' }} />지출</span>
        <span>점선 = 10~12월 예측</span>
      </div>
      <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="월별 수입과 지출 막대그래프. 10월부터 12월은 예측">
        {[0, 1, 2, 3, 4].map((i) => {
          const y = PAD.t + ih - (ih * i) / 4;
          return (
            <g key={i}>
              <line x1={PAD.l} x2={W - PAD.r} y1={y} y2={y} stroke="var(--border)" />
              <text x={PAD.l - 6} y={y + 4} textAnchor="end">{Math.round((max / 4) * i / 1e4)}</text>
            </g>
          );
        })}
        <rect x={fx} y={PAD.t} width={W - PAD.r - fx} height={ih} fill="var(--primary-bg)" opacity=".7" />
        {monthly.inc.map((_, i) => {
          const x = PAD.l + i * bw, fc = i >= MONTH_NOW, b = bw * 0.34;
          const hi = (monthly.inc[i] / max) * ih, he = (monthly.exp[i] / max) * ih;
          return (
            <g key={i}>
              <rect x={x + bw * 0.12} y={PAD.t + ih - hi} width={b} height={hi} rx="3" {...barStyle(fc, 'var(--income)')} />
              <rect x={x + bw * 0.12 + b + 2} y={PAD.t + ih - he} width={b} height={he} rx="3" {...barStyle(fc, 'var(--expense)')} />
              <text x={x + bw / 2} y={H - 8} textAnchor="middle">{i + 1}월</text>
            </g>
          );
        })}
        <text x={fx + 6} y={PAD.t + 12} style={{ fill: 'var(--primary)' }}>예측</text>
        <text x={PAD.l} y={PAD.t - 1} style={{ fontSize: 10 }}>(만원)</text>
      </svg>
      <p className="small muted" style={{ marginTop: 8 }}>예측은 최근 6개월 평균으로 계산했습니다. 월 수입 편차가 큰 경우 실제 결과와 차이가 날 수 있습니다.</p>
    </Card>
  );
}
