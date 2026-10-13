package com.kh.back.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    private final String region;

    // � application.yml의 aws.s3 구조에 맞게 수정
    private final String accessKey;

    // � application.yml의 aws.s3 구조에 맞게 수정
    private final String secretKey;

    private final String endpoint;

    public S3Config(@Value("${aws.s3.region}") String region,
                    @Value("${aws.s3.access-key}") String accessKey,
                    @Value("${aws.s3.secret-key}") String secretKey,
                    @Value("${aws.s3.endpoint:#{null}}") String endpoint) {
        this.region = region;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.endpoint = endpoint;
    }

    @Bean
    public S3Presigner s3Presigner() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }
}
