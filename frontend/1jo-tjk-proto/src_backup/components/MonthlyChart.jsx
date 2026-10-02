import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

const formatWon = (value) => `${value.toLocaleString('ko-KR')}원`

// data: [{ month: '2026-03', income, expense }]
export default function MonthlyChart({ data }) {
  return (
    <ResponsiveContainer width="100%" height={280}>
      <BarChart data={data} margin={{ top: 8, right: 8, left: 8, bottom: 4 }}>
        <CartesianGrid vertical={false} stroke="var(--border)" />
        <XAxis
          dataKey="month"
          tickLine={false}
          axisLine={false}
          tick={{ fill: 'var(--text-muted)', fontSize: 12.5 }}
        />
        <YAxis
          tickLine={false}
          axisLine={false}
          width={80}
          tickFormatter={formatWon}
          tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
        />
        <Tooltip
          formatter={formatWon}
          cursor={{ fill: 'var(--border)', opacity: 0.4 }}
          contentStyle={{
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: 8,
            fontSize: 13,
          }}
        />
        <Legend wrapperStyle={{ fontSize: 13 }} />
        <Bar dataKey="income" name="수입" fill="var(--income)" radius={[4, 4, 0, 0]} isAnimationActive={false} />
        <Bar dataKey="expense" name="지출" fill="var(--expense)" radius={[4, 4, 0, 0]} isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  )
}
