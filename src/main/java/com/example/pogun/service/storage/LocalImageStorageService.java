package com.example.pogun.service.storage;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.storage.StoredImageVariant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 도메인/용도/소유자 기준으로 로컬 이미지를 저장하는 공통 서비스이다.
 */
@Slf4j
@Service
public class LocalImageStorageService {

    private static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.APPLICATION_OCTET_STREAM_VALUE
    );

    private final Path rootDirectory;

    public LocalImageStorageService(@Value("${app.upload.root:uploads}") String uploadRoot) {
        this.rootDirectory = Paths.get(uploadRoot).toAbsolutePath().normalize();
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
        return storeImageVariant(domain, resourceType, ownerId, file).webpUrl();
    }

    public List<StoredImageVariant> storeImageVariants(String domain, String resourceType, UUID ownerId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<StoredImageVariant> storedImages = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            storedImages.add(storeImageVariant(domain, resourceType, ownerId, file));
        }
        return storedImages;
    }

    public StoredImageVariant storeImageVariant(String domain, String resourceType, UUID ownerId, MultipartFile file) {
        validateImage(file);

        String baseName = UUID.randomUUID().toString();
        String originalFilename = baseName + extractExtension(file.getOriginalFilename());
        String webpFilename = baseName + ".webp";
        String mediumFilename = baseName + "-medium.webp";
        String thumbnailFilename = baseName + "-thumbnail.webp";
        String previewFilename = baseName + "-preview.webp";
        List<Path> writtenFiles = new ArrayList<>();

        try {
            Path targetDirectory = resolveTargetDirectory(domain, resourceType, ownerId);
            Files.createDirectories(targetDirectory);
            Path originalTarget = targetDirectory.resolve(originalFilename);
            Path webpTarget = targetDirectory.resolve(webpFilename);
            Path mediumTarget = targetDirectory.resolve(mediumFilename);
            Path thumbnailTarget = targetDirectory.resolve(thumbnailFilename);
            Path previewTarget = targetDirectory.resolve(previewFilename);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, originalTarget);
            }
            writtenFiles.add(originalTarget);
            writeWebp(file, webpTarget, null);
            writtenFiles.add(webpTarget);
            writeWebp(file, mediumTarget, 960);
            writtenFiles.add(mediumTarget);
            writeWebp(file, thumbnailTarget, 320);
            writtenFiles.add(thumbnailTarget);
            writeWebp(file, previewTarget, 80);
            writtenFiles.add(previewTarget);

            return new StoredImageVariant(
                    buildPublicPath(domain, resourceType, ownerId, originalFilename),
                    buildPublicPath(domain, resourceType, ownerId, webpFilename),
                    buildPublicPath(domain, resourceType, ownerId, mediumFilename),
                    buildPublicPath(domain, resourceType, ownerId, thumbnailFilename),
                    buildPublicPath(domain, resourceType, ownerId, previewFilename)
            );
        } catch (IOException e) {
            for (Path writtenFile : writtenFiles) {
                try {
                    Files.deleteIfExists(writtenFile);
                } catch (IOException deleteFailure) {
                    log.warn("이미지 저장 실패 후 파일 정리에 실패했습니다. path={}", writtenFile, deleteFailure);
                }
            }
            throw ApiException.internal("IMAGE_UPLOAD_FAILED", "이미지를 저장하지 못했습니다.");
        }
    }

    private Path resolveTargetDirectory(String domain, String resourceType, UUID ownerId) {
        return rootDirectory
                .resolve(sanitizeSegment(domain))
                .resolve(sanitizeSegment(resourceType))
                .resolve(ownerId.toString());
    }

    private String buildPublicPath(String domain, String resourceType, UUID ownerId, String filename) {
        return "/uploads/"
                + sanitizeSegment(domain) + "/"
                + sanitizeSegment(resourceType) + "/"
                + ownerId + "/"
                + filename;
    }

    private void writeWebp(MultipartFile file, Path target, Integer maxWidth) throws IOException {
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

        try (ImageOutputStream outputStream = ImageIO.createImageOutputStream(Files.newOutputStream(target))) {
            writer.setOutput(outputStream);
            writer.write(null, new IIOImage(normalizedImage, null, null), writer.getDefaultWriteParam());
        } finally {
            writer.dispose();
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

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("INVALID_IMAGE", "이미지가 비어 있습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw ApiException.badRequest("INVALID_IMAGE", "이미지 크기는 5MB 이하여야 합니다.");
        }

        String filename = file.getOriginalFilename();
        String extension = extractExtension(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw ApiException.badRequest("INVALID_IMAGE", "허용되지 않는 이미지 확장자입니다.");
        }

        try {
            String contentType = normalizeContentType(file.getContentType());
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

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private String sanitizeSegment(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("INVALID_IMAGE_PATH", "이미지 경로 세그먼트가 비어 있습니다.");
        }
        return value.trim().replace("\\", "-").replace("/", "-");
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
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
        return ImageFormat.UNKNOWN;
    }

    private boolean matchesExpectedFormat(String contentType, ImageFormat imageFormat) {
        if (contentType.isBlank() || ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return true;
        }
        if (MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType)) {
            return true;
        }
        if (contentType.startsWith("image/")) {
            return (imageFormat == ImageFormat.PNG && MediaType.IMAGE_PNG_VALUE.equals(contentType))
                    || (imageFormat == ImageFormat.JPEG && MediaType.IMAGE_JPEG_VALUE.equals(contentType));
        }
        return false;
    }

    private enum ImageFormat {
        PNG,
        JPEG,
        UNKNOWN
    }
}
