import { Card, Grid } from '../ui'

const TONE_COLOR = { default: 'var(--text-h)', danger: 'var(--danger)', warn: 'var(--warn)' }
const percent = (n) => `${(n * 100).toFixed(1)}%`

// 지표 한 칸: 라벨 + 값
function Tile({ label, value, tone = 'default' }) {
  return (
    <Card $radius={16}>
      <span style={{ fontSize: 12, fontWeight: 500, color: 'var(--text-muted)' }}>{label}</span>
      <h3 style={{ marginTop: 4, fontSize: 24, fontWeight: 700, color: TONE_COLOR[tone] }}>{value}</h3>
    </Card>
  )
}

export default function AdminStats({ model, data, ops }) {
  return (
    <>
      <div>
        <h3 style={{ fontSize: 14, fontWeight: 700, marginBottom: 4 }}>모델 성능 · 데이터 현황</h3>
        <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 12 }}>
          예시값입니다. 실제 모델 평가 결과가 아닙니다.
        </p>
        <Grid $cols={3} $gap={16}>
          <Tile label="정확도 (예시값)" value={percent(model.accuracy)} />
          <Tile label="재현율 (예시값)" value={percent(model.recall)} />
          <Tile label="거래 건수 (예시값)" value={`${data.transactionCount.toLocaleString('ko-KR')}건`} />
        </Grid>
      </div>
      <Grid $cols={4} $gap={16}>
        {ops.map((s) => (
          <Tile key={s.id} label={s.label} value={s.value} tone={s.tone} />
        ))}
      </Grid>
    </>
  )
}
