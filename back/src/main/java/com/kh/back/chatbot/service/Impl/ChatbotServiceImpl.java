package com.kh.back.chatbot.service.Impl;

import com.kh.back.chatbot.dto.ChatbotMessageDto;
import com.kh.back.chatbot.dto.ChatbotReply;
import com.kh.back.chatbot.service.ChatbotService;
import com.kh.back.chatbot.service.UserContextProvider;
import com.kh.back.chatbot.util.PiiMasker;
import com.kh.back.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ChatbotServiceImpl implements ChatbotService {

    // 금융 기준이 아닌 챗봇 운영 기준이다.
    private static final int DAILY_LIMIT = 30;
    private static final int MAX_LENGTH = 500;

    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final UserContextProvider userContextProvider;
    private final boolean enabled;

    // 사용자별 한국 날짜와 질문 시도 횟수를 보관한다.
    // 서버 재시작 시 초기화되며 여러 서버 간에는 공유되지 않는다.
    private final Map<Long, DailyUsage> usage =
            new ConcurrentHashMap<>();

    public ChatbotServiceImpl(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            UserContextProvider userContextProvider,
            @Value("classpath:prompts/Chatbot-System.st")
            Resource systemPrompt,
            @Value("${spring.ai.openai.api-key:no-key}")
            String apiKey
    ) {
        this.chatMemory = chatMemory;
        this.userContextProvider = userContextProvider;

        this.chatClient = builder
                // Windows에서도 시스템 지침의 한글이 깨지지 않게 읽는다.
                .defaultSystem(
                        systemPrompt,
                        StandardCharsets.UTF_8
                )
                // 사용자별 대화를 다음 질문에 연결하고 저장한다.
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .build()
                )
                .build();

        this.enabled = apiKey != null
                && !apiKey.isBlank()
                && !"no-key".equals(apiKey.trim());

        if (!enabled) {
            log.warn(
                    "OPENAI_API_KEY가 없어 챗봇 호출을 비활성화합니다."
            );
        }
    }

    @Override
    public ChatbotReply ask(Long userId, String message) {
        validateUserId(userId);

        // 키가 없는 PC에서는 외부 AI 호출을 시작하지 않는다.
        if (!enabled) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CHATBOT_DISABLED",
                    "챗봇이 설정되지 않은 서버입니다."
            );
        }

        String question = message == null
                ? ""
                : message.strip();

        if (question.isEmpty()
                || question.length() > MAX_LENGTH) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "질문은 1~" + MAX_LENGTH + "자로 입력해주세요.",
                    Map.of(
                            "message",
                            "질문은 1~" + MAX_LENGTH + "자로 입력해주세요."
                    )
            );
        }

        // 설정·입력 검사가 끝난 요청에 대해 질문 시도 횟수를 계산한다.
        // 외부 호출이 실패해도 시도 횟수에 포함한다.
        int used = reserveUsage(userId);

        String reply;

        try {
            // 사용자 관련 안내에도 기존 마스킹 함수를 적용한다.
            String userContext = PiiMasker.mask(
                    userContextProvider.build(userId)
            );

            reply = chatClient.prompt()
                    .system(system -> system.param(
                            "userContext",
                            userContext
                    ))
                    // 질문을 템플릿이 아닌 메시지로 전달한다.
                    .messages(
                            new UserMessage(PiiMasker.mask(question))
                    )
                    .advisors(advisor -> advisor.param(
                            ChatMemory.CONVERSATION_ID,
                            conversationId(userId)
                    ))
                    .call()
                    .content();

        } catch (RuntimeException exception) {
            // 키·질문·외부 오류 본문이 로그에 노출되지 않게 한다.
            log.warn(
                    "챗봇 호출 실패. 예외유형={}",
                    exception.getClass().getSimpleName()
            );

            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CHATBOT_UNAVAILABLE",
                    "챗봇을 잠시 사용할 수 없습니다. 다시 시도해주세요."
            );
        }

        if (reply == null || reply.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "CHATBOT_EMPTY_RESPONSE",
                    "AI 답변이 비어 있습니다. 다시 시도해주세요."
            );
        }

        return new ChatbotReply(
                reply,
                DAILY_LIMIT - used
        );
    }

    @Override
    public List<ChatbotMessageDto> getHistory(Long userId) {
        validateUserId(userId);

        // 로그인한 사용자의 대화만 가져온다.
        return chatMemory.get(conversationId(userId))
                .stream()
                .map(message -> new ChatbotMessageDto(
                        message.getMessageType().name(),
                        message.getText()
                ))
                .toList();
    }

    @Override
    public void clearHistory(Long userId) {
        validateUserId(userId);

        // 대화 삭제로 하루 사용 횟수까지 초기화하지는 않는다.
        chatMemory.clear(conversationId(userId));
    }

    private String conversationId(Long userId) {
        return "user-" + userId;
    }

    private int reserveUsage(Long userId) {
        LocalDate today = LocalDate.now(KOREA_ZONE);

        // 날짜가 바뀌면 해당 사용자의 하루 사용량을 초기화한다.
        DailyUsage dailyUsage = usage.compute(
                userId,
                (key, previous) -> {
                    if (previous == null
                            || !previous.date().equals(today)) {
                        return new DailyUsage(
                                today,
                                new AtomicInteger()
                        );
                    }

                    return previous;
                }
        );

        // 동시 요청에서도 한도를 초과해 호출하지 않도록 원자적으로 예약한다.
        while (true) {
            int current = dailyUsage.count().get();

            if (current >= DAILY_LIMIT) {
                throw new ApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "CHATBOT_LIMIT",
                        "오늘 질문 횟수(" + DAILY_LIMIT + "회)를 모두 사용했습니다."
                );
            }

            if (dailyUsage.count().compareAndSet(
                    current,
                    current + 1
            )) {
                return current + 1;
            }
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "로그인이 필요합니다."
            );
        }
    }

    private record DailyUsage(
            LocalDate date,
            AtomicInteger count
    ) {
    }
}