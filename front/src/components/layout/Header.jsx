// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { NAV_ITEMS, ROUTES } from '../../utils/routes';
import { useAuth } from '../../store/useAuthStore';
import { useToast } from '../../store/useToastStore';
import { useNotifications } from '../../utils/useNotifications';
import { Button, Icon, Modal, Pill } from '../common/index';

export function Header() {
  const { loggedIn, isAdmin, logout } = useAuth();
  const toast = useToast();
  const { items, unreadCount, hasMore, loadingMore, openList, loadMore, markAllRead } = useNotifications();
  const [notifOpen, setNotifOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const navigate = useNavigate();

  async function handleLogout() {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      // 서버 세션 종료 성공 후에만 로그아웃 안내와 화면 이동을 수행한다.
      await logout();
      toast('로그아웃했습니다');
      navigate(ROUTES.home);
    } catch (error) {
      toast(error.message || '로그아웃하지 못했습니다. 다시 시도해주세요.');
    } finally {
      setLoggingOut(false);
    }
  }

  // 접속 중에 새 알림이 생기면 팝업으로 알린다. 처음 불러올 때 이미 쌓여 있던 알림은 알리지 않는다.
  const prevUnread = useRef(null);
  useEffect(() => {
    if (!loggedIn) {
      prevUnread.current = null;
      return;
    }
    if (prevUnread.current !== null && unreadCount > prevUnread.current) toast('새 알림이 도착했습니다');
    prevUnread.current = unreadCount;
  }, [loggedIn, unreadCount, toast]);

  return (
    <header className="bar">
      <div className="bar-left">
        <Link to={ROUTES.home} className="brand">
          <span className="logo">
            <Icon name="calculator" />
          </span>
          <span>
            TaxJiKim <Pill tone="primary">프로토타입</Pill>
          </span>
        </Link>
        <nav className="nav" aria-label="주 메뉴">
          {NAV_ITEMS.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end}>
              {n.label}
            </NavLink>
          ))}
          {isAdmin && (
            <NavLink to={ROUTES.admin} className="admin">
              <Icon name="shieldAlert" size={16} /> 관리자 페이지
            </NavLink>
          )}
        </nav>
      </div>
      <div className="bar-right">
        {loggedIn ? (
          <>
            <Button
              variant="soft"
              size="sm"
              className="icon"
              aria-label="알림"
              style={{ position: 'relative' }}
              onClick={() => {
                setNotifOpen(true);
                markAllRead();
                openList(); // 알림창을 열 때마다 최신 1페이지로 목록을 새로 불러온다.
              }}
            >
              <Icon name="bell" size={16} />
              {unreadCount > 0 && <span className="badge">{unreadCount}</span>}
            </Button>
            <Button to={ROUTES.mypage} variant="soft" size="sm">
              <Icon name="user" size={16} /> 마이페이지
            </Button>
            <Button variant="link" size="sm" onClick={handleLogout} disabled={loggingOut}>
              {loggingOut ? '로그아웃 중...' : '로그아웃'}
            </Button>
          </>
        ) : (
          <>
            <Button to={ROUTES.auth} variant="ghost">
              로그인
            </Button>
            <Button to={ROUTES.signup} variant="primary">
              회원가입
            </Button>
          </>
        )}
      </div>
      {notifOpen && (
        <Modal title="알림" onClose={() => setNotifOpen(false)}>
          <div
            className="grid"
            style={{ gap: 8, minWidth: 280, maxWidth: 360, maxHeight: 360, overflowY: 'auto' }}
            onScroll={(e) => {
              const { scrollTop, scrollHeight, clientHeight } = e.currentTarget;
              // 스크롤이 바닥에서 40px 이내로 가까워지면 다음 페이지를 미리 불러온다.
              if (scrollHeight - scrollTop - clientHeight < 40) loadMore();
            }}
          >
            {items.length ? (
              items.map((n) => (
                <Link key={n.id} to={n.to} className="notif-item" onClick={() => setNotifOpen(false)}>
                  <p style={{ fontWeight: 600 }}>{n.title}</p>
                  <p className="small muted">
                    {n.desc} · {n.createdAt}
                  </p>
                </Link>
              ))
            ) : (
              <p className="state-msg">새 알림이 없습니다</p>
            )}
            {loadingMore && <p className="small muted" style={{ textAlign: 'center' }}>불러오는 중...</p>}
            {!hasMore && items.length > 0 && (
              <p className="small muted" style={{ textAlign: 'center' }}>마지막 알림입니다</p>
            )}
          </div>
        </Modal>
      )}
    </header>
  );
}
