package com.example.pogun.service.storage;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalImageStorageServiceTest {

    @Test
    void storeImage_acceptsPngEvenWhenContentTypeIsOctetStream() {
        LocalImageStorageService service = new LocalImageStorageService("build/test-uploads");
        MockMultipartFile file = new MockMultipartFile(
                "images",
                "sample.png",
                "application/octet-stream",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00}
        );

        String path = service.storeImage("community", "posts", UUID.randomUUID(), file);

        assertThat(path).startsWith("/uploads/community/posts/");
        assertThat(path).endsWith(".png");
    }
}
