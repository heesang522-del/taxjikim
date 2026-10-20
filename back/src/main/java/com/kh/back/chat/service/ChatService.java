package com.kh.back.chat.service;

import com.kh.back.chat.dto.ChatMessageDto;
import com.kh.back.chat.dto.ChatRoomDto;
import com.kh.back.chat.dto.ChatRoomSummaryDto;

import java.util.List;

public interface ChatService {

    // 회원의 열린 방(WAITING/IN_PROGRESS)이 있으면 그 방을, 없거나 종료된 방뿐이면 새 대기 방을 반환
    ChatRoomDto openOrCreateRoom(Long userId);

    // 방에 메시지를 저장. 빈 내용 검증과 방 소유자(회원 또는 배정 관리자) 검증을 거친다
    ChatMessageDto sendMessage(Long roomId, Long senderId, String content);

    // 회원 기준 메시지 전송: 열린 방이 없으면 새로 만들고 그 방에 저장
    ChatMessageDto sendMessageAsMember(Long userId, String content);

    // 본인 방의 메시지 목록 조회. 본인 방이 아니면 거절
    List<ChatMessageDto> getMessages(Long roomId, Long requesterId);

    // 본인 방에서 상대방이 보낸 메시지를 읽음 처리
    void markAsRead(Long roomId, Long requesterId);

    // 본인 방에서 상대방이 보낸 안 읽은 메시지 수 조회
    long getUnreadCount(Long roomId, Long requesterId);

    // 관리자용: 상태별(WAITING/IN_PROGRESS/CLOSED) 상담방 목록
    List<ChatRoomSummaryDto> getRoomsByStatus(String status);

    // 관리자용: 대기 상담방 수 (대시보드)
    long getWaitingCount();

    // 관리자 입장. 대기 방일 때만 성공하고, 담당 관리자로 기록된다
    ChatRoomDto enterRoom(Long roomId, Long adminId);

    // 상담 종료. 담당 관리자 본인의 진행중 방일 때만 성공
    void closeRoom(Long roomId, Long adminId);

    // 담당 관리자 본인 방의 메시지 목록 조회
    List<ChatMessageDto> getMessagesForAdmin(Long roomId, Long adminId);

    // 담당 관리자 본인 방에서 회원이 보낸 메시지를 읽음 처리
    void markAsReadForAdmin(Long roomId, Long adminId);

    // 담당 관리자 본인 방의 안 읽은 메시지 수 조회
    long getUnreadCountForAdmin(Long roomId, Long adminId);

    // 담당 관리자가 진행중으로 맡고 있는 모든 방의 안 읽은 메시지 수 합계 (대시보드)
    long getTotalUnreadCountForAdmin(Long adminId);
}
