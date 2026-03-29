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

### 3 — Token refresh

```
POST /api/auth/refresh
Content-Type: application/json

{ "refreshToken": "uuid-string" }
```

Response `200`: same shape as login. Old refresh token is revoked immediately.

### 4 — Logout

```
POST /api/auth/logout
Content-Type: application/json

{ "refreshToken": "uuid-string" }
```

Response `204`.

### 5 — Current user (`/me`)

```
GET /api/auth/me
Authorization: Bearer <accessToken>
```

Response `200`:
```json
{
  "id":              1,
  "username":        "bob",
  "email":           "bob@caseflow.dev",
  "fullName":        "Bob Agent",
  "roleId":          2,
  "roleCode":        "AGENT",
  "roleName":        "Agent",
  "permissionCodes": [
    "TICKET_READ", "TICKET_ASSIGN", "TICKET_STATUS_CHANGE",
    "TICKET_CLOSE", "INTERNAL_NOTE_ADD", "CUSTOMER_REPLY_SEND",
    "TICKET_EMAIL_VIEW", "TICKET_EMAIL_REPLY_SEND"
  ],
  "ticketScope": "OWN_AND_OWN_GROUPS",
  "groupIds":    [3, 7]
}
```

**The FE must use `permissionCodes` to gate UI features — never use `roleCode` for access decisions.** Role names are for display only.

---

## Permission Model

The backend enforces `PERM_<code>` authorities. The FE uses `permissionCodes` from `/me` to show/hide UI elements.

| Permission code             | What it gates                                          |
|-----------------------------|--------------------------------------------------------|
| `TICKET_READ`               | Read tickets (subject to `ticketScope`)                |
| `TICKET_ASSIGN`             | Assign / reassign tickets                              |
| `TICKET_TRANSFER`           | Transfer tickets between groups                        |
| `TICKET_STATUS_CHANGE`      | Change ticket status                                   |
| `TICKET_CLOSE`              | Close tickets                                          |
| `TICKET_PRIORITY_CHANGE`    | Change ticket priority                                 |
| `INTERNAL_NOTE_ADD`         | Add internal notes                                     |
| `ADMIN_POOL_VIEW`           | View unassigned ticket pool                            |
| `EMAIL_CONFIG_VIEW`         | Read mailboxes and customer email settings             |
| `EMAIL_CONFIG_MANAGE`       | Create / update / delete mailboxes and routing rules   |
| `EMAIL_OPERATIONS_VIEW`     | Read ingress events                                    |
| `EMAIL_OPERATIONS_MANAGE`   | Replay, quarantine, release ingress events             |
| `TICKET_EMAIL_VIEW`         | View email thread on a ticket                          |
| `TICKET_EMAIL_REPLY_SEND`   | Send outbound replies from a ticket                    |

### Starter role defaults

| Role       | Key permissions included                                                       |
|------------|--------------------------------------------------------------------------------|
| ADMIN      | All permissions                                                                |
| SUPERVISOR | TICKET_READ + workflow + EMAIL_CONFIG_VIEW + EMAIL_OPERATIONS_VIEW + TICKET_EMAIL_* |
| AGENT      | TICKET_READ + workflow + INTERNAL_NOTE_ADD + TICKET_EMAIL_VIEW + TICKET_EMAIL_REPLY_SEND |
| VIEWER     | TICKET_READ + TICKET_EMAIL_VIEW                                                |

---

## Pagination

Only `GET /api/tickets` is paginated.

```json
{
  "items":         [ ...TicketSummaryResponse ],
  "page":          0,
  "size":          20,
  "totalElements": 150,
  "totalPages":    8
}
```

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

```json
{
  "timestamp": "2026-03-29T10:00:00Z",
  "status":    404,
  "error":     "Not Found",
  "code":      "TICKET_NOT_FOUND",
  "message":   "Ticket not found: 99",
  "path":      "/api/tickets/99",
  "details":   [],
  "requestId": "abc-123"
}
```

Validation errors (400) populate `details`:
```json
"details": [
  { "field": "subject", "message": "must not be blank", "rejectedValue": "", "code": "NotBlank" }
]
```

Every response includes `X-Correlation-Id` header.

### Error codes

