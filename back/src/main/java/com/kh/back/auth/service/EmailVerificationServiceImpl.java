package com.kh.back.auth.service;

import com.kh.back.common.exception.ApiException;
import com.kh.back.common.validation.InputValidator;
import com.kh.back.common.validation.UserInputValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class EmailVerificationServiceImpl implements EmailVerificationService {
    private static final int MAX_FAILURES = 5;
    private static final int CODE_TTL_SECONDS = 300;
    private static final int VERIFIED_TTL_SECONDS = 600;
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final String senderEmail;
    private final String senderPassword;

    // KEYS는 순서대로 재발송 제한·인증번호·인증 완료 키다.
    // 메일 발송을 예약한 요청만 새 번호를 저장하고, 저장 성공 시점부터 1분 제한을 시작한다.
    private static final DefaultRedisScript<Long> SAVE_CODE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            redis.call('HSET', KEYS[2], 'code', ARGV[2], 'failures', 0)
            redis.call('EXPIRE', KEYS[2], ARGV[3])
            redis.call('DEL', KEYS[3])
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[4])
            return 1
            """, Long.class);

    // 발송 실패 시 본인 예약만 해제해, 뒤에 들어온 다른 요청의 제한을 삭제하지 않는다.
    private static final DefaultRedisScript<Long> RELEASE_SEND_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    // 조회·실패 증가·차단·성공을 Lua로 묶어 여러 서버의 동시 요청에도 한 번만 인증한다.
    // 결과: 0 만료, 1 성공, 2 불일치, 3 횟수 초과. 오입력은 번호의 5분 만료를 연장하지 않는다.
    private static final DefaultRedisScript<Long> VERIFY_CODE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            local failures = tonumber(redis.call('HGET', KEYS[1], 'failures') or '0')
            if failures >= tonumber(ARGV[2]) then
                return 3
            end
            if redis.call('HGET', KEYS[1], 'code') == ARGV[1] then
                redis.call('DEL', KEYS[1])
                redis.call('SET', KEYS[2], 'true', 'EX', ARGV[3])
                return 1
            end
            failures = redis.call('HINCRBY', KEYS[1], 'failures', 1)
            if failures >= tonumber(ARGV[2]) then
                redis.call('HDEL', KEYS[1], 'code')
                return 3
            end
            return 2
            """, Long.class);

    public EmailVerificationServiceImpl(JavaMailSender mailSender, StringRedisTemplate redisTemplate,
                                        @Value("${spring.mail.username:}") String senderEmail,
                                        @Value("${spring.mail.password:}") String senderPassword) {
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
        this.senderEmail = senderEmail;
        this.senderPassword = senderPassword;
    }

    @Override
    public void sendVerificationCode(String email) {
        email = UserInputValidator.normalizeEmail(email);
        if (senderEmail.isBlank() || senderPassword.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE",
                    "메일 발송 설정이 준비되지 않았습니다. 관리자에게 문의해주세요.");
        }

        String cooldownKey = key(email, "cooldown");
        String requestId = UUID.randomUUID().toString();
        // SET NX와 만료 시간을 함께 지정해 같은 이메일의 동시 발송도 제한한다.
        Boolean reserved = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, requestId, RESEND_COOLDOWN);
        if (reserved == null) {
            throw new IllegalStateException("Redis 이메일 발송 예약에 실패했습니다.");
        }
        if (!reserved) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "VERIFICATION_RESEND_TOO_SOON",
                    "인증번호는 1분 후에 다시 받을 수 있습니다.");
        }

        String authCode = Integer.toString(RANDOM.nextInt(900000) + 100000);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("TaxJiKim <" + senderEmail + ">");
        message.setTo(email);
        message.setSubject("[TaxJiKim] 이메일 인증번호 안내");
        message.setText("""
                TaxJiKim 이메일 인증번호입니다.

                %s

                인증번호는 발송 후 5분간 유효합니다.
                직접 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(authCode));

        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            // 발송 실패는 즉시 재시도할 수 있도록 본인 예약만 해제한다.
            try {
                redisTemplate.execute(RELEASE_SEND_SCRIPT, List.of(cooldownKey), requestId);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }

        // 새 번호 저장·실패 횟수 초기화·이전 인증 완료 삭제를 한 번에 처리한다.
        Long saved = redisTemplate.execute(SAVE_CODE_SCRIPT,
                List.of(cooldownKey, key(email, "code"), key(email, "verified")),
                requestId, authCode, Integer.toString(CODE_TTL_SECONDS),
                Long.toString(RESEND_COOLDOWN.toSeconds()));
        if (!Long.valueOf(1).equals(saved)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VERIFICATION_SEND_FAILED",
                    "인증번호를 저장하지 못했습니다. 잠시 후 다시 받아 주세요.");
        }
    }

    @Override
    public boolean verifyCode(String email, String inputCode) {
        email = UserInputValidator.normalizeEmail(email);
        InputValidator.requireText("code", inputCode);
        Long result = redisTemplate.execute(VERIFY_CODE_SCRIPT,
                List.of(key(email, "code"), key(email, "verified")),
                inputCode, Integer.toString(MAX_FAILURES), Integer.toString(VERIFIED_TTL_SECONDS));
        if (result == null) {
            throw new IllegalStateException("Redis 이메일 인증 결과를 확인하지 못했습니다.");
        }
        return switch (result.intValue()) {
            case 0 -> throw new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_CODE_EXPIRED",
                    "인증번호가 요청되지 않았거나 만료되었습니다.");
            case 1 -> true;
            case 2 -> false;
            case 3 -> throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "VERIFICATION_ATTEMPTS_EXCEEDED",
                    "인증번호 입력 횟수를 초과했습니다. 인증번호를 다시 받아 주세요.");
            default -> throw new IllegalStateException("알 수 없는 Redis 이메일 인증 결과입니다.");
        };
    }

    @Override
    public boolean isVerified(String email) {
        return "true".equals(redisTemplate.opsForValue().get(key(email, "verified")));
    }

    @Override
    public void removeVerification(String email) {
        // 최종 가입·변경 성공 후 인증 완료 기록만 소비한다. 재발송 제한은 유지한다.
        redisTemplate.delete(key(email, "verified"));
    }

    private String key(String email, String type) {
        // 같은 이메일의 키를 Redis Cluster에서도 같은 슬롯에 두어 Lua로 함께 처리한다.
        return "EMAIL_VERIFICATION:{" + UserInputValidator.normalizeEmail(email) + "}:" + type;
    }
}
