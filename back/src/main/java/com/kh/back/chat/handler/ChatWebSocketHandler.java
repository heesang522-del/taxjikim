package com.kh.back.chat.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String roomId = extractRoomId(session);
        // 요청 경로에서 받은 방 번호를 보관해 메시지와 연결 종료 때 재사용한다.
        session.getAttributes().put("roomId", roomId);
        rooms.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("채팅 연결: sessionId={}, roomId={}", session.getId(), roomId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if ("quit".equals(message.getPayload())) {
            session.close();
            return;
        }
        String roomId = (String) session.getAttributes().get("roomId");
        Set<WebSocketSession> roomSessions = rooms.get(roomId);
        if (roomSessions == null) {
            return;
        }
        // 기존 동작처럼 같은 방의 모든 세션에 메시지를 전달한다.
        for (WebSocketSession recipient : roomSessions) {
            try {
                recipient.sendMessage(message);
            } catch (IOException exception) {
                log.warn("채팅 메시지 전송 실패: sessionId={}", recipient.getId(), exception);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = (String) session.getAttributes().get("roomId");
        Set<WebSocketSession> roomSessions = rooms.get(roomId);
        if (roomSessions == null) {
            return;
        }
        roomSessions.remove(session);
        if (roomSessions.isEmpty()) {
            rooms.remove(roomId);
        }
        log.debug("채팅 종료: sessionId={}, roomId={}", session.getId(), roomId);
    }

    private String extractRoomId(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
