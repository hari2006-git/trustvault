
package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.entity.AppUser;
import com.trustvault.trustvault.entity.Document;
import com.trustvault.trustvault.repository.AppUserRepository;
import com.trustvault.trustvault.repository.DocumentRepository;
import com.trustvault.trustvault.storage.LocalFileStorageService;
import com.trustvault.trustvault.storage.LocalFileStorageService.StoredFile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DocumentAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private LocalFileStorageService fileStorageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> createdStorageKeys = new ArrayList<>();

    private AppUser owner;
    private AppUser otherUser;
    private Document document;

    @BeforeEach
    void setUp() throws Exception {
        owner = appUserRepository.saveAndFlush(
                new AppUser(
                        UUID.randomUUID(),
                        "owner-" + UUID.randomUUID() + "@example.test",
                        "not-a-real-password-hash",
                        "Integration Test Owner"));

        otherUser = appUserRepository.saveAndFlush(
                new AppUser(
                        UUID.randomUUID(),
                        "other-" + UUID.randomUUID() + "@example.test",
                        "not-a-real-password-hash",
                        "Integration Test Other User"));

        byte[] fileContents =
                "Private integration test document."
                        .getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = fileStorageService.store(
                new ByteArrayInputStream(fileContents));

        createdStorageKeys.add(storedFile.storageKey());

        document = documentRepository.saveAndFlush(
                new Document(
                        UUID.randomUUID(),
                        "private-integration-test.txt",
                        storedFile.storageKey(),
                        "text/plain",
                        storedFile.sizeBytes(),
                        storedFile.checksumSha256(),
                        owner));
    }

    @AfterEach
    void cleanUpStoredFiles() throws Exception {
        for (String storageKey : createdStorageKeys) {
            fileStorageService.delete(storageKey);
        }
    }

    @Test
    void ownerCanDownloadTheirOwnDocument() throws Exception {
        mockMvc.perform(
                        get("/api/documents/{id}/download", document.getId())
                                .with(SecurityMockMvcRequestPostProcessors
                                        .user(owner.getEmail())
                                        .roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        "Private integration test document."));
    }

    @Test
    void differentUserCannotDownloadDocumentWithoutGrant()
            throws Exception {

        mockMvc.perform(
                        get("/api/documents/{id}/download", document.getId())
                                .with(SecurityMockMvcRequestPostProcessors
                                        .user(otherUser.getEmail())
                                        .roles("OWNER")))
                .andExpect(status().isNotFound());

        Integer eventCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_events
                WHERE actor_email = ?
                  AND action = 'DOCUMENT_DOWNLOAD'
                  AND outcome = 'DENIED'
                  AND document_id = ?
                """,
                Integer.class,
                otherUser.getEmail(),
                document.getId());

        assertEquals(1, eventCount,
                "The denied download should produce an audit event.");
    }

    @Test
    void grantingAndRevokingPermissionCreatesAuditEvents()
            throws Exception {

        AppUser recipient = appUserRepository.saveAndFlush(
                new AppUser(
                        UUID.randomUUID(),
                        "recipient-" + UUID.randomUUID() + "@example.test",
                        "not-a-real-password-hash",
                        "Integration Test Recipient"));

        // The owner grants READ access through the real HTTP endpoint.
        mockMvc.perform(
                        post("/api/documents/{id}/permissions",
                                document.getId())
                                .with(SecurityMockMvcRequestPostProcessors
                                        .user(owner.getEmail())
                                        .roles("OWNER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "%s",
                                          "permission": "READ"
                                        }
                                        """.formatted(recipient.getEmail())))
                .andExpect(status().isCreated());

        // The recipient can download the document after receiving READ.
        mockMvc.perform(
                        get("/api/documents/{id}/download", document.getId())
                                .with(SecurityMockMvcRequestPostProcessors
                                        .user(recipient.getEmail())
                                        .roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        "Private integration test document."));

        Integer grantCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_events
                WHERE actor_email = ?
                  AND target_email = ?
                  AND document_id = ?
                  AND action = 'PERMISSION_GRANT'
                  AND outcome = 'SUCCESS'
                  AND reason_code = 'PERMISSION_GRANTED'
                """,
                Integer.class,
                owner.getEmail(),
                recipient.getEmail(),
                document.getId());

        assertEquals(1, grantCount,
                "A successful permission grant must be audited.");

        UUID permissionId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM document_permissions
                WHERE document_id = ?
                  AND user_id = ?
                  AND revoked_at IS NULL
                """,
                UUID.class,
                document.getId(),
                recipient.getId());

        // The owner revokes access through the real HTTP endpoint.
        mockMvc.perform(
                        delete(
                                "/api/documents/{documentId}/permissions/{permissionId}",
                                document.getId(),
                                permissionId)
                                .with(SecurityMockMvcRequestPostProcessors
                                        .user(owner.getEmail())
                                        .roles("OWNER"))
                                .with(csrf()))
                .andExpect(status().isNoContent());

        // Revocation must block the recipient's next download immediately.
        mockMvc.perform(
                        get("/api/documents/{id}/download", document.getId())
                                .with(SecurityMockMvcRequestPostProcessors
                                        .user(recipient.getEmail())
                                        .roles("OWNER")))
                .andExpect(status().isNotFound());

        Integer revokeCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_events
                WHERE actor_email = ?
                  AND target_email = ?
                  AND document_id = ?
                  AND action = 'PERMISSION_REVOKE'
                  AND outcome = 'SUCCESS'
                  AND reason_code = 'PERMISSION_REVOKED'
                """,
                Integer.class,
                owner.getEmail(),
                recipient.getEmail(),
                document.getId());

        assertEquals(1, revokeCount,
                "A successful permission revocation must be audited.");

        Integer activePermissionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM document_permissions
                WHERE id = ?
                  AND revoked_at IS NULL
                """,
                Integer.class,
                permissionId);

        assertEquals(0, activePermissionCount,
                "Revoked access must no longer be active.");
    }

    @Test
    void nonOwnerCannotGrantOrRevokePermissionsAndAttemptsAreAudited()
            throws Exception {

        AppUser recipient = appUserRepository.saveAndFlush(
                new AppUser(
                        UUID.randomUUID(),
                        "recipient-" + UUID.randomUUID() + "@example.test",
                        "not-a-real-password-hash",
                        "Integration Test Recipient"));

        AppUser thirdUser = appUserRepository.saveAndFlush(
                new AppUser(
                        UUID.randomUUID(),
                        "third-" + UUID.randomUUID() + "@example.test",
                        "not-a-real-password-hash",
                        "Integration Test Third User"));

        // The owner first grants READ access to the recipient.
        mockMvc.perform(
                        post("/api/documents/{id}/permissions",
                                document.getId())
                                .with(SecurityMockMvcRequestPostProcessors
                                        .user(owner.getEmail())
                                        .roles("OWNER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "%s",
                                          "permission": "READ"
                                        }
                                        """.formatted(recipient.getEmail())))
                .andExpect(status().isCreated());

        UUID permissionId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM document_permissions
                WHERE document_id = ?
                  AND user_id = ?
                  AND revoked_at IS NULL
                """,
                UUID.class,
                document.getId(),
                recipient.getId());

        // A recipient must not be able to grant access to another user.
        mockMvc.perform(
                        post("/api/documents/{id}/permissions",
                                document.getId())
                                .with(SecurityMockMvcRequestPostProcessors
                                        .user(recipient.getEmail())
                                        .roles("OWNER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "%s",
                                          "permission": "READ"
                                        }
                                        """.formatted(thirdUser.getEmail())))
                .andExpect(status().isNotFound());

        // A recipient must not be able to revoke their own permission.
        mockMvc.perform(
                        delete(
                                "/api/documents/{documentId}/permissions/{permissionId}",
                                document.getId(),
                                permissionId)
                                .with(SecurityMockMvcRequestPostProcessors
                                        .user(recipient.getEmail())
                                        .roles("OWNER"))
                                .with(csrf()))
                .andExpect(status().isNotFound());

        // Verify the denied grant was recorded in the audit log.
        Integer deniedGrantCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_events
                WHERE actor_email = ?
                  AND action = 'PERMISSION_GRANT'
                  AND outcome = 'DENIED'
                  AND reason_code = 'AUTHORIZATION_DENIED'
                  AND document_id = ?
                """,
                Integer.class,
                recipient.getEmail(),
                document.getId());

        assertEquals(1, deniedGrantCount,
                "An unauthorized grant attempt must be audited.");

        // Verify the denied revocation was recorded in the audit log.
        Integer deniedRevokeCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_events
                WHERE actor_email = ?
                  AND action = 'PERMISSION_REVOKE'
                  AND outcome = 'DENIED'
                  AND reason_code = 'AUTHORIZATION_DENIED'
                  AND document_id = ?
                """,
                Integer.class,
                recipient.getEmail(),
                document.getId());

        assertEquals(1, deniedRevokeCount,
                "An unauthorized revoke attempt must be audited.");

        // Neither denied attempt may remove the existing permission.
        Integer activePermissionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM document_permissions
                WHERE id = ?
                  AND revoked_at IS NULL
                """,
                Integer.class,
                permissionId);

        assertEquals(1, activePermissionCount,
                "Unauthorized actions must not change the permission.");

        // The unauthorized grant must not create access for the third user.
        Integer thirdUserPermissionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM document_permissions
                WHERE document_id = ?
                  AND user_id = ?
                  AND revoked_at IS NULL
                """,
                Integer.class,
                document.getId(),
                thirdUser.getId());

        assertEquals(0, thirdUserPermissionCount,
                "An unauthorized grant must not create a permission.");
    }
}
