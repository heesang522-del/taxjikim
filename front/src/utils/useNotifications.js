// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useEffect, useState } from 'react';
import { ROUTES } from './routes';
import { useAuth } from '../store/useAuthStore';
import { getNotifications, getUnreadNotificationCount, markAllNotificationsRead } from '../api/notifications';

const POLL_MS = 15000; // 실시간 알림 채널이 없어, 로그인 중에는 이 주기로 목록·안읽음 수를 다시 불러온다

// 서버 알림을 화면이 쓰는 모양으로 바꾼다.
// type(NOTICE/COMMENT)과 무관하게 targetType은 항상 POST·targetId는 게시글 ID라서 상세 경로를 하나로 통일한다.
function toNotificationItem(n) {
  return {
    id: n.notificationId,
    title: n.title,
    desc: n.type === 'NOTICE' ? '새 공지가 올라왔습니다' : n.message,
    createdAt: n.createdAt,
    to: ROUTES.postDetail(n.targetId),
    read: n.read,
  };
}

/** 헤더 알림 벨이 쓰는 훅. 실시간 알림 채널이 없어 로그인 중에는 주기적으로 다시 불러오고,
 *  벨을 열 때도 그 사이 쌓인 알림이 바로 보이도록 같은 데이터를 쓴다. */
export function useNotifications() {
  const { loggedIn } = useAuth();
  const [items, setItems] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);

  const refresh = useCallback(() => {
    if (!loggedIn) return Promise.resolve();
    return Promise.all([getNotifications(), getUnreadNotificationCount()])
      .then(([listResult, countResult]) => {
        setItems((listResult.data || []).map(toNotificationItem));
        setUnreadCount(countResult.data || 0);
      })
      .catch(() => {}); // 조회 실패는 다음 주기에 다시 시도한다
  }, [loggedIn]);

  useEffect(() => {
    if (!loggedIn) {
      setItems([]);
      setUnreadCount(0);
      return undefined;
    }
    refresh();
    const timer = setInterval(refresh, POLL_MS);
    return () => clearInterval(timer);
  }, [loggedIn, refresh]);

  // 벨을 열 때마다 호출되므로, 이미 다 읽은 상태면 불필요한 요청을 보내지 않는다.
  const markAllRead = useCallback(() => {
    if (unreadCount === 0) return Promise.resolve();
    return markAllNotificationsRead()
      .then(() => {
        setUnreadCount(0);
        setItems((prev) => prev.map((n) => ({ ...n, read: true })));
      })
      .catch(() => {}); // 실패해도 다음 주기 조회에서 실제 서버 상태로 맞춰진다
  }, [unreadCount]);

  return { items, unreadCount, markAllRead };
}
