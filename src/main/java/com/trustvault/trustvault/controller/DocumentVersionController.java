package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.entity.*;
import com.trustvault.trustvault.repository.*;
import com.trustvault.trustvault.service.*;
import com.trustvault.trustvault.storage.LocalFileStorageService;
import com.trustvault.trustvault.storage.LocalFileStorageService.StoredFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentVersionController {
    private static final long MAX_BYTES = 2L * 1024 * 1024 * 1024;

    private final DocumentAuthorizationService authorization;
    private final DocumentVersionRepository versions;
    private final DocumentRepository documents;
    private final AppUserRepository users;
    private final LocalFileStorageService storage;
    private final AuditService audit;

    public DocumentVersionController(
            DocumentAuthorizationService authorization,
            DocumentVersionRepository versions,
            DocumentRepository documents,
            AppUserRepository users,
            LocalFileStorageService storage,
            AuditService audit) {
        this.authorization = authorization;
        this.versions = versions;
        this.documents = documents;
        this.users = users;
        this.storage = storage;
        this.audit = audit;
    }

    @GetMapping("/{id}/versions")
    public List<VersionResponse> history(
            @PathVariable UUID id, Authentication authentication,
            HttpServletRequest request) {
        AppUser user = currentUser(authentication);
        Document document = authorization.requireReadAccess(id, user.getId());

        audit.record(user.getId(), user.getEmail(), "DOCUMENT_VERSION_LIST",
                "SUCCESS", id, document.getOriginalName(),
                null, null, null, request);

        return versions.findAllByDocument_IdOrderByVersionNumberDesc(id)
                .stream().map(VersionResponse::from).toList();
    }

    @PutMapping(value = "/{id}/content",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VersionResponse replace(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file,
            Authentication authentication, HttpServletRequest request) {
        AppUser user = currentUser(authentication);
        Document document = authorization.requireEditAccess(id, user.getId());

        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Select a non-empty file no larger than 2 GB.");
        }

        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 255
                || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid filename.");
        }
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid filename.");
        }

        StoredFile replacement;
        try (InputStream input = file.getInputStream()) {
            replacement = storage.store(input);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Replacement upload failed.");
        }

        StoredFile snapshot;
        try (InputStream input = storage.open(document.getStorageKey())) {
            snapshot = storage.store(input);
        } catch (IOException exception) {
            try { storage.delete(replacement.storageKey()); }
            catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not preserve the previous document version.");
        }

        DocumentVersion previous = new DocumentVersion(
                UUID.randomUUID(), document,
                versions.countByDocument_Id(id) + 1,
                document.getOriginalName(), snapshot.storageKey(),
                document.getContentType(), snapshot.sizeBytes(),
                snapshot.checksumSha256(), user);

        try {
            versions.saveAndFlush(previous);
            document.replaceContent(name, replacement.storageKey(),
                    file.getContentType(), replacement.sizeBytes(),
                    replacement.checksumSha256());
            documents.saveAndFlush(document);
        } catch (RuntimeException exception) {
            try { storage.delete(snapshot.storageKey()); }
            catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            try { storage.delete(replacement.storageKey()); }
            catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }

        audit.record(user.getId(), user.getEmail(), "DOCUMENT_REPLACE",
                "SUCCESS", id, name, null, null, null, request);
        return VersionResponse.from(previous);
    }

    @GetMapping("/{id}/versions/{versionId}/download")
    public ResponseEntity<InputStreamResource> downloadVersion(
            @PathVariable UUID id, @PathVariable UUID versionId,
            Authentication authentication, HttpServletRequest request) {
        AppUser user = currentUser(authentication);
        authorization.requireReadAccess(id, user.getId());

        DocumentVersion version = versions.findByIdAndDocument_Id(versionId, id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Document version not found."));

        InputStream input;
        try {
            input = storage.open(version.getStorageKey());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The stored version could not be read.");
        }

        audit.record(user.getId(), user.getEmail(), "DOCUMENT_VERSION_DOWNLOAD",
                "SUCCESS", id, version.getOriginalName(),
                null, null, null, request);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(version.getOriginalName(),
                                        StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(input));
    }

    private AppUser currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authentication is required.");
        }
        return users.findByEmailIgnoreCase(authentication.getName())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Account is unavailable."));
    }

    public record VersionResponse(
            UUID id, UUID documentId, Integer versionNumber,
            String originalName, String contentType, Long sizeBytes,
            String checksumSha256, String createdByEmail,
            java.time.Instant createdAt) {
        static VersionResponse from(DocumentVersion version) {
            return new VersionResponse(version.getId(),
                    version.getDocument().getId(), version.getVersionNumber(),
                    version.getOriginalName(), version.getContentType(),
                    version.getSizeBytes(), version.getChecksumSha256(),
                    version.getCreatedBy().getEmail(), version.getCreatedAt());
        }
    }
}
