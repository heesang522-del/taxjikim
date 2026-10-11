package com.example.practice.chat;

public class ChatController {

    // websocket : Full-Duplex 연결
    // 1. 연결요청이 들어오면 그 연결에 대해 handshake 검증
    // - http/1.1로 요청이 들어옴
    // - upgrade: websocket 헤더를 통해 TCP연결 유지
    // - websocket 프로토콜 전환
    // spring websocketconfigurer 인터페이스 구현 (웹소켓 엔드포인트 등록을 위해)
    // 2. 연결 유지 됐으니 stream 열어야됨(데이터 송수신용)
    // 3. client에서 메시지를 보냄
    // 4. 서버에서 그에 대한 응답 or 메시지를 돌려줌
    // db에 저장시켜야되나? 그럼 모든 방에 대해 정보를 담고 있는가? 아니면 각 방에 대해 정보를 따로 저장하는가?
    // 그 메시지를 해당 방에 있는 모든 사용자에게 boradcast해줘야함.
    // 연결은 어떻게 끊어야하는가 - 조건 걸기
    //
}
