package com.kh.back.chatbot.service.Impl;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotConversationDto;
import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto;
import com.kh.back.chatbot.dto.ChatbotUsageDto;
import com.kh.back.chatbot.mapper.ChatbotMapper;
import com.kh.back.chatbot.service.ChatbotHistoryService;
import com.kh.back.chatbot.service.ChatbotUsageService;
import com.kh.back.chatbot.validator.ChatbotValidator;
import com.kh.back.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatbotHistoryServiceImpl implements ChatbotHistoryService {

    private final ChatbotMapper chatbotMapper;
    private final ChatbotUsageService chatbotUsageService;
    private final ChatbotValidator chatbotValidator;

    // 운영 정책값이며 application.yml에서 조정할 수 있다.
    @Value("${chatbot.pending-timeout-seconds:300}")
    private int pendingTimeoutSeconds;

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversations(Long userId) {
        return chatbotMapper.findConversations(userId, null)
                .stream()
                .map(ChatbotConversationDto::toResponse)
                .toList();
    } // 해당 사용자의 채팅방 목록을 db에서 조회하고 , 응답용 객체 목록으로 변환해 반환하는 메서드

    @Override
    @Transactional
    public ConversationResponse createConversation(Long userId) {
        ChatbotConversationDto conversation =
                ChatbotConversationDto.builder()
                        .userId(userId)
                        .title("새 대화")
                        .build();

        chatbotMapper.insertConversation(conversation);

        return requireConversation(
                userId,
                conversation.getConversationId()
        ).toResponse();
    }   // 해당 사용자의 새 채팅방을 db에 저장하고, 생성된 채팅방 정보를 반환하는 메서드

    @Override
    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public ConversationSnapshot getSnapshot(
            Long userId,
            Long conversationId
    ) {
        // 대화 버전과 메시지를 동일한 시점 기준으로 조회한다.
        ChatbotConversationDto conversation =
                requireConversation(userId, conversationId);

        List<ChatbotMessageDto> messages =
                chatbotMapper.findMessages(userId, conversationId);

        return ConversationSnapshot.builder()
                .revision(conversation.getRevision())
                .messages(messages)
                .build();
    } // 채팅방의 버전과 메세지 목록을 묶어서 조회하는 메서드 , 이렇게 한번의 일관된 조회결과를 snapshot이라고 부름

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public QuestionReservation beginQuestion(
            CustomUserDetails principal,
            Long conversationId,
            String question
    ) {
        Long userId = principal.getUserId();

        ChatbotConversationDto conversation =
                requireLockedConversation(userId, conversationId);
 // 질문 처리를 시작하면서 로그인 사용자 id를 가져오고 해당 채팅방을 잠금 조회하는 부분

        chatbotMapper.expirePendingQuestions(
                conversationId,
                Math.max(1, pendingTimeoutSeconds)
        ); // 해당 채팅방에서 너무 오래 처리 중인 질문을 실패상태로 변경하는부분

        List<ChatbotMessageDto> messages =
                chatbotMapper.findMessages(userId, conversationId); // 해당 사용자의 특정 채팅방 메세지를 db에서 조회해 messages 변수에 담는 코드

        boolean hasPending = messages.stream()
                .anyMatch(message ->
                        "PENDING".equals(message.getStatus())
                ); // 메세지 목록에 '처리중'인 메세지가 하나라도 있는지 확인

        // 처리 중 상태는 DB 조회 결과에 따른 업무 규칙이므로 서비스에서 확인한다.
        if (hasPending) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CHATBOT_REQUEST_IN_PROGRESS",
                    "이 대화방에서 답변을 생성 중입니다. 잠시 후 다시 시도해주세요."
            );
        }


        ChatbotUsageDto usage =
                chatbotUsageService.reserveUsage(principal);  // 로그인한 사용자의 오늘 질문 횟수를 1회 사용 처리하고 변경된 사용량 정보를 usage 변수에 저장

        String title = buildTitle(question);

        int updated = chatbotMapper.updateConversation(
                userId,
                conversationId,
                conversation.getRevision(),
                title
        ); // 채팅방 정보를 db에서 갱신하고 수정된 행 개수를 updated에 저장

        requireUpdated(updated); // 채팅방 갱신 성공 확인뒤, 저장할 사용자 질문 객체를 만드는 부분

        ChatbotMessageDto pendingQuestion = // pendingQuestion 완성된 객체를 담는 변수
                ChatbotMessageDto.builder()
                        .conversationId(conversationId) // 질문이 속할 채팅방 번호 설정
                        .role("USER") // 사용자가 보낸 질문으로 설정
                        .content(question) // 질문 내용 설정
                        .status("PENDING") // 아직 답변처리 중인 상태로 설정
                        .build(); // 객체 완성

        if (chatbotMapper.insertMessage(pendingQuestion) != 1) { // 앞에서 만든 pendingQuestion 을 db에 저장, 반환값은 저장된 행 개수
            throw new ApiException( // 질문 하나를 저장하므로 정상결과는 1, 결과가 1이 아니면 실패로 판단
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CHATBOT_QUESTION_SAVE_FAILED",
                    "질문을 저장하지 못했습니다."
            );
        }

        List<ChatbotMessageDto> completedHistory = messages.stream() // 메세지 목록에서 처리 상태가 completed인 메세지만 골라 새로운 목록으로 만든는 부분
                .filter(message -> // .filter(...) 조건에 맞는 메세지만 통과
                        "COMPLETED".equals(message.getStatus()) // 상태가 완료인지 확인
                )
                .toList(); // 통과한 메세지를 목록으로 모음  ,  completedhistory 결과목록을 담는 변수

        return QuestionReservation.builder()
                .questionId(pendingQuestion.getMessageId())
                .usage(usage)
                .history(completedHistory)
                .build(); // 질문 처리에 필요한 정보를 QhestionReservation 객체로 묶어서 반환
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeQuestion( // 이안에서 진행하는 질문 완료 처리와 답변 저장이 함께 성공하거나 오류가 나면 함꼐 롤백되도록함
            Long userId,
            Long conversationId,
            Long questionId,
            String reply
    ) {
        ChatbotConversationDto conversation =
                requireLockedConversation(userId, conversationId); // 해당 사용자의 채팅방을 잠금 조회하고 그정보를 conversation변수에 담음

        // 질문 완료 처리와 답변 저장은 함께 성공하거나 함께 롤백된다.
        int completed = chatbotMapper.completeQuestion(
                conversationId,
                questionId
        );

        if (completed != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CHATBOT_QUESTION_CHANGED",
                    "질문 상태가 변경되었습니다. 대화를 다시 조회해주세요."
            );
        } // 질문이 이미 실패 처리 됐거나 삭제됐다면 sql조건에 맞지 않아 0이 반환될수 있어 http 409 예외발생

        ChatbotMessageDto answer =
                ChatbotMessageDto.builder()
                        .conversationId(conversationId)
                        .role("ASSISTANT")
                        .content(reply)
                        .status("COMPLETED")
                        .build(); // db에 저장할 ai답변 정모를 builder로 만들어 answer 변수에 담는 코드

        if (chatbotMapper.insertMessage(answer) != 1) { // ai답변을 db에 저장하고, 정상적으로 한행이 저장됐는지 확인
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CHATBOT_ANSWER_SAVE_FAILED",
                    "답변을 저장하지 못했습니다."
            );
        }

        requireUpdated( // 채팅방 정보를 갱신하고 갱신이 성공했는지 바로 확인하는 부분
                chatbotMapper.updateConversation(
                        userId,
                        conversationId,
                        conversation.getRevision(),
                        conversation.getTitle()
                )
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW) // 항상 새로운 트랜잭션으로 실행, 기존 트랜잭션이 있으면 잠시 중단
    public void failQuestion( // 실패한 작업의 트랜잭션이 롤백되더라도 실패 상태 기록은 별도로 저장할수 있도록 사용하는 설정
            Long userId,
            Long conversationId,
            Long questionId
    ) {

        ChatbotConversationDto conversation =
                chatbotMapper.lockConversation(
                        userId,
                        conversationId
                ); // 해당 사용자의 특정 채팅방을 db에서 잠금 조회하고 결과를 conversation변수에 저장

        if (conversation == null) {
            return;
        } // 조회한 채팅방이 없으면 현재 메서드를 바로 종료

        int failed = chatbotMapper.failQuestion(
                conversationId,
                questionId
        ); // 해당 질문 상태를 failed로 변경하고 수정된 행 개수를 failed 변수에 저장

        if (failed == 1) {
            requireUpdated(
                    chatbotMapper.updateConversation(
                            userId,
                            conversationId,
                            conversation.getRevision(),
                            conversation.getTitle()
                    )
            );
        }
    } // 질문을 실패 상태로 변경한 경우에만 채팅방의 버전과 마지막 변경 시각도 갱신

    @Override
    @Transactional
    public void deleteConversation( //
            Long userId, // 삭제를 요청한 사용자 번호
            Long conversationId // 삭제할 채팅방 번호
    ) {

        requireLockedConversation(userId, conversationId); // 채팅방 번호검증 -> 해당 사용자의 채팅방인지 확인 -> select .. for update 로 잠금 조회 -> 없으면 예외발생

        // 기존 다중 테이블 UPDATE로 채팅방과 메시지를 함께 논리 삭제한다.
        int updated = chatbotMapper.deleteConversation(
                userId,
                conversationId
        );

        // 여러 행이 수정될 수 있으므로 1 이상이면 성공이다.
        if (updated < 1) {
            throw conversationNotFound();
        }
    }

    private String buildTitle(String question) {
        // 마스킹된 첫 질문의 앞 40글자를 제목으로 사용한다.
        String title = question.replaceAll("\\s+", " ").strip(); // replaceAll("\\s+", " ") : 연속된 공백, 줄바꿈 , 탭을 공백하나로 바꿈 , strip() 문자열 앞뒤 공백 제거
        int titleLength = Math.min(
                40,
                title.codePointCount(0, title.length())
        );

        return title.substring(
                0,
                title.offsetByCodePoints(0, titleLength)
        );
    } // java 코드에는 정규식을 **`"\s+"`**로 작성해야함, 정리한 제목의 글자 수와 40중 작은 값을 선택함

    private ChatbotConversationDto requireConversation(
            Long userId,
            Long conversationId
    ) { // 채팅방 번호를 검증한뒤 해당 사용자의 채팅방을 db에서 조회하는 부분
        // 입력값 검증은 Validator에 위임한다.
        chatbotValidator.validateConversationId(conversationId); // validator에서 채팅방 번호가 null이거나 0 이하인지 검사, 잘못된 값이면 예외발생

        List<ChatbotConversationDto> conversations =
                chatbotMapper.findConversations(
                        userId,
                        conversationId
                );  // 사용자 번호와 채팅방 번호가 모두 일치하는 삭제되지 않은 채팅방을 조회


        if (conversations.isEmpty()) {
            throw conversationNotFound();
        }

        return conversations.get(0);
    } // 조회한 채팅방이 없으면 예외발생, 있으면 첫번쨰 채팅방을 반환

    private ChatbotConversationDto requireLockedConversation(
            Long userId,
            Long conversationId
    ) {
        chatbotValidator.validateConversationId(conversationId); // 채팅방 번호를 검증하고 , 사용자의 채팅방을 잠금 조회한뒤 반환

        ChatbotConversationDto conversation =
                chatbotMapper.lockConversation(
                        userId,
                        conversationId
                ); // 사용자 번호와 채팅방 번호가 일치하는 , 삭제되지 않은 채팅방을 조회, 연결된 sql 의 for update 로 다른 트랜잭션이 같은 채팅방 행을 동시에 변경하지 못하도록 잠금을 검

        if (conversation == null) {
            throw conversationNotFound();
        } // 조회되지 않으면 404 예외로 처리 중단, 다른 사용자의 채팅방도 조회 조건에 맞지 않아 같은 처리가 됨

        return conversation;
    } // 제목 , 버전 등이 담긴 채팅방 객체 를 호출한 곳에 반환

    private void requireUpdated(int updated) { // 채팅방 한개가 정상적으로 수정됐는지 확인

        if (updated != 1) { // 채팅방 하나를 갱신하는 작업이므로 수정된 행이 정확이 1개가 아니면 실패로 판단
            throw new ApiException( // 실패하면 409 예외 발생 , 채팅방 버전이 달라졌거나 삭제돼 sql 조건에 맞지 않는 경우등
                    HttpStatus.CONFLICT,
                    "CHATBOT_CONVERSATION_CHANGED",
                    "대화가 변경되었습니다. 다시 조회해주세요."
            );
        }
    }

    private ApiException conversationNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "CHATBOT_CONVERSATION_NOT_FOUND",
                "대화를 찾을 수 없습니다."
        );
    }
}