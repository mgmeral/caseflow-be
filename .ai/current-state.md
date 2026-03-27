# CaseFlow — Current State

**Last updated:** 2026-03-27 (CI/CD added)

---

## Build Status

- **Main compile:** PASSING
- **Tests:** 46/46 PASSING
- **Docker image:** Builds successfully (multi-stage, eclipse-temurin:21-jre-alpine)
- **docker-compose config:** VALID (app + postgres + mongo + minio)
- **CI pipeline:** GitHub Actions (`.github/workflows/ci.yml`) — build/test/docker
- **Spring Boot version:** 3.3.0
- **Java:** 21

---

## Implemented Layers

### Domain / Persistence
- All JPA entities in place: Ticket, History, AttachmentMetadata, Customer, Contact, User, Group, Note, Assignment, Transfer
- MongoDB document: EmailDocument
- All Spring Data repositories in place

### Service Layer
- TicketService, TicketQueryService — full CRUD + state transitions
- CustomerService, ContactService — CRUD + activate/deactivate + findAll
- UserService, GroupService — CRUD + activate/deactivate + findAll
- NoteService — add, getById, listByTicket
- AssignmentService — assign/reassign/unassign/getActive
- TransferService — transfer/history
- TicketHistoryService — all history recording methods
- TicketStateMachineService — validated state transitions
- EmailProcessingServiceImpl — email ingestion
- EmailDocumentQueryService — read-only email queries
- AttachmentService — metadata management + binary upload/download via ObjectStorageService

### API Layer
- 9 REST controllers (Ticket, Customer, Contact, User, Group, Note, Assignment, Transfer, EmailDocument)
- All controllers use constructor injection, @Valid, ResponseEntity
- All controllers tagged with @Tag for OpenAPI grouping

### DTOs
- 40 Java record DTOs across all modules
- Full Jakarta validation annotations on request DTOs

### Mappers
- 11 MapStruct interfaces across all modules
- unmappedTargetPolicy = ERROR, unmappedSourcePolicy = IGNORE

---

## Infrastructure Added (this session)

### Application Entry Point
- `CaseFlowApplication.java` — main Spring Boot application class

### Configuration
- `application.properties` — PostgreSQL, MongoDB, Flyway, Springdoc, storage, CORS config
- `application-dev.properties` — debug logging, dev-only flags

### Global Exception Handling
- `ErrorResponse` record — standard JSON error shape (timestamp, status, error, code, message, path, details, requestId)
- `FieldViolation` record — per-field validation error (field, message, rejectedValue, code)
- `GlobalExceptionHandler` (@RestControllerAdvice) — covers: not found, conflict, invalid state, validation, malformed request, missing params, type mismatch, auth exceptions, unexpected errors

### Security
- `SecurityConfig` — Spring Security with HTTP Basic auth
- In-memory users: admin/admin123 (ADMIN), agent/agent123 (AGENT), viewer/viewer123 (VIEWER)
- Role-based access: VIEWER=GET, AGENT=POST/PUT/PATCH, ADMIN=DELETE
- CORS configured for localhost:3000 and localhost:5173
- Swagger/OpenAPI endpoints publicly accessible

### OpenAPI / Swagger
- `OpenApiConfig` — API info, basicAuth security scheme, module tags
- All 9 controllers annotated with @Tag
- Swagger UI available at /swagger-ui.html

### Storage
- `ObjectStorageService` interface — store/retrieve/delete/exists
- `LocalFileStorageService` — filesystem implementation with path traversal protection
- `StorageProperties` (@ConfigurationProperties) — rootPath, provider
- `AttachmentService` updated to accept and use ObjectStorageService
- New methods: `upload()` (store binary + save metadata), `download()` (retrieve stream)

### Database Migrations
- `db/migration/V1__init_schema.sql` — complete baseline schema for all 11 relational tables
- Partial unique index on assignments for active assignment constraint
- Indexes on all FK/lookup paths

