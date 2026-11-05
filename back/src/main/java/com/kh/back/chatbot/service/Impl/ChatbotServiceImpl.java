package com.kh.back.chatbot.service.Impl;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto;
import com.kh.back.chatbot.dto.ChatbotMessageDto.MessageResponse;
import com.kh.back.chatbot.dto.ChatbotReply;
import com.kh.back.chatbot.service.ChatbotHistoryService;
import com.kh.back.chatbot.service.ChatbotService;
import com.kh.back.chatbot.service.UserContextProvider;
import com.kh.back.chatbot.util.PiiMasker;
import com.kh.back.chatbot.validator.ChatbotValidator;
import com.kh.back.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChatbotServiceImpl implements ChatbotService {

    private static final int CONTEXT_MESSAGES = 10;

    private final ChatClient chatClient;
    private final ChatbotHistoryService historyService;
    private final UserContextProvider userContextProvider;
    private final ChatbotValidator chatbotValidator;
    private final boolean enabled;

    public ChatbotServiceImpl( ChatClient.Builder builder, // ai 요처에 사용할 chatclient를 만드는 도구
            ChatbotHistoryService historyService, // 채팅방 . 메세지 db 처리
            UserContextProvider userContextProvider,   //  ai에 전달할 사용자 관련 정보 구성
            ChatbotValidator chatbotValidator,     // 질문 길이 . 채팅방 번호등 입력 검증
            @Value("classpath:prompts/Chatbot-System.st") // @value로 주입받아 , chatbot-System.st 파일을 읽을수 있는 리소스 객체 받음
            Resource systemPrompt,
            @Value("${spring.ai.openai.api-key:no-key}") // 설정의 spring.ai.openai.api-key값을 가져옴 설정이 없으면 기본값인 no key 를 사용
            String apiKey
    ) {
        this.historyService = historyService;
        this.userContextProvider = userContextProvider;
        this.chatbotValidator = chatbotValidator;

        this.chatClient = builder  // 생성준비
                .defaultSystem(   // ai요청에 사용할 기본 시스템 프롬프트 설정
                        systemPrompt,  // 앞서 주입받은 프롬프트 파일
                        StandardCharsets.UTF_8 // 파일을 utf-8 인코딩으로 읽도록 지정
                )
                .build();    // 주입받은 객체를 필드에 저장하고 , 기본 프롬프트가 설정된 ai 클리아이언트를 만드는 부분

        this.enabled = apiKey != null
                && !apiKey.isBlank()
                && !"no-key".equals(apiKey.trim()); // api 키가 설정되어 있는지 확인하고 그결과를 enabled에 저장

        if (!enabled) {
            log.warn("API 키가 없어 챗봇 호출을 비활성화합니다.");
        }
    }   //  만약 설정되있지 않다면 경고 메세지

    @Override
    public List<ConversationResponse> getConversations(
            CustomUserDetails principal
    ) {
        return historyService.getConversations(
                principal.getUserId()
        );
    }   // 로그인한 사용자 id를 꺼내서 그 사용자의 채팅방 목록을 조회하는 메서드

    @Override
    public ConversationResponse createConversation(
            CustomUserDetails principal
    ) {
        return historyService.createConversation(
                principal.getUserId()
        );
    } // 로그인한 사용자의 새 채팅방을 만들도록 기록 서비스에 요청하고 생성 결과를 반환

    @Override
    public List<MessageResponse> getMessages(
            CustomUserDetails principal,
            Long conversationId
    ) {
        return historyService.getSnapshot( // snapshot : 사진을 찍듯이 그때의 데이터를 묶어 놓는 개념
                        principal.getUserId(),
                        conversationId
                )
                .messages()
                .stream()
                .map(ChatbotMessageDto::toResponse)
                .toList();
    }  // 로그인한 사용자의 특정 채팅방 메세지를 조회하고, 프론트에 전달할 응답 목록으로 변환하는 메서드

    @Override
    public void deleteConversation( // 로그인한 사용자의 채팅방을 삭제하도록 기록 서비스에 요청하는 메서드
            CustomUserDetails principal,
            Long conversationId
    ) {
        historyService.deleteConversation(// 로그인 정보에서 사용자 id를 꺼내 채팅방 번호와 함께 historyService에 전달 , 기록서비스에서는 본인 소유의 채팅방인지 확인한후, 채팅방과 포함된 메세지를 논리 삭제
                principal.getUserId(),
                conversationId
        );
    }

    @Override
    public ChatbotReply ask( // 챗봇 질문 처리 메서드의 시작으로, 사용자 id를 가져오고 챗봇 사용가능 여부를 확인
            CustomUserDetails principal,
            Long conversationId,
            String message
    ) {
        Long userId = principal.getUserId();

        if (!enabled) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CHATBOT_DISABLED",
                    "챗봇이 설정되지 않은 서버입니다."
            );
        } // enabled가 false면 예외발생 , 현재 enabled는 생성자에서 api키가 비어있거나 기본값 no-key 인지 등을 확인해 정함

        String question = message == null
                ? ""
                : message.strip(); // 질문이 null이면 빈 문자열로 바꾸고 값이 있으면 앞뒤 공백 제거

        chatbotValidator.validateQuestion(question); // chatbotvalidor 에서 질문 검증

        String maskedQuestion = PiiMasker.mask(question); // piimasker에 구현된 규칙으로 개인정보에 해당하는 부분을 가리고 결과를 maskedQuestion에 담음


        var reservation = historyService.beginQuestion( // ai 호출이 실패해도 질문 기록이 남도록 저장하는 코드
                principal,
                conversationId,
                maskedQuestion
        );

        String reply;  // ai 에게 전달할 대화 목록을 준비하는 코드

        try {
            List<Message> context = new ArrayList<>(); // ai에게 전달할 메세지를 담는 빈 목록을 만듬
            List<ChatbotMessageDto> history = reservation.history();   // 앞서 beginQuestion()이 반환한 객체에서 완료된 이전 질문.답변 목록을 꺼내 history에 담은 이줄에서 db를 다시 조회하지는 않음


            int start = Math.max(
                    0,
                    history.size() - CONTEXT_MESSAGES
            ); // 이전 대화 중 최근 메세지만 가져오기 위해 시작 위치를 계산

            for (int index = start; index < history.size(); index++) { // 앞에서 계산한 start 위치부터 마지막 메세지까지 순서대로 처리
                ChatbotMessageDto item = history.get(index); // 현재 위치의 메세지를 꺼내고, 저장된 내용에도 마스킹 규칙을 다시 적용
                String content = PiiMasker.mask(item.getContent());

                if ("USER".equals(item.getRole())) { // 역할이 user이면 사용자가 보낸 질문이므로 내용을 usermessage 객체로 만들어 context 목록에 추가
                    context.add(new UserMessage(content));
                } else if ("ASSISTANT".equals(item.getRole())) { // 역할이 assistant이면 ai가 작성한 답변이므로 , assistantMessage 객체로 만들어 추가
                    context.add(new AssistantMessage(content));
                }
            } // 같은 문자열 내용이라도 질문인지 ai의 이전 답변인지 구분해서 전달해야 ai가 대화 흐름을 올바르게 이해할수있음

            context.add(new UserMessage(maskedQuestion)); // 현재 사용자가 보낸 질문을 ai 요청용 대화 목록에 추가

            String userContext = PiiMasker.mask(
                    userContextProvider.build(userId)
            ); // 해당 사용자의 참고 정보를 구성하고 개인정보를 마스킹해 userContext에 저장

            reply = chatClient.prompt()
                    .system(system -> system.param(
                            "userContext",
                            userContext
                    ))
                    .messages(context)
                    .call()
                    .content(); // 사용자 참고 정보
        } catch (RuntimeException exception) {
            markFailed(
                    userId,
                    conversationId,
                    reservation.questionId()
            );

            log.warn(
                    "챗봇 호출 실패. 질문ID={}, 예외유형={}",
                    reservation.questionId(),
                    exception.getClass().getSimpleName()
            );

            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CHATBOT_UNAVAILABLE",
                    "답변 생성에 실패했습니다. 질문 기록은 저장되었습니다."
            );
        }

        // 외부 AI 응답 확인과 실패 상태 저장은 서비스 흐름에 유지한다.
        if (reply == null || reply.isBlank()) {
            markFailed(
                    userId,
                    conversationId,
                    reservation.questionId()
            );

            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "CHATBOT_EMPTY_RESPONSE",
                    "AI 답변이 비어 있습니다. 질문 기록은 저장되었습니다."
            );
        }

        String maskedReply;

        try {
            maskedReply = PiiMasker.mask(reply);

            // 답변 저장과 질문 완료 처리를 같은 트랜잭션에서 수행한다.
            historyService.completeQuestion(
                    userId,
                    conversationId,
                    reservation.questionId(),
                    maskedReply
            );
        } catch (RuntimeException exception) {
            markFailed(
                    userId,
                    conversationId,
                    reservation.questionId()
            );

            throw exception;
        }

        return ChatbotReply.builder()
                .reply(maskedReply)
                .remaining(reservation.usage().remaining())
                .build();
    }

    private void markFailed(
            Long userId,
            Long conversationId,
            Long questionId
    ) {
        try {
            // 기존 작업과 별도 트랜잭션으로 실패 상태를 저장한다.
            historyService.failQuestion(
                    userId,
                    conversationId,
                    questionId
            );
        } catch (RuntimeException exception) {
            // 상태 저장 실패가 원래 오류를 덮어쓰지 않도록 한다.
            log.error(
                    "챗봇 실패 상태 저장 실패. 질문ID={}, 예외유형={}",
                    questionId,
                    exception.getClass().getSimpleName()
            );
        }
    }
}