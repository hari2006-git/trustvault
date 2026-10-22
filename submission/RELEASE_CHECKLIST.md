# TrustVault — Final Submission Checklist

Repository: https://github.com/hari2006-git/trustvault
Deployment: https://trustvault-6owi.onrender.com
Review date: 2026-10-22

## 1. Build and automated tests
- [ ] Run `./mvnw verify` successfully against the configured PostgreSQL test database.
- [ ] Review test reports and confirm there are no failures or errors.
- [ ] Confirm the packaged application starts successfully.

## 2. Authentication and authorization
- [ ] Verify ADMIN, OWNER, and VIEWER permissions.
- [ ] Verify per-document READ and EDIT grants.
- [ ] Verify revocation takes effect immediately.
- [ ] Verify unauthorized read, download, edit, and delete requests are denied.
- [ ] Verify users cannot access other users' private documents.

## 3. Audit and data protection
- [ ] Verify successful and denied actions are audited.
- [ ] Verify audit hash-chain integrity using automated tests.
- [ ] Verify upload validation and file-size restrictions.
- [ ] Confirm secrets, credentials, and private uploaded files are not exposed.

## 4. Dashboard and usability
- [ ] Test upload, search, filters, document actions, and permission management.
- [ ] Test the dashboard on desktop and mobile.
- [ ] Check browser console and server logs for errors.

## 5. Deployment and submission
- [ ] Verify the deployed app's login and critical workflows.
- [ ] Configure production secrets, TLS, backups, monitoring, and recovery.
- [ ] Rotate credentials exposed during development.
- [ ] Record a demonstration video and prepare the submission write-up.
- [ ] Review README.md, PROMPTS.md, THREATS.md, and TRUSTVAULT_ROADMAP.md.
- [ ] Review the final diff and remove unintended files before committing.

## Release decision
NOT YET CERTIFIED. Mark each item complete only after collecting supporting evidence.
Do not represent a passing build or HTTP 200 response as proof of production readiness.
