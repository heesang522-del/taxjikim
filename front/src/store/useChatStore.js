// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { create } from 'zustand';
import {
  getMyRoom,
  sendMemberMessage,
  getMemberMessages,
  markMemberRead,
  getMemberUnreadCount,
  closeMemberRoom,
  getAdminRooms,
  getAdminRoom,
  getAdminRoomUnreadCount,
  getAdminWaitingCount,
  enterAdminRoom,
  closeAdminRoom,
  sendAdminMessage,
  getAdminMessages,
  markAdminRead,
} from '../api/chat';
import { useChatSocket } from '../utils/useChatSocket';
import { subscribeChatUnread } from '../utils/chatUnreadBus';

const LIST_POLL_MS = 8000; // 관리자 상담 목록·대기 수: 실시간 알림 채널이 없어 주기적으로 다시 불러온다
const ROOM_PAGE_SIZE = 10; // 상담 목록을 한 번에 불러오는 개수. 서버 기본값(10)과 맞춘다

/** 채팅 패널(ChatWidget) 열림 여부만 전역으로 둔다. 실제 상담 데이터는 서버가 가진다. */
export const useChatPanelStore = create((set) => ({
  open: false,
  setOpen: (open) => set({ open }),
}));

export const useChat = () => useChatPanelStore();

/**
 * 방 하나의 메시지 목록과 WebSocket 실시간 송수신을 담당한다. 회원 위젯·관리자 채팅 화면이 공통으로 쓴다.
 * active가 true인 동안만 연결하며(=실제로 그 화면을 보고 있는 동안), 연결돼 있으면 서버가 상대 메시지를 곧장 읽음 처리한다.
 * WebSocket은 보낸 사람 본인에게는 메시지를 되돌려주지 않으므로, 소켓으로 보낸 내 메시지는 화면에 직접 붙인다(낙관적 갱신).
 */
function useRoomMessages({ roomId, active, myUserId, fetchMessages, sendRest, markRead, onRoomClosed, checkRoomClosed }) {
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const reload = useCallback(() => {
    if (!roomId) {
      setMessages([]);
      return Promise.resolve();
    }
    setLoading(true);
    return fetchMessages(roomId)
      .then((result) => setMessages(result.data))
      .catch((cause) => setError(cause.message))
      .finally(() => setLoading(false));
  }, [roomId, fetchMessages]);

  // 화면에 들어올 때(active가 true로 바뀔 때) 최신 메시지를 REST로 채우고 읽음 처리한다.
  useEffect(() => {
    if (!active || !roomId) return;
    reload().then(() => markRead(roomId).catch(() => {}));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, roomId]);

  // roomId 자체가 없어지면(방을 선택 해제했거나, 종료된 방을 보다가 패널을 다시 열어 완전히 새 상태가 된 경우)
  // 이전 방의 메시지를 화면에 남기지 않고 바로 비운다.
  useEffect(() => {
    if (!roomId) setMessages([]);
  }, [roomId]);

  // 재연결 시 끊긴 동안 쌓였을 수 있는 메시지를 다시 불러오기 위해, "이미 한 번 연결됐었는지"를 방마다 추적한다.
  const hasConnectedRef = useRef(false);
  useEffect(() => {
    hasConnectedRef.current = false;
  }, [roomId]);
  // 패널을 닫아 의도적으로 연결을 끊었을 때도 초기화한다. 안 그러면 패널을 다시 열어 처음 연결될 때를
  // "재연결"로 착각해 messages·read·me를 한 번 더 불필요하게 호출하게 된다.
  useEffect(() => {
    if (!active) hasConnectedRef.current = false;
  }, [active]);

  const { connected, sendText } = useChatSocket(active ? roomId : null, {
    // roomClosed가 true면 상대가 방금 종료하며 남긴 안내 메시지다. 메시지를 먼저 붙이고,
    // 그 다음에 방 상태를 바꾼다(onRoomClosed) — 안내를 보기 전에 입력창이 사라지지 않게 순서를 맞춘다.
    onMessage: (message, roomClosed) => {
      setMessages((prev) => [...prev, message]);
      if (roomClosed) onRoomClosed?.();
    },
    onError: (code, message) => {
      setError(message);
      // 상대가 먼저 상담을 종료해 내가 보낸 메시지가 거절된 경우. 서버가 남긴 종료 안내 메시지가 먼저
      // 화면에 반영되게 하고(reload), 그 다음에 방 상태를 바꾼다(onRoomClosed) — 안내를 보기 전에
      // 입력창이 먼저 사라지는 것처럼 보이지 않도록 순서를 맞춘다.
      if (code === 'CHAT_ROOM_CLOSED') {
        reload().then(() => onRoomClosed?.());
      }
    },
  });

  // 최초 연결이 아니라 "재연결"일 때만 메시지를 다시 불러온다. 끊긴 동안 상대가 보낸 메시지는
  // 서버가 다시 보내주지 않으므로(연결 안 된 세션에는 보낼 대상이 없어서), 재연결 성공 시 REST로 따라잡는다.
  // 메시지를 먼저 반영한 뒤에 방 상태를 확인한다 — 끊긴 동안 상대가 상담을 종료했을 수도 있고,
  // 그 경우 종료 안내 메시지를 먼저 보여주고 그 다음에 입력창을 막아야(onRoomClosed) 순서가 자연스럽다.
  useEffect(() => {
    if (!connected) return;
    if (hasConnectedRef.current) {
      reload().then(() => {
        markRead(roomId).catch(() => {});
        checkRoomClosed?.()
          .then((closed) => {
            if (closed) onRoomClosed?.();
          })
          .catch(() => {});
      });
    }
    hasConnectedRef.current = true;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connected]);

  const send = async (content) => {
    if (sendText(content)) {
      setMessages((prev) => [
        ...prev,
        { messageId: `local-${Date.now()}`, roomId, senderId: myUserId, content, isRead: false, createdAt: new Date().toISOString() },
      ]);
      return;
    }
    // 연결되기 전이거나 끊긴 경우 REST로 보내고 목록을 다시 불러온다.
    try {
      await sendRest(content);
    } catch (cause) {
      // 종료 안내 메시지를 먼저 반영한 뒤 방 상태를 바꾼다(위 onError와 동일한 이유).
      if (cause.code === 'CHAT_ROOM_CLOSED') {
        reload().then(() => onRoomClosed?.());
      }
      throw cause;
    }
    await reload();
  };

  return { messages, loading, error, connected, send, reload };
}

