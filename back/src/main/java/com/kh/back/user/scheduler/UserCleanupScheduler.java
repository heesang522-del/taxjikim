package com.kh.back.user.scheduler;

import com.kh.back.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCleanupScheduler {

    private final UserMapper userMapper;

    /**
     * 매일 새벽 3시에 실행 (Cron 표현식: 초 분 시 일 월 요일)
     * 탈퇴 요청(deleted_at) 후 30일이 지난 회원을 영구 삭제합니다.
     */
    @Scheduled(cron = "${custom.cron:*/10 * * * * *}")
    @Transactional
    public void deleteExpiredUsersJob() {
        // 기준 시각: 현재로부터 30일 전
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);

        log.info("============== [탈퇴 회원 영구 삭제 작업 시작] ==============");
        log.info("기준 일시(30일 전): {}", cutoffDate);

        try {
            int deletedCount = userMapper.deleteExpiredUsers(cutoffDate);
            log.info("영구 삭제 완료된 회원 수: {}명", deletedCount);
        } catch (Exception e) {
            log.error("탈퇴 회원 자동 삭제 중 오류 발생: ");
        }

        log.info("============== [탈퇴 회원 영구 삭제 작업 종료] ==============");
    }
}