import styled from 'styled-components'
import Icon from '../Icon'
import AsyncBoundary from '../AsyncBoundary'
import MockBadge from '../MockBadge'
import { Button, CardTitle, DividedList, Row } from '../ui'
import { useApi } from '../../utils/useApi'
import { getNotifications } from '../../api/notifications'

const Backdrop = styled.div`
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(15, 23, 42, 0.5);
`

const Panel = styled.div`
  width: 100%;
  max-width: 448px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
  border-radius: 24px;
  background: var(--surface);
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.2);
`

const Item = styled.div`
  padding: 12px 0;
  font-size: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;

  .label {
    font-weight: 700;
    color: ${(p) => (p.$tone === 'success' ? 'var(--success)' : 'var(--primary)')};
  }
  .time {
    font-size: 10px;
    color: var(--text-faint);
  }
`

const CloseButton = styled.button`
  border: none;
  background: transparent;
  color: var(--text-faint);
  cursor: pointer;
  &:hover { color: var(--text-muted); }
`

// 열릴 때만 렌더링되어서, 열 때마다 알림을 새로 불러온다
function NotificationList() {
  const { status, data } = useApi(getNotifications)
  return (
    <AsyncBoundary status={status} isEmpty={data?.length === 0} emptyText="새 알림이 없습니다.">
      <DividedList>
        {data?.map((n) => (
          <Item key={n.id} $tone={n.tone}>
            <span className="label">{n.label}</span>
            <p>{n.text}</p>
            <span className="time">{n.time}</span>
          </Item>
        ))}
      </DividedList>
    </AsyncBoundary>
  )
}

export default function NotificationModal({ open, onClose }) {
  if (!open) return null
  return (
    <Backdrop onClick={onClose}>
      <Panel onClick={(e) => e.stopPropagation()}>
        <Row $justify="space-between">
          <CardTitle style={{ fontSize: 18 }}>
            <Icon name="bell" /> 알림 센터 <MockBadge />
          </CardTitle>
          <CloseButton onClick={onClose} aria-label="닫기">
            <Icon name="x" />
          </CloseButton>
        </Row>
        <NotificationList />
        <Button $block onClick={onClose}>
          확인
        </Button>
      </Panel>
    </Backdrop>
  )
}
