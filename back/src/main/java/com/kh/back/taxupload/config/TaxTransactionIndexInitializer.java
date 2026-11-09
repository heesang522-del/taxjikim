package com.kh.back.taxupload.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "elasticsearch.index-initialize.enabled", havingValue = "true")
public class TaxTransactionIndexInitializer implements ApplicationRunner {

    private static final String INDEX_NAME = "tax-transactions";

    private final ElasticsearchClient elasticsearchClient;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 이미 사용하는 인덱스는 삭제하거나 설정을 덮어쓰지 않는다.
            if (elasticsearchClient.indices().exists(request -> request.index(INDEX_NAME)).value()) {
                log.info("거래 검색 인덱스가 이미 있습니다: {}", INDEX_NAME);
                return;
            }

            // 설명은 Nori로 분석하고 분류·거래 유형은 정확히 일치하는 필터에 사용한다.
            // 이번 단계는 검색 공간만 생성하며 MySQL 거래를 읽거나 복사하지 않는다.
            ClassPathResource mapping = new ClassPathResource("elasticsearch/tax-transactions.json");
            try (InputStream input = mapping.getInputStream()) {
                boolean acknowledged = elasticsearchClient.indices()
                        .create(request -> request.index(INDEX_NAME).withJson(input))
                        .acknowledged();
                if (acknowledged) {
                    log.info("거래 검색 인덱스 생성 성공: {}", INDEX_NAME);
                } else {
                    log.warn("거래 검색 인덱스 생성 응답이 지연되었습니다. 인덱스 존재 여부를 확인하세요: {}", INDEX_NAME);
                }
            }
        } catch (IOException | RuntimeException exception) {
            // 초기화 실패가 기존 계산 기능을 중단하지 않도록 하며 민감한 응답 원문은 기록하지 않는다.
            log.warn("거래 검색 인덱스 초기화 실패: {}. 연결 설정과 analysis-nori 설치 여부를 확인하세요.",
                    exception.getClass().getSimpleName());
        }
    }
}
