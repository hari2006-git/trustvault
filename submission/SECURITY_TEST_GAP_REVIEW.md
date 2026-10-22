# TrustVault Security Test Gap Review

Generated: 2026-10-22 12:03:34 IST

## Existing test files
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java
src/test/java/com/trustvault/trustvault/TrustvaultApplicationTests.java

## Existing security-related test references
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:7:import com.trustvault.trustvault.service.AuditService;
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:32:import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:45:    private AuditService auditService;
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:51:                "test-password-hash",
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:64:    @WithMockUser(username = "owner@example.com", roles = "OWNER")
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:69:                                .with(csrf())
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:74:                .andExpect(status().isForbidden());
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:77:        verify(auditService, never()).record(
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:84:    void adminCanChangeRoleAndChangeIsAudited() throws Exception {
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:96:                                .with(csrf())
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:99:                                        {"role":"VIEWER"}
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:104:                .andExpect(jsonPath("$.role").value("VIEWER"));
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:108:        verify(auditService).record(
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:117:                eq("ROLE_CHANGED_FROM_OWNER_TO_VIEWER"),
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:129:                                .with(csrf())
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:137:        verify(auditService, never()).record(
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:158:                                .with(csrf())
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:161:                                        {"role":"VIEWER"}
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java:166:        verify(auditService, never()).record(
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:32:import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:33:import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:61:    private AppUser owner;
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:67:        owner = appUserRepository.saveAndFlush(
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:70:                        "owner-" + UUID.randomUUID() + "@example.test",
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:71:                        "not-a-real-password-hash",
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:72:                        "Integration Test Owner"));
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:78:                        "not-a-real-password-hash",
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:98:                        owner));
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:104:            fileStorageService.delete(storageKey);
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:109:    void ownerCanDownloadTheirOwnDocument() throws Exception {
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:111:                        get("/api/documents/{id}/download", document.getId())
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:113:                                        .user(owner.getEmail())
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:114:                                        .roles("OWNER")))
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:121:    void differentUserCannotDownloadDocumentWithoutGrant()
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:125:                        get("/api/documents/{id}/download", document.getId())
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:128:                                        .roles("OWNER")))
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:134:                FROM audit_events
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:136:                  AND action = 'DOCUMENT_DOWNLOAD'
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:145:                "The denied download should produce an audit event.");
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:149:    void grantingAndRevokingPermissionCreatesAuditEvents()
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:156:                        "not-a-real-password-hash",
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:159:        // The owner grants READ access through the real HTTP endpoint.
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:161:                        post("/api/documents/{id}/permissions",
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:164:                                        .user(owner.getEmail())
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:165:                                        .roles("OWNER"))
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:166:                                .with(csrf())
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:171:                                          "permission": "READ"
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:176:        // The recipient can download the document after receiving READ.
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:178:                        get("/api/documents/{id}/download", document.getId())
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:181:                                        .roles("OWNER")))
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:189:                FROM audit_events
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:193:                  AND action = 'PERMISSION_GRANT'
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:195:                  AND reason_code = 'PERMISSION_GRANTED'
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:198:                owner.getEmail(),
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:203:                "A successful permission grant must be audited.");
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:205:        UUID permissionId = jdbcTemplate.queryForObject(
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:208:                FROM document_permissions
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:211:                  AND revoked_at IS NULL
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:217:        // The owner revokes access through the real HTTP endpoint.
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:219:                        delete(
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:220:                                "/api/documents/{documentId}/permissions/{permissionId}",
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:222:                                permissionId)
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:224:                                        .user(owner.getEmail())
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:225:                                        .roles("OWNER"))
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:226:                                .with(csrf()))
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:229:        // Revocation must block the recipient's next download immediately.
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:231:                        get("/api/documents/{id}/download", document.getId())
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:234:                                        .roles("OWNER")))
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:237:        Integer revokeCount = jdbcTemplate.queryForObject(
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:240:                FROM audit_events
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:244:                  AND action = 'PERMISSION_REVOKE'
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:246:                  AND reason_code = 'PERMISSION_REVOKED'
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:249:                owner.getEmail(),
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:253:        assertEquals(1, revokeCount,
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:254:                "A successful permission revocation must be audited.");
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:256:        Integer activePermissionCount = jdbcTemplate.queryForObject(
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:259:                FROM document_permissions
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:261:                  AND revoked_at IS NULL
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:264:                permissionId);
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:266:        assertEquals(0, activePermissionCount,
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:267:                "Revoked access must no longer be active.");
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:271:    void nonOwnerCannotGrantOrRevokePermissionsAndAttemptsAreAudited()
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:278:                        "not-a-real-password-hash",
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:285:                        "not-a-real-password-hash",
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:288:        // The owner first grants READ access to the recipient.
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:290:                        post("/api/documents/{id}/permissions",
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:293:                                        .user(owner.getEmail())
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:294:                                        .roles("OWNER"))
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:295:                                .with(csrf())
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:300:                                          "permission": "READ"
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:305:        UUID permissionId = jdbcTemplate.queryForObject(
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:308:                FROM document_permissions
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:311:                  AND revoked_at IS NULL
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:319:                        post("/api/documents/{id}/permissions",
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:323:                                        .roles("OWNER"))
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:324:                                .with(csrf())
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:329:                                          "permission": "READ"
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:334:        // A recipient must not be able to revoke their own permission.
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:336:                        delete(
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:337:                                "/api/documents/{documentId}/permissions/{permissionId}",
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:339:                                permissionId)
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:342:                                        .roles("OWNER"))
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:343:                                .with(csrf()))
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:346:        // Verify the denied grant was recorded in the audit log.
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:350:                FROM audit_events
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:352:                  AND action = 'PERMISSION_GRANT'
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:362:                "An unauthorized grant attempt must be audited.");
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:364:        // Verify the denied revocation was recorded in the audit log.
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:365:        Integer deniedRevokeCount = jdbcTemplate.queryForObject(
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:368:                FROM audit_events
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:370:                  AND action = 'PERMISSION_REVOKE'
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:379:        assertEquals(1, deniedRevokeCount,
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:380:                "An unauthorized revoke attempt must be audited.");
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:382:        // Neither denied attempt may remove the existing permission.
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:383:        Integer activePermissionCount = jdbcTemplate.queryForObject(
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:386:                FROM document_permissions
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:388:                  AND revoked_at IS NULL
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:391:                permissionId);
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:393:        assertEquals(1, activePermissionCount,
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:394:                "Unauthorized actions must not change the permission.");
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:396:        // The unauthorized grant must not create access for the third user.
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:397:        Integer thirdUserPermissionCount = jdbcTemplate.queryForObject(
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:400:                FROM document_permissions
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:403:                  AND revoked_at IS NULL
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:409:        assertEquals(0, thirdUserPermissionCount,
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java:410:                "An unauthorized grant must not create a permission.");
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java:7:import com.trustvault.trustvault.service.AuditService;
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java:53:    private AuditService auditService;
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java:56:    @WithMockUser(username = "viewer@example.com")
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java:57:    void ungrantedUserCannotDownloadDocument() throws Exception {
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java:59:        UUID viewerId = UUID.randomUUID();
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java:62:        AppUser viewer = new AppUser(
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java:63:                viewerId,
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java:64:                "viewer@example.com",
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java:65:                "test-password-hash",
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java:66:                "Test Viewer"
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java:70:                "viewer@example.com"))
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java:71:                .thenReturn(Optional.of(viewer));
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java:74:                documentId, viewerId))
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java:80:                get("/api/documents/{id}/download", documentId))
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:7:import com.trustvault.trustvault.entity.DocumentPermission;
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:8:import com.trustvault.trustvault.repository.DocumentPermissionRepository;
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:39:    private DocumentPermissionRepository permissionRepository;
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:43:    private AppUser owner;
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:51:                permissionRepository);
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:53:        owner = new AppUser(
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:55:                "owner@example.com",
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:56:                "test-hash",
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:57:                "Document Owner");
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:62:                "test-hash",
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:72:                owner);
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:76:    void ownerCanReadTheirOwnDocument() {
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:82:                owner.getId());
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:86:        verify(permissionRepository, never())
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:87:                .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:88:                        document.getId(), owner.getId());
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:92:    void recipientWithReadGrantCanDownloadButCannotEdit() {
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:93:        DocumentPermission grant = grant(
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:99:        when(permissionRepository
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:100:                .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:123:        DocumentPermission grant = grant(
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:129:        when(permissionRepository
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:130:                .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:142:    void userWithoutGrantCannotReadOrDownload() {
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:146:        when(permissionRepository
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:147:                .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:163:    void revokedGrantDoesNotAuthorizeTheNextRequest() {
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:164:        DocumentPermission grant = grant(
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:170:        when(permissionRepository
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:171:                .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:211:        verify(permissionRepository, never())
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:212:                .findByDocument_IdAndUser_IdAndRevokedAtIsNull(
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:216:    private DocumentPermission grant(DocumentAccessLevel level) {
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:217:        return new DocumentPermission(
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java:222:                owner);

## Existing test report summary
<testsuite xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="https://maven.apache.org/surefire/maven-surefire-plugin/xsd/surefire-test-report.xsd" version="3.0.2" name="com.trustvault.trustvault.controller.AdminUserControllerTest" time="11.43" tests="4" errors="0" skipped="0" failures="0">
<testsuite xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="https://maven.apache.org/surefire/maven-surefire-plugin/xsd/surefire-test-report.xsd" version="3.0.2" name="com.trustvault.trustvault.controller.DocumentAccessIntegrationTest" time="5.312" tests="4" errors="4" skipped="0" failures="0">
<testsuite xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="https://maven.apache.org/surefire/maven-surefire-plugin/xsd/surefire-test-report.xsd" version="3.0.2" name="com.trustvault.trustvault.controller.DocumentControllerAuthorizationTest" time="3.675" tests="1" errors="0" skipped="0" failures="0">
<testsuite xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="https://maven.apache.org/surefire/maven-surefire-plugin/xsd/surefire-test-report.xsd" version="3.0.2" name="com.trustvault.trustvault.service.DocumentAuthorizationServiceTest" time="1.106" tests="6" errors="0" skipped="0" failures="0">
<testsuite xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="https://maven.apache.org/surefire/maven-surefire-plugin/xsd/surefire-test-report.xsd" version="3.0.2" name="com.trustvault.trustvault.TrustvaultApplicationTests" time="3.676" tests="1" errors="1" skipped="0" failures="0">

## Scenarios to verify explicitly
- VIEWER cannot modify or delete documents without authorization.
- Users cannot access documents belonging to other users without a grant.
- READ grants allow reading but deny editing.
- EDIT grants allow only the intended permitted operations.
- Revocation blocks subsequent requests.
- Denied operations are audited without leaking document contents.
- Audit hash-chain integrity detects unexpected tampering.
- Upload validation rejects disallowed file types and oversized files.

This is an inventory, not proof that missing tests fail or that listed scenarios pass.
