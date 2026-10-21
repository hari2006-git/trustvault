package com.trustvault.trustvault.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class LocalFileStorageService {

    private final Path storageDirectory;
    private final long maxFileBytes;
    private final S3Client s3Client;
    private final String bucket;

    public LocalFileStorageService(
            @Value("${trustvault.storage-path:./private-storage}")
            String storagePath,
            @Value("${trustvault.max-file-bytes:2147483648}")
            long maxFileBytes,
            @Value("${AWS_ENDPOINT_URL_S3:}")
            String endpoint,
            @Value("${AWS_ACCESS_KEY_ID:}")
            String accessKey,
            @Value("${AWS_SECRET_ACCESS_KEY:}")
            String secretKey,
            @Value("${AWS_REGION:us-east-2}")
            String region,
            @Value("${TRUSTVAULT_STORAGE_BUCKET:trustvault-documents}")
            String bucket) {

        this.storageDirectory = Path.of(storagePath)
                .toAbsolutePath().normalize();

        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException(
                    "Maximum file size must be positive.");
        }
        this.maxFileBytes = maxFileBytes;
        this.bucket = bucket;

        boolean hasEndpoint = endpoint != null && !endpoint.isBlank();
        boolean hasAccessKey = accessKey != null && !accessKey.isBlank();
        boolean hasSecretKey = secretKey != null && !secretKey.isBlank();

        if (hasEndpoint && (!hasAccessKey || !hasSecretKey)) {
            throw new IllegalStateException(
                    "S3 endpoint is configured but storage credentials are missing.");
        }

        if (hasEndpoint) {
            this.s3Client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .build();
        } else {
            this.s3Client = null;
        }
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

        boolean useS3 = s3Client != null;
        Path tempFile;

        if (useS3) {
            tempFile = Files.createTempFile("trustvault-upload-", ".tmp");
        } else {
            Files.createDirectories(storageDirectory);
            tempFile = Files.createTempFile(
                    storageDirectory, ".upload-", ".tmp");
        }

        long totalBytes = 0;
        byte[] buffer = new byte[8192];

        try {
            try (OutputStream output = Files.newOutputStream(
                    tempFile, StandardOpenOption.WRITE)) {
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (bytesRead > maxFileBytes - totalBytes) {
                        throw new IOException(
                                "File exceeds the configured size limit.");
                    }
                    totalBytes += bytesRead;
                    digest.update(buffer, 0, bytesRead);
                    output.write(buffer, 0, bytesRead);
                }
            }

            String storageKey = UUID.randomUUID().toString();
            String checksum = HexFormat.of().formatHex(digest.digest());

            if (useS3) {
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(storageKey)
                                .build(),
                        RequestBody.fromFile(tempFile));
            } else {
                Path target = storageDirectory.resolve(storageKey).normalize();
                if (!target.startsWith(storageDirectory)) {
                    throw new IOException("Invalid storage path.");
                }
                Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE);
            }

            return new StoredFile(storageKey, totalBytes, checksum);

        } catch (RuntimeException | IOException exception) {
            throw exception;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public InputStream open(String storageKey) throws IOException {
        validateStorageKey(storageKey);

        if (s3Client != null) {
            try {
                return s3Client.getObject(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(storageKey)
                        .build());
            } catch (RuntimeException exception) {
                throw new IOException("Could not open stored file.", exception);
            }
        }

        Path file = resolveStorageKey(storageKey);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Stored file was not found.");
        }
        return Files.newInputStream(file);
    }

    public void delete(String storageKey) throws IOException {
        validateStorageKey(storageKey);

        if (s3Client != null) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(storageKey)
                        .build());
            } catch (RuntimeException exception) {
                throw new IOException("Could not delete stored file.", exception);
            }
            return;
        }

        Files.deleteIfExists(resolveStorageKey(storageKey));
    }

    private void validateStorageKey(String storageKey) throws IOException {
        if (storageKey == null
                || !storageKey.matches("^[0-9a-fA-F-]{36}$")) {
            throw new IOException("Invalid storage key.");
        }
    }

    private Path resolveStorageKey(String storageKey) throws IOException {
        validateStorageKey(storageKey);

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
