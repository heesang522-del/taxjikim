import { Link, NavLink } from 'react-router-dom'
import styled from 'styled-components'
import Icon from '../Icon'
import { Badge, Button, Row } from '../ui'
import { useAuthStore } from '../../store/useAuthStore'

const Bar = styled.header`
  position: sticky;
  top: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 24px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
`

const Brand = styled(Link)`
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  font-weight: 700;
  font-size: 18px;
  color: var(--text-h);

  .logo {
    width: 36px;
    height: 36px;
    border-radius: 12px;
    background: var(--primary);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 10px rgba(79, 70, 229, 0.25);
  }
`

const Nav = styled.nav`
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 24px;

  @media (max-width: 900px) {
    display: none;
  }

  a {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 8px 12px;
    border-radius: 8px;
    text-decoration: none;
    font-size: 14px;
    font-weight: 500;
    color: var(--text-muted);

    &:hover {
      background: var(--fill);
    }
    &.active {
      color: var(--primary);
      font-weight: 600;
    }
  }

  a.admin {
    color: var(--danger);
    font-weight: 600;
    border: 1px solid var(--danger-border);

    &:hover,
    &.active {
      background: var(--danger-bg);
    }
  }
`

const BellButton = styled.button`
  position: relative;
  padding: 8px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;

  &:hover {
    background: var(--fill);
  }

  .dot {
    position: absolute;
    top: 6px;
    right: 6px;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--expense);
  }
`

const NAV_ITEMS = [
  { to: '/calculator', label: '세금 계산·시뮬레이션' },
  { to: '/community', label: '커뮤니티' },
  { to: '/support', label: '고객센터' },
]

export default function Header({ onOpenNotifications }) {
  const isLoggedIn = useAuthStore((state) => state.isLoggedIn)
  const logout = useAuthStore((state) => state.logout)

  return (
    <Bar>
      <Row $gap={16}>
        <Brand to="/">
          <span className="logo">
            <Icon name="calculator" />
          </span>
          <span>
            TaxFlow <Badge>프로토타입</Badge>
          </span>
        </Brand>
        <Nav>
          {NAV_ITEMS.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end}>
              {item.label}
            </NavLink>
          ))}
          <NavLink to="/admin" className="admin">
            <Icon name="shieldAlert" size={16} /> 관리자 페이지
          </NavLink>
        </Nav>
      </Row>

      <Row $gap={12}>
        <BellButton onClick={onOpenNotifications} aria-label="알림">
          <Icon name="bell" />
          <span className="dot" />
        </BellButton>
        {isLoggedIn ? (
          <Row $gap={8}>
            <Button as={Link} to="/mypage" $variant="soft" $size="sm">
              <Icon name="user" size={16} /> 김프리님
            </Button>
            <Button $variant="link" $size="sm" onClick={logout}>
              로그아웃
            </Button>
          </Row>
        ) : (
          <Row $gap={8}>
            <Button as={Link} to="/login" $variant="ghost">
              로그인
            </Button>
            <Button as={Link} to="/signup" $variant="primary">
              회원가입
            </Button>
          </Row>
        )}
      </Row>
    </Bar>
  )
}
