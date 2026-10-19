package com.kh.back.chat.service;

import com.kh.back.chat.dto.ChatMessageDto;
import com.kh.back.chat.dto.ChatRoomDto;

public interface ChatService {

    // 회원의 열린 방(WAITING/IN_PROGRESS)이 있으면 그 방을, 없거나 종료된 방뿐이면 새 대기 방을 반환
    ChatRoomDto openOrCreateRoom(Long userId);

    // 방에 메시지를 저장. 빈 내용 검증과 방 소유자(회원 또는 배정 관리자) 검증을 거친다
    ChatMessageDto sendMessage(Long roomId, Long senderId, String content);
}
