package com.kh.back.member.scheduler;

import com.kh.back.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DormantScheduler {

    private final MemberRepository memberRepository;

    // 매일 새벽 3시 자동 실행
    @Scheduled(cron = "${custom.cron:*/10 * * * * *}")
    @Transactional
    public void processDormantAccounts() {
        // 기준 시각: 현재로부터 6개월 전 (테스트 시 .minusSeconds(10) 등으로 변경하여 테스트 가능!)
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(6);

        log.info("============== [휴면 계정 자동 전환 작업 시작] ==============");
        log.info("기준 일시(6개월 전): {}", cutoffDate);

        try {
            int updatedCount = memberRepository.convertToDormantAccounts(cutoffDate);
            log.info("휴면 전환 완료된 회원 수: {}명", updatedCount);
        } catch (Exception e) {
            log.error("휴면 계정 자동 전환 중 오류 발생: ");
        }

        log.info("============== [휴면 계정 자동 전환 작업 종료] ==============");
    }
}
