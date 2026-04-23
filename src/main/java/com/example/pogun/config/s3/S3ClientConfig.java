package com.example.pogun.config.s3;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 애플리케이션 전역에서 재사용하는 S3 클라이언트를 구성한다.
 */
@Configuration
public class S3ClientConfig {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(S3StorageProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.requiredRegion()))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .build();
    }
}
