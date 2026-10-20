// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useNavigate } from 'react-router-dom';
import { Button, Card, DataTable, Pill } from '../components/common/index';
import { ROUTES } from '../utils/routes';
import { YEAR } from '../data/taxConstants';
import { man, signedMan } from '../utils/format';
import { useAuth } from '../store/useAuthStore';
import { ProfileSettings } from '../components/mypage/ProfileSettings';
import { useTaxResult } from '../utils/useTaxResult';
import { useHistory } from '../utils/useHistory';
import ScenarioTable from '../components/calculator/ScenarioTable';
import { useChat } from '../store/useChatStore';

const CHART_W = 640,
  CHART_H = 220,
  CHART_PAD = { l: 44, r: 8, t: 10, b: 26 };

/** 증감 KPI 카드. tone: good-up(오를수록 좋음) | good-down(내릴수록 좋음) | neutral */
function DeltaTile({ label, delta, tone = 'neutral' }) {
  let color = 'var(--text-muted)';
  if (tone !== 'neutral' && delta !== 0) {
    const up = delta > 0;
    const good = tone === 'good-up' ? up : !up;
    color = good ? 'var(--success)' : 'var(--danger)';
  }
  return (
    <Card className="stat">
      <p className="lbl">{label}</p>
      <p className="big" style={{ color }}>
        {signedMan(delta)}
      </p>
      <p className="sub">전년 대비</p>
    </Card>
  );
}

/** 연도별 수입·지출 막대그래프. years: [{ year, income, expense, forecast? }] (원 단위, 연도 오름차순) */
function YearlyBarChart({ years }) {
  const iw = CHART_W - CHART_PAD.l - CHART_PAD.r,
    ih = CHART_H - CHART_PAD.t - CHART_PAD.b;
  const max = Math.ceil(Math.max(...years.map((y) => Math.max(y.income, y.expense)), 1) / 1e6) * 1e6 || 1e6;
  const bw = iw / years.length;
  const barStyle = (forecast, color) =>
    forecast ? { fill: 'none', stroke: color, strokeWidth: 1.5, strokeDasharray: '3 2' } : { fill: color };

  return (
    <>
      <div className="legend">
        <span>
          <i style={{ background: 'var(--income)' }} />
          수입
        </span>
        <span>
          <i style={{ background: 'var(--expense)' }} />
          지출
        </span>
        <span>점선 = 올해 예측</span>
      </div>
      <svg
        viewBox={`0 0 ${CHART_W} ${CHART_H}`}
        width="100%"
        role="img"
        aria-label="연도별 수입과 지출 막대그래프. 올해는 예측치"
      >
        {[0, 1, 2, 3, 4].map((i) => {
          const y = CHART_PAD.t + ih - (ih * i) / 4;
          return (
            <g key={i}>
              <line x1={CHART_PAD.l} x2={CHART_W - CHART_PAD.r} y1={y} y2={y} stroke="var(--border)" />
              <text x={CHART_PAD.l - 6} y={y + 4} textAnchor="end">
                {Math.round(((max / 4) * i) / 1e4)}
              </text>
            </g>
          );
        })}
        {years.map((y, i) => {
          const x = CHART_PAD.l + i * bw,
            b = bw * 0.3;
          const hi = (y.income / max) * ih,
            he = (y.expense / max) * ih;
          return (
            <g key={y.year}>
              <rect
                x={x + bw * 0.14}
                y={CHART_PAD.t + ih - hi}
                width={b}
                height={hi}
                rx="3"
                {...barStyle(y.forecast, 'var(--income)')}
              />
              <rect
                x={x + bw * 0.14 + b + 3}
                y={CHART_PAD.t + ih - he}
                width={b}
                height={he}
                rx="3"
                {...barStyle(y.forecast, 'var(--expense)')}
              />
              <text x={x + bw / 2} y={CHART_H - 8} textAnchor="middle">
                {y.year}
                {y.forecast ? ' (예측)' : ''}
              </text>
            </g>
          );
        })}
        <text x={CHART_PAD.l} y={CHART_PAD.t - 1} style={{ fontSize: 10 }}>
          (만원)
        </text>
      </svg>
    </>
  );
}

