# TrustVault Implementation Roadmap

## 1. Authentication and roles
- [ ] Authentication works and invalid credentials are rejected.
- [ ] ADMIN, OWNER, and VIEWER permissions are enforced server-side.
- [ ] Disabled accounts cannot access protected endpoints.
- [ ] Role and permission behavior is documented.

## 2. Document access control
- [ ] Upload and document ownership are enforced.
- [ ] READ grants allow reading/downloading but not editing.
- [ ] EDIT grants allow only the intended modifications.
- [ ] Only document owners can grant and revoke permissions.
- [ ] Revoked grants stop authorizing subsequent requests.
- [ ] Soft-deleted documents cannot be accessed.
- [ ] Unauthorized routes do not disclose document existence.

## 3. Audit and accountability
- [ ] Upload, download, edit, delete, and permission changes are audited.
- [ ] Denied access attempts are audited.
- [ ] Audit records contain actor, action, outcome, timestamp, and reason.
- [ ] Audit-chain integrity is tested.
- [ ] Audit failures have a defined, tested failure policy.
- [ ] Duplicate audit events are prevented.

## 4. Document management
- [ ] Upload validation and size limits are enforced.
- [ ] Search and filtering work correctly.
- [ ] Document metadata and categories are managed safely.
- [ ] Soft deletion and recovery/retention behavior are documented.
- [ ] File storage and download paths are protected.

## 5. Automated testing
- [ ] Cross-user access denial tests cover every document operation.
- [ ] READ versus EDIT behavior is tested.
- [ ] Grant and revoke behavior is tested.
- [ ] Disabled-account behavior is tested.
- [ ] Audit outcomes are asserted in integration tests.
- [ ] Database migration and clean-start tests pass.

## 6. Delivery requirements
- [ ] THREATS.md completed.
- [ ] PROMPTS.md contains at least five dated entries, including two about authentication/auditing.
- [ ] Database setup and clean-clone instructions documented.
- [ ] API documentation and role matrix completed.
- [ ] Five-minute demo script prepared.
- [ ] Deployment instructions and seed credentials handled securely.
- [ ] Live deployment and final write-up completed where required.
