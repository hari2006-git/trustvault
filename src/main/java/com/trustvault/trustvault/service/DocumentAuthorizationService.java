
package com.trustvault.trustvault.service;

import com.trustvault.trustvault.entity.Document;
import com.trustvault.trustvault.entity.DocumentAccessLevel;
import com.trustvault.trustvault.entity.DocumentPermission;
import com.trustvault.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.trustvault.repository.DocumentRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class DocumentAuthorizationService {

    private final DocumentRepository documentRepository;
    private final DocumentPermissionRepository permissionRepository;

    public DocumentAuthorizationService(
            DocumentRepository documentRepository,
            DocumentPermissionRepository permissionRepository) {

        this.documentRepository = documentRepository;
        this.permissionRepository = permissionRepository;
    }

    /**
     * Checks whether a user can read or download a document.
     *
     * The owner has access to their own active document.
     * Users with READ or EDIT permission can also read and download it.
     * Unauthorized and missing documents return the same 404 response.
     */
    @Transactional(readOnly = true)
    public Document requireReadAccess(UUID documentId, UUID userId) {
        Document document = findDocument(documentId, userId);

        // The owner can read and download their own document.
        if (document.getOwner().getId().equals(userId)) {
            return document;
        }

        DocumentPermission grant = findActiveGrant(documentId, userId);

        // EDIT permission also allows reading and downloading.
        if (grant.getPermission() == DocumentAccessLevel.READ
                || grant.getPermission() == DocumentAccessLevel.EDIT) {
            return document;
        }

        throw documentNotFound();
    }

    /**
     * Checks whether a user can modify a document.
     *
     * The owner can modify their own active document.
     * Other users must have an active EDIT permission.
     */
    @Transactional(readOnly = true)
    public Document requireEditAccess(UUID documentId, UUID userId) {
        Document document = findDocument(documentId, userId);

        // The owner can modify their own document.
        if (document.getOwner().getId().equals(userId)) {
            return document;
        }

        DocumentPermission grant = findActiveGrant(documentId, userId);

        // READ permission alone does not allow modifications.
        if (grant.getPermission() == DocumentAccessLevel.EDIT) {
            return document;
        }

        throw documentNotFound();
    }

    /**
     * Loads a document only when both IDs are present and the document
     * exists and has not been soft-deleted.
     *
     * This check applies before owner or permission checks, ensuring that
     * previous grants cannot restore access to a deleted document.
     */
    private Document findDocument(UUID documentId, UUID userId) {
        if (documentId == null || userId == null) {
            throw documentNotFound();
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(this::documentNotFound);

        // Never allow access to a soft-deleted document.
        if (document.isDeleted()) {
            throw documentNotFound();
        }

        return document;
    }

    /**
     * Finds a permission grant that has not been revoked.
     */
    private DocumentPermission findActiveGrant(
            UUID documentId,
            UUID userId) {

        return permissionRepository
                .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
                        documentId,
                        userId)
                .orElseThrow(this::documentNotFound);
    }

    /**
     * Uses the same response for missing and unauthorized documents
     * to avoid revealing whether another user's document exists.
     */
    private ResponseStatusException documentNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Document not found.");
    }
}