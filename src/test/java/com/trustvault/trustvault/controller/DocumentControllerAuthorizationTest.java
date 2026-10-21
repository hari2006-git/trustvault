
package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.config.SecurityConfig;
import com.trustvault.trustvault.entity.AppUser;
import com.trustvault.trustvault.repository.AppUserRepository;
import com.trustvault.trustvault.service.AuditService;
import com.trustvault.trustvault.service.DocumentAuthorizationService;
import com.trustvault.trustvault.service.DocumentService;
import com.trustvault.trustvault.storage.LocalFileStorageService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import(SecurityConfig.class)
class DocumentControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private AppUserRepository appUserRepository;

    @MockBean
    private LocalFileStorageService fileStorageService;

    @MockBean
    private DocumentAuthorizationService authorizationService;

    @MockBean
    private AuditService auditService;

    @Test
    @WithMockUser(username = "viewer@example.com")
    void ungrantedUserCannotDownloadDocument() throws Exception {

        UUID viewerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        AppUser viewer = new AppUser(
                viewerId,
                "viewer@example.com",
                "test-password-hash",
                "Test Viewer"
        );

        when(appUserRepository.findByEmailIgnoreCase(
                "viewer@example.com"))
                .thenReturn(Optional.of(viewer));

        when(authorizationService.requireReadAccess(
                documentId, viewerId))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Document not found."));

        mockMvc.perform(
                get("/api/documents/{id}/download", documentId))
                .andExpect(status().isNotFound());

        // A denied request must never open the private file.
        verify(fileStorageService, never()).open(anyString());
    }
}
