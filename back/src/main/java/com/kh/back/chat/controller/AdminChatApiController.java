package com.kh.back.chat.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chat.dto.ChatMessageDto;
import com.kh.back.chat.dto.ChatRoomDto;
import com.kh.back.chat.dto.ChatRoomSummaryDto;
import com.kh.back.chat.service.ChatService;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 접근 권한(관리자만 허용)은 SecurityConfig의 hasRole("ADMIN")이 처리한다.
@RestController
@RequestMapping("/api/v1/admin/chat")
@RequiredArgsConstructor
public class AdminChatApiController {

    private final ChatService chatService;

    public record SendAdminMessageRequest(String content) {}

    // 상태별 상담방 목록 (WAITING/IN_PROGRESS/CLOSED)
    @GetMapping("/rooms")
    public ApiResponse<List<ChatRoomSummaryDto>> getRooms(@RequestParam String status,
                                                            @AuthenticationPrincipal CustomUserDetails principal) {
        requireAdminId(principal);
        return ApiResponse.ok(chatService.getRoomsByStatus(status));
    }

    // 대기 상담방 수 (대시보드)
    @GetMapping("/rooms/waiting-count")
    public ApiResponse<Long> getWaitingCount(@AuthenticationPrincipal CustomUserDetails principal) {
        requireAdminId(principal);
        return ApiResponse.ok(chatService.getWaitingCount());
    }

    // 방 단건 요약 조회. 새로고침 후 화면에서 선택했던 방을 복원할 때 쓴다.
    @GetMapping("/rooms/{roomId}")
    public ApiResponse<ChatRoomSummaryDto> getRoom(@PathVariable Long roomId,
                                                    @AuthenticationPrincipal CustomUserDetails principal) {
        requireAdminId(principal);
        return ApiResponse.ok(chatService.getRoomSummary(roomId));
    }

    // 입장: 대기 -> 진행중, 담당 관리자로 기록. 이미 처리 중이면 409
    @PostMapping("/rooms/{roomId}/enter")
    public ApiResponse<ChatRoomDto> enterRoom(@PathVariable Long roomId,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        Long adminId = requireAdminId(principal);
        return ApiResponse.ok(chatService.enterRoom(roomId, adminId));
    }

    // 상담 종료
    @PostMapping("/rooms/{roomId}/close")
    public ApiResponse<Void> closeRoom(@PathVariable Long roomId,
                                        @AuthenticationPrincipal CustomUserDetails principal) {
        Long adminId = requireAdminId(principal);
        chatService.closeRoom(roomId, adminId);
        return ApiResponse.ok(null);
    }

    // 관리자 답변 전송
    @PostMapping("/rooms/{roomId}/messages")
    public ApiResponse<ChatMessageDto> sendMessage(@PathVariable Long roomId,
                                                    @RequestBody SendAdminMessageRequest request,
                                                    @AuthenticationPrincipal CustomUserDetails principal) {
        Long adminId = requireAdminId(principal);
        return ApiResponse.ok(chatService.sendMessage(roomId, adminId, request.content()));
    }

    // 담당 중인 방의 메시지 목록 조회
    @GetMapping("/rooms/{roomId}/messages")
    public ApiResponse<List<ChatMessageDto>> getMessages(@PathVariable Long roomId,
                                                           @AuthenticationPrincipal CustomUserDetails principal) {
        Long adminId = requireAdminId(principal);
        return ApiResponse.ok(chatService.getMessagesForAdmin(roomId, adminId));
    }

    // 담당 중인 방에서 회원이 보낸 메시지를 읽음 처리
    @PatchMapping("/rooms/{roomId}/messages/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long roomId,
                                         @AuthenticationPrincipal CustomUserDetails principal) {
        Long adminId = requireAdminId(principal);
        chatService.markAsReadForAdmin(roomId, adminId);
        return ApiResponse.ok(null);
    }

    // 담당 중인 방의 안 읽은 메시지 수
    @GetMapping("/rooms/{roomId}/unread-count")
    public ApiResponse<Long> getUnreadCount(@PathVariable Long roomId,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        Long adminId = requireAdminId(principal);
        return ApiResponse.ok(chatService.getUnreadCountForAdmin(roomId, adminId));
    }

    // 담당 중인 모든 진행중 방의 안 읽은 메시지 수 합계 (대시보드)
    @GetMapping("/unread-count")
    public ApiResponse<Long> getTotalUnreadCount(@AuthenticationPrincipal CustomUserDetails principal) {
        Long adminId = requireAdminId(principal);
        return ApiResponse.ok(chatService.getTotalUnreadCountForAdmin(adminId));
    }

    private Long requireAdminId(CustomUserDetails principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }
        return principal.getUserId();
    }
}
