
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

    @Transactional(readOnly = true)
    public Document requireReadAccess(UUID documentId, UUID userId) {
        Document document = findDocument(documentId, userId);

        // The document owner always has access to their own document.
        if (document.getOwner().getId().equals(userId)) {
            return document;
        }

        DocumentPermission grant = findActiveGrant(documentId, userId);

        // EDIT also includes permission to read and download.
        if (grant.getPermission() == DocumentAccessLevel.READ
                || grant.getPermission() == DocumentAccessLevel.EDIT) {
            return document;
        }

        throw documentNotFound();
    }

    @Transactional(readOnly = true)
    public Document requireEditAccess(UUID documentId, UUID userId) {
        Document document = findDocument(documentId, userId);

        // Owners can modify their own documents.
        if (document.getOwner().getId().equals(userId)) {
            return document;
        }

        DocumentPermission grant = findActiveGrant(documentId, userId);

        // READ alone is not sufficient to edit.
        if (grant.getPermission() == DocumentAccessLevel.EDIT) {
            return document;
        }

        throw documentNotFound();
    }

    private Document findDocument(UUID documentId, UUID userId) {
        if (documentId == null || userId == null) {
            throw documentNotFound();
        }

        return documentRepository.findById(documentId)
                .orElseThrow(this::documentNotFound);
    }

    private DocumentPermission findActiveGrant(
            UUID documentId,
            UUID userId) {

        return permissionRepository
                .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
                        documentId,
                        userId)
                .orElseThrow(this::documentNotFound);
    }

    private ResponseStatusException documentNotFound() {
        // Returning the same response for missing and unauthorized
        // documents avoids revealing whether another user's document exists.
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Document not found.");
    }
}
