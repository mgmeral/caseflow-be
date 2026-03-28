# CaseFlow — Frontend Integration Contract

**Source of truth:** controllers + DTOs in `src/main/java/com/caseflow/`.
**TypeScript types:** `docs/api-models.ts`
**Full endpoint examples:** `docs/api-endpoints.md`
**Swagger UI:** `http://localhost:8080/swagger-ui.html`

---

## Base URL

```
http://localhost:8080/api
```

---

## Auth Flow

CaseFlow uses **JWT Bearer** tokens. There is no session cookie.

### 1 — Login

```
POST /api/auth/login
Content-Type: application/json

{ "username": "bob", "password": "agent123" }
```

Response `200`:
```json
{
  "accessToken":  "eyJ...",
  "refreshToken": "uuid-string",
  "expiresIn":    3600000,
  "tokenType":    "Bearer"
}
```

Store both tokens. `expiresIn` is in **milliseconds** (1 hour).

### 2 — Authenticated requests

```
Authorization: Bearer <accessToken>
```

Include this header on every protected endpoint.

### 3 — Token refresh

When the access token expires (or on 401), rotate the pair:

```
POST /api/auth/refresh
Content-Type: application/json

{ "refreshToken": "uuid-string" }
```

Response `200`: same shape as login. The old refresh token is revoked immediately (rotation).

### 4 — Logout

```
POST /api/auth/logout
Content-Type: application/json

{ "refreshToken": "uuid-string" }
```

Response `204`. Revokes the refresh token server-side.

### 5 — Current user

```
GET /api/auth/me
Authorization: Bearer <accessToken>
```

Response `200`:
```json
{ "id": 1, "username": "bob", "email": "bob@caseflow.dev", "fullName": "Bob Agent", "role": "AGENT" }
```

---

## Role Permission Map

| Role   | HTTP methods allowed           |
|--------|--------------------------------|
| ADMIN  | GET · POST · PUT · PATCH · DELETE |
| AGENT  | GET · POST · PUT · PATCH       |
| VIEWER | GET only                       |

The server returns `403` if the authenticated role is insufficient.

---

## Pagination

Only `GET /api/tickets` is paginated. Response shape:

```json
{
  "items":         [ ...TicketSummaryResponse ],
  "page":          0,
  "size":          20,
  "totalElements": 150,
  "totalPages":    8
}
```

Query params:

| Param       | Default     | Description                                |
|-------------|-------------|--------------------------------------------|
| `page`      | `0`         | Zero-based page index                      |
| `size`      | `20`        | Page size (max 100)                        |
| `sort`      | `createdAt` | Field to sort by                           |
| `direction` | `desc`      | `asc` or `desc`                            |
| `status`    | —           | Filter by `TicketStatus`                   |
| `priority`  | —           | Filter by `TicketPriority`                 |
| `userId`    | —           | Filter by assigned user ID                 |
| `groupId`   | —           | Filter by assigned group ID                |
| `customerId`| —           | Filter by customer ID                      |
| `search`    | —           | Free-text on subject / ticket number       |
| `from`      | —           | ISO 8601 lower bound on `createdAt`        |
| `to`        | —           | ISO 8601 upper bound on `createdAt`        |

---

## Error Response

All errors return the same JSON shape:

```json
{
  "timestamp": "2026-03-27T10:00:00Z",
  "status":    404,
  "error":     "Not Found",
  "code":      "TICKET_NOT_FOUND",
  "message":   "Ticket not found: 99",
  "path":      "/api/tickets/99",
  "details":   [],
  "requestId": "abc-123"
}
```

Validation errors (400) include `details`:

```json
"details": [
  { "field": "subject", "message": "must not be blank", "rejectedValue": "", "code": "NotBlank" }
]
```

Every response includes `X-Correlation-Id` header for log tracing.

### Error codes

