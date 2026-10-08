import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Card, StateMessage, Stack } from '../ui'

const formatWon = (value) => `${value.toLocaleString('ko-KR')}원`

// data: [{ month: '2026-03', income, expense }]
export default function MonthlyChart({ data }) {
  return (
    <Card>
      <Stack $gap={16}>
        <h3 style={{ fontSize: 14, fontWeight: 700 }}>월별 수입 및 지출 추이 시각화</h3>
        {data.length === 0 ? (
          <StateMessage>거래 내역이 없습니다.</StateMessage>
        ) : (
          <ResponsiveContainer width="100%" height={288}>
            <BarChart data={data} margin={{ top: 8, right: 8, left: 8, bottom: 4 }}>
              <CartesianGrid vertical={false} stroke="var(--border)" />
              <XAxis dataKey="month" tickLine={false} axisLine={false} tick={{ fill: 'var(--text-muted)', fontSize: 12.5 }} />
              <YAxis
                tickLine={false}
                axisLine={false}
                width={90}
                tickFormatter={formatWon}
                tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
              />
              <Tooltip
                formatter={formatWon}
                cursor={{ fill: 'var(--border)', opacity: 0.4 }}
                contentStyle={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 8, fontSize: 13 }}
              />
              <Legend wrapperStyle={{ fontSize: 13 }} />
              <Bar dataKey="income" name="월별 수입 (원)" fill="var(--income)" radius={[6, 6, 0, 0]} isAnimationActive={false} />
              <Bar dataKey="expense" name="월별 지출/필요경비 (원)" fill="var(--expense)" radius={[6, 6, 0, 0]} isAnimationActive={false} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </Stack>
    </Card>
  )
}
