package com.kh.back.chat.handler;

import com.kh.back.auth.security.LoginSessionRepository;
import com.kh.back.chat.dto.ChatRoomDto;
import com.kh.back.chat.mapper.ChatMapper;
import com.kh.back.common.security.JwtTokenProvider;
import com.kh.back.user.dto.UserDto;
import com.kh.back.user.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 채팅 WebSocket(`/ws/chat/{roomId}`)은 연결(핸드셰이크) 시점에 딱 한 번만 이 인터셉터를 거친다.
 * 역할은 두 가지뿐이다.
 *   1) REST API와 같은 방식(JWT accessToken)으로 로그인 여부를 확인한다.
 *   2) 그 사람이 "그 방"의 회원 본인이거나 담당 관리자인지 확인한다. 둘 다 아니면 연결 자체를 끊어버린다.
 * 통과하면 확인된 userId/role을 WebSocketSession에 넘겨서, 이후 ChatWebSocketHandler가 매번 다시 인증하지 않고 재사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    // 요청 경로("/ws/chat/10")의 마지막 숫자(roomId)만 뽑아내는 패턴
    private static final Pattern ROOM_ID_PATTERN = Pattern.compile("/ws/chat/(\\d+)$");

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final LoginSessionRepository loginSessionRepository;
    private final ChatMapper chatMapper;

    // true를 반환해야 실제 WebSocket 연결(업그레이드)이 이루어진다. false면 그대로 거절된다.
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 쿠키를 읽으려면 서블릿 기반 요청이어야 한다. (다른 서버 환경이면 애초에 발생하지 않음)
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        // 1. 경로에서 방 번호를 못 뽑으면 애초에 잘못된 요청
        Long roomId = extractRoomId(request.getURI().getPath());
        if (roomId == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        // 2. 로그인 여부 확인 (REST의 JwtAuthenticationFilter와 동일한 기준)
        AuthenticatedUser authenticated = authenticate(servletRequest.getServletRequest());
        if (authenticated == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        Long userId = authenticated.userId();

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
        // sessionId(sid)도 함께 넘겨서, 연결된 이후에도 로그아웃·다른 기기 로그인으로 이 로그인 세션이
        // 끊겼는지 핸들러가 다시 확인할 수 있게 한다.
        attributes.put("userId", userId);
        attributes.put("sessionId", authenticated.sessionId());
        attributes.put("role", userId.equals(room.getUserId()) ? "MEMBER" : "ADMIN");
        return true;
    }



    // 인증된 사용자 번호와, 그 인증이 어느 로그인 세션(sid)에서 비롯됐는지를 함께 담는다.
    private record AuthenticatedUser(Long userId, String sessionId) {}

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // 연결 이후 처리할 것이 없다.
    }

    // REST의 JwtAuthenticationFilter와 같은 기준으로 검증한다. 통과하면 userId·sessionId, 아니면 null.
    private AuthenticatedUser authenticate(HttpServletRequest request) {
        // 토큰이 없거나 서명·만료가 잘못됐으면 즉시 실패
        String accessToken = resolveAccessToken(request);
        if (accessToken == null || !jwtTokenProvider.validateToken(accessToken)) {
            return null;
        }

        // access 타입 토큰이어야 하고, Redis에 기록된 "현재 로그인 세션"과 일치해야 한다.
        // (다른 기기에서 로그인해서 이전 세션이 끊긴 경우 여기서 걸러진다.)
        Long userId = jwtTokenProvider.getUserId(accessToken);
        String sessionId = jwtTokenProvider.getSessionId(accessToken);
        if (!jwtTokenProvider.isTokenType(accessToken, "access")
                || !loginSessionRepository.isCurrentSession(userId, sessionId)) {
            return null;
        }

        // 토큰은 유효해도 그 사이 탈퇴·정지된 계정일 수 있으니 현재 계정 상태까지 확인
        UserDto userDto = userService.getUserById(userId);
        if (userDto == null || !"ACTIVE".equals(userDto.getUserStatus())) {
            return null;
        }
        return new AuthenticatedUser(userId, sessionId);
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

    // Authorization 헤더가 있으면 그걸 쓰고, 없으면 쿠키의 accessToken을 쓴다.
    // (브라우저 WebSocket API는 커스텀 헤더를 못 보내서, 실제로는 거의 항상 쿠키 쪽이 쓰인다.)
    private String resolveAccessToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
