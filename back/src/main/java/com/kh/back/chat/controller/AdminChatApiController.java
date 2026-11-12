package com.kh.back.chat.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chat.dto.ChatMessageDto;
import com.kh.back.chat.dto.ChatRoomDto;
import com.kh.back.chat.dto.ChatRoomPageDto;
import com.kh.back.chat.dto.ChatRoomSummaryDto;
import com.kh.back.chat.service.ChatService;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.dto.CursorMeta;
import lombok.RequiredArgsConstructor;
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

    // 상태별 상담방 목록 커서 페이징 조회 (WAITING/IN_PROGRESS/CLOSED, 무한스크롤)
    // cursorId: 이전 응답 meta.nextCursor 값. 없으면 처음부터 조회한다.
    @GetMapping("/rooms")
    public ApiResponse<List<ChatRoomSummaryDto>> getRooms(@RequestParam String status,
                                                            @RequestParam(required = false) Long cursorId,
                                                            @RequestParam(required = false) Integer size) {
        ChatRoomPageDto page = chatService.getRoomsByStatus(status, cursorId, size);
        return ApiResponse.ok(page.getItems(), new CursorMeta(page.isHasMore(), page.getNextCursor()));
    }

    // 대기 상담방 수 (대시보드)
    @GetMapping("/rooms/waiting-count")
    public ApiResponse<Long> getWaitingCount() {
        return ApiResponse.ok(chatService.getWaitingCount());
    }

    // 방 단건 요약 조회. 새로고침 후 화면에서 선택했던 방을 복원할 때 쓴다.
    @GetMapping("/rooms/{roomId}")
    public ApiResponse<ChatRoomSummaryDto> getRoom(@PathVariable Long roomId) {
        return ApiResponse.ok(chatService.getRoomSummary(roomId));
    }

    // 입장: 대기 -> 진행중, 담당 관리자로 기록. 이미 처리 중이면 409
    @PostMapping("/rooms/{roomId}/enter")
    public ApiResponse<ChatRoomDto> enterRoom(@PathVariable Long roomId,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(chatService.enterRoom(roomId, principal.getUserId()));
    }

    // 상담 종료
    @PostMapping("/rooms/{roomId}/close")
    public ApiResponse<Void> closeAdminRoom(@PathVariable Long roomId,
                                        @AuthenticationPrincipal CustomUserDetails principal) {
        chatService.closeAdminRoom(roomId, principal.getUserId());
        return ApiResponse.ok(null);
    }

    // 관리자 답변 전송
    @PostMapping("/rooms/{roomId}/messages")
    public ApiResponse<ChatMessageDto> sendMessage(@PathVariable Long roomId,
                                                    @RequestBody SendAdminMessageRequest request,
                                                    @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(chatService.sendMessage(roomId, principal.getUserId(), request.content()));
    }

    // 담당 중인 방의 메시지 목록 조회
    @GetMapping("/rooms/{roomId}/messages")
    public ApiResponse<List<ChatMessageDto>> getMessages(@PathVariable Long roomId,
                                                           @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(chatService.getMessagesForAdmin(roomId, principal.getUserId()));
    }

    // 담당 중인 방에서 회원이 보낸 메시지를 읽음 처리
    @PatchMapping("/rooms/{roomId}/messages/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long roomId,
                                         @AuthenticationPrincipal CustomUserDetails principal) {
        chatService.markAsReadForAdmin(roomId, principal.getUserId());
        return ApiResponse.ok(null);
    }

    // 담당 중인 방의 안 읽은 메시지 수
    @GetMapping("/rooms/{roomId}/unread-count")
    public ApiResponse<Long> getUnreadCount(@PathVariable Long roomId,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(chatService.getUnreadCountForAdmin(roomId, principal.getUserId()));
    }

    // 담당 중인 모든 진행중 방의 안 읽은 메시지 수 합계 (대시보드)
    @GetMapping("/unread-count")
    public ApiResponse<Long> getTotalUnreadCount(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(chatService.getTotalUnreadCountForAdmin(principal.getUserId()));
    }
}