| Code                    | Status | Trigger                                  |
|-------------------------|--------|------------------------------------------|
| `TICKET_NOT_FOUND`      | 404    | Unknown ticket ID                        |
| `CUSTOMER_NOT_FOUND`    | 404    | Unknown customer ID                      |
| `CONTACT_NOT_FOUND`     | 404    | Unknown contact ID                       |
| `USER_NOT_FOUND`        | 404    | Unknown user ID                          |
| `GROUP_NOT_FOUND`       | 404    | Unknown group ID                         |
| `NOTE_NOT_FOUND`        | 404    | Unknown note ID                          |
| `ATTACHMENT_NOT_FOUND`  | 404    | Unknown attachment ID                    |
| `ASSIGNMENT_CONFLICT`   | 409    | Active assignment already exists         |
| `DUPLICATE_EMAIL`       | 409    | `messageId` already ingested             |
| `INVALID_TICKET_STATE`  | 422    | Illegal state transition                 |
| `VALIDATION_FAILED`     | 400    | Bean validation error                    |
| `MALFORMED_REQUEST`     | 400    | Request body missing or unparseable      |
| `MISSING_PARAMETER`     | 400    | Required query parameter absent          |
| `TYPE_MISMATCH`         | 400    | Path/query parameter type error          |
| `UNAUTHORIZED`          | 401    | No or invalid Bearer token               |
| `INVALID_CREDENTIALS`   | 401    | Bad username/password or expired token   |
| `FORBIDDEN`             | 403    | Authenticated but insufficient role      |
| `INTERNAL_ERROR`        | 500    | Unexpected server error                  |

---

## Endpoints Quick Reference

### Auth (`/api/auth`) — public

| Method | Path              | Body                           | Response           |
|--------|-------------------|--------------------------------|--------------------|
| POST   | /login            | `{username, password}`         | `TokenResponse`    |
| POST   | /refresh          | `{refreshToken}`               | `TokenResponse`    |
| POST   | /logout           | `{refreshToken}`               | 204                |
| GET    | /me               | —                              | `MeResponse`       |

### Tickets (`/api/tickets`) — bearer required

| Method | Path                          | Min role | Body / Notes                                  | Response                        |
|--------|-------------------------------|----------|-----------------------------------------------|---------------------------------|
| POST   | /                             | AGENT    | `{subject, description?, priority, customerId}` | `TicketResponse` 201          |
| GET    | /                             | VIEWER   | query params (see pagination section)         | `PagedResponse<TicketSummaryResponse>` |
| GET    | /{id}                         | VIEWER   | —                                             | `TicketResponse`                |
| GET    | /{id}/detail                  | VIEWER   | —                                             | `TicketDetailResponse`          |
| GET    | /by-ticket-no/{ticketNo}      | VIEWER   | —                                             | `TicketResponse`                |
| PUT    | /{id}                         | AGENT    | `{subject?, description?, priority?}`         | `TicketResponse`                |
| POST   | /{id}/status                  | AGENT    | `{status}`                                    | `TicketResponse`                |
| POST   | /{id}/close                   | AGENT    | `{}` (empty body)                             | `TicketResponse`                |
| POST   | /{id}/reopen                  | AGENT    | `{}` (empty body)                             | `TicketResponse`                |

> `createdBy` / audit fields are **never** in request bodies — resolved from JWT.

### Customers (`/api/customers`) — bearer required

| Method | Path                      | Min role | Body                  | Response                      |
|--------|---------------------------|----------|-----------------------|-------------------------------|
| POST   | /                         | AGENT    | `{name, code}`        | `CustomerResponse` 201        |
| GET    | /                         | VIEWER   | —                     | `CustomerSummaryResponse[]`   |
| GET    | /{id}                     | VIEWER   | —                     | `CustomerResponse`            |
| PUT    | /{id}                     | AGENT    | `{name?, code?}`      | `CustomerResponse`            |
| PATCH  | /{id}/activate            | AGENT    | —                     | 204                           |
| PATCH  | /{id}/deactivate          | AGENT    | —                     | 204                           |

### Contacts (`/api/contacts`) — bearer required

| Method | Path                      | Min role | Body                                       | Response                    |
|--------|---------------------------|----------|--------------------------------------------|------------------------------|
| POST   | /                         | AGENT    | `{customerId, email, name, isPrimary}`     | `ContactResponse` 201        |
| GET    | /                         | VIEWER   | —                                          | `ContactSummaryResponse[]`  |
| GET    | /{id}                     | VIEWER   | —                                          | `ContactResponse`           |
| GET    | /by-email?email=          | VIEWER   | —                                          | `ContactResponse`           |
| GET    | /by-customer/{customerId} | VIEWER   | —                                          | `ContactSummaryResponse[]`  |
| PUT    | /{id}                     | AGENT    | `{name?, isPrimary?, isActive?}`           | `ContactResponse`           |

