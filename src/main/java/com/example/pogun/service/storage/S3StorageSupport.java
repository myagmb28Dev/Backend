package com.example.pogun.service.storage;

import com.example.pogun.config.s3.S3StorageProperties;
import com.example.pogun.dto.common.ApiResponse.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * S3 객체 키, 퍼블릭 URL, 업로드 동작을 공통으로 처리한다.
 */
@Component
@RequiredArgsConstructor
public class S3StorageSupport {

    public static final String UPLOADS_PREFIX = "uploads";
    public static final String LEGACY_UPLOADS_URL_PREFIX = "/uploads/";

    private final S3Client s3Client;
    private final S3StorageProperties properties;

    public String buildObjectKey(String domain, String resourceType, UUID ownerId, String filename) {
        return UPLOADS_PREFIX + "/"
                + sanitizeSegment(domain) + "/"
                + sanitizeSegment(resourceType) + "/"
                + ownerId + "/"
                + sanitizeFilename(filename);
    }

    public String buildObjectKeyFromLegacyUrl(String legacyUrl) {
        if (legacyUrl == null || !legacyUrl.startsWith(LEGACY_UPLOADS_URL_PREFIX)) {
            throw new IllegalArgumentException("레거시 uploads URL 형식이 아닙니다: " + legacyUrl);
        }
        return legacyUrl.substring(1);
    }

    public String buildPublicUrl(String objectKey) {
        String publicBaseUrl = properties.normalizedPublicBaseUrl();
        if (publicBaseUrl != null) {
            return publicBaseUrl + "/" + objectKey;
        }
        return "https://" + properties.requiredBucket() + ".s3." + properties.requiredRegion() + ".amazonaws.com/" + objectKey;
    }

    public String legacyReplacementPrefix() {
        return buildPublicUrl(UPLOADS_PREFIX) + "/";
    }

    public void uploadMultipartFile(String objectKey, MultipartFile file, String contentType) {
        try (var inputStream = file.getInputStream()) {
            s3Client.putObject(putObjectRequest(objectKey, normalizeContentType(contentType, file.getOriginalFilename())),
                    RequestBody.fromInputStream(inputStream, file.getSize()));
        } catch (IOException e) {
            throw ApiException.internal("IMAGE_UPLOAD_FAILED", "첨부 파일을 읽지 못했습니다.");
        } catch (S3Exception e) {
            throw ApiException.internal("IMAGE_UPLOAD_FAILED", "첨부 파일을 저장하지 못했습니다.");
        }
    }

    public void uploadBytes(String objectKey, byte[] bytes, String contentType) {
        try {
            s3Client.putObject(putObjectRequest(objectKey, normalizeContentType(contentType, objectKey)),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            throw ApiException.internal("IMAGE_UPLOAD_FAILED", "변환 이미지를 저장하지 못했습니다.");
        }
    }

    public UploadDisposition uploadLocalFile(Path file, String objectKey) {
        try {
            long localSize = Files.size(file);
            HeadObjectResponse headObject = headObject(objectKey);
            if (headObject != null) {
                if (headObject.contentLength() == localSize) {
                    return UploadDisposition.SKIPPED_EXISTING;
                }
                throw new IllegalStateException("동일 키에 크기가 다른 객체가 이미 존재합니다: " + objectKey);
            }

            s3Client.putObject(putObjectRequest(objectKey, detectContentType(file)),
                    RequestBody.fromFile(file));
            return UploadDisposition.UPLOADED;
        } catch (IOException e) {
            throw new IllegalStateException("로컬 파일을 읽지 못했습니다: " + file, e);
        } catch (S3Exception e) {
            throw new IllegalStateException("S3 업로드에 실패했습니다. key=" + objectKey, e);
        }
    }

    private PutObjectRequest putObjectRequest(String objectKey, String contentType) {
        return PutObjectRequest.builder()
                .bucket(properties.requiredBucket())
                .key(objectKey)
                .contentType(contentType)
                .build();
    }

    private HeadObjectResponse headObject(String objectKey) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.requiredBucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404 || e.statusCode() == 403) {
                return null;
            }
            throw e;
        }
    }

    private String detectContentType(Path file) {
        return MediaTypeFactory.getMediaType(file.getFileName().toString())
                .map(MediaType::toString)
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    private String normalizeContentType(String contentType, String filename) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType.toLowerCase(Locale.ROOT);
        }
        return MediaTypeFactory.getMediaType(filename == null ? "" : filename)
                .map(MediaType::toString)
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    private String sanitizeSegment(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("INVALID_IMAGE_PATH", "이미지 경로 세그먼트가 비어 있습니다.");
        }
        return value.trim().replace("\\", "-").replace("/", "-");
    }

    private String sanitizeFilename(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("INVALID_IMAGE_PATH", "파일 이름이 비어 있습니다.");
        }
        return value.trim().replace("\\", "-").replace("/", "-");
    }

    public enum UploadDisposition {
        UPLOADED,
        SKIPPED_EXISTING
    }
}
