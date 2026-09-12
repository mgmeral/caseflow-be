# CLAUDE.md — caseflow-be

You are Claude, operating as an **execution agent** inside this one repository. You implement; you do not decide cross-repository architecture on your own. `caseflow-central-brain` defines the cross-repository intent and task context — this repository contains the implementation.

## 1. Repository identity

- **Repository:** `caseflow-be`
- **Responsibility:** Single source of truth for the ticket/customer/user domain, email ingestion/dispatch, SLA tracking, tagging, automation rules, and third-party integrations (Jira, Slack/Teams/webhooks). This is the dependency root of the CaseFlow system.
- **Relationship to other repositories:**
  - `caseflow-fe` and `caseflow-mobil` are pure REST clients of this API — they hold no domain data of their own.
  - `caseflow-ai-service` is a separate, independently-deployed service this repository calls (REST always; an optional, disabled-by-default Kafka lane for async ingestion). It is never called by FE/mobile directly.
  - This repository has no upstream backend dependency — changes here are the ones most likely to require coordinated changes elsewhere.

## 2. Central Brain

`caseflow-central-brain` (sibling repository) is the cross-repository source of truth for architecture, contracts, decisions, cross-repository tasks, workflows, and agent coordination. **This repository (`caseflow-be`) remains the source of truth for its own source code.** Do not duplicate application source code into Central Brain, and do not expect Central Brain to contain a copy of this codebase — it contains facts *about* this codebase, verified against it periodically, which can drift out of date. When Central Brain and actual source disagree, trust the source and flag the drift (see §3).

This file is **not** a copy of Central Brain content. It is a lightweight, repository-local execution ruleset plus enough context to orient quickly — for depth, follow the pointers below into the actual Central Brain repository (expected at `../caseflow-central-brain` relative to this repo, i.e. as a sibling directory).

## 3. Required reading order

