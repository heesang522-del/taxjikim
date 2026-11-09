package com.kh.back.common.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "elasticsearch.connection-check.enabled", havingValue = "true")
public class ElasticsearchConnectionCheck implements ApplicationRunner {

    private final ElasticsearchClient elasticsearchClient;

    @Override
    public void run(ApplicationArguments args) {
        // 확인 옵션을 켠 실행에서만 서버 정보를 요청한다. 거래 데이터는 읽거나 저장하지 않는다.
        try {
            InfoResponse response = elasticsearchClient.info();
            log.info("Elasticsearch 연결 성공: cluster={}, version={}",
                    response.clusterName(), response.version().number());
        } catch (IOException | RuntimeException exception) {
            // 연결 진단 실패로 기존 기능을 중단하지 않으며 인증 정보가 섞일 수 있는 원문은 출력하지 않는다.
            log.warn("Elasticsearch 연결 확인 실패: {}. 서버 실행 상태, 주소, 계정, 비밀번호, CA 인증서 설정을 확인하세요.",
                    exception.getClass().getSimpleName());
        }
    }
}
