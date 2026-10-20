package com.kh.back.chat.service;

import com.kh.back.chat.dto.ChatMessageDto;
import com.kh.back.chat.dto.ChatRoomDto;
import com.kh.back.chat.dto.ChatRoomSummaryDto;
import com.kh.back.chat.mapper.ChatMapper;
import com.kh.back.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMapper chatMapper;

    private static final Set<String> VALID_ROOM_STATUSES = Set.of("WAITING", "IN_PROGRESS", "CLOSED");
    private static final int MAX_MESSAGE_LENGTH = 1000;

    @Override
    @Transactional
    public ChatRoomDto openOrCreateRoom(Long userId) {
        ChatRoomDto openRoom = chatMapper.findOpenRoomByUserId(userId);
        if (openRoom != null) {
            return openRoom;
        }
        // 종료된 방만 있거나 방이 없는 경우. 상담 시간과 무관하게 대기 방을 새로 만든다.
        ChatRoomDto newRoom = ChatRoomDto.builder().userId(userId).status("WAITING").build();
        chatMapper.insertRoom(newRoom);
        return newRoom;
    }

    @Override
    public ChatRoomDto findOpenRoom(Long userId) {
        return chatMapper.findOpenRoomByUserId(userId);
    }

    @Override
    @Transactional
    public ChatMessageDto sendMessage(Long roomId, Long senderId, String content) {
        if (content == null || content.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "메시지 내용을 입력해 주세요.");
        }
        String trimmedContent = content.trim();
        if (trimmedContent.length() > MAX_MESSAGE_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "메시지는 최대 " + MAX_MESSAGE_LENGTH + "자까지 보낼 수 있습니다.");
        }
        ChatRoomDto room = requireRoom(roomId);
        // 방 소유자(상담 요청 회원) 또는 배정된 관리자만 메시지를 보낼 수 있다.
        if (!senderId.equals(room.getUserId()) && !senderId.equals(room.getAdminId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "본인 상담방에서만 메시지를 보낼 수 있습니다.");
        }
        // 종료된 방에는 회원·관리자 모두 메시지를 보낼 수 없다.
        if ("CLOSED".equals(room.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "CHAT_ROOM_CLOSED", "종료된 상담방에는 메시지를 보낼 수 없습니다.");
        }

        // 상담 시간과 무관하게 저장하므로 시간대 검증은 하지 않는다.
        ChatMessageDto message = new ChatMessageDto();
        message.setRoomId(roomId);
        message.setSenderId(senderId);
        message.setContent(trimmedContent);
        chatMapper.insertMessage(message);
        // CREATED_AT은 DB 기본값(CURRENT_TIMESTAMP)으로 채워져서 insert 직후의 message 객체에는 없다. 다시 조회해서 채운다.
        return chatMapper.findMessageById(message.getMessageId());
    }

    @Override
    @Transactional
    public ChatMessageDto sendMessageAsMember(Long userId, String content) {
        ChatRoomDto room = openOrCreateRoom(userId);
        return sendMessage(room.getRoomId(), userId, content);
    }

    @Override
    public List<ChatMessageDto> getMessages(Long roomId, Long requesterId) {
        requireOwnRoom(roomId, requesterId);
        return chatMapper.findMessagesByRoomId(roomId);
    }

    @Override
    @Transactional
    public void markAsRead(Long roomId, Long requesterId) {
        requireOwnRoom(roomId, requesterId);
        chatMapper.markMessagesAsRead(roomId, requesterId);
    }

    @Override
    public long getUnreadCount(Long roomId, Long requesterId) {
        requireOwnRoom(roomId, requesterId);
        return chatMapper.countUnreadByRoomId(roomId, requesterId);
    }

    @Override
    public List<ChatRoomSummaryDto> getRoomsByStatus(String status) {
        if (!VALID_ROOM_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "status는 WAITING/IN_PROGRESS/CLOSED 중 하나여야 합니다.");
        }
        return chatMapper.findRoomsByStatus(status);
    }

    @Override
    public ChatRoomSummaryDto getRoomSummary(Long roomId) {
        ChatRoomSummaryDto summary = chatMapper.findRoomSummaryById(roomId);
        if (summary == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다.");
        }
        return summary;
    }

    @Override
    public long getWaitingCount() {
        return chatMapper.countWaitingRooms();
    }

    @Override
    @Transactional
    public ChatRoomDto enterRoom(Long roomId, Long adminId) {
        requireRoom(roomId);
        int updated = chatMapper.claimRoom(roomId, adminId);
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ROOM_ALREADY_CLAIMED",
                    "이미 다른 상담으로 처리 중이거나 대기 상태가 아닙니다.");
        }
        return chatMapper.findRoomById(roomId);
    }

    @Override
    @Transactional
    public void closeRoom(Long roomId, Long adminId) {
        requireRoom(roomId);
        int updated = chatMapper.closeRoom(roomId, adminId);
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ROOM_NOT_CLOSABLE",
                    "담당 중인 진행중 상담만 종료할 수 있습니다.");
        }
    }

    @Override
    public List<ChatMessageDto> getMessagesForAdmin(Long roomId, Long adminId) {
        requireAdminRoom(roomId, adminId);
        return chatMapper.findMessagesByRoomId(roomId);
    }

    @Override
    @Transactional
    public void markAsReadForAdmin(Long roomId, Long adminId) {
        requireAdminRoom(roomId, adminId);
        chatMapper.markMessagesAsRead(roomId, adminId);
    }

    @Override
    public long getUnreadCountForAdmin(Long roomId, Long adminId) {
        requireAdminRoom(roomId, adminId);
        return chatMapper.countUnreadByRoomId(roomId, adminId);
    }

    @Override
    public long getTotalUnreadCountForAdmin(Long adminId) {
        return chatMapper.countUnreadForAdmin(adminId);
    }

    private ChatRoomDto requireRoom(Long roomId) {
        ChatRoomDto room = chatMapper.findRoomById(roomId);
        if (room == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CHAT_ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다.");
        }
        return room;
    }

    // 회원 전용 API용 소유자 검증. 배정 관리자는 이 범위에서 다루지 않는다.
    private ChatRoomDto requireOwnRoom(Long roomId, Long requesterId) {
        ChatRoomDto room = requireRoom(roomId);
        if (!requesterId.equals(room.getUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "본인 상담방만 조회할 수 있습니다.");
        }
        return room;
    }

    // 관리자 전용 API용 소유자 검증. 담당 관리자 본인의 방인지 확인한다.
    private ChatRoomDto requireAdminRoom(Long roomId, Long adminId) {
        ChatRoomDto room = requireRoom(roomId);
        if (!adminId.equals(room.getAdminId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "담당 관리자만 조회할 수 있습니다.");
        }
        return room;
    }
}
