// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { Card } from '../common/index';
import { MONTH_NOW, FORECAST_WINDOW } from '../../data/taxConstants';
import { man } from '../../utils/format';
import { buildMonthly } from '../../utils/tax';
import { useTax } from '../../store/useTaxStore';

const W = 640, H = 200, PAD = { l: 44, r: 8, t: 10, b: 26 };

/** 최근 6개월 실적과 다음 달 예상 수입·지출 그래프. 입력된 달이 6개월 미만이면 안내만 보여준다. */
export default function NextMonthForecast() {
  const { rows } = useTax();
  const filled = new Set(rows.filter((r) => r.m <= MONTH_NOW).map((r) => r.m)).size;
  const next = MONTH_NOW + 1;

  if (filled < FORECAST_WINDOW) {
    return (
      <Card title={`${next}월 예상 수입·지출`} icon="barChart">
        <p className="muted">다음 달 예상은 {FORECAST_WINDOW}개월 이상 입력된 데이터가 있어야 보여드릴 수 있어요. 지금은 {filled}개월치가 입력되어 있습니다.</p>
      </Card>
    );
  }

  const m = buildMonthly(rows);
  const months = Array.from({ length: FORECAST_WINDOW + 1 }, (_, i) => MONTH_NOW - FORECAST_WINDOW + 1 + i); // 4~9월 실적 + 10월 예상
  const iw = W - PAD.l - PAD.r, ih = H - PAD.t - PAD.b;
  const max = Math.ceil(Math.max(...months.map((mo) => Math.max(m.inc[mo - 1], m.exp[mo - 1])), 1) / 1e6) * 1e6 || 1e6;
  const bw = iw / months.length;
  const barStyle = (forecast, color) => (forecast ? { fill: 'none', stroke: color, strokeWidth: 1.5, strokeDasharray: '3 2' } : { fill: color });

  return (
    <Card title={`${next}월 예상 수입·지출`} icon="barChart">
      <div className="legend">
        <span><i style={{ background: 'var(--income)' }} />수입</span>
        <span><i style={{ background: 'var(--expense)' }} />지출</span>
        <span>점선 = {next}월 예상</span>
      </div>
      <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label={`최근 ${FORECAST_WINDOW}개월 실적과 ${next}월 예상 수입·지출 막대그래프`}>
        {[0, 1, 2, 3, 4].map((i) => {
          const y = PAD.t + ih - (ih * i) / 4;
          return (
            <g key={i}>
              <line x1={PAD.l} x2={W - PAD.r} y1={y} y2={y} stroke="var(--border)" />
              <text x={PAD.l - 6} y={y + 4} textAnchor="end">{Math.round((max / 4) * i / 1e4)}</text>
            </g>
          );
        })}
        {months.map((mo, i) => {
          const x = PAD.l + i * bw, fc = mo === next, b = bw * 0.3;
          const hi = (m.inc[mo - 1] / max) * ih, he = (m.exp[mo - 1] / max) * ih;
          return (
            <g key={mo}>
              <rect x={x + bw * 0.15} y={PAD.t + ih - hi} width={b} height={hi} rx="3" {...barStyle(fc, 'var(--income)')} />
              <rect x={x + bw * 0.15 + b + 2} y={PAD.t + ih - he} width={b} height={he} rx="3" {...barStyle(fc, 'var(--expense)')} />
              <text x={x + bw / 2} y={H - 8} textAnchor="middle">{mo}월{fc ? ' 예상' : ''}</text>
            </g>
          );
        })}
        <text x={PAD.l} y={PAD.t - 1} style={{ fontSize: 10 }}>(만원)</text>
      </svg>
      <p className="small muted" style={{ marginTop: 8 }}>{next}월 예상: 수입 {man(m.inc[MONTH_NOW])} · 지출 {man(m.exp[MONTH_NOW])} (최근 {FORECAST_WINDOW}개월 평균)</p>
    </Card>
  );
}
