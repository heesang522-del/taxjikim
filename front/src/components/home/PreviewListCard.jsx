import { Link } from 'react-router-dom'
import styled from 'styled-components'
import Icon from '../Icon'
import { Button, Card, DividedList, Row } from '../ui'

const Item = styled(Link)`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 8px;
  border-radius: 8px;
  text-decoration: none;
  color: inherit;

  &:hover {
    background: var(--bg);
  }
  .title {
    font-size: 14px;
    font-weight: 500;
    color: var(--text);
  }
`

// 홈의 '공지사항' / '인기 정보' 미리보기 카드. 항목 오른쪽 내용은 renderMeta로 받는다
export default function PreviewListCard({ icon, iconColor, title, items, to, renderMeta }) {
  return (
    <Card $radius={16}>
      <Row $justify="space-between" style={{ marginBottom: 16 }}>
        <h3 style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 18, fontWeight: 700 }}>
          <Icon name={icon} style={{ color: iconColor }} /> {title}
        </h3>
        <Button as={Link} to={to} $variant="link" $size="sm" style={{ color: 'var(--primary)' }}>
          더보기
        </Button>
      </Row>
      <DividedList>
        {items.map((item) => (
          <Item key={item.id} to={to}>
            <span className="title">{item.title}</span>
            {renderMeta(item)}
          </Item>
        ))}
      </DividedList>
    </Card>
  )
}
