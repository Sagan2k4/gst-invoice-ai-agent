package com.gstagent.storage;

import com.gstagent.common.ApiException;
import com.gstagent.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** Stores uploaded bills on local disk, partitioned by business. Swap for S3/GCS in production. */
@Service
public class FileStorageService {

    public static final Map<String, String> ALLOWED_TYPES = Map.of(
            "application/pdf", "pdf",
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final Path root;

    public FileStorageService(AppProperties props) {
        this.root = Path.of(props.storage().dir()).toAbsolutePath().normalize();
    }

    public record StoredFile(String relativePath, String contentType, String sha256, byte[] bytes) {
    }

    public StoredFile store(Long businessId, MultipartFile file) {
        String contentType = file.getContentType();
        String ext = contentType == null ? null : ALLOWED_TYPES.get(contentType);
        if (ext == null) {
            throw ApiException.badRequest("Only PDF, JPG, PNG or WEBP bills are supported");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                throw ApiException.badRequest("Uploaded file is empty");
            }
            String relative = businessId + "/" + UUID.randomUUID() + "." + ext;
            Path target = root.resolve(relative);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return new StoredFile(relative, contentType, sha256(bytes), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store upload", e);
        }
    }

    public byte[] read(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw ApiException.badRequest("Invalid path");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw ApiException.notFound("Stored file");
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
