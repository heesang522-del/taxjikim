// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useEffect, useRef, useState } from 'react';
import { ROUTES } from './routes';
import { useAuth } from '../store/useAuthStore';
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '../api/notifications';

const PAGE_SIZE = 10; // 한 번에 불러오는 알림 개수. 서버 기본값(10)과 맞춘다
const SUBSCRIBE_URL = '/api/v1/notifications/subscribe'; // EventSource는 axios client를 거치지 않아 경로를 직접 적는다
const RECONNECT_DELAY_MS = 5000; // 연결이 끊겼을 때 재연결을 시도하기 전 대기 시간
const MAX_RECONNECT_ATTEMPTS = 5; // 계속 실패하면 재시도를 멈춘다. 로그인 상태가 바뀌면(재로그인 등) 다시 시도한다

// 알림 시각은 분 단위까지만 "00:00" 형식(한국 시간)으로 표시한다. CommentSection의 formatCommentTime과 동일한 규칙.
function formatNotificationTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleTimeString('ko-KR', {
    timeZone: 'Asia/Seoul',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

// 서버 알림을 화면이 쓰는 모양으로 바꾼다.
// type(NOTICE/COMMENT)과 무관하게 targetType은 항상 POST·targetId는 게시글 ID라서 상세 경로를 하나로 통일한다.
// 목록에는 항상 미읽음 알림만 내려오므로 읽음 여부는 따로 담지 않는다.
function toNotificationItem(n) {
  return {
    id: n.notificationId,
    title: n.title,
    desc: n.type === 'NOTICE' ? '새 공지가 올라왔습니다' : n.message,
    createdAt: formatNotificationTime(n.createdAt),
    to: ROUTES.postDetail(n.targetId),
  };
}

/** 헤더 알림 벨이 쓰는 훅. 로그인 중인 사용자 하나당 서버 SSE 구독(/notifications/subscribe)을
 *  하나만 열어 두고, 알림과 중복 로그인 감지가 이 연결을 함께 쓴다.
 *  안읽음 수(unreadCount)는 로그인 중 이 SSE로 받는다.
 *  서버가 보내는 'notification' 이벤트는 "알림이 바뀌었다"는 신호만 담고 있어서, 받으면 안읽음 수를 다시 조회한다.
 *  알림 목록(items)은 항상 미읽음 알림만 담는다. 알림창을 열 때(openList) 최신 1페이지를 불러오고,
 *  열려 있는 동안 SSE 신호를 받으면 같은 방식으로 다시 불러와 실시간으로 반영한다(closeList로 끈다).
 *  스크롤로 더 볼 때만 loadMore로 이어붙이고, 알림을 읽으면(markRead) 서버에서 바로 삭제되므로
 *  화면 목록에서도 같이 제거한다.
 *  같은 계정으로 다른 곳에서 로그인하면 서버가 'session-replaced' 이벤트를 보내고, 그러면
 *  sessionReplaced를 true로 바꾼다. 실제 로그아웃 처리(새로고침)는 이 값을 보는 Header가 담당한다. */
export function useNotifications() {
  const { loggedIn } = useAuth();
  const [items, setItems] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  // 무한스크롤용: 다음 페이지가 있는지, 그 다음 조회에 쓸 커서(마지막 알림 ID)
  const [hasMore, setHasMore] = useState(false);
  const [nextCursor, setNextCursor] = useState(null);
  const [loadingMore, setLoadingMore] = useState(false);
  // 알림창이 화면에 열려 있는지. SSE 신호를 받았을 때 목록을 같이 다시 불러올지 판단하는 데만 쓴다.
  const isListOpenRef = useRef(false);
  // 다른 기기에서 로그인해 이 세션이 밀려났는지. true가 되면 Header가 강제 로그아웃 모달을 띄운다.
  const [sessionReplaced, setSessionReplaced] = useState(false);

  const refreshUnreadCount = useCallback(() => {
    if (!loggedIn) return Promise.resolve();
    return getUnreadNotificationCount()
      .then((result) => setUnreadCount(result.data || 0))
      .catch(() => {}); // 조회 실패는 다음 이벤트·재연결 때 다시 시도한다
  }, [loggedIn]);

  // 알림창을 열 때(+SSE로 변경 신호를 받았을 때 열려 있는 상태면) 호출: 최신 1페이지로 목록을 통째로 새로 불러온다.
  const openList = useCallback(() => {
    isListOpenRef.current = true;
    if (!loggedIn) return Promise.resolve();
    return getNotifications({ size: PAGE_SIZE })
      .then((result) => {
        setItems((result.data || []).map(toNotificationItem));
        setHasMore(Boolean(result.meta?.hasMore));
        setNextCursor(result.meta?.nextCursor ?? null);
      })
      .catch(() => {});
  }, [loggedIn]);

  // 알림창을 닫을 때 호출: 이후 SSE 신호를 받아도 목록을 자동으로 다시 불러오지 않는다.
  const closeList = useCallback(() => {
    isListOpenRef.current = false;
  }, []);

  // 로그인 상태가 될 때(로그인 성공 또는 새로고침 후 인증 확인 성공) 알림 SSE를 구독하고,
  // 로그아웃되면 연결을 끊는다. 연결이 끊기면 제한된 횟수만 재시도해서, 서버가 계속 응답하지
  // 않는 상황에 무한 재시도하지 않게 한다.
  useEffect(() => {
    if (!loggedIn) {
      setUnreadCount(0);
      setItems([]);
      setHasMore(false);
      setNextCursor(null);
      isListOpenRef.current = false;
      setSessionReplaced(false);
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
        // 알림창이 열려 있는 동안 새 알림이 오면 닫았다 다시 열지 않아도 바로 목록에 반영되게 한다.
        if (isListOpenRef.current) openList();
      });

      // 같은 계정으로 다른 곳에서 로그인하면 서버가 이 세션의 sid를 덮어쓰고 이 이벤트를 보낸다.
      // 신호만 담겨 있으므로 값은 보지 않고, 받으면 바로 모달을 띄울 상태로만 바꾼다.
      eventSource.addEventListener('session-replaced', () => {
        setSessionReplaced(true);
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
  }, [loggedIn, refreshUnreadCount, openList]);

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

  // 미읽음 알림을 전부 읽음 처리한다. 서버에서 바로 삭제되므로 화면 목록도 전부 비운다.
  // 단건 읽음(markRead)과 달리 실패를 삼키지 않고 그대로 던져서, 호출한 쪽(버튼)이 사용자에게 알릴 수 있게 한다.
  const markAllRead = useCallback(() => {
    return markAllNotificationsRead().then(() => {
      setItems([]);
      setUnreadCount(0);
    });
  }, []);

  return {
    items,
    unreadCount,
    hasMore,
    loadingMore,
    openList,
    closeList,
    loadMore,
    markRead,
    markAllRead,
    sessionReplaced,
  };
}