export default function MyPage() {
  const { loggedIn } = useAuth();
  const navigate = useNavigate();
  const c = useTaxResult();
  const { records } = useHistory();
  const { setOpen: openChat } = useChat();

  if (!loggedIn) {
    return (
      <Card className="" style={{ maxWidth: 480, margin: '40px auto', textAlign: 'center' }}>
        <h3>로그인이 필요합니다</h3>
        <p className="muted" style={{ marginBottom: 14 }}>
          입력한 수입·지출과 시나리오는 로그인하면 연도별로 저장됩니다.
        </p>
        <Button variant="primary" to={ROUTES.auth}>
          로그인
        </Button>
      </Card>
    );
  }

  // 저장한 기록 + 올해(저장 전이면 현재 입력 기준 예측). 연도 오름차순, 원 단위
  const live = { year: YEAR, income: c.income, expense: c.expense, total: c.total, forecast: true };
  const years = [...records.filter((r) => r.year !== YEAR), records.find((r) => r.year === YEAR) ?? live]
    .sort((a, b) => a.year - b.year)
    .map((r) => ({ ...r, forecast: Boolean(r.forecast) }));
  const prevYear = years.length >= 2 ? years[years.length - 2] : null;
  const curYear = years[years.length - 1];
  // 표용: [연도, 수입, 지출, 결과(만원), 예측 여부]
  const hist = years.map((r) => [r.year, r.income / 1e4, r.expense / 1e4, r.total / 1e4, r.forecast]);
  const columns = [
    {
      key: 'y',
      header: '연도',
      render: (h) => (
        <>
          {h[0]}
          {h[4] && (
            <>
              {' '}
              <Pill tone="warn">예측</Pill>
            </>
          )}
        </>
      ),
    },
    { key: 'i', header: '수입', align: 'right', num: true, render: (h) => Math.round(h[1]).toLocaleString() },
    { key: 'e', header: '지출', align: 'right', num: true, render: (h) => Math.round(h[2]).toLocaleString() },
    {
      key: 'r',
      header: '결과',
      align: 'right',
      num: true,
      render: (h) => `${h[3] < 0 ? '환급 ' : '납부 '}${Math.abs(Math.round(h[3]))}`,
    },
  ];

  return (
    <div className="grid g-side">
      <div className="grid" style={{ minWidth: 0 }}>
        {prevYear ? (
          <div className="grid g3">
            <DeltaTile label="수입 증감" delta={curYear.income - prevYear.income} tone="good-up" />
            <DeltaTile label="지출 증감" delta={curYear.expense - prevYear.expense} tone="neutral" />
            <DeltaTile label="세금 결과 증감" delta={curYear.total - prevYear.total} tone="good-down" />
          </div>
        ) : (
          <p className="small muted">작년 기록이 쌓이면 전년 대비 증감도 보여드려요.</p>
        )}
        <Card title="연도별 비교 대시보드" icon="trendingUp" aside={<span className="muted small">(만원)</span>}>
          <YearlyBarChart years={years} />
          <div style={{ marginTop: 14 }}>
            <DataTable
              columns={columns}
              rows={hist}
              rowKey={(h) => h[0]}
              onRowClick={() => navigate(ROUTES.calcResult)}
            />
          </div>
        </Card>
        <Card title="내 계산 결과" icon="calculator">
          <p className="num" style={{ fontSize: 24, fontWeight: 600 }}>
            {c.total < 0 ? '환급 ' : '추가납부 '}
            {man(Math.abs(c.total))}
          </p>
          <p className="small muted">{YEAR}년 귀속 · 연말 예측 포함</p>
          <div className="row" style={{ marginTop: 12 }}>
            <Button to={ROUTES.calcResult}>결과 보기</Button>
            <Button to={ROUTES.calcSim}>시뮬레이션</Button>
          </div>
        </Card>
        <ScenarioTable />
        <Card title="상담 내역" icon="messageSquare">
          {/* 지난 상담 목록 조회는 별도 작업으로 남겨두고, 현재 진행 중인 상담은 채팅 패널에서 바로 이어서 할 수 있다. */}
          <p className="state-msg">지난 상담 내역 보기는 준비 중입니다. 진행 중인 상담은 채팅 상담에서 이어서 볼 수 있어요.</p>
          <div style={{ marginTop: 12 }}>
            <Button onClick={() => openChat(true)}>상담하기</Button>
          </div>
        </Card>
      </div>
      <ProfileSettings />
    </div>
  );
}
