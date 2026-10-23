package com.highpass.runspot.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 서명은 오프라인 연산이므로 더미 자격증명만 고정하면 테스트에서도 실제 presigned URL을 만들 수 있다.
 * 운영 빈은 DefaultCredentialsProvider를 쓰기 때문에 CI에서 자격증명 해석에 실패한다.
 */
@Configuration
@Profile("test")
public class TestS3PresignerConfig {

    @Bean
    @Primary
    S3Presigner testS3Presigner() {
        return S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build();
    }
}
