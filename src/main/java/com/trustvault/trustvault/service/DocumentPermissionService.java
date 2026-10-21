
package com.trustvault.trustvault.service;

import com.trustvault.trustvault.entity.AppUser;
import com.trustvault.trustvault.entity.Document;
import com.trustvault.trustvault.entity.DocumentAccessLevel;
import com.trustvault.trustvault.entity.DocumentPermission;
import com.trustvault.trustvault.repository.AppUserRepository;
import com.trustvault.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.trustvault.repository.DocumentRepository;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentPermissionService {

    private final DocumentRepository documentRepository;
    private final AppUserRepository appUserRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final AuditService auditService;

    public DocumentPermissionService(
            DocumentRepository documentRepository,
            AppUserRepository appUserRepository,
            DocumentPermissionRepository permissionRepository,
            AuditService auditService) {

        this.documentRepository = documentRepository;
        this.appUserRepository = appUserRepository;
        this.permissionRepository = permissionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PermissionView> listPermissions(
            UUID documentId,
            UUID actorId) {

        requireOwnedDocument(documentId, actorId);

        return permissionRepository
                .findAllByDocument_IdAndRevokedAtIsNullOrderByGrantedAtDesc(
                        documentId)
                .stream()
                .map(DocumentPermissionService::toView)
                .toList();
    }

    @Transactional
    public PermissionView grantPermission(
            UUID documentId,
            UUID actorId,
            String recipientEmail,
            DocumentAccessLevel accessLevel) {

        return grantPermission(
                documentId,
                actorId,
                recipientEmail,
                accessLevel,
                null);
    }

    @Transactional
    public PermissionView grantPermission(
            UUID documentId,
            UUID actorId,
            String recipientEmail,
            DocumentAccessLevel accessLevel,
            HttpServletRequest request) {

        Document document = null;
        AppUser actor = null;
        AppUser recipient = null;

        try {
            document = requireOwnedDocument(documentId, actorId);
            actor = findEnabledActor(actorId);

            if (recipientEmail == null
                    || recipientEmail.isBlank()
                    || recipientEmail.trim().length() > 320) {

                throw new IllegalArgumentException(
                        "A valid recipient email is required.");
            }

            if (accessLevel == null) {
                throw new IllegalArgumentException(
                        "Permission must be READ or EDIT.");
            }

            String normalizedEmail =
                    recipientEmail.trim().toLowerCase(Locale.ROOT);

            recipient = appUserRepository
                    .findByEmailIgnoreCase(normalizedEmail)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "The recipient account could not be found."));

            if (!recipient.isEnabled()) {
                throw new IllegalArgumentException(
                        "The recipient account is disabled.");
            }

            if (recipient.getId().equals(actorId)) {
                throw new IllegalArgumentException(
                        "You already own this document.");
            }

            boolean alreadyGranted = permissionRepository
                    .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
                            documentId,
                            recipient.getId())
                    .isPresent();

            if (alreadyGranted) {
                throw new IllegalStateException(
                        "This user already has active access. "
                                + "Revoke it before creating a new grant.");
            }

            DocumentPermission permission = new DocumentPermission(
                    UUID.randomUUID(),
                    document,
                    recipient,
                    accessLevel,
                    actor);

            DocumentPermission saved =
                    permissionRepository.saveAndFlush(permission);

            recordAudit(
                    actor,
                    "PERMISSION_GRANT",
                    "SUCCESS",
                    document,
                    recipient,
                    "PERMISSION_GRANTED",
                    request);

            return toView(saved);

        } catch (RuntimeException exception) {

            if (exception instanceof DataIntegrityViolationException) {
                recordAudit(
                        actor,
                        "PERMISSION_GRANT",
                        "FAILURE",
                        document,
                        recipient,
                        "PERMISSION_CONFLICT",
                        request);

                throw new IllegalStateException(
                        "An active permission already exists for this user.",
                        exception);
            }

            if (exception instanceof ResponseStatusException response
                    && (response.getStatusCode() == HttpStatus.NOT_FOUND
                    || response.getStatusCode() == HttpStatus.FORBIDDEN)) {

                recordAudit(
                        actor,
                        "PERMISSION_GRANT",
                        "DENIED",
                        document,
                        recipient,
                        "AUTHORIZATION_DENIED",
                        request);

            } else if (exception instanceof IllegalArgumentException
                    || exception instanceof IllegalStateException) {

                recordAudit(
                        actor,
                        "PERMISSION_GRANT",
                        "FAILURE",
                        document,
                        recipient,
                        "INVALID_PERMISSION_REQUEST",
                        request);
            }

            throw exception;
        }
    }

    @Transactional
    public PermissionView revokePermission(
            UUID documentId,
            UUID permissionId,
            UUID actorId) {

        return revokePermission(
                documentId,
                permissionId,
                actorId,
                null);
    }

    @Transactional
    public PermissionView revokePermission(
            UUID documentId,
            UUID permissionId,
            UUID actorId,
            HttpServletRequest request) {

        Document document = null;
        AppUser actor = null;
        DocumentPermission permission = null;

        try {
            document = requireOwnedDocument(documentId, actorId);
            actor = findEnabledActor(actorId);

            permission = permissionRepository
                    .findAllByDocument_IdAndRevokedAtIsNullOrderByGrantedAtDesc(
                            documentId)
                    .stream()
                    .filter(item -> item.getId().equals(permissionId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Active permission not found."));

            permission.revoke(actor);

            DocumentPermission saved =
                    permissionRepository.saveAndFlush(permission);

            recordAudit(
                    actor,
                    "PERMISSION_REVOKE",
                    "SUCCESS",
                    document,
                    saved.getUser(),
                    "PERMISSION_REVOKED",
                    request);

            return toView(saved);

        } catch (RuntimeException exception) {

            if (exception instanceof ResponseStatusException response
                    && (response.getStatusCode() == HttpStatus.NOT_FOUND
                    || response.getStatusCode() == HttpStatus.FORBIDDEN)) {

                recordAudit(
                        actor,
                        "PERMISSION_REVOKE",
                        "DENIED",
                        document,
                        permission == null ? null : permission.getUser(),
                        "AUTHORIZATION_DENIED",
                        request);
            }

            throw exception;
        }
    }

    private Document requireOwnedDocument(
            UUID documentId,
            UUID actorId) {

        if (documentId == null || actorId == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document not found.");
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Document not found."));

        if (document.isDeleted()
                || document.getOwner() == null
                || !document.getOwner().getId().equals(actorId)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document not found.");
        }

        return document;
    }

    private AppUser findEnabledActor(UUID actorId) {
        return appUserRepository.findById(actorId)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "The account is not available."));
    }

    private void recordAudit(
            AppUser actor,
            String action,
            String outcome,
            Document document,
            AppUser target,
            String reason,
            HttpServletRequest request) {

        auditService.record(
                actor == null ? null : actor.getId(),
                actor == null ? "anonymous" : actor.getEmail(),
                action,
                outcome,
                document == null ? null : document.getId(),
                document == null ? null : document.getOriginalName(),
                target == null ? null : target.getId(),
                target == null ? null : target.getEmail(),
                reason,
                request);
    }

    private static PermissionView toView(
            DocumentPermission permission) {

        return new PermissionView(
                permission.getId(),
                permission.getUser().getId(),
                permission.getUser().getEmail(),
                permission.getUser().getDisplayName(),
                permission.getPermission(),
                permission.getGrantedAt(),
                permission.getRevokedAt());
    }

    public record PermissionView(
            UUID permissionId,
            UUID userId,
            String email,
            String displayName,
            DocumentAccessLevel permission,
            Instant grantedAt,
            Instant revokedAt) {
    }
}
