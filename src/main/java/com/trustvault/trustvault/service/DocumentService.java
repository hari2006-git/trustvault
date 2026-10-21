package com.trustvault.trustvault.service;

import com.trustvault.trustvault.entity.AppUser;
import com.trustvault.trustvault.entity.Document;
import com.trustvault.trustvault.entity.DocumentPermission;
import com.trustvault.trustvault.repository.AppUserRepository;
import com.trustvault.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.trustvault.repository.DocumentRepository;
import com.trustvault.trustvault.storage.LocalFileStorageService;
import com.trustvault.trustvault.storage.LocalFileStorageService.StoredFile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final AppUserRepository appUserRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final LocalFileStorageService fileStorageService;

    public DocumentService(
            DocumentRepository documentRepository,
            AppUserRepository appUserRepository,
            DocumentPermissionRepository permissionRepository,
            LocalFileStorageService fileStorageService) {

        this.documentRepository = documentRepository;
        this.appUserRepository = appUserRepository;
        this.permissionRepository = permissionRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public List<Document> findAllAccessibleToUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }

        Map<UUID, Document> accessible = new LinkedHashMap<>();

        for (Document document :
                documentRepository.findAllByOwner_IdOrderByCreatedAtDesc(
                        userId)) {

            if (!document.isDeleted()) {
                accessible.put(document.getId(), document);
            }
        }

        for (DocumentPermission permission :
                permissionRepository.findAllByUser_IdAndRevokedAtIsNull(
                        userId)) {

            Document document = permission.getDocument();

            if (document != null && !document.isDeleted()) {
                accessible.putIfAbsent(document.getId(), document);
            }
        }

        List<Document> result = new ArrayList<>(accessible.values());

        result.sort(
                Comparator.comparing(
                        Document::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
        );

        return result;
    }

    /**
     * Backward-compatible search method.
     */
    @Transactional(readOnly = true)
    public List<Document> searchAccessibleDocuments(
            UUID userId,
            String name,
            String ownerEmail) {

        return searchAccessibleDocuments(
                userId, name, ownerEmail, null, null, null);
    }

    /**
     * Searches only active documents the user is authorized to discover.
     *
     * Name: case-insensitive partial match.
     * Owner email: case-insensitive exact match.
     * Category: case-insensitive exact match.
     * Date range: inclusive start and inclusive end dates, interpreted in UTC.
     * Null or blank text filters and null date filters are ignored.
     */
    @Transactional(readOnly = true)
    public List<Document> searchAccessibleDocuments(
            UUID userId,
            String name,
            String ownerEmail,
            String category,
            LocalDate createdFrom,
            LocalDate createdTo) {

        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }

        if (createdFrom != null
                && createdTo != null
                && createdFrom.isAfter(createdTo)) {
            throw new IllegalArgumentException(
                    "Start date must not be after end date.");
        }

        String normalizedName = normalize(name);
        String normalizedOwnerEmail = normalize(ownerEmail);
        String normalizedCategory = normalize(category);

        Instant fromInstant = createdFrom == null
                ? null
                : createdFrom.atStartOfDay().toInstant(ZoneOffset.UTC);

        // Exclusive upper boundary includes every time on createdTo.
        Instant toExclusive = createdTo == null
                ? null
                : createdTo.plusDays(1)
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC);

        return findAllAccessibleToUser(userId)
                .stream()
                .filter(document ->
                        normalizedName.isEmpty()
                        || (document.getOriginalName() != null
                        && document.getOriginalName()
                                .toLowerCase(Locale.ROOT)
                                .contains(normalizedName)))
                .filter(document ->
                        normalizedOwnerEmail.isEmpty()
                        || (document.getOwner() != null
                        && document.getOwner().getEmail() != null
                        && document.getOwner().getEmail()
                                .toLowerCase(Locale.ROOT)
                                .equals(normalizedOwnerEmail)))
                .filter(document ->
                        normalizedCategory.isEmpty()
                        || (document.getCategory() != null
                        && document.getCategory()
                                .toLowerCase(Locale.ROOT)
                                .equals(normalizedCategory)))
                .filter(document ->
                        fromInstant == null
                        || (document.getCreatedAt() != null
                        && !document.getCreatedAt().isBefore(fromInstant)))
                .filter(document ->
                        toExclusive == null
                        || (document.getCreatedAt() != null
                        && document.getCreatedAt().isBefore(toExclusive)))
                .toList();
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public Optional<Document> findByIdForOwner(
            UUID documentId,
            UUID ownerId) {

        if (documentId == null || ownerId == null) {
            throw new IllegalArgumentException(
                    "Document ID and owner ID are required.");
        }

        return documentRepository.findByIdAndOwner_Id(
                        documentId, ownerId)
                .filter(document -> !document.isDeleted());
    }

    /**
     * Soft-deletes a document owned by the supplied user.
     * The stored file is retained for controlled retention or recovery.
     */
    @Transactional
    public Optional<Document> softDeleteDocument(
            UUID documentId,
            UUID ownerId) {

        if (documentId == null || ownerId == null) {
            throw new IllegalArgumentException(
                    "Document ID and owner ID are required.");
        }

        Optional<Document> result =
                documentRepository.findByIdAndOwner_Id(
                        documentId, ownerId);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        Document document = result.get();

        if (document.isDeleted()) {
            return Optional.empty();
        }

        AppUser owner = appUserRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Document owner was not found."));

        document.markDeleted(owner);

        return Optional.of(
                documentRepository.saveAndFlush(document));
    }

    @Transactional
    public Document storeDocument(
            UUID ownerId,
            String originalName,
            String contentType,
            InputStream content) throws IOException {

        return storeDocument(
                ownerId, originalName, contentType, content, null);
    }

    @Transactional
    public Document storeDocument(
            UUID ownerId,
            String originalName,
            String contentType,
            InputStream content,
            String category) throws IOException {

        if (ownerId == null) {
            throw new IllegalArgumentException("Owner ID is required.");
        }

        if (content == null) {
            throw new IllegalArgumentException(
                    "Document content is required.");
        }

        AppUser owner = appUserRepository.findById(ownerId)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Enabled document owner was not found."));

        String safeName = validateOriginalName(originalName);

        if (contentType != null && contentType.length() > 255) {
            throw new IllegalArgumentException(
                    "Content type is too long.");
        }

        if (contentType != null && contentType.isBlank()) {
            contentType = null;
        }

        StoredFile storedFile = fileStorageService.store(content);

        Document document = new Document(
                UUID.randomUUID(),
                safeName,
                storedFile.storageKey(),
                contentType,
                storedFile.sizeBytes(),
                storedFile.checksumSha256(),
                owner
        );

        try {
            document.setCategory(category);
            return documentRepository.saveAndFlush(document);
        } catch (RuntimeException exception) {
            try {
                fileStorageService.delete(storedFile.storageKey());
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            throw exception;
        }
    }

    private String validateOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException(
                    "Original filename must not be blank.");
        }

        String name = originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();

        if (name.isEmpty()
                || name.equals(".")
                || name.equals("..")
                || name.length() > 255
                || name.codePoints()
                        .anyMatch(Character::isISOControl)) {

            throw new IllegalArgumentException(
                    "Invalid original filename.");
        }

        return name;
    }
}
