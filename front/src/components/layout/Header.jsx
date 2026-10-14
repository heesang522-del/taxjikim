// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useRef, useState } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { NAV_ITEMS, ROUTES } from '../../utils/routes';
import { useAuth } from '../../store/useAuthStore';
import { useToast } from '../../store/useToastStore';
import { useNotifications } from '../../utils/useNotifications';
import { useChat } from '../../store/useChatStore';
import { Button, Icon, Modal, Pill } from '../common/index';

export default function Header() {
  const { loggedIn, isAdmin, logout } = useAuth();
  const toast = useToast();
  const { items, unreadCount, markAllRead } = useNotifications();
  const { setOpen: openChat } = useChat();
  const [notifOpen, setNotifOpen] = useState(false);

  // 접속 중에 새 알림이 생기면 팝업으로 알린다. 처음 불러올 때 이미 쌓여 있던 알림은 알리지 않는다.
  const prevUnread = useRef(null);
  useEffect(() => {
    if (!loggedIn) { prevUnread.current = null; return; }
    if (prevUnread.current !== null && unreadCount > prevUnread.current) toast('새 알림이 도착했습니다');
    prevUnread.current = unreadCount;
  }, [loggedIn, unreadCount, toast]);

  return (
    <header className="bar">
      <div className="bar-left">
        <Link to={ROUTES.home} className="brand">
          <span className="logo"><Icon name="calculator" /></span>
          <span>TaxJiKim <Pill tone="primary">프로토타입</Pill></span>
        </Link>
        <nav className="nav" aria-label="주 메뉴">
          {NAV_ITEMS.map((n) => <NavLink key={n.to} to={n.to} end={n.end}>{n.label}</NavLink>)}
          {isAdmin && <NavLink to={ROUTES.admin} className="admin"><Icon name="shieldAlert" size={16} /> 관리자 페이지</NavLink>}
        </nav>
      </div>
      <div className="bar-right">
        {loggedIn ? (
          <>
            <Button variant="soft" size="sm" className="icon" aria-label="알림" style={{ position: 'relative' }} onClick={() => { setNotifOpen(true); markAllRead(); }}>
              <Icon name="bell" size={16} />
              {unreadCount > 0 && <span className="badge">{unreadCount}</span>}
            </Button>
            <Button to={ROUTES.mypage} variant="soft" size="sm"><Icon name="user" size={16} /> 마이페이지</Button>
            <Button variant="link" size="sm" onClick={() => { logout(); toast('로그아웃했습니다'); }}>로그아웃</Button>
          </>
        ) : (
          <>
            <Button to={ROUTES.auth} variant="ghost">로그인</Button>
            <Button to={ROUTES.signup} variant="primary">회원가입</Button>
          </>
        )}
      </div>
      {notifOpen && (
        <Modal title="알림" onClose={() => setNotifOpen(false)}>
          <div className="grid" style={{ gap: 8, minWidth: 280, maxWidth: 360 }}>
            {items.length
              ? items.map((n) => (
                n.action === 'chat' ? (
                  <button key={n.id} type="button" className="notif-item" style={{ textAlign: 'left', width: '100%', background: 'none', border: 0, cursor: 'pointer' }} onClick={() => { setNotifOpen(false); openChat(true); }}>
                    <p style={{ fontWeight: 600 }}>{n.title}</p>
                    <p className="small muted">{n.desc} · {n.createdAt}</p>
                  </button>
                ) : (
                  <Link key={n.id} to={n.to} className="notif-item" onClick={() => setNotifOpen(false)}>
                    <p style={{ fontWeight: 600 }}>{n.title}</p>
                    <p className="small muted">{n.desc} · {n.createdAt}</p>
                  </Link>
                )
              ))
              : <p className="state-msg">새 알림이 없습니다</p>}
          </div>
        </Modal>
      )}
    </header>
  );
}
