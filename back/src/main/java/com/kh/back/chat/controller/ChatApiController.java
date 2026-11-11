package com.kh.back.chat.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chat.dto.ChatMessageDto;
import com.kh.back.chat.dto.ChatRoomDto;
import com.kh.back.chat.dto.ChatHistoryRoomDto;
import com.kh.back.chat.service.ChatService;
import com.kh.back.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 이 컨트롤러 경로(/api/v1/chat/**)는 SecurityConfig에서 인증을 요구하므로,
// 여기 도달한 요청은 이미 principal이 채워져 있다. 별도 null 체크가 필요 없다.
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatApiController {

    private final ChatService chatService;

    public record SendMessageRequest(String content) {}

    // 내 열린 방(WAITING/IN_PROGRESS) 조회. 새로 만들지 않으며 없으면 data가 null이다.
    // 회원 화면이 접속·새로고침 시 이 응답으로 기존 방 번호를 얻어 메시지 목록을 불러오고 WebSocket에 연결한다.
    @GetMapping("/rooms/me")
    public ApiResponse<ChatRoomDto> getMyRoom(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(chatService.findOpenRoom(principal.getUserId()));
    }

    @GetMapping("/rooms/me/history")
    public ApiResponse<List<ChatHistoryRoomDto>> getMyHistory(@AuthenticationPrincipal CustomUserDetails principal) {
        // 요청 파라미터 대신 인증된 회원번호로 본인의 종료 상담만 조회한다.
        return ApiResponse.ok(chatService.getClosedRooms(principal.getUserId()));
    }

    // 메시지 전송. 열린 방이 없으면 대기 방을 새로 만들고 그 방에 저장한다.
    @PostMapping("/messages")
    public ApiResponse<ChatMessageDto> sendMessage(@RequestBody SendMessageRequest request,
                                                    @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(chatService.sendMessageAsMember(principal.getUserId(), request.content()));
    }

    // 방의 대화 목록 조회. 본인 방이 아니면 거절된다.
    @GetMapping("/rooms/{roomId}/messages")
    public ApiResponse<List<ChatMessageDto>> getMessages(@PathVariable Long roomId,
                                                           @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(chatService.getMessages(roomId, principal.getUserId()));
    }

    // 상대방이 보낸 메시지를 읽음 처리한다.
    @PatchMapping("/rooms/{roomId}/messages/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long roomId,
                                         @AuthenticationPrincipal CustomUserDetails principal) {
        chatService.markAsRead(roomId, principal.getUserId());
        return ApiResponse.ok(null);
    }

    // 상대방이 보낸 안 읽은 메시지 수 조회
    @GetMapping("/rooms/{roomId}/unread-count")
    public ApiResponse<Long> getUnreadCount(@PathVariable Long roomId,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(chatService.getUnreadCount(roomId, principal.getUserId()));
    }

    @PostMapping("/rooms/{roomId}/close")
    public ApiResponse<Void> closeUserRoom(@PathVariable Long roomId,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        chatService.closeUserRoom(roomId, principal.getUserId());
        return ApiResponse.ok(null);
    }
}
