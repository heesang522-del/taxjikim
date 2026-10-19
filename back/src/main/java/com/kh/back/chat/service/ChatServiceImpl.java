package com.kh.back.chat.service;

import com.kh.back.chat.dto.ChatMessageDto;
import com.kh.back.chat.dto.ChatRoomDto;
import com.kh.back.chat.mapper.ChatMapper;
import com.kh.back.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMapper chatMapper;

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
    @Transactional
    public ChatMessageDto sendMessage(Long roomId, Long senderId, String content) {
        if (content == null || content.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "메시지 내용을 입력해 주세요.");
        }
        ChatRoomDto room = requireRoom(roomId);
        // 방 소유자(상담 요청 회원) 또는 배정된 관리자만 메시지를 보낼 수 있다.
        if (!senderId.equals(room.getUserId()) && !senderId.equals(room.getAdminId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "본인 상담방에서만 메시지를 보낼 수 있습니다.");
        }

        // 상담 시간과 무관하게 저장하므로 시간대 검증은 하지 않는다.
        ChatMessageDto message = new ChatMessageDto();
        message.setRoomId(roomId);
        message.setSenderId(senderId);
        message.setContent(content.trim());
        chatMapper.insertMessage(message);
        return message;
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
}
