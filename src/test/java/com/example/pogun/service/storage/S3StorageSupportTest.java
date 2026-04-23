package com.example.pogun.service.storage;

import com.example.pogun.config.s3.S3StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3StorageSupportTest {

    @Mock
    private S3Client s3Client;

    private S3StorageSupport s3StorageSupport;
    private S3StorageProperties s3StorageProperties;

    @BeforeEach
    void setUp() {
        s3StorageProperties = new S3StorageProperties();
        s3StorageProperties.setBucket("2026capstone-ktw");
        s3StorageProperties.setRegion("ap-northeast-2");
        s3StorageSupport = new S3StorageSupport(s3Client, s3StorageProperties);
    }

    @Test
    void buildObjectKeyKeepsUploadsPrefix() {
        UUID ownerId = UUID.fromString("226b3446-7355-455a-8fd4-c7fe5e219c0b");

        String objectKey = s3StorageSupport.buildObjectKey("community", "posts", ownerId, "photo.png");

        assertThat(objectKey).isEqualTo("uploads/community/posts/226b3446-7355-455a-8fd4-c7fe5e219c0b/photo.png");
    }

    @Test
    void buildPublicUrlPrefersConfiguredBaseUrl() {
        s3StorageProperties.setPublicBaseUrl("https://2026capstone-ktw.s3.amazonaws.com/");

        String publicUrl = s3StorageSupport.buildPublicUrl("uploads/community/posts/test.png");

        assertThat(publicUrl).isEqualTo("https://2026capstone-ktw.s3.amazonaws.com/uploads/community/posts/test.png");
    }

    @Test
    void uploadLocalFileSkipsWhenSameSizeObjectAlreadyExists(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("test.png");
        Files.write(file, new byte[]{1, 2, 3});
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(3L).build());

        S3StorageSupport.UploadDisposition disposition = s3StorageSupport.uploadLocalFile(file, "uploads/test.png");

        assertThat(disposition).isEqualTo(S3StorageSupport.UploadDisposition.SKIPPED_EXISTING);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadLocalFileTreatsForbiddenHeadAsMissingObject(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("test.png");
        Files.write(file, new byte[]{1, 2, 3});
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow((S3Exception) S3Exception.builder().statusCode(403).message("Forbidden").build());

        S3StorageSupport.UploadDisposition disposition = s3StorageSupport.uploadLocalFile(file, "uploads/test.png");

        assertThat(disposition).isEqualTo(S3StorageSupport.UploadDisposition.UPLOADED);
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
