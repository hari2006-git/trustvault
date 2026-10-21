
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        // Verify that the denied attempt was recorded by the real audit
        // service, rather than a mocked authorization or audit component.
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
}
