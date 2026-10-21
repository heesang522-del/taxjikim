import { client } from './client.js';

// 실제 채팅 REST 요청은 기존 공통 client와 Vite /api 프록시를 거친다.
async function requestChat(config) {
  try {
    const response = await client.request(config);
    if (!response.data?.success) {
      const error = new Error(response.data?.error?.message ?? '요청을 처리하지 못했습니다.');
      error.code = response.data?.error?.code;
      throw error;
    }
    return response.data;
  } catch (cause) {
    const detail = cause.response?.data?.error;
    const error = new Error(
      detail?.message ??
        (cause.response
          ? '서버 오류가 발생했습니다.'
          : cause.isAxiosError
            ? '서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.'
            : cause.message)
    );
    error.status = cause.response?.status ?? cause.status;
    error.code = detail?.code ?? cause.code;
    throw error;
  }
}

/* ================= 회원 ================= */

// 내 열린 방(WAITING/IN_PROGRESS) 조회. 없으면 data가 null이다.
export function getMyRoom() {
  return requestChat({ url: '/chat/rooms/me' });
}

// 메시지 전송. 열린 방이 없으면 서버가 대기 방을 새로 만든다.
export function sendMemberMessage(content) {
  return requestChat({ url: '/chat/messages', method: 'POST', data: { content } });
}

export function getMemberMessages(roomId) {
  return requestChat({ url: `/chat/rooms/${roomId}/messages` });
}

export function markMemberRead(roomId) {
  return requestChat({ url: `/chat/rooms/${roomId}/messages/read`, method: 'PATCH' });
}

// 패널을 닫아 둔 동안의 안읽은 메시지 수 배지용
export function getMemberUnreadCount(roomId) {
  return requestChat({ url: `/chat/rooms/${roomId}/unread-count` });
}

/* ================= 관리자 ================= */

// 상태별 상담방 목록. status: WAITING | IN_PROGRESS | CLOSED
export function getAdminRooms(status) {
  return requestChat({ url: '/admin/chat/rooms', params: { status } });
}

// 대기 상담방 수 (대시보드)
export function getAdminWaitingCount() {
  return requestChat({ url: '/admin/chat/rooms/waiting-count' });
}

// 방 단건 요약 조회. 새로고침 후 선택했던 방을 복원할 때 쓴다.
export function getAdminRoom(roomId) {
  return requestChat({ url: `/admin/chat/rooms/${roomId}` });
}

// 입장: 대기 -> 진행중. 이미 다른 관리자가 입장했으면 409(ROOM_ALREADY_CLAIMED)
export function enterAdminRoom(roomId) {
  return requestChat({ url: `/admin/chat/rooms/${roomId}/enter`, method: 'POST' });
}

export function closeAdminRoom(roomId) {
  return requestChat({ url: `/admin/chat/rooms/${roomId}/close`, method: 'POST' });
}

export function sendAdminMessage(roomId, content) {
  return requestChat({ url: `/admin/chat/rooms/${roomId}/messages`, method: 'POST', data: { content } });
}

export function getAdminMessages(roomId) {
  return requestChat({ url: `/admin/chat/rooms/${roomId}/messages` });
}

export function markAdminRead(roomId) {
  return requestChat({ url: `/admin/chat/rooms/${roomId}/messages/read`, method: 'PATCH' });
}

// 담당 중인 방 하나의 안읽은 메시지 수. 진행 중 목록의 새 메시지 표시에 쓴다.
export function getAdminRoomUnreadCount(roomId) {
  return requestChat({ url: `/admin/chat/rooms/${roomId}/unread-count` });
}
