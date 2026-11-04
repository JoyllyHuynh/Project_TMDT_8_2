package com.example.demo.service;

import com.example.demo.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ReviewImageStorage {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final Path root;

    public ReviewImageStorage(@Value("${app.upload.review-dir:uploads/reviews}") String directory) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo thư mục lưu ảnh đánh giá.", exception);
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Vui lòng chọn ảnh cần tải lên.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw ApiException.badRequest("Mỗi ảnh tối đa 5MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw ApiException.badRequest("Tệp tải lên phải là ảnh.");
        }

        String extension = resolveExtension(file.getOriginalFilename(), contentType);
        String fileName = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.badRequest("Tên tệp không hợp lệ.");
        }

        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể lưu ảnh đánh giá.", exception);
        }

        return "/uploads/reviews/" + fileName;
    }

    public Path getRoot() {
        return root;
    }

    private String resolveExtension(String originalName, String contentType) {
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0 && dot < originalName.length() - 1) {
                String candidate = originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (ALLOWED_EXTENSIONS.contains(candidate)) {
                    return candidate;
                }
            }
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }
}
