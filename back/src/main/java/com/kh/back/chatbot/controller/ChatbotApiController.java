package com.kh.back.chatbot.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto.MessageResponse;
import com.kh.back.chatbot.dto.ChatbotReply;
import com.kh.back.chatbot.dto.ChatbotRequest;
import com.kh.back.chatbot.service.ChatbotService;
import com.kh.back.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chatbot/conversations")
@RequiredArgsConstructor
public class ChatbotApiController {

    private final ChatbotService chatbotService;

    @GetMapping
    public ApiResponse<List<ConversationResponse>> conversations( @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(
                chatbotService.getConversations(principal)
        ); // 로그인한 사용자의 채팅방 목록 조회 요청을 받아, 조회 결과를 응답
    }

    @PostMapping
    public ApiResponse<ConversationResponse> create( @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(
                chatbotService.createConversation(principal)
        );
    } // 로그인한 사용자의 새 채팅방을 만드는 api , 화면에서 새 대화를 누르면 호출

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<MessageResponse>> messages(@AuthenticationPrincipal CustomUserDetails principal,
                                                       @PathVariable("conversationId") Long conversationId) {
        return ApiResponse.ok(
                chatbotService.getMessages(
                        principal,
                        conversationId
                )
        );
    }  //  선택한 체팅방의 질문, 답변 목록을 조회하는 api, 왼쪽에서 채팅방을 선택하면 해당 대화내용을 가져오는 역할

    @PostMapping("/{conversationId}/messages")
    public ApiResponse<ChatbotReply> ask(@AuthenticationPrincipal CustomUserDetails principal,
                                         @PathVariable("conversationId") Long conversationId,
                                         @RequestBody ChatbotRequest request) {
        return ApiResponse.ok(
                chatbotService.ask(
                        principal,
                        conversationId,
                        request.getMessage()
                )
        );
    } // 선택한 채팅방에 질문을 보내고, ai 답변을 받는 api , 사용자가 질문 보내기 를 누르면 호출됨

    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> delete( @AuthenticationPrincipal CustomUserDetails principal,
                                     @PathVariable("conversationId") Long conversationId) {
        // 채팅방 전체 삭제는 유지한다.
        chatbotService.deleteConversation(
                principal,
                conversationId
        );

        return ApiResponse.ok(null);
    }
} // 선택한 채팅방 전체를 삭제하는 api , 왼쪽 대화 목록의 삭제 버튼을 누르면 호출됨