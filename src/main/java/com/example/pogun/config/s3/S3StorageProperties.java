package com.example.pogun.config.s3;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * S3 저장소 설정값을 바인딩한다.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "app.storage.s3")
public class S3StorageProperties {

    @NotBlank(message = "AWS_S3_BUCKET 설정이 필요합니다.")
    private String bucket;

    @NotBlank(message = "AWS_REGION 설정이 필요합니다.")
    private String region = "ap-northeast-2";

    private String publicBaseUrl;

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String requiredBucket() {
        return bucket.trim();
    }

    public String requiredRegion() {
        return region.trim();
    }

    public String normalizedPublicBaseUrl() {
        if (publicBaseUrl == null) {
            return null;
        }
        String trimmed = publicBaseUrl.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