### Users (`/api/users`) — bearer required

| Method | Path                 | Min role | Body                        | Response                 |
|--------|----------------------|----------|-----------------------------|--------------------------|
| POST   | /                    | ADMIN    | `{username, email, fullName}` | `UserResponse` 201     |
| GET    | /                    | VIEWER   | —                           | `UserSummaryResponse[]`  |
| GET    | /{id}                | VIEWER   | —                           | `UserResponse`           |
| GET    | /by-username?username=| VIEWER  | —                           | `UserResponse`           |
| GET    | /by-email?email=     | VIEWER   | —                           | `UserResponse`           |
| PUT    | /{id}                | ADMIN    | `{email?, fullName?}`       | `UserResponse`           |
| PATCH  | /{id}/activate       | ADMIN    | —                           | 204                      |
| PATCH  | /{id}/deactivate     | ADMIN    | —                           | 204                      |

### Groups (`/api/groups`) — bearer required

| Method | Path                      | Min role | Body              | Response                  |
|--------|---------------------------|----------|-------------------|---------------------------|
| POST   | /                         | ADMIN    | `{name, type}`    | `GroupResponse` 201       |
| GET    | /                         | VIEWER   | —                 | `GroupSummaryResponse[]`  |
| GET    | /{id}                     | VIEWER   | —                 | `GroupResponse`           |
| GET    | /by-type?type=            | VIEWER   | —                 | `GroupSummaryResponse[]`  |
| PUT    | /{id}                     | ADMIN    | `{name?, type?}`  | `GroupResponse`           |
| PATCH  | /{id}/activate            | ADMIN    | —                 | 204                       |
| PATCH  | /{id}/deactivate          | ADMIN    | —                 | 204                       |

### Notes (`/api/notes`) — bearer required

| Method | Path                      | Min role | Body                              | Response           |
|--------|---------------------------|----------|-----------------------------------|--------------------|
| POST   | /                         | AGENT    | `{ticketId, content, type}`       | `NoteResponse` 201 |
| GET    | /{id}                     | VIEWER   | —                                 | `NoteResponse`     |
| GET    | /by-ticket/{ticketId}     | VIEWER   | —                                 | `NoteResponse[]`   |

### Assignments (`/api/assignments`) — bearer required

| Method | Path                          | Min role | Body                                          | Response             |
|--------|-------------------------------|----------|-----------------------------------------------|----------------------|
| POST   | /assign                       | AGENT    | `{ticketId, assignedUserId?, assignedGroupId?}` | `AssignmentResponse` |
| POST   | /reassign                     | AGENT    | `{ticketId, newUserId?, newGroupId?}`         | `AssignmentResponse` |
| POST   | /unassign                     | AGENT    | `{ticketId}`                                  | 204                  |
| GET    | /by-ticket/{ticketId}         | VIEWER   | —                                             | `AssignmentResponse` |

### Transfers (`/api/transfers`) — bearer required

| Method | Path                          | Min role | Body                                                        | Response               |
|--------|-------------------------------|----------|-------------------------------------------------------------|------------------------|
| POST   | /                             | AGENT    | `{ticketId, fromGroupId, toGroupId, reason, clearAssignee}` | `TransferResponse` 201 |
| GET    | /by-ticket/{ticketId}         | VIEWER   | —                                                           | `TransferSummaryResponse[]` |

### Emails (`/api/emails`) — bearer required

| Method | Path                          | Min role | Body / Notes                                         | Response                        |
|--------|-------------------------------|----------|------------------------------------------------------|---------------------------------|
| POST   | /ingest                       | AGENT    | `IngestEmailRequest` — idempotent on `messageId`     | `EmailDocumentResponse` 201 / 409 |
| GET    | /{id}                         | VIEWER   | —                                                    | `EmailDocumentResponse`         |
| GET    | /by-ticket/{ticketId}         | VIEWER   | —                                                    | `EmailDocumentSummaryResponse[]` |
| GET    | /by-thread/{threadKey}        | VIEWER   | —                                                    | `EmailDocumentSummaryResponse[]` |

