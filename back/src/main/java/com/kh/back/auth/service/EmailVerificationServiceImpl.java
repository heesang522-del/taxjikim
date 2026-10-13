package com.kh.back.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import com.kh.back.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import com.kh.back.common.validation.InputValidator;

@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final JavaMailSender mailSender;

    /* [수정] DB Repository 삭제 후 Caffeine In-Memory Cache 객체 생성
     * - verificationCodes: (이메일 -> 인증번호) 저장 (유효기간: 5분)
     * - verifiedEmails: (이메일 -> 인증완료여부(Boolean)) 저장 (유효기간: 10분)
     */
    private final Cache<String, String> verificationCodes = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    private final Cache<String, Boolean> verifiedEmails = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    // 6자리 랜덤 인증번호 생성
    @Override
    public String createAuthCode() {
        return String.valueOf((int) (Math.random() * 899999) + 100000);
    }

    // 메일 발송 및 메모리 캐시 저장 처리 (DB 관련 @Transactional 제거)
    @Override
    public void sendVerificationCode(String email) {
        InputValidator.requireText("email", email);
        String authCode = createAuthCode();

        /* [수정] DB 저장 대신 Caffeine Cache 메모리에 저장 (자동으로 5분 후 만료됨) */
        verificationCodes.put(email, authCode);

        // 이메일 발송
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[example.demo] 이메일 인증번호 안내");
        message.setText("안녕하세요.\n요청하신 인증번호는 [" + authCode + "] 입니다.\n5분 이내에 입력해주세요.");

        mailSender.send(message);
    }

    // 인증번호 검증 처리 (DB 관련 @Transactional 제거)
    @Override
    public boolean verifyCode(String email, String inputCode) {
        InputValidator.requireText("email", email);
        InputValidator.requireText("code", inputCode);
        /* Caffeine Cache에서 해당 이메일의 인증번호 조회 */
        String storedCode = verificationCodes.getIfPresent(email);

        if (storedCode == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_CODE_EXPIRED", "인증번호가 요청되지 않았거나 만료되었습니다.");
        }

        if (!storedCode.equals(inputCode)) {
            return false; // 인증번호 불일치
        }

        // 인증번호 캐시 삭제
        verificationCodes.invalidate(email);
        // 인증 완료 상태 저장
        verifiedEmails.put(email, true);
        return true;
    }

    // 인증 완료 여부 확인 (최종 회원가입/비밀번호 변경 직전에 확인용)
    @Override
    public boolean isVerified(String email) {
        Boolean isVerified = verifiedEmails.getIfPresent(email);
        return Boolean.TRUE.equals(isVerified);
    }

    // 사용 완료된 인증 데이터 삭제
    @Override
    public void removeVerification(String email) {
        verifiedEmails.invalidate(email);
    }
}
