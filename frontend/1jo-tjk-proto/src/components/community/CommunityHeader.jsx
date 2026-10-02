import { Button, Row } from '../ui'
import MockBadge from '../MockBadge'

export default function CommunityHeader({ tabs, activeTab, onChangeTab }) {
  return (
    <Row $justify="space-between" $wrap $gap={16}>
      <div>
        <h2 style={{ fontSize: 24, fontWeight: 700 }}>
          프리랜서 커뮤니티 & 지식 공유 <MockBadge />
        </h2>
        <p style={{ fontSize: 12, color: 'var(--text-muted)' }}>정보 공유, 절세 팁, 공지사항 및 자료실을 이용하세요.</p>
      </div>
      <Row $gap={8}>
        {tabs.map((t) => (
          <Button
            key={t.id}
            $size="sm"
            $variant={activeTab === t.id ? 'primary' : 'secondary'}
            onClick={() => onChangeTab(t.id)}
            style={activeTab === t.id ? undefined : { background: 'var(--surface)', border: '1px solid var(--border)' }}
          >
            {t.label}
          </Button>
        ))}
      </Row>
    </Row>
  )
}