### Attachments (`/api/attachments`) — bearer required

| Method | Path                          | Min role | Body / Notes                          | Response                        |
|--------|-------------------------------|----------|---------------------------------------|---------------------------------|
| POST   | /upload                       | AGENT    | `multipart/form-data`, field `file`, max 25 MB | `AttachmentMetadataResponse` 201 |
| GET    | /{id}                         | VIEWER   | —                                     | `AttachmentMetadataResponse`    |
| GET    | /by-ticket/{ticketId}         | VIEWER   | —                                     | `AttachmentMetadataResponse[]`  |
| GET    | /{id}/download                | VIEWER   | — streams binary                      | binary (original Content-Type)  |
| DELETE | /{id}                         | ADMIN    | —                                     | 204                             |

---

## Key Response Shapes

### TicketResponse
```json
{
  "id":              1,
  "ticketNo":        "TKT-0001",
  "subject":         "string",
  "description":     "string | null",
  "status":          "NEW",
  "priority":        "HIGH",
  "customerId":      123,
  "assignedUserId":  null,
  "assignedGroupId": null,
  "createdAt":       "2026-03-27T10:00:00Z",
  "updatedAt":       "2026-03-27T10:00:00Z",
  "closedAt":        null
}
```

### TicketDetailResponse
Same as `TicketResponse` plus:
```json
{
  "attachments": [ ...AttachmentMetadataResponse ],
  "history":     [ ...HistorySummaryResponse ]
}
```

### AttachmentMetadataResponse
```json
{
  "id":          1,
  "ticketId":    1,
  "emailId":     null,
  "fileName":    "screenshot.png",
  "objectKey":   "tickets/1/uuid_screenshot.png",
  "contentType": "image/png",
  "size":        204800,
  "uploadedAt":  "2026-03-27T10:00:00Z"
}
```

### EmailDocumentResponse
```json
{
  "id":         "mongo-object-id",
  "messageId":  "<abc@mail.com>",
  "threadKey":  "thread-abc",
  "subject":    "string",
  "from":       "sender@example.com",
  "to":         ["support@caseflow.dev"],
  "cc":         [],
  "receivedAt": "2026-03-27T09:00:00Z",
  "parsedAt":   "2026-03-27T09:00:10Z",
  "ticketId":   1
}
```

---

## Enums

```
TicketStatus  : NEW | TRIAGED | ASSIGNED | IN_PROGRESS | WAITING_CUSTOMER | RESOLVED | CLOSED | REOPENED
TicketPriority: LOW | MEDIUM | HIGH | CRITICAL
NoteType      : INFO | INVESTIGATION | ESCALATION | INTERNAL
GroupType     : TRADE | OPERATIONS | SUPPORT
```

All enums serialize as their string name (Spring Boot default).

---

## CORS

Pre-configured allowed origins:
- `http://localhost:3000` (CRA)
- `http://localhost:5173` (Vite)

Configure additional origins via `CORS_ORIGINS` environment variable.

---

## Dev Seed Data

Start with `SPRING_PROFILES_ACTIVE=dev` to load:

| Entity     | Count | Details                                    |
|------------|-------|--------------------------------------------|
| Groups     | 2     | Support, Operations                        |
| Users      | 3     | alice (ADMIN), bob (AGENT), carol (VIEWER) |
| Customers  | 2     | ACME Corp, Globex Inc                      |
| Contacts   | 3     | john.doe, jane.doe, burns                  |
| Tickets    | 3     | TKT-0001 (NEW/CRITICAL), TKT-0002 (TRIAGED/HIGH), TKT-0003 (IN_PROGRESS/MEDIUM) |
| Notes      | 2     | On TKT-0001 and TKT-0003                   |
| Emails     | 1     | Linked to TKT-0001                         |

---

## What NOT to send

- `createdBy`, `performedBy`, `assignedBy`, `transferredBy` — resolved from JWT, never in request bodies
- `ticketNo` on create — managed by the server
- `status` on create — always starts as `NEW`
