// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { ROUTES } from '../../utils/routes';
import { rememberPath } from '../../utils/returnTo';
import { useAuth } from '../../store/useAuthStore';
import { useToastStore } from '../../store/useToastStore';
import Toast from '../common/Toast';
import Header from './Header';
import ChatWidget from './ChatWidget';
import StoreSync from './StoreSync';

// 모든 페이지 공통: 헤더 + 본문(Outlet) + 채팅 + 안내 문구(Toast)
export default function Layout() {
  const { pathname, search } = useLocation();
  const navigate = useNavigate();
  const { sessionBlocked, clearSessionBlocked } = useAuth();
  const toastMessage = useToastStore((state) => state.message);

  // 다음 접속 때 이미 정지된 계정이면 차단 안내 페이지로 보낸다
  useEffect(() => {
    if (sessionBlocked) {
      clearSessionBlocked();
      navigate(ROUTES.blocked, { replace: true });
    }
  }, [sessionBlocked, clearSessionBlocked, navigate]);
  useEffect(() => {
    window.scrollTo({ top: 0 });
  }, [pathname]);
  // 로그인 후 돌아올 곳을 기억한다
  useEffect(() => {
    rememberPath(pathname + search);
  }, [pathname, search]);

  return (
    <>
      <StoreSync />
      <Header />
      <main className="shell">
        <Outlet />
      </main>
      <ChatWidget />
      <Toast message={toastMessage} />
    </>
  );
}
