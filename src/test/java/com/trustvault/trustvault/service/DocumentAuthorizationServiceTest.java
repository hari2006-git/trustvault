
package com.trustvault.trustvault.service;

import com.trustvault.trustvault.entity.AppUser;
import com.trustvault.trustvault.entity.Document;
import com.trustvault.trustvault.entity.DocumentAccessLevel;
import com.trustvault.trustvault.entity.DocumentPermission;
import com.trustvault.trustvault.repository.DocumentPermissionRepository;
import com.trustvault.trustvault.repository.DocumentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentAuthorizationServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentPermissionRepository permissionRepository;

    private DocumentAuthorizationService authorizationService;

    private AppUser owner;
    private AppUser recipient;
    private Document document;

    @BeforeEach
    void setUp() {
        authorizationService = new DocumentAuthorizationService(
                documentRepository,
                permissionRepository);

        owner = new AppUser(
                UUID.randomUUID(),
                "owner@example.com",
                "test-hash",
                "Document Owner");

        recipient = new AppUser(
                UUID.randomUUID(),
                "recipient@example.com",
                "test-hash",
                "Recipient");

        document = new Document(
                UUID.randomUUID(),
                "confidential.txt",
                UUID.randomUUID().toString(),
                "text/plain",
                100L,
                "a".repeat(64),
                owner);
    }

    @Test
    void ownerCanReadTheirOwnDocument() {
        when(documentRepository.findById(document.getId()))
                .thenReturn(Optional.of(document));

        Document result = authorizationService.requireReadAccess(
                document.getId(),
                owner.getId());

        assertSame(document, result);

        verify(permissionRepository, never())
                .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
                        document.getId(), owner.getId());
    }

    @Test
    void recipientWithReadGrantCanDownloadButCannotEdit() {
        DocumentPermission grant = grant(
                DocumentAccessLevel.READ);

        when(documentRepository.findById(document.getId()))
                .thenReturn(Optional.of(document));

        when(permissionRepository
                .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
                        document.getId(), recipient.getId()))
                .thenReturn(Optional.of(grant));

        Document readable = authorizationService.requireReadAccess(
                document.getId(),
                recipient.getId());

        assertSame(document, readable);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authorizationService.requireEditAccess(
                        document.getId(),
                        recipient.getId()));

        assertEquals(
                HttpStatus.NOT_FOUND.value(),
                exception.getStatusCode().value());
    }

    @Test
    void recipientWithEditGrantCanEdit() {
        DocumentPermission grant = grant(
                DocumentAccessLevel.EDIT);

        when(documentRepository.findById(document.getId()))
                .thenReturn(Optional.of(document));

        when(permissionRepository
                .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
                        document.getId(), recipient.getId()))
                .thenReturn(Optional.of(grant));

        Document result = authorizationService.requireEditAccess(
                document.getId(),
                recipient.getId());

        assertSame(document, result);
    }

    @Test
    void userWithoutGrantCannotReadOrDownload() {
        when(documentRepository.findById(document.getId()))
                .thenReturn(Optional.of(document));

        when(permissionRepository
                .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
                        document.getId(), recipient.getId()))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authorizationService.requireReadAccess(
                        document.getId(),
                        recipient.getId()));

        assertEquals(
                HttpStatus.NOT_FOUND.value(),
                exception.getStatusCode().value());
    }

    @Test
    void revokedGrantDoesNotAuthorizeTheNextRequest() {
        DocumentPermission grant = grant(
                DocumentAccessLevel.READ);

        when(documentRepository.findById(document.getId()))
                .thenReturn(Optional.of(document));

        when(permissionRepository
                .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
                        document.getId(), recipient.getId()))
                .thenReturn(Optional.of(grant), Optional.empty());

        // The first request has an active grant.
        assertSame(
                document,
                authorizationService.requireReadAccess(
                        document.getId(),
                        recipient.getId()));

        // Simulate revocation: the active-grant query now returns empty.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authorizationService.requireReadAccess(
                        document.getId(),
                        recipient.getId()));

        assertEquals(
                HttpStatus.NOT_FOUND.value(),
                exception.getStatusCode().value());
    }

    @Test
    void nonexistentDocumentIsNotRevealed() {
        UUID unknownDocumentId = UUID.randomUUID();

        when(documentRepository.findById(unknownDocumentId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authorizationService.requireReadAccess(
                        unknownDocumentId,
                        recipient.getId()));

        assertEquals(
                HttpStatus.NOT_FOUND.value(),
                exception.getStatusCode().value());

        verify(permissionRepository, never())
                .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
                        unknownDocumentId, recipient.getId());
    }

    private DocumentPermission grant(DocumentAccessLevel level) {
        return new DocumentPermission(
                UUID.randomUUID(),
                document,
                recipient,
                level,
                owner);
    }
}