| Code                       | Status | Trigger                                          |
|----------------------------|--------|--------------------------------------------------|
| `TICKET_NOT_FOUND`         | 404    | Unknown ticket ID                                |
| `CUSTOMER_NOT_FOUND`       | 404    | Unknown customer ID                              |
| `CONTACT_NOT_FOUND`        | 404    | Unknown contact ID                               |
| `USER_NOT_FOUND`           | 404    | Unknown user ID                                  |
| `GROUP_NOT_FOUND`          | 404    | Unknown group ID                                 |
| `NOTE_NOT_FOUND`           | 404    | Unknown note ID                                  |
| `ATTACHMENT_NOT_FOUND`     | 404    | Unknown attachment ID                            |
| `MAILBOX_NOT_FOUND`        | 404    | Unknown mailbox ID                               |
| `INGRESS_EVENT_NOT_FOUND`  | 404    | Unknown ingress event ID                         |
| `DISPATCH_NOT_FOUND`       | 404    | Unknown dispatch ID                              |
| `ROUTING_RULE_NOT_FOUND`   | 404    | Unknown routing rule ID or customer mismatch     |
| `ASSIGNMENT_CONFLICT`      | 409    | Active assignment already exists                 |
| `DUPLICATE_EMAIL`          | 409    | `messageId` already ingested                     |
| `INVALID_TICKET_STATE`     | 422    | Illegal state transition                         |
| `VALIDATION_FAILED`        | 400    | Bean validation error                            |
| `MALFORMED_REQUEST`        | 400    | Request body missing or unparseable              |
| `MISSING_PARAMETER`        | 400    | Required query parameter absent                  |
| `TYPE_MISMATCH`            | 400    | Path/query parameter type error                  |
| `UNAUTHORIZED`             | 401    | No or invalid Bearer token                       |
| `INVALID_CREDENTIALS`      | 401    | Bad username/password or expired token           |
| `FORBIDDEN`                | 403    | Authenticated but insufficient permission        |
| `EMAIL_DISPATCH_FAILED`    | 503    | SMTP not configured or unreachable               |
| `INTERNAL_ERROR`           | 500    | Unexpected server error                          |

---

## Endpoints Quick Reference

### Auth (`/api/auth`) — public

| Method | Path     | Body                   | Response        |
|--------|----------|------------------------|-----------------|
| POST   | /login   | `{username, password}` | `TokenResponse` |
| POST   | /refresh | `{refreshToken}`       | `TokenResponse` |
| POST   | /logout  | `{refreshToken}`       | 204             |
| GET    | /me      | —                      | `MeResponse`    |

---

### Tickets (`/api/tickets`) — `TICKET_READ` required

| Method | Path                     | Required permission  | Body / Notes                                    | Response                               |
|--------|--------------------------|----------------------|-------------------------------------------------|----------------------------------------|
| POST   | /                        | TICKET_READ          | `{subject, description?, priority, customerId}` | `TicketResponse` 201                   |
| GET    | /                        | TICKET_READ          | query params (see pagination)                   | `PagedResponse<TicketSummaryResponse>` |
| GET    | /{id}                    | TICKET_READ          | —                                               | `TicketResponse`                       |
| GET    | /{id}/detail             | TICKET_READ          | —                                               | `TicketDetailResponse`                 |
| GET    | /by-ticket-no/{ticketNo} | TICKET_READ          | —                                               | `TicketResponse`                       |
| PUT    | /{id}                    | TICKET_READ          | `{subject?, description?, priority?}`           | `TicketResponse`                       |
| POST   | /{id}/status             | TICKET_STATUS_CHANGE | `{status}`                                      | `TicketResponse`                       |
| POST   | /{id}/close              | TICKET_CLOSE         | —                                               | `TicketResponse`                       |
| POST   | /{id}/reopen             | TICKET_STATUS_CHANGE | —                                               | `TicketResponse`                       |

---

### Ticket Email Thread (`/api/tickets/{ticketId}/email`) — **primary FE contract**

> Requires `TICKET_EMAIL_VIEW` + ticketScope on the ticket.

| Method | Path                   | Required permission     | Body / Notes                                                           | Response               |
|--------|------------------------|-------------------------|------------------------------------------------------------------------|------------------------|
| GET    | /thread                | TICKET_EMAIL_VIEW       | **Primary.** Merged inbound+outbound chronological timeline            | `EmailThreadItem[]`    |
| GET    | /inbound/{eventId}     | TICKET_EMAIL_VIEW       | Full detail for one inbound event                                      | `IngressEventResponse` |
| GET    | /outbound/{dispatchId} | TICKET_EMAIL_VIEW       | Full detail for one outbound dispatch                                  | `DispatchResponse`     |
| POST   | /reply                 | TICKET_EMAIL_REPLY_SEND | `{mailboxId, toAddress, subject, textBody, htmlBody?, inReplyToMessageId?}` | 202 Accepted      |
| GET    | /inbound               | TICKET_EMAIL_VIEW       | *(compat)* Inbound events list                                         | `IngressEventResponse[]` |
| GET    | /dispatches            | TICKET_EMAIL_VIEW       | *(compat)* Outbound dispatches list                                    | `DispatchResponse[]`   |

