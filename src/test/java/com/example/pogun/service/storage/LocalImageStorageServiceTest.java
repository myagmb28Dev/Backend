package com.example.pogun.service.storage;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalImageStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storeImage_convertsInputToWebp() throws IOException {
        LocalImageStorageService service = new LocalImageStorageService(tempDir.toString());
        UUID ownerId = UUID.randomUUID();

        String publicPath = service.storeImage(
                "community",
                "posts",
                ownerId,
                new MockMultipartFile("image", "sample.jpg", "image/jpeg", createImageBytes("jpg"))
        );

        assertThat(publicPath).startsWith("/uploads/community/posts/" + ownerId + "/");
        assertThat(publicPath).endsWith(".webp");
        Path storedFile = tempDir.resolve(publicPath.replace("/uploads/", "").replace("/", "\\"));
        assertThat(Files.exists(storedFile)).isTrue();
    }

    @Test
    void storeImages_preservesOrder() throws IOException {
        LocalImageStorageService service = new LocalImageStorageService(tempDir.toString());
        UUID ownerId = UUID.randomUUID();

        List<String> publicPaths = service.storeImages(
                "missing-pets",
                "notices",
                ownerId,
                List.of(
                        new MockMultipartFile("images", "one.png", "image/png", createImageBytes("png")),
                        new MockMultipartFile("images", "two.jpg", "image/jpeg", createImageBytes("jpg"))
                )
        );

        assertThat(publicPaths).hasSize(2);
        assertThat(publicPaths).allMatch(path -> path.endsWith(".webp"));
    }

    @Test
    void storeImage_rejectsFilesOverFiveMegabytes() {
        LocalImageStorageService service = new LocalImageStorageService(tempDir.toString());
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];

        assertThatThrownBy(() -> service.storeImage(
                "profile",
                "users",
                UUID.randomUUID(),
                new MockMultipartFile("image", "large.jpg", "image/jpeg", oversized)
        ))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("INVALID_IMAGE"));
    }

    @Test
    void storeImage_rejectsNonImagePayload() {
        LocalImageStorageService service = new LocalImageStorageService(tempDir.toString());

        assertThatThrownBy(() -> service.storeImage(
                "profile",
                "users",
                UUID.randomUUID(),
                new MockMultipartFile("image", "bad.png", "image/png", "not-an-image".getBytes())
        ))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("INVALID_IMAGE"));
    }

    private byte[] createImageBytes(String format) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.ORANGE.getRGB());
        image.setRGB(1, 1, Color.BLUE.getRGB());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, format, outputStream);
        return outputStream.toByteArray();
    }
}
