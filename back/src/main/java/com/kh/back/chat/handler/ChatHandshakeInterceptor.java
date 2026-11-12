package com.kh.back.chat.handler;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chat.dto.ChatRoomDto;
import com.kh.back.chat.mapper.ChatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 채팅 WebSocket(`/ws/chat/{roomId}`)은 연결(핸드셰이크) 시점에 딱 한 번만 이 인터셉터를 거친다.
 * 이 요청도 평범한 HTTP 요청이라 먼저 JwtAuthenticationFilter를 거치며, 로그인 여부(서명·만료·세션 유효성·계정 상태)는
 * 그 필터가 이미 확인해 SecurityContextHolder에 채워둔다. 여기서는 그 결과를 그대로 재사용할 뿐, 토큰을 다시 검증하지 않는다.
 * 이 인터셉터의 역할은 하나뿐이다 — 로그인한 사람이 "그 방"의 회원 본인이거나 담당 관리자인지 확인한다. 둘 다 아니면 연결을 끊는다.
 * 통과하면 확인된 userId/role을 WebSocketSession에 넘겨서, 이후 ChatWebSocketHandler가 매번 다시 인증하지 않고 재사용한다.
 * 연결된 이후에 로그아웃하거나 다른 기기에서 재로그인해도 이 소켓은 핸드셰이크 때의 인증을 계속 신뢰한다(재검증하지 않음) — 당장은 의도적으로 남겨둔 제약이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    // 요청 경로("/ws/chat/10")의 마지막 숫자(roomId)만 뽑아내는 패턴
    private static final Pattern ROOM_ID_PATTERN = Pattern.compile("/ws/chat/(\\d+)$");

    private final ChatMapper chatMapper;

    // true를 반환해야 실제 WebSocket 연결(업그레이드)이 이루어진다. false면 그대로 거절된다.
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 1. 경로에서 방 번호를 못 뽑으면 애초에 잘못된 요청
        Long roomId = extractRoomId(request.getURI().getPath());
        if (roomId == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }


        // 매 요청마다 SecurityContextHolder에서 꺼내 써야 하는 "요청별 값"
        // JwtAuthenticationFilter가 토큰을 검증해서 만들어둔 진짜 CustomUserDetails를 다시 가져와야 하는데, 그게 바로
        // SecurityContextHolder에 저장
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails principal = (CustomUserDetails) auth.getPrincipal();
        Long userId = principal.getUserId();


        // 3. 그 방이 실제로 존재하는지 확인
        ChatRoomDto room = chatMapper.findRoomById(roomId);
        if (room == null) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }

        // 4. 로그인한 사람이 "그 방"의 회원 본인이거나 담당 관리자인지 확인. 둘 다 아니면 남의 방이므로 거절
        if (!userId.equals(room.getUserId()) && !userId.equals(room.getAdminId())) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        // 여기 담아두는 값은 그대로 WebSocketSession.getAttributes()로 넘어가서 ChatWebSocketHandler가 그대로 쓴다.
        attributes.put("userId", userId);
        attributes.put("role", userId.equals(room.getUserId()) ? "MEMBER" : "ADMIN");
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // 연결 이후 처리할 것이 없다.
    }

    private Long extractRoomId(String path) {
        Matcher matcher = ROOM_ID_PATTERN.matcher(path);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
