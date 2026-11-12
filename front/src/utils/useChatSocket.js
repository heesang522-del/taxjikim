import { useEffect, useRef, useState } from 'react';

// 재연결 시도 간격(ms)과 최대 시도 횟수. 다른 화면·설정에서도 참조할 수 있도록 공개한다.
export const RECONNECT_BASE_DELAY_MS = 1000;
export const RECONNECT_MAX_DELAY_MS = 30000;
export const RECONNECT_MAX_ATTEMPTS = 5;
// 하트비트 주기와 응답 대기 시간(ms). 네트워크가 조용히 끊기면(와이파이 꺼짐 등) 브라우저가 onclose/onerror를
// 바로 못 받을 수 있어서, 주기적으로 ping을 보내 응답이 없으면 끊긴 것으로 간주하고 직접 닫는다.
export const HEARTBEAT_INTERVAL_MS = 15000;
export const HEARTBEAT_TIMEOUT_MS = 10000;

/**
 * 채팅방 하나(roomId)에 WebSocket("/ws/chat/{roomId}")으로 연결해 실시간 송수신을 담당한다.
 * 인증은 accessToken 쿠키로 이루어지며(핸드셰이크 때 서버가 확인), 브라우저가 같은 출처 요청에 자동으로 실어 보낸다.
 * roomId가 없거나 바뀌면 이전 연결을 정리하고 다시 연결한다.
 * 연결이 예기치 않게 끊기면(서버 재시작, 네트워크 문제 등) 지수 백오프로 최대 RECONNECT_MAX_ATTEMPTS번까지 재연결을 시도한다.
 * roomId 변경·컴포넌트 해제처럼 의도적으로 끊을 때는 재연결을 시도하지 않는다.
 * 연결돼 있는 동안은 HEARTBEAT_INTERVAL_MS마다 "ping"을 보내고, HEARTBEAT_TIMEOUT_MS 안에 "pong"이 안 오면
 * 조용히 끊긴 연결로 보고 강제로 닫는다 — 그러면 onclose가 이어서 재연결을 트리거한다.
 *
 * onMessage(messageDto, roomClosed): 서버가 저장에 성공해 전달한 상대방 메시지. roomClosed가 true면
 *   상대가 방금 상담을 종료해서 남긴 안내 메시지라는 뜻이다 — 받는 쪽이 메시지를 붙인 뒤 방 상태도 반영해야 한다.
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
    let heartbeatInterval = null;
    let pongTimeout = null;

    const stopHeartbeat = () => {
      if (heartbeatInterval) clearInterval(heartbeatInterval);
      if (pongTimeout) clearTimeout(pongTimeout);
      heartbeatInterval = null;
      pongTimeout = null;
    };

    const startHeartbeat = () => {
      heartbeatInterval = setInterval(() => {
        if (socket.readyState !== WebSocket.OPEN) return;
        socket.send('ping');
        pongTimeout = setTimeout(() => {
          // 응답(pong)이 안 왔다 — 조용히 끊긴 연결로 보고 직접 닫는다. 이어서 onclose가 재연결을 시작한다.
          socket.close();
        }, HEARTBEAT_TIMEOUT_MS);
      }, HEARTBEAT_INTERVAL_MS);
    };

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
        startHeartbeat();
      };
      socket.onclose = () => {
        stopHeartbeat();
        setConnected(false);
        scheduleReconnect();
      };
      socket.onerror = () => setConnected(false);
      socket.onmessage = (event) => {
        if (event.data === 'pong') {
          if (pongTimeout) {
            clearTimeout(pongTimeout);
            pongTimeout = null;
          }
          return;
        }
        let payload;
        try {
          payload = JSON.parse(event.data);
        } catch {
          return; // 알 수 없는 형식은 무시
        }
        if (payload.type === 'MESSAGE') handlersRef.current.onMessage?.(payload.message, payload.roomClosed === true);
        else if (payload.type === 'ERROR') handlersRef.current.onError?.(payload.code, payload.message);
      };
    }

    connect();

    return () => {
      closedByEffect = true;
      stopHeartbeat();
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