### Seed Data
- `DevDataLoader` (@Component @Profile("dev")) — idempotent ApplicationRunner
- Seeds: 3 groups, 3 users, 2 customers, 3 contacts, 3 tickets, 2 notes, 1 sample email

### Tests
- **Service tests (26):**
  - TicketServiceTest — 7 tests (create, close, invalid state, not found, update, changeStatus, reopen)
  - TicketQueryServiceTest — 6 tests (getById found/not found, getByTicketNo, listByStatus, findAll)
  - AssignmentServiceTest — 6 tests (assign, conflict, not found, unassign, reassign, getActive empty)
  - NoteServiceTest — 4 tests (add+history, getById, not found, listByTicket)
  - EmailDocumentQueryServiceTest — 5 tests (findById, not found, findByTicketId empty/found, findByThreadKey)
- **Controller tests (20):**
  - TicketControllerTest — 7 tests (getById 200/404/401, list 200, create 201/400/403)
  - CustomerControllerTest — 5 tests (getById 200/404, list 200, create 201, 401 unauth)
  - NoteControllerTest — 6 tests (add 201/400, getById 200/404, getByTicket 200/401)

### FE Integration
- `docs/api-notes.md` — endpoint reference, auth, error shapes, enum values, CORS, seed data
- Consistent JSON error responses with FE-friendly field violation structure
- CORS pre-configured for common FE dev server ports
- Enum serialization uses string names by default (Spring Boot default)

---

## Module Boundaries (preserved)

```
customer     → none
identity     → none
ticket       → customer, identity (via FK only, no service cross-calls)
workflow     → ticket, identity (AssignmentService, TransferService, HistoryService)
note         → ticket, workflow.history
email        → ticket (ticketId FK), storage (optional)
storage      → none (AttachmentService depends on ticket.repository)
common       → exception classes + api (ErrorResponse, GlobalExceptionHandler) + security + config + dev
```

---

## Containerisation (added 2026-03-27)

- `Dockerfile` — multi-stage (maven:3.9.6-eclipse-temurin-21-alpine builder → eclipse-temurin:21-jre-alpine runtime), non-root user, `MaxRAMPercentage=75`
- `.dockerignore` — excludes target/, .git, IDE files, logs, storage-data, .env, docs, .ai
- `docker-compose.yml` — app + postgres:16-alpine + mongo:7-jammy, named volumes, health checks, `depends_on: service_healthy`
- `.env.example` — documents all overridable env vars
- `README.md` — added quick-start instructions
- `docs/deployment-local.md` — full local Docker guide
- `application.properties` — all backing-service URLs now env-var driven with localhost defaults preserved

One-command local start: `docker compose up -d`
Seed data: `SPRING_PROFILES_ACTIVE=dev docker compose up -d`
Health check path: `GET /actuator/health` (public, no auth)

## CI/CD (added 2026-03-27)

- `.github/workflows/ci.yml` — GitHub Actions pipeline: build+test (Java 21, Maven) → Docker build → GHCR push on main/master
- `Dockerfile.ci` — CI-specific runtime-only Dockerfile (copies pre-built JAR; faster than multi-stage in CI)
- `Dockerfile.ci.dockerignore` — companion ignore file that does NOT exclude `target/`
- `docs/ci-cd.md` — full CI/CD guide (pipeline overview, registry config, secrets, local commands, env vars)
- `.gitignore` — added `.env`, `*.log`, `logs/`, `storage-data/` exclusions
- `README.md` — added CI badge placeholder and CI/CD section

Pipeline jobs:
1. `test` — `mvn verify` (no real DB needed; all tests are unit/@WebMvcTest), uploads test reports + JAR
2. `docker` — downloads JAR, builds with Dockerfile.ci, pushes to GHCR on main/master (uses GITHUB_TOKEN, no extra secrets)

Image tagging: `sha-<short-sha>`, `latest` (main/master only), branch name

## Remaining Gaps / Known Issues

See `.ai/remaining-issues.md`
