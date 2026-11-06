package com.kh.back.chat.event;

import com.kh.back.chat.dto.ChatMessageDto;

// 상담 종료 트랜잭션이 커밋된 뒤, 종료한 쪽이 아닌 상대방에게 실시간으로 알리기 위한 이벤트.
// ChatServiceImpl(업무 규칙)과 ChatWebSocketHandler(실시간 전송)가 서로를 직접 참조하면 순환 의존이 생기므로,
// Spring의 트랜잭션 이벤트로 느슨하게 연결한다.
public record ChatRoomClosedEvent(Long roomId, Long closerId, ChatMessageDto notice) {
}
