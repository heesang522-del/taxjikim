// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useMemo } from 'react';
import { create } from 'zustand';
import { getCurrentUser, isOnline, useAuth } from './useAuthStore';

const KEY = 'taxlab.chat.v1';
/** 종료된 상담은 종료 시점부터 이 기간만 보관하고 삭제한다 */
export const RETENTION_MS = 30 * 24 * 60 * 60 * 1000;

const isRoom = (r) => r && [r.id, r.memberId, r.nickname, r.status].every((v) => typeof v === 'string') && Array.isArray(r.messages);

// 저장된 상담방을 읽으면서 보관 기간이 지난 종료 상담은 지운다
const read = () => {
  try {
    const list = JSON.parse(localStorage.getItem(KEY));
    if (Array.isArray(list)) return list.filter(isRoom).filter((r) => !(r.closedAt && Date.now() - r.closedAt > RETENTION_MS));
  } catch { /* 저장값이 깨진 경우 빈 목록으로 시작 */ }
  return [];
};

/** 다른 탭(회원 탭 ↔ 관리자 탭)의 변경은 storage 이벤트로 받아 실시간처럼 보여준다. */
export const syncChatFromStorage = (key) => { if (key === KEY) useChatStore.setState({ rooms: read() }); };

/**
 * 1:1 채팅 상담. MOCK: 서버가 없는 동안 상담방을 localStorage에 저장한다. (실서비스는 WebSocket)
 * 상담방 상태: '대기'(회원이 보냄) → '진행 중'(관리자가 입장) → '종료'. 회원 1명당 열린 방은 1개다.
 */
export const useChatStore = create((set) => {
  const mutate = (fn) => {
    const next = fn(read());
    try { localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* 저장 공간이 없거나 막힌 경우 무시 */ }
    set({ rooms: next });
  };

  return {
    rooms: read(),
    open: false, // 채팅 패널 열림 (화면을 이동해도 유지)
    setOpen: (open) => set({ open }),

    /** 접속할 때 보관 기간이 지난 상담을 저장소에서도 실제로 지운다 */
    purge: () => mutate((list) => list),

    /** 회원이 메시지를 보낸다. 열린 방이 있으면 이어서 보내고, 없으면(종료됐으면) 새 상담방을 만든다. */
    sendMember: (text) => {
      const user = getCurrentUser();
      const body = text.trim();
      if (!user || !body) return false;
      const now = Date.now();
      const msg = { id: `m${now}`, from: 'member', text: body, at: now };
      mutate((list) => {
        const target = list.find((r) => r.memberId === user.id && r.status !== '종료');
        if (target) return list.map((r) => (r.id === target.id ? { ...r, messages: [...r.messages, msg], memberReadAt: now } : r));
        return [...list, { id: `chat${now}`, memberId: user.id, nickname: user.nickname, status: '대기', createdAt: now, startedAt: null, closedAt: null, memberReadAt: now, adminReadAt: 0, messages: [msg] }];
      });
      return true;
    },

    /** 관리자가 상담방에 입장한다 (대기 → 진행 중) */
    enterRoom: (id) => {
      const now = Date.now();
      mutate((list) => list.map((r) => {
        if (r.id !== id || r.status === '종료') return r;
        return { ...r, status: '진행 중', startedAt: r.startedAt ?? now, adminReadAt: now };
      }));
    },

    /**
     * 관리자 답변. 회원이 접속 중이면 채팅 패널로 바로 전달('online'), 아니면 메일을 보내고 알림에 쌓는다('mail').
     * 진행 중이 아닌 방에는 보낼 수 없다. 처리 방식을 돌려준다.
     */
    sendAdmin: (id, text) => {
      const body = text.trim();
      const room = read().find((r) => r.id === id);
      if (!body || !room || room.status !== '진행 중') return null;
      const delivery = isOnline(room.nickname) ? 'online' : 'mail';
      const now = Date.now();
      mutate((list) => list.map((r) => (r.id === id ? { ...r, messages: [...r.messages, { id: `m${now}`, from: 'admin', text: body, at: now, delivery }], adminReadAt: now } : r)));
      return delivery;
    },

    /** 상담 종료. 이 시점부터 30일 보관한다. */
    closeRoom: (id) => {
      mutate((list) => list.map((r) => (r.id === id && r.status !== '종료' ? { ...r, status: '종료', closedAt: Date.now() } : r)));
    },

    markMemberRead: (id) => {
      const now = Date.now();
      mutate((list) => list.map((r) => (r.id === id ? { ...r, memberReadAt: now } : r)));
    },
    markAdminRead: (id) => {
      const now = Date.now();
      mutate((list) => list.map((r) => (r.id === id ? { ...r, adminReadAt: now } : r)));
    },
  };
});

/** 화면에서 쓰는 채팅 정보. 로그인한 회원의 상담방과 안 읽은 답변 수를 계산해 함께 돌려준다. */
export function useChat() {
  const state = useChatStore();
  const { user } = useAuth();
  const myRooms = useMemo(
    () => (user ? state.rooms.filter((r) => r.memberId === user.id).sort((a, b) => b.createdAt - a.createdAt) : []),
    [state.rooms, user],
  );
  const myOpenRoom = myRooms.find((r) => r.status !== '종료') ?? null;
  // 채팅 패널을 닫아 둔 동안 도착한 관리자 답변 수 (버튼 배지)
  const unreadCount = myRooms.reduce((n, r) => n + r.messages.filter((m) => m.from === 'admin' && m.at > (r.memberReadAt ?? 0)).length, 0);
  const waitingCount = state.rooms.filter((r) => r.status === '대기').length;
  return { ...state, myRooms, myOpenRoom, unreadCount, waitingCount };
}
