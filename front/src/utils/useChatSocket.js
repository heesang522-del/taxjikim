import { useEffect, useRef, useState } from 'react';

// 재연결 시도 간격(ms)과 최대 시도 횟수. 다른 화면·설정에서도 참조할 수 있도록 공개한다.
export const RECONNECT_BASE_DELAY_MS = 1000;
export const RECONNECT_MAX_DELAY_MS = 30000;
export const RECONNECT_MAX_ATTEMPTS = 5;

/**
 * 채팅방 하나(roomId)에 WebSocket("/ws/chat/{roomId}")으로 연결해 실시간 송수신을 담당한다.
 * 인증은 accessToken 쿠키로 이루어지며(핸드셰이크 때 서버가 확인), 브라우저가 같은 출처 요청에 자동으로 실어 보낸다.
 * roomId가 없거나 바뀌면 이전 연결을 정리하고 다시 연결한다.
 * 연결이 예기치 않게 끊기면(서버 재시작, 네트워크 문제 등) 지수 백오프로 최대 RECONNECT_MAX_ATTEMPTS번까지 재연결을 시도한다.
 * roomId 변경·컴포넌트 해제처럼 의도적으로 끊을 때는 재연결을 시도하지 않는다.
 *
 * onMessage(messageDto): 서버가 저장에 성공해 전달한 상대방 메시지
 * onError(code, message): 저장 실패 등 내가 보낸 메시지에 대한 오류 응답, 또는 재연결 실패 알림
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
    let socket;
    let reconnectTimer = null;
    let reconnectAttempt = 0;
    let closedByEffect = false; // roomId 변경·해제로 의도적으로 닫을 때는 재연결하지 않는다.

    const scheduleReconnect = () => {
      if (closedByEffect) return;
      if (reconnectAttempt >= RECONNECT_MAX_ATTEMPTS) {
        handlersRef.current.onError?.('RECONNECT_FAILED', '채팅 서버와 연결이 끊어졌습니다. 잠시 후 다시 열어주세요.');
        return;
      }
      const delay = Math.min(RECONNECT_BASE_DELAY_MS * 2 ** reconnectAttempt, RECONNECT_MAX_DELAY_MS);
      reconnectAttempt += 1;
      reconnectTimer = setTimeout(connect, delay);
    };

    function connect() {
      socket = new WebSocket(`${protocol}//${window.location.host}/ws/chat/${roomId}`);
      socketRef.current = socket;

      socket.onopen = () => {
        reconnectAttempt = 0; // 재연결에 성공했으니 다음 끊김은 다시 처음부터 백오프
        setConnected(true);
      };
      socket.onclose = () => {
        setConnected(false);
        scheduleReconnect();
      };
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
    }

    connect();

    return () => {
      closedByEffect = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
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
