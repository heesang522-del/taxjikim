package com.example.practice.chat;


import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


//TextWebSocketHandler를 상속받아서 접속, 연결유지, 해제를 처리함.

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    // key : roomId , value : 방에 접속한 세션들
    // 세션 목록은 중복 저장될 필요 없고 순서도 무관하므로 Set 사용
    private final Map<String,Set<WebSocketSession>> room = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 연결 성립 (TCP의 accept 직후에 해당)
        String roomId = extractRoomId(session); //핸드셰이크 요청온 방 이름을 파싱함.
        session.getAttributes().put("roomId",roomId); // 파싱한 roomId를 세션에 저장(이후 메시지 수신/ 연결 종료 시 재사용)

        // roomId에 해당하는 방이 없으면 새 세션 Set을 만들어 등록하고, 있으면 기존 Set을 그대로 사용 -> 거기에 현재 session 추가
        room.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);


        System.out.println("[브라우저 연결] : " + session.getId() + "/ room=" + roomId);

    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 메시지 수신 (TCP의 readLine 루프에 해당)

        if (message.getPayload().equals("quit")) {
            session.close();
            return;
        }

        String roomId = (String) session.getAttributes().get("roomId");
        Set<WebSocketSession> members = room.get(roomId);
        if (members == null) {
            return;
        }

        // roomId에 해당하는 방 유저들에게 브로드캐스팅
        for (WebSocketSession c : members) {
            try {
                c.sendMessage(message);
            } catch (IOException e) {
                // 한 세션이 막혔을 때 전체 메시지를 막지 않고 로그만 남기고 넘어감.
            }
        }
    }

        @Override
        public void afterConnectionClosed (WebSocketSession session, CloseStatus status){
            // 연결 종료 (TCP의 소켓 close 처리에 해당)
            String roomId = (String) session.getAttributes().get("roomId");
            Set<WebSocketSession> members = room.get(roomId);
            if (members == null) {
                return;
            }
            members.remove(session);
            if (members.isEmpty()) {
                room.remove(roomId);
            }
    }

    // /ws/chat/{roomId}로 핸드셰이크 요청시 파싱용 메서드
    private String extractRoomId(WebSocketSession session) {
        String path = session.getUri().getPath(); // "/ws/chat/room1"
        return path.substring(path.lastIndexOf('/') + 1);
    }
}