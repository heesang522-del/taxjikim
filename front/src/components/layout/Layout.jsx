// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect, useRef } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { rememberPath } from '../../utils/returnTo';
import { useAuth } from '../../store/useAuthStore';
import { useToastStore } from '../../store/useToastStore';
import Toast from '../common/Toast';
import { Header } from './Header';
import ChatWidget from './ChatWidget';
import StoreSync from './StoreSync';
import { Button } from '../common/index';

// 모든 페이지 공통: 헤더 + 본문(Outlet) + 채팅 + 안내 문구(Toast)
export default function Layout() {
  const { pathname, search } = useLocation();
  const { authStatus, authError, checkAuth, loggedIn, refreshProfile } = useAuth();
  const toastMessage = useToastStore((state) => state.message);
  const toast = useToastStore((state) => state.toast);
  const previousUrl = useRef(pathname + search);

  useEffect(() => {
    void checkAuth();
  }, [checkAuth]);

  useEffect(() => {
    const currentUrl = pathname + search;
    const urlChanged = previousUrl.current !== currentUrl;
    previousUrl.current = currentUrl;
    // 최초 실행은 checkAuth가 담당하고, 로그인 중 URL이 변경된 경우에만 다시 조회한다.
    if (!urlChanged || authStatus !== 'ready' || !loggedIn) return;
    let active = true;
    void refreshProfile().catch((error) => {
      if (!active) return;
      // 401의 로그인 정보 정리는 스토어에서 처리하며, 통신 장애에는 로그인을 유지한다.
      toast(
        error.status === 401
          ? error.code === 'SESSION_REPLACED'
            ? '다른 기기에서 로그인했거나 현재 로그인이 종료되었습니다.'
            : '로그인이 만료되었습니다. 다시 로그인해주세요.'
          : '로그인 상태를 확인하지 못했습니다. 잠시 후 다시 시도해주세요.'
      );
    });
    return () => {
      active = false;
    };
  }, [pathname, search, authStatus, loggedIn, refreshProfile, toast]);

  useEffect(() => {
    window.scrollTo({ top: 0 });
  }, [pathname]);
  // 로그인 후 돌아올 곳을 기억한다
  useEffect(() => {
    rememberPath(pathname + search);
  }, [pathname, search]);

  // 서버 확인 전에는 비로그인으로 단정하지 않고, 장애는 인증 실패와 구분해 재시도를 제공한다.
  if (authStatus !== 'ready') {
    return (
      <main className="shell" aria-busy={authStatus === 'checking'}>
        {authStatus === 'checking' ? (
          <p role="status">로그인 상태를 확인하고 있습니다.</p>
        ) : (
          <div className="grid">
            <p role="alert">{authError}</p>
            <Button onClick={() => checkAuth()}>다시 확인</Button>
          </div>
        )}
      </main>
    );
  }

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
