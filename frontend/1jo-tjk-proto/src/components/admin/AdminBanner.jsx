import Icon from '../Icon'
import { Badge, Row } from '../ui'

export default function AdminBanner() {
  return (
    <Row
      $justify="space-between"
      $wrap
      style={{ padding: 24, borderRadius: 24, border: '1px solid var(--danger-border)', background: 'var(--danger-bg)' }}
    >
      <div>
        <h2 style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 20, fontWeight: 700, color: '#881337' }}>
          <Icon name="shieldAlert" size={24} style={{ color: 'var(--danger)' }} /> 관리자 전용 대시보드
        </h2>
        <p style={{ fontSize: 12, color: '#be123c' }}>
          모델 성능 지표, 데이터 현황, 회원정보 관리, 문의 처리 및 악질 사용자 제재 기능
        </p>
      </div>
      <Badge style={{ background: 'var(--danger)', color: '#fff', padding: '4px 12px', fontSize: 12 }}>ADMIN MODE</Badge>
    </Row>
  )
}
