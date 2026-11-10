// 로그인 중인 유저당 서버 SSE 구독은 useNotifications가 하나만 열어 둔다(그 주석 참고).
// 채팅 배지(useMemberChat)는 별도 연결을 열지 않고, 그 연결이 받은 'chat-unread' 신호를 이 버스로 전달받는다.
const listeners = new Set();

export function subscribeChatUnread(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function emitChatUnread() {
  listeners.forEach((listener) => listener());
}
