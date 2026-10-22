# TrustVault Security Acceptance Matrix

Generated: 2026-10-22 12:06:57 IST

Status definitions: NOT VERIFIED, PASS WITH EVIDENCE, FAIL.

| Control | Required verification | Evidence to record |
|---|---|---|
| Authentication | Invalid credentials are rejected; sessions are protected | Automated test and HTTP result |
| Role authorization | ADMIN, OWNER and VIEWER restrictions are enforced server-side | Role-based integration tests |
| Document isolation | An unrelated user cannot read or download another user's file | Cross-user access-denial tests |
| READ grant | Reading is permitted; modification and deletion are denied | Permission-specific tests |
| EDIT grant | Only intended operations are permitted | Permission-specific tests |
| Revocation | Subsequent requests fail after access is revoked | Grant/revoke integration test |
| Upload validation | Invalid types and oversized files are rejected | Validation tests |
| Audit coverage | Successful and denied sensitive actions are recorded | Event assertions |
| Audit integrity | Tampering or broken hash links are detected | Hash-chain integrity tests |
| CSRF and sessions | State-changing requests and session handling are protected | Security configuration and integration tests |
| Data persistence | Data survives restart and migrations apply correctly | PostgreSQL integration test |
| CI | Workflow completes successfully using the intended test profile | GitHub Actions run URL |
| Operations | Secrets, storage access, backups and recovery are reviewed | Deployment evidence |

## Existing test files
src/test/java/com/trustvault/trustvault/controller/AdminUserControllerTest.java
src/test/java/com/trustvault/trustvault/controller/DocumentAccessIntegrationTest.java
src/test/java/com/trustvault/trustvault/controller/DocumentControllerAuthorizationTest.java
src/test/java/com/trustvault/trustvault/service/DocumentAuthorizationServiceTest.java
src/test/java/com/trustvault/trustvault/TrustvaultApplicationTests.java

## Security configuration references
src/main/java/com/trustvault/trustvault/config/SecurityConfig.java:7:import org.springframework.security.web.SecurityFilterChain;
src/main/java/com/trustvault/trustvault/config/SecurityConfig.java:8:import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
src/main/java/com/trustvault/trustvault/config/SecurityConfig.java:11:@EnableMethodSecurity
src/main/java/com/trustvault/trustvault/config/SecurityConfig.java:15:    public SecurityFilterChain securityFilterChain(
src/main/java/com/trustvault/trustvault/config/SecurityConfig.java:44:        // CSRF protection remains enabled by default.
src/main/java/com/trustvault/trustvault/controller/AdminUserController.java:12:import org.springframework.security.access.prepost.PreAuthorize;
src/main/java/com/trustvault/trustvault/controller/AdminUserController.java:36:    @PreAuthorize("hasRole('ADMIN')")
src/main/java/com/trustvault/trustvault/controller/CsrfController.java:4:import org.springframework.security.web.csrf.CsrfToken;
src/main/java/com/trustvault/trustvault/controller/CsrfController.java:9:public class CsrfController {
src/main/java/com/trustvault/trustvault/controller/CsrfController.java:12:            value = "/api/csrf",
src/main/java/com/trustvault/trustvault/controller/CsrfController.java:14:    public CsrfTokenResponse getCsrfToken(CsrfToken csrfToken) {
src/main/java/com/trustvault/trustvault/controller/CsrfController.java:15:        return new CsrfTokenResponse(
src/main/java/com/trustvault/trustvault/controller/CsrfController.java:16:                csrfToken.getToken(),
src/main/java/com/trustvault/trustvault/controller/CsrfController.java:17:                csrfToken.getHeaderName(),
src/main/java/com/trustvault/trustvault/controller/CsrfController.java:18:                csrfToken.getParameterName()
src/main/java/com/trustvault/trustvault/controller/CsrfController.java:22:    public record CsrfTokenResponse(
src/main/java/com/trustvault/trustvault/controller/LoginPageController.java:5:import org.springframework.security.web.csrf.CsrfToken;
src/main/java/com/trustvault/trustvault/controller/LoginPageController.java:15:            CsrfToken csrfToken,
src/main/java/com/trustvault/trustvault/controller/LoginPageController.java:123:                                       name="__CSRF_PARAMETER__"
src/main/java/com/trustvault/trustvault/controller/LoginPageController.java:124:                                       value="__CSRF_TOKEN__">
src/main/java/com/trustvault/trustvault/controller/LoginPageController.java:184:                        "__CSRF_PARAMETER__",
src/main/java/com/trustvault/trustvault/controller/LoginPageController.java:185:                        escapeHtml(csrfToken.getParameterName()))
src/main/java/com/trustvault/trustvault/controller/LoginPageController.java:187:                        "__CSRF_TOKEN__",
src/main/java/com/trustvault/trustvault/controller/LoginPageController.java:188:                        escapeHtml(csrfToken.getToken()))
src/main/java/com/trustvault/trustvault/controller/RegistrationController.java:9:import org.springframework.security.web.csrf.CsrfToken;
src/main/java/com/trustvault/trustvault/controller/RegistrationController.java:25:    public String registrationForm(CsrfToken csrfToken) {
src/main/java/com/trustvault/trustvault/controller/RegistrationController.java:109:                                    name="__CSRF_PARAMETER__"
src/main/java/com/trustvault/trustvault/controller/RegistrationController.java:110:                                    value="__CSRF_TOKEN__">
src/main/java/com/trustvault/trustvault/controller/RegistrationController.java:187:                        "__CSRF_PARAMETER__",
src/main/java/com/trustvault/trustvault/controller/RegistrationController.java:188:                        escapeHtml(csrfToken.getParameterName()))
src/main/java/com/trustvault/trustvault/controller/RegistrationController.java:190:                        "__CSRF_TOKEN__",
src/main/java/com/trustvault/trustvault/controller/RegistrationController.java:191:                        escapeHtml(csrfToken.getToken()));

## Existing CI workflow
name: TrustVault Quality Gate

on:
  push:
    branches: [master, main]
  pull_request:
    branches: [master, main]
  workflow_dispatch:

permissions:
  contents: read

jobs:
  verify:
    runs-on: ubuntu-latest
    timeout-minutes: 20

    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: trustvault_test
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: trustvault_ci_test_password
        ports:
          - 5432:5432
        options: >-
          --health-cmd "pg_isready -U postgres -d trustvault_test"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 10

    steps:
      - name: Check out source
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Validate patch whitespace
        run: git diff --check

      - name: Verify build and tests
        env:
          TRUSTVAULT_TEST_DB_PASSWORD: trustvault_ci_test_password
        run: ./mvnw --batch-mode --no-transfer-progress verify

## Important
No control is marked PASS automatically. Record PASS only after the corresponding test or operational check succeeds.
