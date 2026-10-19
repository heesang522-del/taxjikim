package com.kh.back.chat.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chat.dto.ChatMessageDto;
import com.kh.back.chat.service.ChatService;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatApiController {

    private final ChatService chatService;

    public record SendMessageRequest(String content) {}

    // 메시지 전송. 열린 방이 없으면 대기 방을 새로 만들고 그 방에 저장한다.
    @PostMapping("/messages")
    public ApiResponse<ChatMessageDto> sendMessage(@RequestBody SendMessageRequest request,
                                                    @AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = requireUserId(principal);
        return ApiResponse.ok(chatService.sendMessageAsMember(userId, request.content()));
    }

    // 방의 대화 목록 조회. 본인 방이 아니면 거절된다.
    @GetMapping("/rooms/{roomId}/messages")
    public ApiResponse<List<ChatMessageDto>> getMessages(@PathVariable Long roomId,
                                                           @AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = requireUserId(principal);
        return ApiResponse.ok(chatService.getMessages(roomId, userId));
    }

    // 상대방이 보낸 메시지를 읽음 처리한다.
    @PatchMapping("/rooms/{roomId}/messages/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long roomId,
                                         @AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = requireUserId(principal);
        chatService.markAsRead(roomId, userId);
        return ApiResponse.ok(null);
    }

    // 상대방이 보낸 안 읽은 메시지 수 조회
    @GetMapping("/rooms/{roomId}/unread-count")
    public ApiResponse<Long> getUnreadCount(@PathVariable Long roomId,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = requireUserId(principal);
        return ApiResponse.ok(chatService.getUnreadCount(roomId, userId));
    }

    private Long requireUserId(CustomUserDetails principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }
        return principal.getUserId();
    }
}
