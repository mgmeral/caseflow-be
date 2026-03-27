# CaseFlow — Remaining Issues

**Last updated:** 2026-03-27 (V2 pass)

---

## Resolved in V2

- ✅ JWT auth replaces HTTP Basic (`AuthController`, `JwtTokenService`, `SecurityConfig`)
- ✅ Attachment upload/download endpoints (`AttachmentController`)
- ✅ `POST /api/emails/ingest` — email ingestion with idempotency and thread resolution
- ✅ `createdBy`/`performedBy` removed from request bodies — resolved from JWT SecurityContext
- ✅ `GET /api/tickets/{id}/detail` — full response with attachments and history
- ✅ `GET /api/tickets` — pagination with `PagedResponse<T>` and JPA Specifications
- ✅ `CorrelationIdFilter` — MDC-based correlation IDs on all requests

---

## P1 — Blockers for Production Use

(None remaining from original backlog)

---

## P2 — Important but Non-Blocking

### Ticket number generation
- `TicketService.createTicket()` needs to generate unique ticket numbers (e.g., TKT-00042)
- Currently the field exists but generation strategy is not implemented
- Suggest: DB sequence + zero-padded string, or UUID-based if human-readability is not required

### MongoDB indexes not declared programmatically
- `EmailDocument` fields `messageId`, `threadKey`, `ticketId` are indexed via `@Indexed` on the entity
- These create indexes on startup by default in Spring Data MongoDB
- Verify `spring.data.mongodb.auto-index-creation=true` (Spring Boot default) is acceptable, or add explicit `@Document`-level `@CompoundIndex` for query paths

---

## P3 — Quality / Nice to Have

### Integration tests
- All existing tests are unit tests (Mockito / @WebMvcTest)
- No integration tests against real databases
- Add a test profile with Testcontainers for PostgreSQL + MongoDB to catch schema/query issues

### More controller tests
- `GroupController`, `UserController`, `AssignmentController`, `TransferController`, `EmailDocumentController` have no tests yet
- `ContactController` has no tests yet

### Validation messages
- Currently use Jakarta default messages (e.g., "must not be blank")
- Consider adding `ValidationMessages.properties` for custom, more user-friendly messages

### Storage: cloud provider
- `LocalFileStorageService` is the only implementation
- S3/MinIO/Azure Blob provider can be added as a second implementation
- Activate via `caseflow.storage.provider=s3` and spring profile-based conditional bean

### Flyway: additional migrations
- V1 is the baseline schema
- Future changes to schema (e.g., adding `password` to users) should use V2__add_user_password.sql, etc.

### API rate limiting / request throttling
- Not currently implemented
- Consider Spring Boot's Bucket4j or an API gateway layer if needed

---

---

## CI/CD — External Dependencies (requires real environment)

### GHCR visibility
- First push to `ghcr.io` may fail if GitHub Actions workflow permissions are not set to "Read and write"
- Fix: `Settings → Actions → General → Workflow permissions → Read and write permissions`
- Alternatively set explicit `permissions: packages: write` (already in ci.yml) and ensure the repo owner has accepted the GitHub Packages ToS

### No integration tests in CI
- All 46 tests are unit / `@WebMvcTest` (no Testcontainers)
- Real database connectivity (Flyway migrations, JPA queries, MongoDB document ops) is NOT verified in CI
- Risk: schema migration errors only appear at deployment time
- Fix: add `@SpringBootTest` + Testcontainers tests with a `ci` profile that spins up real postgres + mongo + minio

### CI badge URL needs updating
- `README.md` contains `your-org/caseflow` placeholder in the CI badge URL
- Replace with actual GitHub org/username once the repo is pushed

### Maven not wrapped
- No `mvnw` in the repo — CI uses system Maven provided by `actions/setup-java`
- Locally, developers must have Maven 3.9+ installed
- Fix: run `mvn wrapper:wrapper -Dmaven=3.9.6` to generate `mvnw`, then commit

---

## Known Assumptions

- `groups` table name is used as-is in PostgreSQL (not a reserved keyword at table level)
- `performed_by` in `ticket_history` is nullable to support system-generated events
- `flyway.baseline-on-migrate=true` assumes the database may already have tables from `ddl-auto=create` runs
