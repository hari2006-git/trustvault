
package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.entity.AppUser;
import com.trustvault.trustvault.entity.Document;
import com.trustvault.trustvault.entity.UserRole;
import com.trustvault.trustvault.repository.AppUserRepository;
import com.trustvault.trustvault.service.AuditService;
import com.trustvault.trustvault.service.DocumentAuthorizationService;
import com.trustvault.trustvault.service.DocumentService;
import com.trustvault.trustvault.storage.LocalFileStorageService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final long MAX_FILE_BYTES =
            2L * 1024 * 1024 * 1024;

    private final DocumentService documentService;
    private final AppUserRepository appUserRepository;
    private final LocalFileStorageService fileStorageService;
    private final DocumentAuthorizationService authorizationService;
    private final AuditService auditService;

    public DocumentController(
            DocumentService documentService,
            AppUserRepository appUserRepository,
            LocalFileStorageService fileStorageService,
            DocumentAuthorizationService authorizationService,
            AuditService auditService) {

        this.documentService = documentService;
        this.appUserRepository = appUserRepository;
        this.fileStorageService = fileStorageService;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<DocumentResponse> listDocuments(
            Authentication authentication,
            HttpServletRequest request) {

        AppUser user = getCurrentUser(authentication);

        List<Document> documents;

        try {
            documents = documentService.findAllAccessibleToUser(
                    user.getId());

            auditService.record(
                    user.getId(), user.getEmail(),
                    "DOCUMENT_LIST", "SUCCESS",
                    null, null, null, null, null, request);

        } catch (RuntimeException exception) {
            auditService.record(
                    user.getId(), user.getEmail(),
                    "DOCUMENT_LIST", "FAILURE",
                    null, null, null, null,
                    "LIST_FAILED", request);

            throw exception;
        }

        return documents.stream()
                .map(DocumentController::toResponse)
                .toList();
    }

    /**
     * Search documents accessible to the authenticated user.
     * Both filters are optional.
     */
    @GetMapping("/search")
    public List<DocumentResponse> searchDocuments(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String ownerEmail,
            Authentication authentication,
            HttpServletRequest request) {

        AppUser user = getCurrentUser(authentication);

        try {
            List<Document> documents =
                    documentService.searchAccessibleDocuments(
                            user.getId(), name, ownerEmail);

            auditService.record(
                    user.getId(), user.getEmail(),
                    "DOCUMENT_SEARCH", "SUCCESS",
                    null, null, null, null, null, request);

            return documents.stream()
                    .map(DocumentController::toResponse)
                    .toList();

        } catch (RuntimeException exception) {
            auditService.record(
                    user.getId(), user.getEmail(),
                    "DOCUMENT_SEARCH", "FAILURE",
                    null, null, null, null,
                    "SEARCH_FAILED", request);

            throw exception;
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            HttpServletRequest request) {

        AppUser user = getCurrentUser(authentication);

        if (user.getRole() != UserRole.OWNER) {
            auditService.record(
                    user.getId(), user.getEmail(),
                    "DOCUMENT_UPLOAD", "DENIED",
                    null, null, null, null,
                    "ROLE_NOT_ALLOWED", request);

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only document owners can upload files.");
        }

        if (file == null || file.isEmpty()) {
            auditService.record(
                    user.getId(), user.getEmail(),
                    "DOCUMENT_UPLOAD", "DENIED",
                    null,
                    file == null ? null : file.getOriginalFilename(),
                    null, null, "EMPTY_FILE", request);

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please select a non-empty file.");
        }

        if (file.getSize() > MAX_FILE_BYTES) {
            auditService.record(
                    user.getId(), user.getEmail(),
                    "DOCUMENT_UPLOAD", "DENIED",
                    null, file.getOriginalFilename(),
                    null, null, "FILE_TOO_LARGE", request);

            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "The maximum file size is 2 GB.");
        }

        Document saved;

        try (InputStream input = file.getInputStream()) {
            saved = documentService.storeDocument(
                    user.getId(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    input);

        } catch (IllegalArgumentException exception) {
            auditService.record(
                    user.getId(), user.getEmail(),
                    "DOCUMENT_UPLOAD", "DENIED",
                    null, file.getOriginalFilename(),
                    null, null, "VALIDATION_FAILED", request);

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The uploaded file metadata is invalid.");

        } catch (IOException exception) {
            String message = exception.getMessage();

            boolean tooLarge = message != null
                    && message.contains(
                            "exceeds the configured size limit");

            auditService.record(
                    user.getId(), user.getEmail(),
                    "DOCUMENT_UPLOAD",
                    tooLarge ? "DENIED" : "FAILURE",
                    null, file.getOriginalFilename(),
                    null, null,
                    tooLarge ? "FILE_TOO_LARGE" : "STORAGE_FAILURE",
                    request);

            throw new ResponseStatusException(
                    tooLarge
                            ? HttpStatus.PAYLOAD_TOO_LARGE
                            : HttpStatus.INTERNAL_SERVER_ERROR,
                    tooLarge
                            ? "The maximum file size is 2 GB."
                            : "The document could not be stored.");
        }

        auditService.record(
                user.getId(), user.getEmail(),
                "DOCUMENT_UPLOAD", "SUCCESS",
                saved.getId(), saved.getOriginalName(),
                null, null, null, request);

        Document reloaded = documentService
                .findByIdForOwner(saved.getId(), user.getId())
                .orElse(saved);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(reloaded));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest request) {

        AppUser user = getCurrentUser(authentication);
        Document document;

        try {
            document = authorizationService.requireReadAccess(
                    id, user.getId());

        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 404
                    || exception.getStatusCode().value() == 403) {

                auditService.record(
                        user.getId(), user.getEmail(),
                        "DOCUMENT_DOWNLOAD", "DENIED",
                        id, null, null, null,
                        "ACCESS_DENIED_OR_NOT_FOUND", request);
            }

            throw exception;
        }

        InputStream input;

        try {
            input = fileStorageService.open(
                    document.getStorageKey());

        } catch (IOException exception) {
            auditService.record(
                    user.getId(), user.getEmail(),
                    "DOCUMENT_DOWNLOAD", "FAILURE",
                    document.getId(), document.getOriginalName(),
                    null, null, "STORAGE_READ_FAILED", request);

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "The stored document could not be read.");
        }

        try {
            auditService.record(
                    user.getId(), user.getEmail(),
                    "DOCUMENT_DOWNLOAD", "SUCCESS",
                    document.getId(), document.getOriginalName(),
                    null, null, null, request);

        } catch (RuntimeException exception) {
            try {
                input.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }

            throw exception;
        }

        ContentDisposition disposition =
                ContentDisposition.attachment()
                        .filename(
                                document.getOriginalName(),
                                StandardCharsets.UTF_8)
                        .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString())
                .body(new InputStreamResource(input));
    }

    /**
     * Soft-delete a document. Only its owner can delete it.
     * The record and stored file are retained for audit/recovery.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest request) {

        AppUser user = getCurrentUser(authentication);

        var deleted = documentService.softDeleteDocument(
                id, user.getId());

        if (deleted.isEmpty()) {
            auditService.record(
                    user.getId(), user.getEmail(),
                    "DOCUMENT_DELETE", "DENIED",
                    id, null, null, null,
                    "NOT_OWNER_OR_NOT_FOUND", request);

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document not found.");
        }

        Document document = deleted.get();

        auditService.record(
                user.getId(), user.getEmail(),
                "DOCUMENT_DELETE", "SUCCESS",
                document.getId(), document.getOriginalName(),
                null, null, null, request);

        return ResponseEntity.noContent().build();
    }

    private AppUser getCurrentUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required.");
        }

        return appUserRepository
                .findByEmailIgnoreCase(authentication.getName())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "The authenticated account is unavailable."));
    }

    private static DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getOriginalName(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getChecksumSha256(),
                document.getCreatedAt());
    }

    public record DocumentResponse(
            UUID id,
            String originalName,
            String contentType,
            Long sizeBytes,
            String checksumSha256,
            Instant createdAt) {
    }
}
