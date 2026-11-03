package com.kh.back.chat.service;

import com.kh.back.chat.dto.ChatMessageDto;
import com.kh.back.chat.dto.ChatRoomDto;
import com.kh.back.chat.dto.ChatRoomPageDto;
import com.kh.back.chat.dto.ChatRoomSummaryDto;
import com.kh.back.chat.event.ChatRoomClosedEvent;
import com.kh.back.chat.mapper.ChatMapper;
import com.kh.back.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMapper chatMapper;
    private final ApplicationEventPublisher eventPublisher;

    private static final Set<String> VALID_ROOM_STATUSES = Set.of("WAITING", "IN_PROGRESS", "CLOSED");
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int DEFAULT_ROOM_PAGE_SIZE = 10; // size 파라미터가 없을 때 기본 페이지 크기
    private static final int MAX_ROOM_PAGE_SIZE = 50; // 클라이언트가 요청할 수 있는 최대 페이지 크기
    private static final String ADMIN_CLOSE_NOTICE_CONTENT = "관리자가 상담을 종료하였습니다.";
    private static final String MEMBER_CLOSE_NOTICE_CONTENT = "회원이 상담을 종료하였습니다.";

    @Override
    @Transactional
    public ChatRoomDto openOrCreateRoom(Long userId) {
        ChatRoomDto openRoom = chatMapper.findOpenRoomByUserId(userId);
        if (openRoom != null) {
            return openRoom;
        }
        // 종료된 방만 있거나 방이 없는 경우. 상담 시간과 무관하게 대기 방을 새로 만든다.
        ChatRoomDto newRoom = ChatRoomDto.builder().userId(userId).status("WAITING").build();
        try {
            chatMapper.insertRoom(newRoom);
        } catch (DuplicateKeyException e) {
            // 위의 조회와 이 INSERT 사이에 동시에 들어온 다른 요청이 먼저 방을 만든 경우.
            // CHAT_ROOMS.OPEN_USER_ID 유니크 제약(유저당 열린 방 1개)이 막아준 것이므로, 그 방을 그대로 반환한다.
            // 일반 조회(findOpenRoomByUserId)로는 REPEATABLE READ 스냅샷 때문에 상대가 방금 커밋한 방이
            // 안 보일 수 있어, FOR UPDATE로 최신 커밋 데이터를 다시 읽는다.
            return chatMapper.findOpenRoomByUserIdForUpdate(userId);
        }
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
    public ChatRoomPageDto getRoomsByStatus(String status, Long cursorId, Integer size) {
        if (!VALID_ROOM_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "status는 WAITING/IN_PROGRESS/CLOSED 중 하나여야 합니다.");
        }
        int pageSize = resolveRoomPageSize(size);
        // 다음 페이지가 있는지 알아보려고 한 건 더 가져온 뒤, 넘치면 마지막 한 건을 떼어내고 hasMore로 알린다.
        List<ChatRoomSummaryDto> fetched = chatMapper.findRoomsByStatus(status, cursorId, pageSize + 1);
        boolean hasMore = fetched.size() > pageSize;
        List<ChatRoomSummaryDto> items = hasMore ? fetched.subList(0, pageSize) : fetched;
        Long nextCursor = hasMore ? items.get(items.size() - 1).getRoomId() : null;
        return new ChatRoomPageDto(items, hasMore, nextCursor);
    }

    // size가 비어있으면 기본값을, 범위를 벗어나면 1~MAX_ROOM_PAGE_SIZE로 잘라낸다.
    private int resolveRoomPageSize(Integer size) {
        if (size == null) {
            return DEFAULT_ROOM_PAGE_SIZE;
        }
        return Math.min(Math.max(size, 1), MAX_ROOM_PAGE_SIZE);
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
    public void closeAdminRoom(Long roomId, Long adminId) {
        requireRoom(roomId);
        int updated = chatMapper.closeAdminRoom(roomId, adminId);
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ROOM_NOT_CLOSABLE",
                    "담당 중인 진행중 상담만 종료할 수 있습니다.");
        }
        insertCloseNotice(roomId, adminId, ADMIN_CLOSE_NOTICE_CONTENT);
    }

    @Override
    @Transactional
    public void closeUserRoom(Long roomId, Long userId) {
        requireRoom(roomId);
        int updated = chatMapper.closeUserRoom(roomId, userId);
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ROOM_NOT_CLOSABLE",
                    "본인의 상담만 종료할 수 있습니다.");
        }
        insertCloseNotice(roomId, userId, MEMBER_CLOSE_NOTICE_CONTENT);
    }

    // 종료 직후 "(회원/관리자)가 상담을 종료하였습니다." 안내를 채팅방 메시지로 남겨 누가 종료했는지 알 수 있게 한다.
    // 발신자는 종료한 쪽으로 기록해, 화면에서 상대 쪽 메시지와 같은 방식(정렬·"상담사 ·" 표시)으로도 구분된다.
    // 종료한 사람 본인은 REST 응답으로 바로 반영하지만, 상대방은 다음 전송 시도나 목록 폴링 전까지 모르는 문제가
    // 있었다. ChatWebSocketHandler가 이 이벤트를 받아 상대방에게 실시간으로 전달한다(둘이 서로 직접 참조하면
    // 순환 의존이 생기므로 이벤트로 느슨하게 연결). 커밋 전에 전달되면 상대가 받고 다시 조회해도 아직 안 보일 수
    // 있어, 이 트랜잭션이 실제로 커밋된 뒤에만 전달되도록 ChatRoomClosedEvent 리스너 쪽에서 AFTER_COMMIT으로 받는다.
    private void insertCloseNotice(Long roomId, Long closerId, String content) {
        ChatMessageDto notice = new ChatMessageDto();
        notice.setRoomId(roomId);
        notice.setSenderId(closerId);
        notice.setContent(content);
        chatMapper.insertMessage(notice);
        ChatMessageDto savedNotice = chatMapper.findMessageById(notice.getMessageId());
        eventPublisher.publishEvent(new ChatRoomClosedEvent(roomId, closerId, savedNotice));
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
