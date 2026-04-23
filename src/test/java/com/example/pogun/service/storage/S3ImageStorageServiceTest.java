package com.example.pogun.service.storage;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ImageStorageServiceTest {

    @Mock
    private S3StorageSupport s3StorageSupport;

    private S3ImageStorageService s3ImageStorageService;

    @BeforeEach
    void setUp() {
        s3ImageStorageService = new S3ImageStorageService(s3StorageSupport);
    }

    @Test
    void storeImageUploadsToS3AndReturnsAbsoluteUrl() {
        UUID ownerId = UUID.fromString("226b3446-7355-455a-8fd4-c7fe5e219c0b");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "profile.jpg",
                "image/jpeg",
                createJpegBytes()
        );
        when(s3StorageSupport.buildObjectKey(eq("profile"), eq("users"), eq(ownerId), anyString()))
                .thenAnswer(invocation -> "uploads/profile/users/" + ownerId + "/" + invocation.getArgument(3, String.class));
        when(s3StorageSupport.buildPublicUrl(anyString()))
                .thenAnswer(invocation -> "https://2026capstone-ktw.s3.amazonaws.com/" + invocation.getArgument(0, String.class));

        String storedUrl = s3ImageStorageService.storeImage("profile", "users", ownerId, file);

        assertThat(storedUrl).startsWith("https://2026capstone-ktw.s3.amazonaws.com/uploads/profile/users/" + ownerId + "/");
        assertThat(storedUrl).endsWith(".webp");
        verify(s3StorageSupport).uploadMultipartFile(anyString(), same(file), eq("image/jpeg"));
        verify(s3StorageSupport, org.mockito.Mockito.times(4))
                .uploadBytes(anyString(), org.mockito.ArgumentMatchers.any(byte[].class), eq("image/webp"));
    }

    @Test
    void storeImageRejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "profile.bmp",
                "image/bmp",
                new byte[]{'B', 'M'}
        );

        assertThatThrownBy(() -> s3ImageStorageService.storeImage("profile", "users", UUID.randomUUID(), file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("허용되지 않는 이미지 확장자");
        verifyNoInteractions(s3StorageSupport);
    }

    @Test
    void storeImageStoresGifAsOriginalWithoutVariantGeneration() {
        UUID ownerId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "profile.gif",
                "image/gif",
                new byte[]{'G', 'I', 'F', '8', '9', 'a'}
        );
        when(s3StorageSupport.buildObjectKey(eq("profile"), eq("users"), eq(ownerId), anyString()))
                .thenAnswer(invocation -> "uploads/profile/users/" + ownerId + "/" + invocation.getArgument(3, String.class));
        when(s3StorageSupport.buildPublicUrl(anyString()))
                .thenAnswer(invocation -> "https://2026capstone-ktw.s3.amazonaws.com/" + invocation.getArgument(0, String.class));

        String storedUrl = s3ImageStorageService.storeImage("profile", "users", ownerId, file);

        assertThat(storedUrl).endsWith(".gif");
        verify(s3StorageSupport).uploadMultipartFile(anyString(), same(file), eq("image/gif"));
        verify(s3StorageSupport, never()).uploadBytes(anyString(), org.mockito.ArgumentMatchers.any(byte[].class), anyString());
    }

    private byte[] createJpegBytes() {
        try {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "jpeg", outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
