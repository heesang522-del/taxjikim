import { useEffect, useRef, useState } from 'react';

/**
 * 채팅방 하나(roomId)에 WebSocket("/ws/chat/{roomId}")으로 연결해 실시간 송수신을 담당한다.
 * 인증은 accessToken 쿠키로 이루어지며(핸드셰이크 때 서버가 확인), 브라우저가 같은 출처 요청에 자동으로 실어 보낸다.
 * roomId가 없거나 바뀌면 이전 연결을 정리하고 다시 연결한다.
 *
 * onMessage(messageDto): 서버가 저장에 성공해 전달한 상대방 메시지
 * onError(code, message): 저장 실패 등 내가 보낸 메시지에 대한 오류 응답
 */
export function useChatSocket(roomId, { onMessage, onError } = {}) {
  const [connected, setConnected] = useState(false);
  const socketRef = useRef(null);
  // 최신 콜백을 ref로 들고 있어, roomId가 같은 동안은 콜백이 바뀌어도 재연결하지 않는다.
  const handlersRef = useRef({ onMessage, onError });
  handlersRef.current = { onMessage, onError };

  useEffect(() => {
    if (!roomId) {
      setConnected(false);
      return undefined;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const socket = new WebSocket(`${protocol}//${window.location.host}/ws/chat/${roomId}`);
    socketRef.current = socket;

    socket.onopen = () => setConnected(true);
    socket.onclose = () => setConnected(false);
    socket.onerror = () => setConnected(false);
    socket.onmessage = (event) => {
      let payload;
      try {
        payload = JSON.parse(event.data);
      } catch {
        return; // 알 수 없는 형식은 무시
      }
      if (payload.type === 'MESSAGE') handlersRef.current.onMessage?.(payload.message);
      else if (payload.type === 'ERROR') handlersRef.current.onError?.(payload.code, payload.message);
    };

    return () => {
      socket.onopen = null;
      socket.onclose = null;
      socket.onerror = null;
      socket.onmessage = null;
      if (socket.readyState === WebSocket.OPEN) socket.send('quit');
      socket.close();
      socketRef.current = null;
      setConnected(false);
    };
  }, [roomId]);

  // 연결돼 있을 때만 보낸다. 실패하면(아직 연결 전 등) false를 돌려준다.
  const sendText = (content) => {
    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN) return false;
    socket.send(JSON.stringify({ content }));
    return true;
  };

  return { connected, sendText };
}
