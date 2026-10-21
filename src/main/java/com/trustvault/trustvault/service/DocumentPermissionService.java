
package com.trustvault.trustvault.service;

import com.trustvault.trustvault.entity.AppUser;
import com.trustvault.trustvault.entity.Document;
import com.trustvault.trustvault.entity.DocumentAccessLevel;
import com.trustvault.trustvault.entity.DocumentPermission;
import com.trustvault.trustvault.repository.AppUserRepository;
import com.trustvault.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.trustvault.repository.DocumentRepository;

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

    public DocumentPermissionService(
            DocumentRepository documentRepository,
            AppUserRepository appUserRepository,
            DocumentPermissionRepository permissionRepository) {

        this.documentRepository = documentRepository;
        this.appUserRepository = appUserRepository;
        this.permissionRepository = permissionRepository;
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

        Document document = requireOwnedDocument(documentId, actorId);

        if (recipientEmail == null || recipientEmail.isBlank()
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

        AppUser recipient = appUserRepository
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

        AppUser actor = findEnabledActor(actorId);

        boolean alreadyGranted = permissionRepository
                .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
                        documentId,
                        recipient.getId())
                .isPresent();

        if (alreadyGranted) {
            throw new IllegalStateException(
                    "This user already has active access. Revoke it before creating a new grant.");
        }

        DocumentPermission permission = new DocumentPermission(
                UUID.randomUUID(),
                document,
                recipient,
                accessLevel,
                actor
        );

        try {
            DocumentPermission saved =
                    permissionRepository.saveAndFlush(permission);

            return toView(saved);

        } catch (DataIntegrityViolationException exception) {
            // The database's partial unique index protects against two
            // simultaneous attempts to grant the same active permission.
            throw new IllegalStateException(
                    "An active permission already exists for this user.",
                    exception);
        }
    }

    @Transactional
    public void revokePermission(
            UUID documentId,
            UUID permissionId,
            UUID actorId) {

        requireOwnedDocument(documentId, actorId);
        AppUser actor = findEnabledActor(actorId);

        DocumentPermission permission = permissionRepository
                .findAllByDocument_IdAndRevokedAtIsNullOrderByGrantedAtDesc(
                        documentId)
                .stream()
                .filter(item -> item.getId().equals(permissionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active permission not found."));

        permission.revoke(actor);
        permissionRepository.saveAndFlush(permission);

        // The record is revoked rather than deleted so its history remains.
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

        if (!document.getOwner().getId().equals(actorId)) {
            // Do not disclose another user's document to a non-owner.
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

    private static PermissionView toView(
            DocumentPermission permission) {

        return new PermissionView(
                permission.getId(),
                permission.getUser().getId(),
                permission.getUser().getEmail(),
                permission.getUser().getDisplayName(),
                permission.getPermission(),
                permission.getGrantedAt(),
                permission.getRevokedAt()
        );
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
