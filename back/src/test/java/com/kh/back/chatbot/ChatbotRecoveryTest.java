package com.kh.back.chatbot;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotConversationDto;
import com.kh.back.chatbot.dto.ChatbotReply;
import com.kh.back.chatbot.dto.ChatbotSseEvent;
import com.kh.back.chatbot.mapper.ChatbotMapper;
import com.kh.back.chatbot.service.ChatbotService;
import com.kh.back.chatbot.service.ChatbotUsageService;
import com.kh.back.chatbot.service.Impl.ChatbotHistoryServiceImpl;
import com.kh.back.chatbot.service.Impl.ChatbotServiceImpl;
import com.kh.back.chatbot.service.Impl.ChatbotSseServiceImpl;
import com.kh.back.chatbot.util.PiiMasker;
import com.kh.back.chatbot.validator.ChatbotValidator;
import com.kh.back.common.exception.ApiException;
import com.kh.back.common.sse.SseEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatbotRecoveryTest {
    @Test
    void rejectsEleventhConversationBeforeInsert() {
        ChatbotMapper mapper = mock(ChatbotMapper.class);
        when(mapper.lockConversationOwner(1L)).thenReturn(1L);
        when(mapper.countActiveConversations(1L)).thenReturn(10);
        var service = new ChatbotHistoryServiceImpl(mapper,
                mock(ChatbotUsageService.class), new ChatbotValidator());
        ApiException error = assertThrows(ApiException.class,
                () -> service.createConversation(1L));
        assertEquals("CHATBOT_CONVERSATION_LIMIT", error.getCode());
        verify(mapper, never()).insertConversation(any());
    }

    @Test
    void locksOwnerBeforeCountingAndCreatingTenthConversation() {
        ChatbotMapper mapper = mock(ChatbotMapper.class);
        when(mapper.lockConversationOwner(1L)).thenReturn(1L);
        when(mapper.countActiveConversations(1L)).thenReturn(9);
        doAnswer(call -> {
            ChatbotConversationDto row = call.getArgument(0);
            row.setConversationId(10L);
            return 1;
        }).when(mapper).insertConversation(any());
        when(mapper.findConversations(1L, 10L)).thenReturn(List.of(
                ChatbotConversationDto.builder().conversationId(10L)
                        .userId(1L).title("새 대화")
                        .createdAt(java.time.LocalDateTime.of(2026, 11, 6, 9, 0))
                        .updatedAt(java.time.LocalDateTime.of(2026, 11, 6, 9, 0)).build()));
        var service = new ChatbotHistoryServiceImpl(mapper,
                mock(ChatbotUsageService.class), new ChatbotValidator());
        service.createConversation(1L);
        var order = inOrder(mapper);
        order.verify(mapper).lockConversationOwner(1L);
        order.verify(mapper).countActiveConversations(1L);
        order.verify(mapper).insertConversation(any());
    }

    @Test
    void rejectsConversationNotOwnedByUser() {
        ChatbotMapper mapper = mock(ChatbotMapper.class);
        when(mapper.findConversations(1L, 2L)).thenReturn(List.of());
        var service = new ChatbotHistoryServiceImpl(mapper,
                mock(ChatbotUsageService.class), new ChatbotValidator());
        ApiException error = assertThrows(ApiException.class,
                () -> service.getSnapshot(1L, 2L));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        verify(mapper, never()).findMessages(anyLong(), anyLong());
    }

    @Test
    void sendsDeltaBeforeDoneWithMatchingRequest() {
        ChatbotService chatbot = mock(ChatbotService.class);
        SseEmitterRegistry registry = mock(SseEmitterRegistry.class);
        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(1L);
        when(chatbot.askStream(eq(principal), eq(2L), eq("질문"), any()))
                .thenAnswer(call -> {
                    Consumer<String> delta = call.getArgument(3);
                    delta.accept("첫 조각 ");
                    delta.accept("다음 조각");
                    return new ChatbotReply("첫 조각 다음 조각", 29);
                });
        var service = new ChatbotSseServiceImpl(chatbot, registry, Runnable::run);
        service.submitQuestion(principal, 2L, "request-1", "질문");
        var order = inOrder(registry);
        order.verify(registry).send(eq(1L), eq("chatbot-delta"),
                argThat(value -> value instanceof ChatbotSseEvent event
                        && "request-1".equals(event.getRequestId())
                        && "첫 조각 ".equals(event.getDelta())));
        order.verify(registry).send(eq(1L), eq("chatbot-delta"), any());
        order.verify(registry).send(eq(1L), eq("chatbot-done"),
                argThat(value -> value instanceof ChatbotSseEvent event
                        && event.getConversationId().equals(2L)
                        && "첫 조각 다음 조각".equals(event.getReply())));
    }

    @Test
    void sendsFailureWithoutDoneWhenAiFails() {
        ChatbotService chatbot = mock(ChatbotService.class);
        SseEmitterRegistry registry = mock(SseEmitterRegistry.class);
        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(1L);
        when(chatbot.askStream(any(), anyLong(), anyString(), any()))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "CHATBOT_UNAVAILABLE", "답변 생성 실패"));
        var service = new ChatbotSseServiceImpl(chatbot, registry, Runnable::run);
        service.submitQuestion(principal, 2L, "request-1", "질문");
        verify(registry).send(eq(1L), eq("chatbot-failed"),
                argThat(value -> value instanceof ChatbotSseEvent event
                        && "CHATBOT_UNAVAILABLE".equals(event.getCode())));
        verify(registry, never()).send(anyLong(), eq("chatbot-done"), any());
    }

    @Test
    void masksSensitivePatternsAcrossEveryChunkBoundary() throws Exception {
        Class<?> type = Class.forName(ChatbotServiceImpl.class.getName()
                + "$StreamingPiiBuffer");
        Constructor<?> constructor = type.getDeclaredConstructor(Consumer.class);
        Method append = type.getDeclaredMethod("append", String.class);
        Method finish = type.getDeclaredMethod("finish");
        constructor.setAccessible(true);
        append.setAccessible(true);
        finish.setAccessible(true);
        String[] values = {"안녕하세요 답변입니다. ",
                "전화 010 1234 5678 입니다. ",
                "이메일 person@example.com 입니다. ",
                "번호 900101 - 1234567 입니다. ",
                "카드 1234 5678 9012 3456 입니다. "};
        for (String input : values) {
            String expected = PiiMasker.mask(input);
            for (int split = 0; split <= input.length(); split++) {
                StringBuilder visible = new StringBuilder();
                Object buffer = constructor.newInstance((Consumer<String>) visible::append);
                append.invoke(buffer, input.substring(0, split));
                assertTrue(expected.startsWith(visible.toString()));
                append.invoke(buffer, input.substring(split));
                assertTrue(expected.startsWith(visible.toString()));
                assertEquals(expected, finish.invoke(buffer));
                assertEquals(expected, visible.toString());
            }
        }
    }
}
