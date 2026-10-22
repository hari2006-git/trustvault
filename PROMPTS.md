# TrustVault AI Usage Log

Date: 2026-10-21

## 1. Document authorization
Prompt: Help implement and review per-document READ and EDIT permissions, including ownership checks and permission revocation.
Use: Reviewed permission service and controller code.
Verification: Backend build and existing tests must be checked before claiming correctness.

## 2. Security audit logging
Prompt: Help record successful and denied document and permission operations in the audit log.
Use: Reviewed audit service integration and event recording.
Verification: Confirm event coverage and audit integrity through code review and tests.

## 3. Document dashboard
Prompt: Improve the document dashboard and its search and upload interactions.
Use: Developed dashboard functionality using the existing frontend.
Verification: Build success alone does not verify browser behavior.

## 4. Search and filtering
Prompt: Review document search for name, owner email, category, and date range.
Use: Inspected DocumentService.java and identified existing filter implementation.
Verification: Confirm controller parameters, frontend integration, and authorization tests.

## 5. Threat model and submission readiness
Prompt: Identify remaining security risks and prepare documentation and deployment tasks.
Use: Prepared a threat-model outline and prioritized remaining work.
Verification: Validate every claimed security control against the actual code and tests.

## 6. Test database compatibility
Date: 2026-10-22

Prompt: Help resolve full-suite failures caused by using H2 with PostgreSQL-specific Flyway migrations.
What went wrong: The H2 test database failed on the PostgreSQL TIMESTAMPTZ type, so the integration tests could not start.
What I did: I examined the test output and identified the migration/database compatibility problem instead of treating the failed tests as passing.
Current status: The latest reported local verification passed 16 tests with 0 failures, 0 errors, and 0 skipped, using the PostgreSQL test configuration. This does not certify production readiness. No production-readiness claim is made.