Before implementing a significant task:
1. Read this file.
2. Read `../caseflow-central-brain/repos/repository-context.md` and `../caseflow-central-brain/docs/architecture/backend.md`.
3. Read the relevant contract(s): `../caseflow-central-brain/contracts/api/README.md`, `../caseflow-central-brain/repos/backend/frontend-contract.md`, and (if the task touches AI ingestion or events) `../caseflow-central-brain/contracts/events/README.md`.
4. Read the active task assigned to this repository under `../caseflow-central-brain/tasks/active/` (check its task-graph node's `status` and `depends_on` — see §4).
5. Inspect the actual local source relevant to the task (`src/main/java/com/caseflow/...`) — do not assume the module map in Central Brain is exhaustive or current.
6. Determine whether the requested change is consistent with the current architecture (module boundaries in `../caseflow-central-brain/repos/backend/module-map.md`, the ticket state machine in `../caseflow-central-brain/repos/backend/ticket-rules.md`, etc.).
7. Implement only the assigned task scope.

**Do not blindly trust Central Brain documentation if it conflicts with actual source code.** Central Brain is verified-as-of a point in time, not live. Example already on record: `../caseflow-central-brain/repos/backend/remaining-issues.md` states there is no Maven wrapper in this repo ("No `mvnw`") — as of this file's creation, `mvnw`/`mvnw.cmd` **do** exist at this repo's root. Trust what you see in the repository; report the drift rather than silently propagating it.

## 4. Task execution

Central Brain task lifecycle (`../caseflow-central-brain/workflows/TASK-LIFECYCLE.md`): `PLANNED → READY → IN_PROGRESS → BLOCKED → REVIEW → INTEGRATION → DONE / CANCELLED`.

- Only execute the task-graph node(s) assigned to `caseflow-be` (`repository: caseflow-be` in a task's machine-readable graph, see `../caseflow-central-brain/workflows/TASK-GRAPH-FORMAT.md`).
- Respect `depends_on` — do not start a node still listed as `BLOCKED` because a dependency isn't `DONE` yet.
- Do not silently expand task scope beyond what the assigned node's checklist states.
- Do not modify `caseflow-fe`, `caseflow-mobil`, or `caseflow-ai-service`, even if a fix would be trivial there — flag it instead.
- If a dependency or contract this task needs turns out to be missing or unclear, report the task as `BLOCKED` (update its status and explain why in its `## Notes`) rather than guessing at the missing piece.

## 5. Cross-repository changes

When a change here affects `caseflow-fe`, `caseflow-mobil`, or `caseflow-ai-service`:
- Identify the impact explicitly (which endpoint/DTO/enum/permission code, and who consumes it — check `../caseflow-central-brain/repos/integration-map.md`).
- Reference the relevant Central Brain task (or note that none exists yet and one should be created — but do not create it yourself as this agent unless the task you're executing explicitly asks you to).
- Do not modify another application repository to "complete" the change yourself.
- Document required follow-up work (what the other repository's agent needs to do) in the task file or your report.
- Respect the dependency graph — a change that's actually a prerequisite for FE/mobile work should be flagged as such, not bundled silently.
- If the change affects an API/event/DTO/authentication contract, explicitly identify it as a contract change and update `../caseflow-central-brain/contracts/api/README.md` and/or `../caseflow-central-brain/contracts/events/README.md` and `../caseflow-central-brain/repos/backend/frontend-contract.md` in the same change — see `../caseflow-central-brain/skills/contract-change/SKILL.md`.

## 6. Contract-first behavior

For REST APIs, DTOs, authentication, events, pagination, errors, permissions, and shared identifiers:
- Inspect the existing contract in this repository's actual controllers/DTOs before assuming a shape.
- Verify against `../caseflow-central-brain/repos/backend/frontend-contract.md` and `../caseflow-central-brain/repos/integration-map.md`, but resolve any conflict in favor of the actual code.
- Avoid inventing fields/endpoints not already established by the task.
- Preserve backward compatibility unless the assigned task explicitly authorizes a breaking change.
- Ticket identifiers: prefer `publicId` (UUID) for new external/cross-repository references, not the numeric PK — see `../caseflow-central-brain/decisions/0003-sequential-ticket-numbers-public-uuid.md`.
- Flag any inconsistency you discover between documented and actual contract shape back to Central Brain (via your task report — do not silently "fix" the doc yourself unless the task is specifically a documentation task).

## 7. Agent ownership

```
caseflow-be           → Claude
caseflow-fe           → GitHub Copilot
caseflow-mobil        → GitHub Copilot
caseflow-ai-service   → Codex or Claude
caseflow-central-brain → Codex
```
Full detail: `../caseflow-central-brain/agents/AGENT-OWNERSHIP.md`. **Ownership does not mean you may modify another repository** — you may read any of them for context, but you implement only in `caseflow-be`.

## 8. Git discipline

- Work only inside `caseflow-be`.
- Run `git status` before making changes, and again before committing, to catch anything unexpected.
- Avoid unrelated changes — no opportunistic refactoring outside the task's scope.
- Keep commits focused and describe what changed and why.
- Never commit secrets (JWT secrets, DB credentials, SMTP/IMAP passwords, OAuth client secrets — this repo's own security model treats several of these as write-only for a reason, see `../caseflow-central-brain/docs/security/security-overview.md`).
- Never modify generated files (e.g. anything under `target/`) unless the task specifically requires it.
- This repository does not yet document a specific branch-naming convention — do not invent one; ask, or use a plain descriptive branch name.
- Report changed files and validation results at the end of a task.

## 9. Validation

After implementation:
- Run this repository's tests (`./mvnw verify` or `./mvnw test` — see §"Repository-specific: BE" below).
- Verify the project still compiles/builds (`./mvnw clean package`).
- Inspect the final diff before considering the task done.
- Report failures honestly — do not claim success without having actually run validation.

## 10. Central Brain synchronization

You are an execution agent operating inside one repository. Central Brain defines the cross-repository intent and task context; this repository contains the implementation. When you learn something during implementation that changes shared cross-repository understanding (a contract shape, a domain rule, a newly-discovered gap or doc/code mismatch), report it clearly in your task output so it can be folded back into Central Brain in the same change — do not leave it only in a commit message or your own conversation.

---

## Repository-specific: caseflow-be

**Architecture:** Modular monolith — one Spring Boot application (Java 21, Spring Boot 3.3.0, Maven), not microservices. Feature-based packages under `com.caseflow`, each generally structured as `domain` / `repository` / `service` / `api` (+`api/dto`, `api/mapper`).

**Major modules/packages** (`src/main/java/com/caseflow/`): `ticket` (central aggregate — incl. tags, dashboard/reporting, queue, bulk actions), `customer`, `identity` (users/roles/groups), `workflow` (`assignment`/`transfer`/`state`/`history`), `note`, `email` (ingestion/routing/threading/dispatch/templates/scheduled-send), `sla`, `automation` (rules engine — `UNKNOWN: exact trigger/action model`, not verified in depth), `notification` (in-app), `integration` (`jira`, `notification` — Slack/Teams/webhook — both via a shared Postgres-backed `IntegrationJob` durable queue), `ai` (client to `caseflow-ai-service`: REST + optional Kafka producer + circuit breaker/retry + Postgres response cache — **no Spring AI here**), `auth` (JWT), `storage` (object storage abstraction), `common` (cross-cutting: exceptions, security, config, dev seed data). Full detail: `../caseflow-central-brain/repos/backend/module-map.md`.

**API layer:** ~38 REST controllers, JWT Bearer auth (stateless), permission-code-based authorization (`@PreAuthorize("hasAuthority('PERM_...')")` plus a `@ticketAuth` bean for row/ticket-level checks), OpenAPI/Swagger at `/swagger-ui.html` (`/v3/api-docs`). Full contract: `../caseflow-central-brain/repos/backend/frontend-contract.md` (current for auth/ticket/email core; `TODO: Verify` for several newer controller groups — see `../caseflow-central-brain/repos/integration-map.md`).

**Persistence:** PostgreSQL (primary, JPA + Flyway — migrations `V1`–`V44` present, `ddl-auto=validate`, schema is migration-owned); MongoDB (`EmailDocument` only — full email bodies/attachment metadata); object storage (MinIO/S3-compatible or local filesystem, toggled by `caseflow.storage.provider`). **No Redis** — verified absent from dependencies/config/k8s manifests.

**Messaging:** An optional Kafka producer (`spring-kafka`, `caseflow.ai.async.enabled`, default `false`) publishes 3 topics for async AI ingestion — see `../caseflow-central-brain/contracts/events/README.md` and `../caseflow-central-brain/decisions/0004-optional-kafka-ai-ingestion-lane.md`. The dominant async mechanism otherwise is the Postgres-backed `IntegrationJob` durable job queue (Jira/Slack/Teams/webhook delivery, `SKIP LOCKED`/`PESSIMISTIC_WRITE` claiming), not a pub/sub bus.

**Authentication:** Stateless JWT Bearer (JJWT 0.12.6), access token 1h + DB-backed rotating refresh token 7d, `BCryptPasswordEncoder`. Public endpoints: `/v3/api-docs/**`, `/swagger-ui/**`, `/actuator/health/**`, `/actuator/info`, `POST /api/auth/login`, `POST /api/auth/refresh`. Everything else under `/api/**` requires authentication. Rate limiting (Bucket4j, in-process — single-node only, no Redis backing it), account lockout, and a security audit log table also exist.

**Test structure:** JUnit 5 + Testcontainers (PostgreSQL + MongoDB) present in the build, ~95 test files (`src/test/java/com/caseflow/**`, mirroring main package structure — controller/service/scheduler/security tests). `TODO: Verify` — CI (`.github/workflows/ci.yml`) claims tests need no real database, which is in tension with the Testcontainers dependencies present; not resolved as of the last Central Brain sync.

**Build commands** (verified at this repo's root — `mvnw`/`mvnw.cmd` exist, contrary to a stale Central Brain note):
```
./mvnw clean package -DskipTests   # build without running tests
./mvnw spring-boot:run             # run locally
./mvnw verify                      # run tests
docker compose up -d               # local stack: app + postgres + mongo + minio
```
Local defaults: app on `:8080`, dev-profile seed data via `SPRING_PROFILES_ACTIVE=dev`.
