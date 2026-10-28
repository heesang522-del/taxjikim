package com.kh.back.notification.sse;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// 로그인한 회원의 알림 SSE 연결(emitter)을 유저ID 기준으로 들고 있다가, 이벤트가 생기면 그 유저에게 push 하는 역할만 담당한다.
// 알림이 "무엇을" 의미하는지는 모르고 NotificationService가 결정해서 이 레지스트리에 위임한다.
// 나중에 알림 외 다른 기능이 실시간 push가 필요해지면 이 클래스를 그대로 재사용할 수 있도록 알림 도메인 지식은 담지 않는다.
@Slf4j
@Component
public class NotificationEmitterRegistry {

    // SseEmitter 자체 타임아웃(ms). 지정하지 않으면 서블릿 컨테이너 기본 비동기 타임아웃(Tomcat 기준 30초)이 적용돼 너무 빨리 끊긴다.
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L; // 30분
    // 하트비트 주기(초). 리버스 프록시·로드밸런서의 idle timeout보다 짧게 유지해서 연결이 끊긴 것처럼 보이지 않게 한다.
    private static final long HEARTBEAT_INTERVAL_SECONDS = 20L;

    // 유저 한 명이 탭을 여러 개 열면 emitter가 여러 개 쌓이므로 리스트로 관리한다.
    private final Map<Long, List<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

    private ScheduledExecutorService heartbeatExecutor;

    @PostConstruct
    void startHeartbeat() {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeatToAll, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stopHeartbeat() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
    }

    // 로그인한 회원의 알림 구독 연결을 새로 만들어 등록한다. 연결이 끊기는 모든 경로(정상 종료·타임아웃·오류)에서 맵 정리를 보장한다.
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        List<SseEmitter> emitters = emittersByUserId.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> {
            // 타임아웃되면 emitter를 완료 처리해야 브라우저 EventSource가 재연결을 시도한다.
            removeEmitter(userId, emitter);
            emitter.complete();
        });
        emitter.onError(ex -> removeEmitter(userId, emitter));

        log.debug("알림 SSE 구독 등록: userId={}", userId);
        return emitter;
    }

    // 특정 회원에게 연결된 모든 탭(emitter)으로 이벤트를 보낸다. 로그인하지 않았거나 연결이 없으면 조용히 무시한다.
    public void send(Long userId, String eventName, Object data) {
        List<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            sendSafely(userId, emitter, eventName, data);
        }
    }

    // 여러 회원에게 같은 이벤트를 보낼 때 사용한다(예: 공지사항 전체 브로드캐스트).
    public void sendToUsers(Collection<Long> userIds, String eventName, Object data) {
        userIds.forEach(userId -> send(userId, eventName, data));
    }

    // 지금 연결돼 있는 모든 회원에게 같은 이벤트를 보낸다. 전체 브로드캐스트처럼 대상 유저 목록을 따로 조회할 필요가 없을 때 쓴다.
    public void sendToAll(String eventName, Object data) {
        emittersByUserId.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                sendSafely(userId, emitter, eventName, data);
            }
        });
    }

    private void sendSafely(Long userId, SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            // 클라이언트가 이미 연결을 끊은 뒤 아직 맵 정리가 안 된 상태일 수 있다. 에러로 처리하지 않고 정리만 한다.
            removeEmitter(userId, emitter);
        }
    }

    private void sendHeartbeatToAll() {
        emittersByUserId.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    // 데이터 없는 주석(comment) 라인만 보내서 연결을 살려둔다. 클라이언트의 알림 처리 로직에는 영향이 없다.
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException e) {
                    removeEmitter(userId, emitter);
                }
            }
        });
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUserId.remove(userId, emitters);
        }
    }
}
