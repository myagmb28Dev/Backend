package com.example.pogun.service.storage;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.storage.StoredImageVariant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 도메인/용도/소유자 기준으로 S3에 이미지를 저장하는 공통 서비스이다.
 */
@Service
@Slf4j
public class S3ImageStorageService {

    private static final long MAX_FILE_SIZE = 30L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".mp4");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_GIF_VALUE,
            "video/mp4",
            MediaType.APPLICATION_OCTET_STREAM_VALUE
    );

    private final S3StorageSupport s3StorageSupport;

    public S3ImageStorageService(S3StorageSupport s3StorageSupport) {
        this.s3StorageSupport = s3StorageSupport;
        ImageIO.scanForPlugins();
    }

    public List<String> storeImages(String domain, String resourceType, UUID ownerId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<String> storedPaths = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            storedPaths.add(storeImage(domain, resourceType, ownerId, file));
        }
        return storedPaths;
    }

    public String storeImage(String domain, String resourceType, UUID ownerId, MultipartFile file) {
        StoredImageVariant variant = storeImageVariant(domain, resourceType, ownerId, file);
        return variant.webpUrl() != null ? variant.webpUrl() : variant.originalUrl();
    }

    public List<StoredImageVariant> storeImageVariants(String domain, String resourceType, UUID ownerId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<StoredImageVariant> variants = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            variants.add(storeImageVariant(domain, resourceType, ownerId, file));
        }
        return variants;
    }

    public StoredImageVariant storeImageVariant(String domain, String resourceType, UUID ownerId, MultipartFile file) {
        validateImage(file);

        String baseName = UUID.randomUUID().toString();
        String extension = extractExtension(file.getOriginalFilename());
        String originalFilename = baseName + extension;
        String contentType = normalizeContentType(file.getContentType());

        if (".gif".equals(extension) || ".mp4".equals(extension)) {
            return storeOriginalMedia(domain, resourceType, ownerId, file, originalFilename, contentType);
        }

        String originalKey = s3StorageSupport.buildObjectKey(domain, resourceType, ownerId, originalFilename);
        String webpKey = s3StorageSupport.buildObjectKey(domain, resourceType, ownerId, baseName + ".webp");
        String mediumKey = s3StorageSupport.buildObjectKey(domain, resourceType, ownerId, baseName + "-medium.webp");
        String thumbnailKey = s3StorageSupport.buildObjectKey(domain, resourceType, ownerId, baseName + "-thumbnail.webp");
        String previewKey = s3StorageSupport.buildObjectKey(domain, resourceType, ownerId, baseName + "-preview.webp");

        s3StorageSupport.uploadMultipartFile(originalKey, file, contentType);
        s3StorageSupport.uploadBytes(webpKey, writeWebp(file, null), "image/webp");
        s3StorageSupport.uploadBytes(mediumKey, writeWebp(file, 960), "image/webp");
        s3StorageSupport.uploadBytes(thumbnailKey, writeWebp(file, 320), "image/webp");
        s3StorageSupport.uploadBytes(previewKey, writeWebp(file, 80), "image/webp");

        return new StoredImageVariant(
                s3StorageSupport.buildPublicUrl(originalKey),
                s3StorageSupport.buildPublicUrl(webpKey),
                s3StorageSupport.buildPublicUrl(mediumKey),
                s3StorageSupport.buildPublicUrl(thumbnailKey),
                s3StorageSupport.buildPublicUrl(previewKey)
        );
    }

    public boolean isVideoFile(MultipartFile file) {
        return ".mp4".equals(extractExtension(file != null ? file.getOriginalFilename() : null))
                || "video/mp4".equals(normalizeContentType(file != null ? file.getContentType() : null));
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("INVALID_IMAGE", "이미지가 비어 있습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw ApiException.badRequest("INVALID_IMAGE", "파일 크기는 30MB 이하여야 합니다.");
        }

        String filename = file.getOriginalFilename();
        String extension = extractExtension(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw ApiException.badRequest("INVALID_IMAGE", "허용되지 않는 이미지 확장자입니다.");
        }

        try {
            String contentType = normalizeContentType(file.getContentType());
            if (".mp4".equals(extension)) {
                if (!MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType) && !"video/mp4".equals(contentType)) {
                    throw ApiException.badRequest("INVALID_IMAGE", "허용되지 않는 영상 형식입니다.");
                }
                if (!isMp4File(file)) {
                    throw ApiException.badRequest("INVALID_IMAGE", "?뚯씪 ?댁슜???좏슚???곸긽??MP4媛 ?꾨떃?덈떎.");
                }
                return;
            }
            ImageFormat imageFormat = detectImageFormat(file);
            if (imageFormat == ImageFormat.UNKNOWN) {
                throw ApiException.badRequest("INVALID_IMAGE", "파일 내용이 유효한 이미지가 아닙니다.");
            }
            if (!matchesExpectedFormat(contentType, imageFormat)) {
                throw ApiException.badRequest("INVALID_IMAGE", "허용되지 않는 이미지 형식입니다.");
            }
        } catch (IOException e) {
            throw ApiException.badRequest("INVALID_IMAGE", "이미지 파일을 읽을 수 없습니다.");
        }
    }

    private StoredImageVariant storeOriginalMedia(String domain, String resourceType, UUID ownerId, MultipartFile file, String filename, String contentType) {
        String originalKey = s3StorageSupport.buildObjectKey(domain, resourceType, ownerId, filename);
        s3StorageSupport.uploadMultipartFile(originalKey, file, contentType);
        String publicUrl = s3StorageSupport.buildPublicUrl(originalKey);
        return new StoredImageVariant(publicUrl, publicUrl, publicUrl, publicUrl, publicUrl);
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
    }

    private byte[] writeWebp(MultipartFile file, Integer maxWidth) {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw ApiException.badRequest("INVALID_IMAGE", "파일 내용이 유효한 이미지가 아닙니다.");
            }
            BufferedImage normalizedImage = normalizeForWebp(resizeIfNeeded(image, maxWidth));

            ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").hasNext()
                    ? ImageIO.getImageWritersByMIMEType("image/webp").next()
                    : null;
            if (writer == null) {
                throw ApiException.internal("IMAGE_UPLOAD_FAILED", "WEBP 변환기를 찾을 수 없습니다.");
            }

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                 ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
                writer.setOutput(imageOutputStream);
                writer.write(null, new IIOImage(normalizedImage, null, null), writer.getDefaultWriteParam());
                imageOutputStream.flush();
                return outputStream.toByteArray();
            } finally {
                writer.dispose();
            }
        } catch (IOException e) {
            throw ApiException.internal("IMAGE_UPLOAD_FAILED", "이미지를 변환하지 못했습니다.");
        }
    }

    private BufferedImage resizeIfNeeded(BufferedImage source, Integer maxWidth) {
        if (maxWidth == null || source.getWidth() <= maxWidth) {
            return source;
        }
        int targetWidth = maxWidth;
        int targetHeight = Math.max(1, (int) Math.round((double) source.getHeight() * targetWidth / source.getWidth()));
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_4BYTE_ABGR
                : BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private BufferedImage normalizeForWebp(BufferedImage source) {
        ColorModel colorModel = source.getColorModel();
        int targetType = colorModel != null && colorModel.hasAlpha()
                ? BufferedImage.TYPE_4BYTE_ABGR
                : BufferedImage.TYPE_3BYTE_BGR;
        BufferedImage normalized = new BufferedImage(source.getWidth(), source.getHeight(), targetType);
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return normalized;
    }

    private ImageFormat detectImageFormat(MultipartFile file) throws IOException {
        byte[] header = file.getInputStream().readNBytes(8);
        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return ImageFormat.PNG;
        }
        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return ImageFormat.JPEG;
        }
        if (header.length >= 6
                && header[0] == 0x47
                && header[1] == 0x49
                && header[2] == 0x46) {
            return ImageFormat.GIF;
        }
        return ImageFormat.UNKNOWN;
    }

    private boolean isMp4File(MultipartFile file) throws IOException {
        byte[] header = file.getInputStream().readNBytes(12);
        return header.length >= 8
                && header[4] == 0x66
                && header[5] == 0x74
                && header[6] == 0x79
                && header[7] == 0x70;
    }

    private boolean matchesExpectedFormat(String contentType, ImageFormat imageFormat) {
        if (contentType.isBlank() || MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType)) {
            return true;
        }
        if (contentType.startsWith("image/")) {
            return (imageFormat == ImageFormat.PNG && MediaType.IMAGE_PNG_VALUE.equals(contentType))
                    || (imageFormat == ImageFormat.JPEG && MediaType.IMAGE_JPEG_VALUE.equals(contentType))
                    || (imageFormat == ImageFormat.GIF && MediaType.IMAGE_GIF_VALUE.equals(contentType));
        }
        return false;
    }

    private enum ImageFormat {
        PNG,
        JPEG,
        GIF,
        UNKNOWN
    }
}
