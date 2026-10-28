// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useEffect, useState } from 'react';
import { ROUTES } from './routes';
import { useAuth } from '../store/useAuthStore';
import { getNotifications, getUnreadNotificationCount, markAllNotificationsRead } from '../api/notifications';

const POLL_MS = 15000; // 실시간 알림 채널이 없어, 로그인 중에는 이 주기로 목록·안읽음 수를 다시 불러온다
const PAGE_SIZE = 20; // 한 번에 불러오는 알림 개수. 서버 기본값(20)과 맞춘다

// 서버 알림을 화면이 쓰는 모양으로 바꾼다.
// type(NOTICE/COMMENT)과 무관하게 targetType은 항상 POST·targetId는 게시글 ID라서 상세 경로를 하나로 통일한다.
function toNotificationItem(n) {
  return {
    id: n.notificationId,
    title: n.title,
    desc: n.type === 'NOTICE' ? '새 공지가 올라왔습니다' : n.message,
    createdAt: n.createdAt,
    to: ROUTES.boardPost(n.targetId),
    read: n.read,
  };
}

/** 헤더 알림 벨이 쓰는 훅.
 *  안읽음 수(unreadCount)는 실시간 채널이 없어 로그인 중 계속 폴링한다(벨 뱃지용).
 *  알림 목록(items)은 폴링하지 않고, 알림창을 열 때(openList) 최신 1페이지를 불러온 뒤
 *  스크롤로 더 볼 때만 loadMore로 이어붙인다 — 그래야 스크롤해서 불러온 뒷부분이
 *  폴링 때문에 사라지는 일이 없다. */
export function useNotifications() {
  const { loggedIn } = useAuth();
  const [items, setItems] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  // 무한스크롤용: 다음 페이지가 있는지, 그 다음 조회에 쓸 커서(마지막 알림 ID)
  const [hasMore, setHasMore] = useState(false);
  const [nextCursor, setNextCursor] = useState(null);
  const [loadingMore, setLoadingMore] = useState(false);

  const refreshUnreadCount = useCallback(() => {
    if (!loggedIn) return Promise.resolve();
    return getUnreadNotificationCount()
      .then((result) => setUnreadCount(result.data || 0))
      .catch(() => {}); // 조회 실패는 다음 주기에 다시 시도한다
  }, [loggedIn]);

  useEffect(() => {
    if (!loggedIn) {
      setUnreadCount(0);
      setItems([]);
      setHasMore(false);
      setNextCursor(null);
      return undefined;
    }
    refreshUnreadCount();
    const timer = setInterval(refreshUnreadCount, POLL_MS);
    return () => clearInterval(timer);
  }, [loggedIn, refreshUnreadCount]);

  // 알림창을 열 때 호출: 최신 1페이지로 목록을 통째로 새로 불러온다.
  const openList = useCallback(() => {
    if (!loggedIn) return Promise.resolve();
    return getNotifications({ size: PAGE_SIZE })
      .then((result) => {
        setItems((result.data || []).map(toNotificationItem));
        setHasMore(Boolean(result.meta?.hasMore));
        setNextCursor(result.meta?.nextCursor ?? null);
      })
      .catch(() => {});
  }, [loggedIn]);

  // 스크롤이 끝에 닿았을 때 다음 페이지를 이어붙인다. 이미 불러오는 중이거나 더 없으면 그대로 둔다.
  const loadMore = useCallback(() => {
    if (!hasMore || loadingMore || nextCursor == null) return Promise.resolve();
    setLoadingMore(true);
    return getNotifications({ cursorId: nextCursor, size: PAGE_SIZE })
      .then((result) => {
        setItems((prev) => [...prev, ...(result.data || []).map(toNotificationItem)]);
        setHasMore(Boolean(result.meta?.hasMore));
        setNextCursor(result.meta?.nextCursor ?? null);
      })
      .catch(() => {}) // 실패하면 hasMore는 그대로 두어 다시 시도할 수 있게 한다
      .finally(() => setLoadingMore(false));
  }, [hasMore, loadingMore, nextCursor]);

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

  return { items, unreadCount, hasMore, loadingMore, openList, loadMore, markAllRead };
}
