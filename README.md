# TrustVault

TrustVault is a document management and sharing application built with Java,
Spring Boot, and a browser-based HTML/CSS/JavaScript interface.

**Project status:** In development. Security controls, deployment procedures,
and production readiness must be verified before use with sensitive documents.

## Technology stack

- Java 21
- Spring Boot 3.5.6
- Maven Wrapper
- Spring Security
- Spring Data JPA and Hibernate
- PostgreSQL
- Flyway database migrations
- HTML, CSS, and JavaScript

## Prerequisites

Install and configure:

1. JDK 21.
2. PostgreSQL.
3. Git.

Verify Java and PostgreSQL client availability:

```bash
java -version
git --version
psql --version
```

The Maven Wrapper (`mvnw` and `mvnw.cmd`) is included in the repository.

## Database setup

The default database configuration is:

| Setting | Default |
| --- | --- |
| Database | `trustvault` |
| Host and port | `localhost:5432` |
| Database username | `trustvault_app` |
| Database password | `change-me` |

These are development defaults, not production credentials. Configure a strong
password and set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` before running the
application. Do not commit credentials to source control.

For a local development database, connect to PostgreSQL as an administrator
and run the following SQL, replacing the example password with a strong,
locally chosen password:

```sql
CREATE ROLE trustvault_app LOGIN PASSWORD 'REPLACE_WITH_A_STRONG_PASSWORD';
CREATE DATABASE trustvault OWNER trustvault_app;
```

If the role or database already exists, inspect the existing configuration
instead of running these statements again unchanged.

The application enables Flyway migrations and includes these migration files:

- `V1__create_documents_table.sql`
- `V2__create_users_and_document_ownership.sql`
- `V3__document_permissions_and_audit.sql`
- `V4__soft_delete_documents.sql`

Hibernate is configured with `ddl-auto=validate`. Flyway is responsible for
applying versioned schema migrations; Hibernate validates the resulting schema.

## Configure and run locally

Set environment variables in the shell where you will start the application.
For Git Bash, an example is:

```bash
export DB_URL="jdbc:postgresql://localhost:5432/trustvault"
export DB_USERNAME="trustvault_app"
export DB_PASSWORD="REPLACE_WITH_YOUR_LOCAL_PASSWORD"
```

Do not paste real credentials into shared logs or commit them to the repository.

Run the application:

```bash
./mvnw spring-boot:run
```

On Windows Command Prompt, use:

```bat
set DB_URL=jdbc:postgresql://localhost:5432/trustvault
set DB_USERNAME=trustvault_app
set DB_PASSWORD=REPLACE_WITH_YOUR_LOCAL_PASSWORD
mvnw.cmd spring-boot:run
```

The configured default HTTP port is `8080`; the `PORT` environment variable
can override it. When startup succeeds, open:

`http://localhost:8080/`

The application needs a reachable PostgreSQL database and valid credentials.
Successful startup and database migration have not been established by this
document alone.

## File storage and upload limits

The default private file storage path is `./private-storage`. The
`TV_STORAGE_PATH` environment variable can override it.

The configured maximum individual upload size is 2 GiB. The related settings
can be overridden with `TV_MAX_FILE_SIZE`, `TV_MAX_REQUEST_SIZE`, and
`TV_MAX_FILE_BYTES`.

Keep private storage outside public web roots, restrict filesystem access,
monitor available disk space, and include stored files in a tested backup and
recovery plan. A configured size limit does not replace quotas or abuse controls.

## Run tests

Run the automated test suite:

```bash
./mvnw test
```

On Windows Command Prompt:

```bat
mvnw.cmd test
```

Latest local verification reported 16 tests, 0 failures, 0 errors, and 0 skipped. The packaged JAR was present. These results should be reconfirmed from the latest Surefire reports if the code changes.

## Security and operational notes

- Enforce authorization on the server for all document operations.
- Verify that permission revocation takes effect immediately.
- Verify audit event coverage and hash-chain integrity with automated tests.
- Do not expose database credentials, private storage, or uploaded files.
- Configure TLS, secure production secrets, backups, monitoring, and recovery
  procedures before production deployment.
- Review `THREATS.md` and `TRUSTVAULT_ROADMAP.md`; unchecked or unverified items
  must not be represented as completed.
- `PROMPTS.md` summarizes AI-assisted development topics and review obligations.

## Current limitations

This README does not certify that every required feature has been implemented,
that the application has passed a security audit, or that it is production-ready.
Documented behavior must be checked against the code, tests, and deployment
environment.

## Documentation

- `TRUSTVAULT_ROADMAP.md` — implementation and verification checklist
- `THREATS.md` — initial threat model and security testing plan
- `PROMPTS.md` — AI assistance log

## Submission links

- Source repository: https://github.com/hari2006-git/trustvault
- Deployed application: https://trustvault-6owi.onrender.com

## Current verification status

Latest local verification reported 16 tests, 0 failures, 0 errors, and
0 skipped. The packaged application JAR was present. The deployed site
responded with HTTP 200 and redirected to `/login`.

These checks confirm the reported automated test result, build artifact,
and login-page response. They do not establish that every deployed
authentication, document authorization, permission revocation, and audit
workflow has passed end-to-end verification. Complete those checks before
claiming full submission readiness or production readiness.
