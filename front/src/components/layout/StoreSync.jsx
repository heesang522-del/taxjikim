// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useEffect } from 'react';
import { beatPresence, clearPresence, PRESENCE_BEAT, useAuth } from '../../store/useAuthStore';
import { syncBoardFromStorage } from '../../store/useBoardStore';
import { syncInquiryFromStorage } from '../../store/useInquiryStore';

// store에 넣을 수 없는 부수 효과(브라우저 이벤트, 타이머)를 한 곳에서 처리한다. Layout에서 한 번만 렌더링한다.
// MOCK: 서버가 생기면 다른 탭 동기화와 접속 표시는 서버(WebSocket 등)가 대신한다.
// 채팅은 실제 API·WebSocket으로 연동되어 있어 이 탭 동기화 대상에서 제외한다.
export default function StoreSync() {
  const { user } = useAuth();

  // 인증 정보는 메모리로만 관리하고, 기존 게시글·문의의 탭 동기화는 유지한다.
  useEffect(() => {
    const handleStorage = (e) => {
      syncBoardFromStorage(e.key);
      syncInquiryFromStorage(e.key);
    };
    window.addEventListener('storage', handleStorage);
    return () => window.removeEventListener('storage', handleStorage);
  }, []);

  // 접속 표시: 로그인한 동안 주기적으로 갱신하고, 로그아웃·창 닫기 때 지운다.
  const nickname = user?.nickname;
  useEffect(() => {
    if (!nickname) return undefined;
    const handleBeat = () => beatPresence(nickname);
    const handleClear = () => clearPresence(nickname);
    handleBeat();
    const timer = setInterval(handleBeat, PRESENCE_BEAT);
    window.addEventListener('beforeunload', handleClear);
    return () => {
      clearInterval(timer);
      window.removeEventListener('beforeunload', handleClear);
      handleClear();
    };
  }, [nickname]);

  return null;
}