**`EmailThreadItem` shape:**
```json
{
  "direction":   "INBOUND",
  "id":          42,
  "messageId":   "<abc@mail.example>",
  "fromAddress": "john@acme.com",
  "toAddress":   null,
  "subject":     "Re: Support ticket",
  "status":      "PROCESSED",
  "timestamp":   "2026-03-29T09:00:00Z"
}
```
- `direction`: `"INBOUND"` | `"OUTBOUND"`
- `status`: `IngressEventStatus` name (INBOUND) or `DispatchStatus` name (OUTBOUND)
- Items are sorted chronologically by `timestamp`

---

### Email Ingest (`/api/emails`) — webhook / system

| Method | Path                    | Notes                                                                           | Response                         |
|--------|-------------------------|---------------------------------------------------------------------------------|----------------------------------|
| POST   | /ingest                 | Idempotent on `messageId`. Returns **202 Accepted**. Stage-2 runs async.        | `IngressEventResponse` 202       |
| GET    | /{id}                   | EmailDocument by MongoDB ID                                                     | `EmailDocumentResponse`          |
| GET    | /by-ticket/{ticketId}   | *(compat)* All email docs for a ticket                                          | `EmailDocumentSummaryResponse[]` |
| GET    | /by-thread/{threadKey}  | All email docs in a thread                                                      | `EmailDocumentSummaryResponse[]` |

---

### Admin — Mailboxes (`/api/admin/mailboxes`)

| Method | Path             | Required permission | Body           | Response              |
|--------|------------------|---------------------|----------------|-----------------------|
| GET    | /                | EMAIL_CONFIG_VIEW   | —              | `MailboxResponse[]`   |
| GET    | /{id}            | EMAIL_CONFIG_VIEW   | —              | `MailboxResponse`     |
| POST   | /                | EMAIL_CONFIG_MANAGE | `MailboxRequest` | `MailboxResponse` 201 |
| PUT    | /{id}            | EMAIL_CONFIG_MANAGE | `MailboxRequest` | `MailboxResponse`     |
| PATCH  | /{id}/activate   | EMAIL_CONFIG_MANAGE | —              | `MailboxResponse`     |
| PATCH  | /{id}/deactivate | EMAIL_CONFIG_MANAGE | —              | `MailboxResponse`     |
| DELETE | /{id}            | EMAIL_CONFIG_MANAGE | —              | 204                   |

`smtpPassword` is **never returned** — write-only.

---

### Admin — Ingress Events (`/api/admin/ingress-events`)

| Method | Path             | Required permission      | Body / Notes                             | Response                 |
|--------|------------------|--------------------------|------------------------------------------|--------------------------|
| GET    | /                | EMAIL_OPERATIONS_VIEW    | `?status=&mailboxId=&ticketId=`          | `IngressEventResponse[]` |
| GET    | /{id}            | EMAIL_OPERATIONS_VIEW    | —                                        | `IngressEventResponse`   |
| POST   | /{id}/process    | EMAIL_OPERATIONS_MANAGE  | — Replay FAILED event                    | `IngressEventResponse`   |
| POST   | /{id}/quarantine | EMAIL_OPERATIONS_MANAGE  | `{"reason": "string"}`                   | `IngressEventResponse`   |
| POST   | /{id}/release    | EMAIL_OPERATIONS_MANAGE  | — QUARANTINED → RECEIVED                 | `IngressEventResponse`   |

---

### Customer Email Settings (`/api/customers/{customerId}/email-settings`)

| Method | Path            | Required permission | Body / Notes               | Response                        |
|--------|-----------------|---------------------|----------------------------|---------------------------------|
| GET    | /               | EMAIL_CONFIG_VIEW   | —                          | `CustomerEmailSettingsResponse` |
| PUT    | /               | EMAIL_CONFIG_MANAGE | `CustomerEmailSettingsRequest` | `CustomerEmailSettingsResponse` |
| POST   | /rules          | EMAIL_CONFIG_MANAGE | `RoutingRuleRequest`       | `RoutingRuleResponse` 201       |
| PUT    | /rules/{ruleId} | EMAIL_CONFIG_MANAGE | `RoutingRuleRequest`       | `RoutingRuleResponse`           |
| DELETE | /rules/{ruleId} | EMAIL_CONFIG_MANAGE | —                          | 204                             |

---

### Customers, Contacts, Users, Groups, Notes, Assignments, Transfers, Attachments

> Unchanged. See `docs/api-endpoints.md` for full request/response details.

---

## Key Response Shapes

### `IngressEventResponse`
```json
{
  "id": 1, "mailboxId": 2,
  "messageId": "<abc@mail.example>", "rawFrom": "john@acme.com",
  "rawSubject": "Help with order", "receivedAt": "2026-03-29T09:00:00Z",
  "status": "PROCESSED", "failureReason": null,
  "processingAttempts": 1, "lastAttemptAt": "2026-03-29T09:00:05Z",
  "processedAt": "2026-03-29T09:00:05Z",
  "documentId": "mongo-object-id", "ticketId": 42
}
```

