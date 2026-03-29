# CaseFlow — Remaining Issues

**Last updated:** 2026-03-29 (V7 pass — P2 all resolved, P3 partial)

---

## Resolved in V2

- ✅ JWT auth replaces HTTP Basic
- ✅ Attachment upload/download endpoints
- ✅ `POST /api/emails/ingest` — idempotent email ingestion
- ✅ `createdBy`/`performedBy` removed from request bodies — resolved from JWT SecurityContext
- ✅ `GET /api/tickets/{id}/detail` — full response with attachments and history
- ✅ `GET /api/tickets` — pagination + JPA Specifications
- ✅ `CorrelationIdFilter` — MDC-based correlation IDs

## Resolved in V5

- ✅ Durable two-stage email ingress pipeline (receiveEvent → processEvent)
- ✅ EmailMailbox, EmailIngressEvent, OutboundEmailDispatch JPA entities
- ✅ EmailRoutingService — deterministic routing (exact-email → domain → contact → policy)
- ✅ EmailThreadingService — In-Reply-To / References header chain resolution
- ✅ LoopDetectionService — auto-reply/bounce/mailer-daemon detection
- ✅ SmtpEmailSender — optional JavaMailSender, permanent failure if unconfigured
- ✅ EmailDispatchService + EmailReplyService — durable outbound queue
- ✅ SKIP LOCKED scheduler workers (EmailIngressRetryScheduler, OutboundDispatchScheduler)
- ✅ CustomerEmailSettings + CustomerEmailRoutingRule entities and services
- ✅ Micrometer email metrics

## Resolved in V6

- ✅ New permissions: EMAIL_CONFIG_VIEW/MANAGE, EMAIL_OPERATIONS_VIEW/MANAGE, TICKET_EMAIL_VIEW, TICKET_EMAIL_REPLY_SEND — added to Permission enum + seeded in V10 migration
- ✅ `GET /api/auth/me` exposes `permissionCodes[]` — FE uses these, not role names
- ✅ EmailMailbox: displayName, defaultGroupId, defaultPriority, lastSuccessfulInboundAt, lastSuccessfulOutboundAt fields
- ✅ CustomerEmailSettings: trustedContactsOnly, autoCreateContact, allowSubdomains, defaultGroupId, defaultPriority fields
- ✅ MailboxController: PATCH activate/deactivate; permissions → PERM_EMAIL_CONFIG_VIEW/MANAGE
- ✅ IngressEventController: POST quarantine/release; list filters (status, mailboxId, ticketId); permissions → PERM_EMAIL_OPERATIONS_VIEW/MANAGE
- ✅ CustomerEmailSettingsController: PUT /rules/{ruleId}; permissions → PERM_EMAIL_CONFIG_VIEW/MANAGE
- ✅ TicketEmailController: GET /thread (primary FE contract), GET /inbound/{id}, GET /outbound/{id}; permissions → TICKET_EMAIL_VIEW / TICKET_EMAIL_REPLY_SEND via TicketAuthorizationService
- ✅ EmailIngressService: quarantineEvent, releaseEvent
- ✅ EmailDispatchService: getById
- ✅ RoutingRuleNotFoundException, DispatchNotFoundException — GlobalExceptionHandler updated
- ✅ V10 Flyway migration for new columns and permission seeds
- ✅ 202/202 tests passing (33 new: MailboxControllerTest expanded, IngressEventControllerTest, TicketEmailControllerTest, CustomerEmailSettingsControllerTest, EmailIngressServiceTest quarantine/release)
- ✅ docs/frontend-contract.md fully updated with V6 permission model and all email endpoints

---

## P1 — Blockers for Production Use

(None)

---

## Resolved in V7

- ✅ `lastSuccessfulInboundAt` stamped by `EmailIngressServiceImpl` after CREATE_TICKET / LINK_TO_TICKET
- ✅ `lastSuccessfulOutboundAt` stamped by `OutboundDispatchScheduler` after successful SMTP send (via `findByAddress`)
- ✅ Ticket numbers are now sequential — `V11__ticket_no_sequence.sql` + `ticket_no_seq` PostgreSQL sequence; format `TKT-%07d`; both `TicketService` and `EmailIngressServiceImpl.createTicketFromEvent` use it
- ✅ MongoDB indexes activated — `spring.data.mongodb.auto-index-creation=true` added; `@CompoundIndex(ticket_received)` added to `EmailDocument`
- ✅ Ingress event list paginated — `GET /api/admin/ingress-events` now returns `PagedResponse<IngressEventResponse>`; defaults `size=20, sort=receivedAt DESC`; `searchPaged()` method on `EmailIngressEventQueryService`
- ✅ Mailbox list `?activeOnly=true` filter added to `GET /api/admin/mailboxes`
- ✅ `CustomerEmailRoutingRule.updatedAt` — `V12__routing_rule_updated_at.sql` + `@PreUpdate` lifecycle hook; exposed in `RoutingRuleResponse`

---

## P2 — Important but Non-Blocking

(None)

---

## P3 — Quality / Nice to Have

### Integration tests
- All 202 tests are unit / @WebMvcTest — no real DB
- Schema errors only surface at deploy time
- Fix: `@SpringBootTest` + Testcontainers for PostgreSQL + MongoDB

### Controller coverage gaps
- `GroupController`, `UserController`, `AssignmentController`, `TransferController`, `ContactController`, `AttachmentController`, `EmailDocumentController` — no tests

### Validation messages
- Jakarta defaults ("must not be blank") — consider `ValidationMessages.properties`

### Storage: cloud provider
- `LocalFileStorageService` only; S3/MinIO/Azure Blob via `caseflow.storage.provider=s3`

### API rate limiting
- Not implemented; consider Bucket4j or API gateway

---

## CI/CD — External Dependencies

### No integration tests in CI
- Flyway migration regressions only surface at deployment time
- Add `@SpringBootTest` + Testcontainers profile

### GHCR visibility
- First push may fail if `Settings → Actions → Workflow permissions` is not "Read and write"

### CI badge URL placeholder
- `README.md` contains `your-org/caseflow` — replace once repo is pushed

### Maven not wrapped
- No `mvnw` — run `mvn wrapper:wrapper -Dmaven=3.9.6`

---

## Known Assumptions

- `groups` table name is used as-is in PostgreSQL (not a reserved keyword at table level)
- `performed_by` in `ticket_history` is nullable for system-generated events
- `flyway.baseline-on-migrate=true` — database may have tables from prior `ddl-auto=create` runs
- V10 permission seeds use `WHERE r.code = 'ADMIN'` etc. — safe no-ops if those role codes don't exist
