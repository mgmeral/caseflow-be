# CaseFlow — Remaining Issues

**Last updated:** 2026-03-27 (CI/CD pass)

---

## P1 — Blockers for Production Use

### Security: Replace in-memory auth
- `SecurityConfig` uses `InMemoryUserDetailsManager` with hardcoded dev passwords
- Replace with a `UserDetailsService` backed by the `users` table (integrate with `User` JPA entity)
- Add password field to `User` entity and `users` table migration
- Consider JWT or session-based tokens depending on FE requirements

### No attachment upload/download endpoints
- `AttachmentService.upload()` and `download()` are implemented
- No REST endpoints expose these operations yet
- Suggested: `POST /api/tickets/{id}/attachments` (multipart), `GET /api/attachments/{objectKey}/download`

---

## P2 — Important but Non-Blocking

### Email processing entry point
- `EmailProcessingServiceImpl` exists but has no inbound trigger
- Missing: IMAP polling, webhook endpoint, or MQ listener to feed ParsedEmail into the processing pipeline
- `POST /api/emails/ingest` or a Spring Integration flow would complete this

### Ticket number generation
- `TicketService.createTicket()` needs to generate unique ticket numbers (e.g., TKT-00042)
- Currently the field exists but generation strategy is not implemented
- Suggest: DB sequence + zero-padded string, or UUID-based if human-readability is not required

### createdBy/performedBy from SecurityContext
- Controllers currently receive `createdBy`/`performedBy` from request bodies
- Should be populated from `SecurityContextHolder` for authenticated users
- Requires binding the `User.id` to the Spring Security principal

### MongoDB indexes not declared programmatically
- `EmailDocument` fields `messageId`, `threadKey`, `ticketId` are indexed via `@Indexed` on the entity
- These create indexes on startup by default in Spring Data MongoDB
- Verify `spring.data.mongodb.auto-index-creation=true` (Spring Boot default) is acceptable, or add explicit `@Document`-level `@CompoundIndex` for query paths

### TicketDetailResponse not wired to controller
- `TicketDetailResponse` DTO exists with attachments + history
- `TicketController` uses `TicketResponse` not `TicketDetailResponse` for GET /api/tickets/{id}
- Consider adding `GET /api/tickets/{id}/detail` that calls attachment + history services and assembles the full response

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
