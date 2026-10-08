import { Card, Grid, IconBox, Row } from '../ui'
import Icon from '../Icon'

export default function StatsSection({ stats }) {
  return (
    <Grid $cols={3} $gap={24}>
      {stats.map((s) => (
        <Card key={s.id} $radius={16}>
          <Row $gap={16}>
            <IconBox $tone={s.tone}>
              <Icon name={s.icon} size={24} />
            </IconBox>
            <div>
              <p style={{ fontSize: 14, color: 'var(--text-muted)', fontWeight: 500 }}>{s.label}</p>
              <h3 style={{ fontSize: 24, fontWeight: 700 }}>{s.value}</h3>
            </div>
          </Row>
        </Card>
      ))}
    </Grid>
  )
}
