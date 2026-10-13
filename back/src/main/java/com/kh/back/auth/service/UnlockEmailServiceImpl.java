package com.kh.back.auth.service;

import com.kh.back.auth.security.LoginUnlockTokenService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnlockEmailServiceImpl implements UnlockEmailService {

    private final JavaMailSender mailSender;
    private final LoginUnlockTokenService unlockTokenService;

    /**
     * 1. [5회/10회] 로그인 실패 경고 및 조기 해제 메일 발송
     */
    @Override
    public void sendWarningEmail(String loginId, String toEmail, String failReason) {
        // 해제 토큰 및 URL 생성
        String token = unlockTokenService.createUnlockToken(loginId);
        String unlockUrl = "http://localhost:8080/auth/unlock-account?token=" + token;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("[example.demo] 비정상적인 로그인 시도 경고 및 잠금 해제 안내");

            String htmlContent = "<div style='font-family: Arial, sans-serif; padding: 20px; border: 1px solid #ddd; border-radius: 8px;'>"
                    + "<h2 style='color: #d9534f;'>⚠️ 로그인 실패 경고 및 차단 안내</h2>"
                    + "<p>회원님의 계정(<b>" + loginId + "</b>)으로 연속된 로그인 실패가 발생했습니다.</p>"
                    + "<p><b>사유:</b> <span style='color: #d9534f;'>" + failReason + "</span></p>"
                    + "<p>일시 차단을 바로 해제하시려면 아래 버튼을 눌러주세요.</p>"
                    + "<p style='color: red;'><b>※ 이 링크는 15분 동안만 유효합니다.</b></p>"
                    + "<a href='" + unlockUrl + "' style='display: inline-block; padding: 10px 20px; color: white; background-color: #007bff; text-decoration: none; border-radius: 5px; font-weight: bold;'>즉시 차단 해제하기</a>"
                    + "<hr style='border: 0; border-top: 1px solid #eee; margin: 20px 0;'>"
                    + "<p style='font-size: 13px; color: #666;'>본인이 시도한 것이 아니라면 즉시 비밀번호를 변경해 주세요.</p>"
                    + "</div>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.warn("[경고/해제 메일 발송 성공] 회원 ID: {}, 이메일: {}, 사유: {}", loginId, toEmail, failReason);

        } catch (MessagingException | MailException e) {
            // 메일 장애가 로그인 차단 응답을 서버 오류로 바꾸지 않도록 처리한다.
            log.error("[경고 메일 발송 실패] 회원 ID: {}", loginId, e);
        }
    }

    /**
     * 계정 잠금 해제 메일 발송
     */
    @Override
    public void sendUnlockEmail(String loginId, String toEmail) {
        // 1. 유효기간 15분짜리 토큰 생성
        String token = unlockTokenService.createUnlockToken(loginId);

        // 2. 잠금 해제 URL 생성 (서버 도메인에 맞게 수정)
        String unlockUrl = "http://localhost:8080/auth/unlock-account?token=" + token;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("[example.demo] 계정 잠금 해제 안내");

            String htmlContent = "<div style='font-family: Arial, sans-serif; padding: 20px;'>"
                    + "<h2>계정이 연속 실패로 인해 잠금 처리되었습니다.</h2>"
                    + "<p>본인이 요청한 경우 아래 버튼을 클릭하여 계정 잠금을 해제해 주세요.</p>"
                    + "<p style='color: red;'><b>※ 이 링크는 15분 동안만 유효합니다.</b></p>"
                    + "<a href='" + unlockUrl + "' style='display: inline-block; padding: 10px 20px; color: white; background-color: #007bff; text-decoration: none; border-radius: 5px;'>계정 잠금 해제하기</a>"
                    + "</div>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("[메일 발송 성공] 회원 ID: {}, 이메일: {}", loginId, toEmail);

        } catch (MessagingException | MailException e) {
            // 계정 잠금은 유지하고 메일 발송 오류는 로그로 확인한다.
            log.error("[메일 발송 실패] 회원 ID: {}", loginId, e);
        }
    }
}