/** 회원 채팅 위젯 전용 훅. open(패널 열림)이 true인 동안만 실시간 연결한다. */
export function useMemberChat(open, myUserId) {
  const [room, setRoom] = useState(null);
  const [roomLoading, setRoomLoading] = useState(true);
  const [roomError, setRoomError] = useState('');
  const [badgeCount, setBadgeCount] = useState(0);

  const loadRoom = useCallback(
    () =>
      getMyRoom()
        .then((result) => setRoom(result.data))
        .catch((cause) => setRoomError(cause.message))
        .finally(() => setRoomLoading(false)),
    []
  );

  useEffect(() => {
    loadRoom();
  }, [loadRoom]);

  // 패널을 다시 열 때마다 방 상태(종료 여부 등)를 최신으로 확인한다.
  useEffect(() => {
    if (open) loadRoom();
  }, [open, loadRoom]);

  const activeRoomId = room && room.status !== 'CLOSED' ? room.roomId : null;
  const active = open && Boolean(activeRoomId);
  // 소켓 연결·REST 재조회는 열린 방일 때만(activeRoomId) 하지만, 메시지 초기화 판단은 종료된 방도
  // 구분해야 한다. 그래서 상태와 무관한 실제 방 ID(room?.roomId)를 useRoomMessages에 그대로 넘긴다.
  // 종료된 방(roomId는 있지만 activeRoomId는 null)을 보다가 패널을 닫았다가 다시 열면 room이 null로
  // 바뀌어 roomId도 null이 되고, 그 변화로 useRoomMessages가 이전 메시지를 비워 새 화면을 보여준다.
  const roomIdForMessages = room?.roomId ?? null;

  // 상대(관리자)가 먼저 종료해 내 전송이 거절된 경우. 서버가 남긴 종료 안내 메시지는 useRoomMessages가
  // 알아서 다시 불러오므로, 여기서는 방 상태만 CLOSED로 맞춰 안내문·종료 버튼이 즉시 반영되게 한다.
  const handleClosedByAdmin = useCallback(() => {
    setRoom((prev) => (prev ? { ...prev, status: 'CLOSED' } : prev));
  }, []);

  // 재연결 시 방 상태를 다시 확인한다. getMyRoom()은 열린 방(WAITING/IN_PROGRESS)만 찾으므로,
  // 끊긴 동안 상대가 종료해 더 이상 열린 방이 없다면 이 방이 끊긴 사이에 종료된 것으로 본다.
  const checkRoomClosedOnReconnect = useCallback(() => getMyRoom().then((result) => !result.data), []);

  const {
    messages,
    loading: messagesLoading,
    error: messagesError,
    connected,
    send: sendInRoom,
    reload: reloadMessages,
  } = useRoomMessages({
    roomId: roomIdForMessages,
    active,
    myUserId,
    fetchMessages: getMemberMessages,
    sendRest: sendMemberMessage,
    markRead: markMemberRead,
    onRoomClosed: handleClosedByAdmin,
    checkRoomClosed: checkRoomClosedOnReconnect,
  });

  // 패널이 닫혀 있는 동안 플로팅 버튼 배지용 안읽은 수를 확인한다. 폴링 대신, 알림과 같은 SSE 연결로
  // 관리자가 메시지를 보내거나 상담을 종료할 때마다 오는 'chat-unread' 신호(chatUnreadBus 경유)를 받아 다시 조회한다.
  useEffect(() => {
    if (active || !activeRoomId) {
      setBadgeCount(0);
      return undefined;
    }
    const check = () => getMemberUnreadCount(activeRoomId).then((r) => setBadgeCount(r.data)).catch(() => {});
    check(); // 패널이 닫히는 순간까지 쌓였을 수 있는 값을 먼저 맞춰둔다
    return subscribeChatUnread(check);
  }, [active, activeRoomId]);

  const send = async (content) => {
    // 종료된 방에서는 이어서 보낼 수 없다(입력창도 막혀 있다). 패널을 닫았다가 다시 열면
    // loadRoom이 다시 조회되어 room이 null로 바뀌고, 그때 메시지를 보내야 새 대기 방이 생긴다.
    if (room?.status === 'CLOSED') {
      throw new Error('종료된 상담입니다. 패널을 닫았다가 다시 열어 새 상담을 시작해주세요.');
    }
    if (!room) {
      // 방이 아직 없음(첫 문의): REST로 보내면 서버가 새 대기 방을 만든다.
      await sendMemberMessage(content);
      await loadRoom();
      return;
    }
    await sendInRoom(content);
  };

  // 상담 종료. 대기중(WAITING)이어도 가능하다 — 메시지를 보내자마자 생기는 대기 방을 바로 취소할 수 있어야 하므로.
  // loadRoom()(= GET /chat/rooms/me)은 열린 방(WAITING/IN_PROGRESS)만 찾으므로 종료 직후 호출하면
  // 방이 null로 보여 "종료되었습니다" 안내가 사라진다. 그래서 다시 조회하지 않고 상태만 직접 CLOSED로 바꾼다.
  const close = async () => {
    if (!activeRoomId) return;
    try {
      await closeMemberRoom(activeRoomId);
    } catch (cause) {
      // 이미 종료된 상담이면(상대가 먼저 종료했거나 중복 클릭) 원하는 상태이므로 에러로 취급하지 않는다.
      if (cause.code !== 'ROOM_NOT_CLOSABLE') throw cause;
    }
    await reloadMessages(); // 서버가 종료 시 남긴 "상담이 종료되었습니다." 메시지를 가져온다.
    setRoom((prev) => (prev ? { ...prev, status: 'CLOSED' } : prev));
  };

  return {
    room,
    messages,
    loading: roomLoading || messagesLoading,
    error: roomError || messagesError,
    connected,
    unreadCount: badgeCount,
    send,
    close,
  };
}

