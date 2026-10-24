package com.kh.back.chat.config;

import com.kh.back.chat.handler.ChatHandshakeInterceptor;
import com.kh.back.chat.handler.ChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// 채팅 WebSocket 엔드포인트("/ws/chat/{roomId}")를 등록한다.
// 연결 요청이 오면 먼저 chatHandshakeInterceptor가 로그인·방 소유자 여부를 확인하고(거절되면 여기서 끝),
// 통과한 연결만 chatHandler(ChatWebSocketHandler)로 넘어가 실제 메시지 송수신을 처리한다.
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatHandler;
    private final ChatHandshakeInterceptor chatHandshakeInterceptor;

    public WebSocketConfig(ChatWebSocketHandler chatHandler, ChatHandshakeInterceptor chatHandshakeInterceptor) {
        this.chatHandler = chatHandler;
        this.chatHandshakeInterceptor = chatHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler, "/ws/chat/{roomId}")
               .addInterceptors(chatHandshakeInterceptor)
                .setAllowedOrigins("*"); // 개발용. 운영에서는 도메인 지정
    }
}