### `DispatchResponse`
```json
{
  "id": 10, "ticketId": 42,
  "messageId": "<uuid@caseflow>",
  "fromAddress": "support@caseflow.dev", "toAddress": "john@acme.com",
  "subject": "Re: Help with order", "status": "SENT",
  "attempts": 1, "lastAttemptAt": "2026-03-29T10:00:01Z",
  "sentAt": "2026-03-29T10:00:01Z", "failureReason": null,
  "scheduledAt": "2026-03-29T10:00:00Z", "createdAt": "2026-03-29T10:00:00Z"
}
```

### `MailboxResponse`
```json
{
  "id": 1, "name": "Support Inbox", "displayName": "CaseFlow Support",
  "address": "support@caseflow.dev",
  "providerType": "SMTP_RELAY", "inboundMode": "WEBHOOK", "outboundMode": "SMTP",
  "isActive": true, "defaultGroupId": 3, "defaultPriority": "MEDIUM",
  "smtpHost": "smtp.example.com", "smtpPort": 587, "smtpUsername": "user",
  "smtpUseSsl": false,
  "lastSuccessfulInboundAt": "2026-03-29T08:00:00Z",
  "lastSuccessfulOutboundAt": "2026-03-29T09:30:00Z",
  "createdAt": "2026-03-01T00:00:00Z", "updatedAt": "2026-03-29T08:00:00Z"
}
```

### `CustomerEmailSettingsResponse`
```json
{
  "id": 1, "customerId": 5,
  "unknownSenderPolicy": "MANUAL_REVIEW", "matchingStrategy": "CONTACT_FIRST",
  "isActive": true, "trustedContactsOnly": false,
  "autoCreateContact": false, "allowSubdomains": false,
  "defaultGroupId": 3, "defaultPriority": "MEDIUM",
  "updatedAt": "2026-03-29T08:00:00Z",
  "rules": [
    { "id": 1, "customerId": 5, "senderMatchType": "EXACT_EMAIL",
      "matchValue": "support@bigcorp.com", "priority": 10,
      "isActive": true, "createdAt": "2026-03-01T00:00:00Z" }
  ]
}
```

---

## Enums

```
TicketStatus       : NEW | TRIAGED | ASSIGNED | IN_PROGRESS | WAITING_CUSTOMER | RESOLVED | CLOSED | REOPENED
TicketPriority     : LOW | MEDIUM | HIGH | CRITICAL
NoteType           : INFO | INVESTIGATION | ESCALATION | INTERNAL
GroupType          : TRADE | OPERATIONS | SUPPORT
IngressEventStatus : RECEIVED | PROCESSING | PROCESSED | FAILED | QUARANTINED
DispatchStatus     : PENDING | SENDING | SENT | FAILED | PERMANENTLY_FAILED
ProviderType       : SMTP_RELAY | SENDGRID | MAILGUN
InboundMode        : WEBHOOK | IMAP_POLL | MANUAL
OutboundMode       : SMTP | API
SenderMatchType    : EXACT_EMAIL | DOMAIN
MatchingStrategy   : CONTACT_FIRST | RULE_FIRST
UnknownSenderPolicy: MANUAL_REVIEW | CREATE_UNMATCHED_TICKET | IGNORE | REJECT
```

All enums serialize as their string name (Spring Boot default).

---

## CORS

Pre-configured: `http://localhost:3000`, `http://localhost:5173`.
Add origins via `CORS_ORIGINS` env var.

---

## Dev Seed Data

Start with `SPRING_PROFILES_ACTIVE=dev`:

| Entity    | Count | Details                                                                  |
|-----------|-------|--------------------------------------------------------------------------|
| Groups    | 2     | Support, Operations                                                      |
| Users     | 3     | alice (ADMIN), bob (AGENT), carol (VIEWER)                              |
| Customers | 2     | ACME Corp, Globex Inc                                                    |
| Contacts  | 3     | john.doe, jane.doe, burns                                                |
| Tickets   | 3     | TKT-0001 (NEW/CRITICAL), TKT-0002 (TRIAGED/HIGH), TKT-0003 (IN_PROGRESS/MEDIUM) |
| Notes     | 2     | On TKT-0001 and TKT-0003                                                 |
| Emails    | 1     | Linked to TKT-0001                                                       |

---

## What NOT to send

- `createdBy`, `performedBy`, `assignedBy`, `transferredBy` — resolved from JWT, never in request bodies
- `ticketNo` on create — server-managed
- `status` on create — always starts as `NEW`
- `smtpPassword` — never in GET responses; send only when creating/updating a mailbox
