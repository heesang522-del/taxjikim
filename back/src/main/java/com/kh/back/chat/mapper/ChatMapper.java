package com.kh.back.chat.mapper;

import com.kh.back.chat.dto.ChatMessageDto;
import com.kh.back.chat.dto.ChatRoomDto;
import com.kh.back.chat.dto.ChatRoomSummaryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMapper {

    // 상담 방 생성 (STATUS는 WAITING으로 시작)
    int insertRoom(ChatRoomDto room);

    // 회원의 열린 방(WAITING/IN_PROGRESS) 1건 조회
    ChatRoomDto findOpenRoomByUserId(@Param("userId") Long userId);

    // 방 단건 조회 (상태 무관). 메시지 저장 시 소유자 검증 등에 사용
    ChatRoomDto findRoomById(@Param("roomId") Long roomId);

    // 채팅 메시지 등록
    int insertMessage(ChatMessageDto message);

    // 방의 전체 메시지 조회 (오래된 순)
    List<ChatMessageDto> findMessagesByRoomId(@Param("roomId") Long roomId);

    // 상대방이 보낸 메시지를 읽음 처리 (본인이 보낸 메시지는 제외)
    int markMessagesAsRead(@Param("roomId") Long roomId, @Param("readerId") Long readerId);

    // 상대방이 보낸 안 읽은 메시지 수 조회
    long countUnreadByRoomId(@Param("roomId") Long roomId, @Param("readerId") Long readerId);

    // 방에 속한 메시지 전체 삭제 (FK 제약으로 방 삭제보다 먼저 호출해야 함)
    int deleteMessagesByRoomId(@Param("roomId") Long roomId);

    // 방 삭제. CHAT_ROOMS/CHAT_MESSAGES에는 DELETED_AT이 없어 소프트 삭제가 아닌 실제 삭제다.
    int deleteRoomById(@Param("roomId") Long roomId);

    // 관리자용: 상태별 상담방 목록 (닉네임·첫 메시지 포함)
    List<ChatRoomSummaryDto> findRoomsByStatus(@Param("status") String status);

    // 대기 상담방 수 (관리자 대시보드)
    long countWaitingRooms();

    // 입장(claim). STATUS가 WAITING일 때만 성공 — 동시 입장 방지용 조건부 UPDATE
    int claimRoom(@Param("roomId") Long roomId, @Param("adminId") Long adminId);

    // 상담 종료. 담당 관리자 본인의 IN_PROGRESS 방일 때만 성공
    int closeRoom(@Param("roomId") Long roomId, @Param("adminId") Long adminId);
}
