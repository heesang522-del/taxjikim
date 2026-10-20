// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { create } from 'zustand';
import {
  getMyRoom,
  sendMemberMessage,
  getMemberMessages,
  markMemberRead,
  getMemberUnreadCount,
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

const LIST_POLL_MS = 8000; // 관리자 상담 목록·대기 수: 실시간 알림 채널이 없어 주기적으로 다시 불러온다
const BADGE_POLL_MS = 15000; // 회원 패널이 닫혀 있는 동안 배지용 안읽은 수 확인 주기

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
function useRoomMessages({ roomId, active, myUserId, fetchMessages, sendRest, markRead }) {
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

  const { connected, sendText } = useChatSocket(active ? roomId : null, {
    onMessage: (message) => setMessages((prev) => [...prev, message]),
    onError: (code, message) => setError(message),
  });

  const send = async (content) => {
    if (sendText(content)) {
      setMessages((prev) => [
        ...prev,
        { messageId: `local-${Date.now()}`, roomId, senderId: myUserId, content, isRead: false, createdAt: new Date().toISOString() },
      ]);
      return;
    }
    // 연결되기 전이거나 끊긴 경우 REST로 보내고 목록을 다시 불러온다.
    await sendRest(content);
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

  const {
    messages,
    loading: messagesLoading,
    error: messagesError,
    connected,
    send: sendInRoom,
  } = useRoomMessages({
    roomId: activeRoomId,
    active,
    myUserId,
    fetchMessages: getMemberMessages,
    sendRest: sendMemberMessage,
    markRead: markMemberRead,
  });

  // 패널이 닫혀 있는 동안 플로팅 버튼 배지용 안읽은 수를 주기적으로 확인한다.
  useEffect(() => {
    if (active || !activeRoomId) {
      setBadgeCount(0);
      return undefined;
    }
    const check = () => getMemberUnreadCount(activeRoomId).then((r) => setBadgeCount(r.data)).catch(() => {});
    check();
    const timer = setInterval(check, BADGE_POLL_MS);
    return () => clearInterval(timer);
  }, [active, activeRoomId]);

  const send = async (content) => {
    if (!room || room.status === 'CLOSED') {
      // 방이 없거나 이미 종료됨: REST로 보내면 서버가 새 대기 방을 만든다.
      await sendMemberMessage(content);
      await loadRoom();
      return;
    }
    await sendInRoom(content);
  };

  return {
    room,
    messages,
    loading: roomLoading || messagesLoading,
    error: roomError || messagesError,
    connected,
    unreadCount: badgeCount,
    send,
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

  const reloadList = useCallback(
    () =>
      getAdminRooms(status)
        .then((result) => setRooms(result.data))
        .catch((cause) => setListError(cause.message))
        .finally(() => setListLoading(false)),
    [status]
  );

  useEffect(() => {
    setListLoading(true);
    reloadList();
  }, [reloadList]);

  // 새 대기 상담이 들어와도 알림 채널이 없으므로, 목록은 짧은 주기로 다시 불러온다.
  useEffect(() => {
    const timer = setInterval(reloadList, LIST_POLL_MS);
    return () => clearInterval(timer);
  }, [reloadList]);

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