const ADMIN_CHAT_STATUSES = ['WAITING', 'IN_PROGRESS', 'CLOSED'];

/** 관리자 상담 관리 화면 전용 훅: 상태별 목록 + 선택한 방의 실시간 채팅. 새로고침해도 URL 쿼리로 탭·선택 방을 복원한다. */
export function useAdminChat(myUserId) {
  const [searchParams, setSearchParams] = useSearchParams();
  const statusParam = searchParams.get('status');
  const status = ADMIN_CHAT_STATUSES.includes(statusParam) ? statusParam : 'WAITING';
  const roomIdParam = searchParams.get('roomId');

  // status·roomId 중 바뀐 값만 URL 쿼리에 반영한다 (다른 값은 유지, null이면 제거).
  const updateParams = useCallback(
    (patch) => {
      setSearchParams(
        (prev) => {
          const params = new URLSearchParams(prev);
          Object.entries(patch).forEach(([key, value]) => {
            if (value === null || value === undefined) params.delete(key);
            else params.set(key, String(value));
          });
          return params;
        },
        { replace: true }
      );
    },
    [setSearchParams]
  );

  const setStatus = useCallback((next) => updateParams({ status: next }), [updateParams]);

  const [rooms, setRooms] = useState([]);
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState('');
  const [selectedRoom, setSelectedRoomState] = useState(null); // { roomId, status, nickname, ... }
  const [notice, setNotice] = useState('');
  // 무한스크롤용: 다음 페이지가 있는지, 그 다음 조회에 쓸 커서(마지막 방 ID)
  const [hasMore, setHasMore] = useState(false);
  const [nextCursor, setNextCursor] = useState(null);
  const [loadingMore, setLoadingMore] = useState(false);

  // 새로고침 직후 URL에 roomId가 있으면 현재 목록 탭과 무관하게 그 방을 바로 복원한다.
  useEffect(() => {
    if (!roomIdParam) return;
    let active = true;
    getAdminRoom(roomIdParam)
      .then((result) => {
        if (active) setSelectedRoomState(result.data);
      })
      .catch(() => {
        if (active) updateParams({ roomId: null });
      });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 탭을 바꿀 때는 항상 최신 1페이지로 목록을 통째로 새로 불러온다.
  const reloadList = useCallback(
    () =>
      getAdminRooms(status, { size: ROOM_PAGE_SIZE })
        .then((result) => {
          setRooms(result.data);
          setHasMore(Boolean(result.meta?.hasMore));
          setNextCursor(result.meta?.nextCursor ?? null);
        })
        .catch((cause) => setListError(cause.message))
        .finally(() => setListLoading(false)),
    [status]
  );

  useEffect(() => {
    setListLoading(true);
    reloadList();
  }, [reloadList]);

  // 새 대기 상담이 들어와도 알림 채널이 없으므로, 목록은 짧은 주기로 다시 불러온다.
  // reloadList(1페이지만)를 그대로 쓰면 스크롤로 더 불러온 뒷부분이 매번 사라지므로, 지금까지
  // 불러온 개수만큼 다시 가져와 이미 보고 있던 목록이 줄어들지 않게 한다.
  const refreshList = useCallback(
    () =>
      getAdminRooms(status, { size: Math.max(rooms.length, ROOM_PAGE_SIZE) })
        .then((result) => {
          setRooms(result.data);
          setHasMore(Boolean(result.meta?.hasMore));
          setNextCursor(result.meta?.nextCursor ?? null);
        })
        .catch((cause) => setListError(cause.message)),
    [status, rooms.length]
  );

  useEffect(() => {
    const timer = setInterval(refreshList, LIST_POLL_MS);
    return () => clearInterval(timer);
  }, [refreshList]);

  // 스크롤이 끝에 닿았을 때 다음 페이지를 이어붙인다. 이미 불러오는 중이거나 더 없으면 그대로 둔다.
  const loadMore = useCallback(() => {
    if (!hasMore || loadingMore || nextCursor == null) return Promise.resolve();
    setLoadingMore(true);
    return getAdminRooms(status, { cursorId: nextCursor, size: ROOM_PAGE_SIZE })
      .then((result) => {
        setRooms((prev) => [...prev, ...result.data]);
        setHasMore(Boolean(result.meta?.hasMore));
        setNextCursor(result.meta?.nextCursor ?? null);
      })
      .catch((cause) => setListError(cause.message))
      .finally(() => setLoadingMore(false));
  }, [status, hasMore, loadingMore, nextCursor]);

  // 진행 중 목록에서, 내가 담당 중인 방마다 안읽은 메시지 수를 확인해 새 메시지 배지로 보여준다.
  const [unreadByRoom, setUnreadByRoom] = useState({});
  useEffect(() => {
    if (status !== 'IN_PROGRESS') {
      setUnreadByRoom({});
      return;
    }
    const myRooms = rooms.filter((r) => r.adminId === myUserId);
    if (!myRooms.length) {
      setUnreadByRoom({});
      return;
    }
    let active = true;
    Promise.all(
      myRooms.map((r) => getAdminRoomUnreadCount(r.roomId).then((result) => [r.roomId, result.data]).catch(() => [r.roomId, 0]))
    ).then((pairs) => {
      if (active) setUnreadByRoom(Object.fromEntries(pairs));
    });
    return () => {
      active = false;
    };
  }, [status, rooms, myUserId]);

  const selectedRoomId = selectedRoom?.roomId ?? null;
  const active = Boolean(selectedRoomId) && selectedRoom.status === 'IN_PROGRESS';

  // 회원이 먼저 상담을 종료했을 때(소켓 전송이 CHAT_ROOM_CLOSED로 거절됐거나, 재연결 후 상태 확인으로 발견한 경우):
  // 화면 상태를 최신으로 맞추고 안내한다.
  const handleRoomClosedByOther = useCallback(() => {
    setNotice('회원이 상담을 종료했습니다.');
    if (selectedRoomId) {
      getAdminRoom(selectedRoomId).then((result) => setSelectedRoomState(result.data)).catch(() => {});
    }
    reloadList();
  }, [selectedRoomId, reloadList]);

  // 재연결 시 방 상태를 다시 확인한다. getAdminRoom은 상태 무관하게 조회되므로 바로 CLOSED 여부를 알 수 있다.
  const checkRoomClosedOnReconnect = useCallback(() => {
    if (!selectedRoomId) return Promise.resolve(false);
    return getAdminRoom(selectedRoomId).then((result) => result.data?.status === 'CLOSED');
  }, [selectedRoomId]);

  const {
    messages,
    loading: messagesLoading,
    error: messagesError,
    connected,
    send,
    reload: reloadMessages,
  } = useRoomMessages({
    roomId: selectedRoomId,
    active,
    myUserId,
    fetchMessages: getAdminMessages,
    sendRest: (content) => sendAdminMessage(selectedRoomId, content),
    markRead: markAdminRead,
    onRoomClosed: handleRoomClosedByOther,
    checkRoomClosed: checkRoomClosedOnReconnect,
  });

  // 종료된 방을 선택했을 때는 실시간 연결 없이 지난 메시지만 한 번 불러온다.
  useEffect(() => {
    if (selectedRoomId && selectedRoom?.status === 'CLOSED') reloadMessages();
  }, [selectedRoomId, selectedRoom?.status, reloadMessages]);

  // 대기 방 입장: 성공하면 진행중으로 선택하고 탭도 옮긴다. 이미 다른 관리자가 입장했으면 안내만 띄운다.
  const open = async (room) => {
    setNotice('');
    if (room.status !== 'WAITING') {
      setSelectedRoomState(room);
      updateParams({ roomId: room.roomId });
      return;
    }
    try {
      await enterAdminRoom(room.roomId);
      setSelectedRoomState({ ...room, status: 'IN_PROGRESS' });
      updateParams({ status: 'IN_PROGRESS', roomId: room.roomId });
    } catch (cause) {
      setNotice(cause.code === 'ROOM_ALREADY_CLAIMED' ? '이미 다른 관리자가 입장한 상담방입니다.' : cause.message);
      reloadList();
    }
  };

  const close = async () => {
    if (!selectedRoomId) return;
    try {
      await closeAdminRoom(selectedRoomId);
      await reloadMessages(); // 종료 안내 메시지를 먼저 반영한 뒤 방 상태를 바꾼다(입력창이 먼저 사라지는 것처럼 보이지 않게).
      setSelectedRoomState((prev) => (prev ? { ...prev, status: 'CLOSED' } : prev));
      reloadList();
    } catch (cause) {
      setNotice(cause.message);
    }
  };

  return {
    status,
    setStatus,
    rooms,
    listLoading,
    listError,
    hasMore,
    loadingMore,
    loadMore,
    unreadByRoom,
    selectedRoom,
    messages,
    messagesLoading,
    messagesError,
    connected,
    send,
    open,
    close,
    notice,
  };
}

/** 관리자 대시보드의 대기 상담방 수. 상담 관리 화면에 들어가지 않아도 주기적으로 확인한다. */
export function useWaitingCount() {
  const [count, setCount] = useState(0);
  useEffect(() => {
    const check = () => getAdminWaitingCount().then((r) => setCount(r.data)).catch(() => {});
    check();
    const timer = setInterval(check, LIST_POLL_MS);
    return () => clearInterval(timer);
  }, []);
  return count;
}
