// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useEffect, useState } from 'react';
import { ROUTES } from './routes';
import { useAuth } from '../store/useAuthStore';
import { getNotifications, getUnreadNotificationCount, markNotificationRead } from '../api/notifications';

const PAGE_SIZE = 10; // 한 번에 불러오는 알림 개수. 서버 기본값(20)과 맞춘다
const SUBSCRIBE_URL = '/api/v1/notifications/subscribe'; // EventSource는 axios client를 거치지 않아 경로를 직접 적는다
const RECONNECT_DELAY_MS = 5000; // 연결이 끊겼을 때 재연결을 시도하기 전 대기 시간
const MAX_RECONNECT_ATTEMPTS = 5; // 계속 실패하면 재시도를 멈춘다. 로그인 상태가 바뀌면(재로그인 등) 다시 시도한다

// 서버 알림을 화면이 쓰는 모양으로 바꾼다.
// type(NOTICE/COMMENT)과 무관하게 targetType은 항상 POST·targetId는 게시글 ID라서 상세 경로를 하나로 통일한다.
// 목록에는 항상 미읽음 알림만 내려오므로 읽음 여부는 따로 담지 않는다.
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

/** 헤더 알림 벨이 쓰는 훅.
 *  안읽음 수(unreadCount)는 로그인 중 서버 SSE 구독(/notifications/subscribe)으로 받는다.
 *  서버가 보내는 이벤트는 "알림이 바뀌었다"는 신호만 담고 있어서, 받으면 안읽음 수를 다시 조회한다.
 *  알림 목록(items)은 항상 미읽음 알림만 담는다. 알림창을 열 때(openList) 최신 1페이지를 불러온 뒤
 *  스크롤로 더 볼 때만 loadMore로 이어붙이고, 알림을 읽으면(markRead) 서버에서 바로 삭제되므로
 *  화면 목록에서도 같이 제거한다. */
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
      .catch(() => {}); // 조회 실패는 다음 이벤트·재연결 때 다시 시도한다
  }, [loggedIn]);

  // 로그인 상태가 될 때(로그인 성공 또는 새로고침 후 인증 확인 성공) 알림 SSE를 구독하고,
  // 로그아웃되면 연결을 끊는다. 연결이 끊기면 제한된 횟수만 재시도해서, 서버가 계속 응답하지
  // 않는 상황에 무한 재시도하지 않게 한다.
  useEffect(() => {
    if (!loggedIn) {
      setUnreadCount(0);
      setItems([]);
      setHasMore(false);
      setNextCursor(null);
      return undefined;
    }

    refreshUnreadCount(); // 구독이 열리기 전까지의 현재 값을 먼저 맞춰둔다

    let eventSource;
    let reconnectTimer;
    let attempts = 0;
    let stopped = false;

    const connect = () => {
      eventSource = new EventSource(SUBSCRIBE_URL, { withCredentials: true });

      // 구독이 (재)연결될 때마다 혹시 그 사이 놓친 변경이 있을 수 있어 한 번 다시 조회해 맞춘다.
      eventSource.onopen = () => {
        attempts = 0;
        refreshUnreadCount();
      };

      eventSource.addEventListener('notification', () => {
        refreshUnreadCount();
      });

      eventSource.onerror = () => {
        eventSource.close();
        if (stopped) return;
        attempts += 1;
        if (attempts > MAX_RECONNECT_ATTEMPTS) return;
        reconnectTimer = setTimeout(connect, RECONNECT_DELAY_MS);
      };
    };

    connect();

    return () => {
      stopped = true;
      clearTimeout(reconnectTimer);
      eventSource?.close();
    };
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

  // 알림 1건을 읽음 처리한다. 서버에서 바로 삭제되므로 화면 목록에서도 같이 지운다.
  const markRead = useCallback((notificationId) => {
    return markNotificationRead(notificationId)
      .then(() => {
        setItems((prev) => prev.filter((n) => n.id !== notificationId));
        setUnreadCount((prev) => Math.max(0, prev - 1));
      })
      .catch(() => {}); // 실패하면 목록에 남겨두고, 다음 알림 이벤트·재연결 시 실제 서버 상태로 맞춰진다
  }, []);

  return { items, unreadCount, hasMore, loadingMore, openList, loadMore, markRead };
}
