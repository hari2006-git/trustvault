
package com.trustvault.trustvault.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class LocalFileStorageService {

    private final Path storageDirectory;
    private final long maxFileBytes;

    public LocalFileStorageService(
            @Value("${trustvault.storage-path:./private-storage}")
            String storagePath,
            @Value("${trustvault.max-file-bytes:2147483648}")
            long maxFileBytes) {

        this.storageDirectory = Path.of(storagePath)
                .toAbsolutePath()
                .normalize();

        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException(
                    "Maximum file size must be positive.");
        }

        this.maxFileBytes = maxFileBytes;
    }

    public StoredFile store(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException(
                    "Input stream must not be null.");
        }

        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available.", exception);
        }

        Files.createDirectories(storageDirectory);

        String storageKey = UUID.randomUUID().toString();

        Path target = storageDirectory.resolve(storageKey).normalize();

        if (!target.startsWith(storageDirectory)) {
            throw new IOException("Invalid storage path.");
        }

        long totalBytes = 0;
        byte[] buffer = new byte[8192];

        try (OutputStream outputStream = Files.newOutputStream(
                target,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {

            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (bytesRead > maxFileBytes - totalBytes) {
                    throw new IOException(
                            "File exceeds the configured size limit.");
                }

                totalBytes += bytesRead;
                digest.update(buffer, 0, bytesRead);
                outputStream.write(buffer, 0, bytesRead);
            }

        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            throw exception;
        }

        String checksumSha256 = HexFormat.of().formatHex(digest.digest());

        return new StoredFile(storageKey, totalBytes, checksumSha256);
    }

    public InputStream open(String storageKey) throws IOException {
        Path file = resolveStorageKey(storageKey);

        if (!Files.isRegularFile(file)) {
            throw new IOException("Stored file was not found.");
        }

        return Files.newInputStream(file);
    }

    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolveStorageKey(storageKey));
    }

    private Path resolveStorageKey(String storageKey) throws IOException {
        if (storageKey == null
                || !storageKey.matches(
                        "^[0-9a-fA-F-]{36}$")) {
            throw new IOException("Invalid storage key.");
        }

        Path file = storageDirectory.resolve(storageKey).normalize();

        if (!file.startsWith(storageDirectory)) {
            throw new IOException("Invalid storage path.");
        }

        return file;
    }

    public record StoredFile(
            String storageKey,
            long sizeBytes,
            String checksumSha256) {
    }
}
