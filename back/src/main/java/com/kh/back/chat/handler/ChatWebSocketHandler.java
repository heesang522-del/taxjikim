package com.kh.back.chat.handler;

import com.kh.back.auth.security.LoginSessionRepository;
import com.kh.back.chat.dto.ChatMessageDto;
import com.kh.back.chat.dto.ChatRoomDto;
import com.kh.back.chat.service.ChatService;
import com.kh.back.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 채팅방 하나(roomId)마다, 그 방에 연결된 WebSocketSession들을 들고 있다가 실시간으로 메시지를 주고받게 해주는 핸들러.
 *
 * 전체 흐름 (한쪽이 메시지를 보냈을 때):
 *   1. {@link #handleTextMessage}: 클라이언트가 보낸 JSON({"content":"..."})을 받는다.
 *      발신자(senderId)는 클라이언트가 알려주는 게 아니라, 연결 시 {@link ChatHandshakeInterceptor}가
 *      확인해서 세션에 넣어둔 인증된 userId를 그대로 쓴다. (클라이언트가 다른 사람인 척 보낼 수 없게)
 *      다만 연결은 핸드셰이크 때 한 번만 인증하므로, 처리 전에 {@link #isSessionStillValid}로
 *      그 로그인 세션이 아직 유효한지(로그아웃·다른 기기 로그인으로 끝나지 않았는지) 매번 다시 확인한다.
 *   2. 기존 REST API와 똑같은 {@link ChatService#sendMessage}를 호출해 DB에 저장한다.
 *      (종료된 방에 보내면 여기서 예외가 나고, 발신자에게만 에러로 알려준다.)
 *   3. {@link #deliverToCounterpart}: 저장에 성공하면, 같은 방에 연결된 "상대방"에게만 그 메시지를 쏴준다.
 *   4. 상대방이 지금 이 순간 연결돼 있다는 건 상대가 채팅창을 보고 있다는 뜻이므로, **실제로 전달에 성공했을 때만**
 *      읽음 처리까지 함께 한다. 전송이 실패하면 상대가 받지 못한 것이므로 읽음 처리하지 않는다.
 *
 * 연결이 끊긴 동안 쌓인 메시지는 이 핸들러가 다시 보내주지 않는다 — 재연결 후 REST 목록 API를 다시 불러서 채우는 건 클라이언트 책임이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    // 클라이언트가 보내는 메시지 본문. 발신자는 핸드셰이크에서 인증된 userId를 그대로 쓰므로 클라이언트가 보낼 필요 없다.
    private record IncomingMessage(String content) {}

    private final ChatService chatService;
    //문자열 <-> JSON 전환용 Websocket은 Spring MVC처럼 변환해주는게 없음.
    private final ObjectMapper objectMapper;
    private final LoginSessionRepository loginSessionRepository;

    // roomId별로 그 방에 지금 연결돼 있는 세션들을 모아둔다. 메시지를 "같은 방의 누구에게" 보낼지 찾는 용도.
    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    // 연결이 맺어진 직후 호출된다. 이 시점에 ChatHandshakeInterceptor가 이미 인증/인가를 끝내고
    // attributes에 userId·role을 넣어둔 상태라, 여기서는 그 방의 세션 목록에 등록만 하면 된다.
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String roomId = extractRoomId(session);
        // 요청 경로에서 받은 방 번호를 보관해 메시지와 연결 종료 때 재사용한다.
        session.getAttributes().put("roomId", roomId);
        rooms.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("채팅 연결: sessionId={}, roomId={}, userId={}", session.getId(), roomId,
                session.getAttributes().get("userId"));
    }

    // 클라이언트가 메시지를 보낼 때마다 호출된다. (연결을 끊고 싶을 때 보내는 "quit"만 예외로 처리)
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if ("quit".equals(message.getPayload())) {
            closeQuietly(session);
            return;
        }

        // message -> json변환 통해서 객체 만들어서 userId, roomIdList
        // roomIdList 통해서 -> websocket Session 메모리내에 있는것들 제거
        // return.

        // 핸드셰이크 때 넣어둔 값. 둘 중 하나라도 없으면(=인증 안 된 세션) 조용히 무시한다.
        String roomId = (String) session.getAttributes().get("roomId");
        Long senderId = (Long) session.getAttributes().get("userId");
        if (roomId == null || senderId == null) {
            return;
        }

        // 연결된 이후에 로그아웃했거나 다른 기기에서 로그인해 이 로그인 세션이 끝났을 수 있으니,
        // 메시지를 처리하기 전에 다시 한번 "현재 세션"인지 확인한다. 핸드셰이크 때 한 번만 확인하고 끝내면
        // 로그아웃해도 이미 연결된 소켓으로는 계속 송수신이 가능해지기 때문이다.
        if (!isSessionStillValid(session)) {
            sendError(session, "SESSION_ENDED", "로그인이 종료되어 더 이상 채팅을 이용할 수 없습니다.");
            closeQuietly(session);
            return;
        }

        // 1. 클라이언트가 보낸 JSON을 {"content": "..."} 형태로 해석한다.
        String content;
        try {
            content = objectMapper.readValue(message.getPayload(), IncomingMessage.class).content();
        } catch (RuntimeException e) {
            sendError(session, "INVALID_PAYLOAD", "메시지 형식이 올바르지 않습니다.");
            return;
        }

        // 2. 저장은 REST와 같은 검증 로직(빈 내용 금지, 종료된 방 금지 등)을 그대로 쓴다.
        ChatMessageDto saved;
        try {
            saved = chatService.sendMessage(Long.valueOf(roomId), senderId, content);
        } catch (ApiException e) {
            sendError(session, e.getCode(), e.getMessage());
            return;
        }

        // 3. 저장에 성공한 메시지만 상대방에게 실시간으로 전달한다.
        deliverToCounterpart(roomId, senderId, saved);
    }

    // 저장된 메시지를 같은 방의 "상대방"(발신자 본인이 아닌 세션)에게 전달하고, 실제로 전달됐을 때만 읽음 처리한다.
    private void deliverToCounterpart(String roomId, Long senderId, ChatMessageDto saved) {
        Set<WebSocketSession> roomSessions = rooms.get(roomId);
        if (roomSessions == null) {
            return;
        }
        for (WebSocketSession recipient : roomSessions) {
            Long recipientId = (Long) recipient.getAttributes().get("userId");
            if (recipientId == null || recipientId.equals(senderId)) {
                continue; // 발신자 본인 세션(다른 탭 등)에는 다시 보내지 않는다.
            }
            // 상대의 로그인 세션이 이미 끝났다면(로그아웃·다른 기기 로그인) 그 연결을 정리하고 넘어간다.
            if (!isSessionStillValid(recipient)) {
                closeQuietly(recipient);
                continue;
            }
            // 전송에 실패하면(끊기는 중인 연결 등) 그 메시지는 읽음 처리하지 않는다 — 상대가 실제로 받지 못했으므로.
            if (sendMessage(recipient, saved)) {
                markAsReadQuietly(Long.valueOf(roomId), recipientId, (String) recipient.getAttributes().get("role"));
            }
        }
    }

    // 세션에 저장해둔 userId·sessionId가 Redis의 "현재 로그인 세션"과 여전히 일치하는지 확인한다.
    private boolean isSessionStillValid(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        String sessionId = (String) session.getAttributes().get("sessionId");
        return userId != null && sessionId != null && loginSessionRepository.isCurrentSession(userId, sessionId);
    }

    // 회원용/관리자용 읽음 처리 메서드가 따로 있어서 role로 분기한다. 실시간 알림용 부가 처리라 실패해도 메시지 전달 자체는 막지 않는다.
    private void markAsReadQuietly(Long roomId, Long recipientId, String recipientRole) {
        try {
            if ("ADMIN".equals(recipientRole)) {
                chatService.markAsReadForAdmin(roomId, recipientId);
            } else {
                chatService.markAsRead(roomId, recipientId);
            }
        } catch (ApiException e) {
            log.warn("실시간 읽음 처리 실패: roomId={}, recipientId={}, code={}", roomId, recipientId, e.getCode());
        }
    }

    // 정상적으로 전달된 메시지. 클라이언트는 {"type":"MESSAGE","message":{...ChatMessageDto...}} 형태로 받는다.
    // 실제로 전송됐는지 호출한 쪽(deliverToCounterpart)이 알 수 있도록 성공 여부를 돌려준다.
    private boolean sendMessage(WebSocketSession session, ChatMessageDto message) {
        return send(session, Map.of("type", "MESSAGE", "message", message));
    }

    // 저장이 실패했을 때(종료된 방 등). 상대방에게는 보내지 않고, 보낸 사람 본인에게만 알려준다.
    private void sendError(WebSocketSession session, String code, String errorMessage) {
        send(session, Map.of("type", "ERROR", "code", code, "message", errorMessage));
    }

    // 상대가 연결을 끊었거나 네트워크가 잠깐 끊긴 경우 전송이 실패할 수 있다. 실패하면 로그만 남기고
    // false를 돌려준다(그 세션은 곧 afterConnectionClosed로 정리된다) — 호출한 쪽이 성공 여부로 후속 처리를 판단한다.
    private boolean send(WebSocketSession session, Object payload) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            return true;
        } catch (IOException e) {
            log.warn("채팅 이벤트 전송 실패: sessionId={}", session.getId(), e);
            return false;
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException e) {
            log.warn("채팅 연결 종료 실패: sessionId={}", session.getId(), e);
        }
    }

    // 로그아웃한 회원이 열어둔 상담방을 DB에서 먼저 찾고, 그 방에 연결된 해당 유저의 세션만 닫는다.
    // 실제 제거는 session.close()가 트리거하는 afterConnectionClosed가 처리하므로 여기서 Set을 직접 건드리지 않는다.
    public void closeSessionsForUser(Long userId) {
        //해당 방 객체를 찾아서 가져옴
        ChatRoomDto room = chatService.findOpenRoom(userId);
        if (room == null) {
            return;
        }
        // roomSessions는 해당 websocket세션에 있는 방정보를 가져옴
        Set<WebSocketSession> roomSessions = rooms.get(String.valueOf(room.getRoomId()));
        if (roomSessions == null) {
            return;
        }
        // 해당 방의 세션을 손회하면서 각 세션의 userId와 로그아웃한 userId를 대조해 일치하면 session.close호출
        for (WebSocketSession session : roomSessions) {
            if (userId.equals(session.getAttributes().get("userId"))) {
                try {
                    //
                    session.close();
                }catch (IOException e) {
                    // 이 close가 죽은 소켓애 대해 실행돼 서버가 멈추지 않게 하기 위한 장치
                    log.warn("로그아웃 : sessionId={}", session.getId(), e);
                }
            }
        }
    }

    // 연결이 끊길 때(클라이언트가 닫거나, 네트워크가 끊기거나) 호출된다. 더 이상 메시지를 보낼 대상이 아니므로 방 목록에서 뺀다.
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = (String) session.getAttributes().get("roomId");
        Set<WebSocketSession> roomSessions = rooms.get(roomId);
        if (roomSessions == null) {
            return;
        }
        roomSessions.remove(session);
        if (roomSessions.isEmpty()) {
            rooms.remove(roomId); // 그 방에 아무도 없으면 Map에서도 통째로 지워 메모리를 늘리지 않는다.
        }
        log.debug("채팅 종료: sessionId={}, roomId={}", session.getId(), roomId);
    }

    // 연결 경로("/ws/chat/10")에서 맨 끝 숫자만 방 번호로 꺼낸다. (ChatHandshakeInterceptor도 같은 경로에서 독립적으로 roomId를 뽑는다)
    private String extractRoomId(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
