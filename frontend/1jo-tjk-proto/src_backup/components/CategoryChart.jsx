import {
  Bar,
  BarChart,
  Cell,
  LabelList,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { CATEGORY_COLORS } from '../utils/categoryColors'

function formatWon(value) {
  return `${value.toLocaleString('ko-KR')}원`
}

function CustomTooltip({ active, payload }) {
  if (!active || !payload?.length) return null
  const { category, amount } = payload[0].payload
  return (
    <div
      style={{
        background: 'var(--surface)',
        border: '1px solid var(--border)',
        borderRadius: 8,
        padding: '8px 12px',
        fontSize: 13,
      }}
    >
      <div style={{ color: 'var(--text-muted)', marginBottom: 2 }}>{category}</div>
      <div style={{ color: 'var(--text-h)', fontWeight: 600 }}>{formatWon(amount)}</div>
    </div>
  )
}

export default function CategoryChart({ data }) {
  const height = Math.max(data.length * 40 + 20, 100)

  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart
        data={data}
        layout="vertical"
        margin={{ top: 4, right: 64, left: 8, bottom: 4 }}
      >
        <XAxis type="number" hide />
        <YAxis
          type="category"
          dataKey="category"
          width={110}
          tickLine={false}
          axisLine={false}
          tick={{ fill: 'var(--text-muted)', fontSize: 12.5 }}
        />
        <Tooltip cursor={{ fill: 'var(--border)', opacity: 0.4 }} content={<CustomTooltip />} />
        <Bar dataKey="amount" barSize={20} radius={[0, 4, 4, 0]} isAnimationActive={false}>
          {data.map((entry) => (
            <Cell key={entry.category} fill={CATEGORY_COLORS[entry.category] ?? 'var(--cat-6)'} />
          ))}
          <LabelList
            dataKey="amount"
            position="right"
            formatter={formatWon}
            style={{ fill: 'var(--text)', fontSize: 12.5, fontWeight: 600 }}
          />
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  )
}
