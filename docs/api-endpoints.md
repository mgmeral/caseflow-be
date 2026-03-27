# CaseFlow — API Endpoint Map

**Base URL:** `http://localhost:8080/api`
**Auth:** HTTP Basic — include header on every request
**Swagger UI:** `http://localhost:8080/swagger-ui.html`

---

## Authentication

```
Authorization: Basic <base64(username:password)>
```

| Username | Password | Role | Allowed methods |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | GET · POST · PUT · PATCH · DELETE |
| `agent` | `agent123` | AGENT | GET · POST · PUT · PATCH |
| `viewer` | `viewer123` | VIEWER | GET only |

---

## Error Response Shape

All errors return the same JSON structure:

```json
{
  "timestamp": "2026-03-27T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "code": "TICKET_NOT_FOUND",
  "message": "Ticket not found: 99",
  "path": "/api/tickets/99",
  "details": [],
  "requestId": "abc-123"
}
```

Validation errors include `details` array:

```json
"details": [
  { "field": "subject", "message": "must not be blank", "rejectedValue": "", "code": "NotBlank" }
]
```

---

## Enums

| Enum | Values |
|---|---|
| `TicketStatus` | `NEW` `TRIAGED` `ASSIGNED` `IN_PROGRESS` `WAITING_CUSTOMER` `RESOLVED` `CLOSED` `REOPENED` |
| `TicketPriority` | `LOW` `MEDIUM` `HIGH` `CRITICAL` |
| `NoteType` | `INFO` `INVESTIGATION` `ESCALATION` `INTERNAL` |
| `GroupType` | `TRADE` `OPERATIONS` `SUPPORT` |

---

## Tickets `/api/tickets`

### `POST /api/tickets`
Create a new ticket.

**Auth:** AGENT+

**Request body:**
```json
{
  "subject":     "string (required, max 255)",
  "description": "string (optional)",
  "priority":    "TicketPriority (required)",
  "customerId":  123,
  "createdBy":   456
}
```

**Response `201`:** `TicketResponse`
```json
{
  "id":              1,
  "ticketNo":        "string",
  "subject":         "string",
  "description":     "string",
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

---

### `GET /api/tickets`
List tickets with optional filters. Without filters, returns all tickets.

**Auth:** VIEWER+

**Query params (all optional, mutually exclusive):**

| Param | Type | Description |
|---|---|---|
| `status` | `TicketStatus` | Filter by status |
| `userId` | `Long` | Filter by assigned user |
| `groupId` | `Long` | Filter by assigned group |
| `customerId` | `Long` | Filter by customer |

**Response `200`:** `TicketSummaryResponse[]`
```json
[
  {
    "id":              1,
    "ticketNo":        "string",
    "subject":         "string",
    "status":          "NEW",
    "priority":        "HIGH",
    "assignedUserId":  null,
    "assignedGroupId": null,
    "createdAt":       "2026-03-27T10:00:00Z"
  }
]
```

---

### `GET /api/tickets/{id}`
Get a single ticket by database ID.

**Auth:** VIEWER+

**Response `200`:** `TicketResponse` (same shape as POST response)
**Response `404`:** `TICKET_NOT_FOUND`

---

### `GET /api/tickets/by-ticket-no/{ticketNo}`
Get a ticket by its human-readable ticket number.

**Auth:** VIEWER+

**Response `200`:** `TicketResponse`
**Response `404`:** `TICKET_NOT_FOUND`

---

### `PUT /api/tickets/{id}`
Update subject, description, or priority.

**Auth:** AGENT+

**Request body:**
```json
{
  "subject":     "string (required, max 255)",
  "description": "string (optional)",
  "priority":    "TicketPriority (required)"
}
```

**Response `200`:** `TicketResponse`

---

### `POST /api/tickets/{id}/status`
Change ticket status to any valid target state.

**Auth:** AGENT+

**Request body:**
```json
{
  "status":      "TicketStatus (required)",
  "performedBy": 456
}
```

**Response `200`:** `TicketResponse`
**Response `422`:** `INVALID_TICKET_STATE` (invalid transition)

---

### `POST /api/tickets/{id}/close`
Close a ticket. Shortcut for `status → CLOSED`.

**Auth:** AGENT+

**Request body:**
```json
{
  "performedBy": 456
}
```

**Response `200`:** `TicketResponse`

---

### `POST /api/tickets/{id}/reopen`
Reopen a closed ticket. Transitions to `REOPENED`.

**Auth:** AGENT+

**Request body:**
```json
{
  "performedBy": 456
}
```

**Response `200`:** `TicketResponse`

---

## Customers `/api/customers`

### `POST /api/customers`
Create a customer.

**Auth:** AGENT+

**Request body:**
```json
{
  "name": "string (required, max 255)",
  "code": "string (required, max 100)"
}
```

**Response `201`:** `CustomerResponse`
```json
{
  "id":        1,
  "name":      "ACME Corp",
  "code":      "ACME",
  "isActive":  true,
  "createdAt": "2026-03-27T10:00:00Z",
  "updatedAt": "2026-03-27T10:00:00Z"
}
```

---

### `GET /api/customers`
List all customers.

**Auth:** VIEWER+

**Response `200`:** `CustomerSummaryResponse[]`
```json
[{ "id": 1, "name": "ACME Corp", "code": "ACME" }]
```

---

### `GET /api/customers/{id}`
Get customer by ID.

**Auth:** VIEWER+

**Response `200`:** `CustomerResponse`
**Response `404`:** `CUSTOMER_NOT_FOUND`

---

### `PUT /api/customers/{id}`
Update customer name and code.

**Auth:** AGENT+

**Request body:** same fields as create (`name`, `code`)

**Response `200`:** `CustomerResponse`

---

### `PATCH /api/customers/{id}/activate`
Activate a customer.

**Auth:** AGENT+
**Response `204`:** no body

---

### `PATCH /api/customers/{id}/deactivate`
Deactivate a customer.

**Auth:** AGENT+
**Response `204`:** no body

---

## Contacts `/api/contacts`

### `POST /api/contacts`
Create a contact for a customer.

**Auth:** AGENT+

**Request body:**
```json
{
  "customerId": 1,
  "email":      "string (required, valid email, max 255)",
  "name":       "string (required, max 255)",
  "isPrimary":  false
}
```

**Response `201`:** `ContactResponse`
```json
{
  "id":         1,
  "customerId": 1,
  "email":      "alice@acme.com",
  "name":       "Alice",
  "isPrimary":  true,
  "isActive":   true,
  "createdAt":  "2026-03-27T10:00:00Z"
}
```

---

### `GET /api/contacts`
List all contacts.

**Auth:** VIEWER+

**Response `200`:** `ContactSummaryResponse[]`
```json
[{ "id": 1, "customerId": 1, "email": "alice@acme.com", "name": "Alice", "isPrimary": true }]
```

---

### `GET /api/contacts/{id}`
Get contact by ID.

**Auth:** VIEWER+

**Response `200`:** `ContactResponse`
**Response `404`:** `CONTACT_NOT_FOUND`

---

### `GET /api/contacts/by-email?email={email}`
Look up a contact by email address.

**Auth:** VIEWER+

**Response `200`:** `ContactResponse`
**Response `404`:** no body (contact not found)

---

### `GET /api/contacts/by-customer/{customerId}`
List all contacts belonging to a customer.

**Auth:** VIEWER+

**Response `200`:** `ContactSummaryResponse[]`

---

### `PUT /api/contacts/{id}`
Update contact name, primary flag, or active status.

**Auth:** AGENT+

**Request body:**
```json
{
  "name":      "string (required, max 255)",
  "isPrimary": false,
  "isActive":  true
}
```

**Response `200`:** `ContactResponse`

---

## Users `/api/users`

### `POST /api/users`
Create a user (agent/support staff).

**Auth:** ADMIN

**Request body:**
```json
{
  "username": "string (required, max 100)",
  "email":    "string (required, valid email, max 255)",
  "fullName": "string (required, max 255)"
}
```

**Response `201`:** `UserResponse`
```json
{
  "id":          1,
  "username":    "alice",
  "email":       "alice@example.com",
  "fullName":    "Alice Smith",
  "isActive":    true,
  "createdAt":   "2026-03-27T10:00:00Z",
  "lastLoginAt": null
}
```

---

### `GET /api/users`
List all users.

**Auth:** VIEWER+

**Response `200`:** `UserSummaryResponse[]`
```json
[{ "id": 1, "username": "alice", "fullName": "Alice Smith", "isActive": true }]
```

---

### `GET /api/users/{id}`
Get user by ID.

**Auth:** VIEWER+

**Response `200`:** `UserResponse`
**Response `404`:** `USER_NOT_FOUND`

---

### `GET /api/users/by-username?username={username}`
Look up a user by username.

**Auth:** VIEWER+

**Response `200`:** `UserResponse`
**Response `404`:** no body

---

### `GET /api/users/by-email?email={email}`
Look up a user by email.

**Auth:** VIEWER+

**Response `200`:** `UserResponse`
**Response `404`:** no body

---

### `PUT /api/users/{id}`
Update user email and full name.

**Auth:** AGENT+

**Request body:**
```json
{
  "email":    "string (required, valid email, max 255)",
  "fullName": "string (required, max 255)"
}
```

**Response `200`:** `UserResponse`

---

### `PATCH /api/users/{id}/activate`
Activate a user.

**Auth:** ADMIN
**Response `204`:** no body

---

### `PATCH /api/users/{id}/deactivate`
Deactivate a user.

**Auth:** ADMIN
**Response `204`:** no body

---

## Groups `/api/groups`

### `POST /api/groups`
Create a group.

**Auth:** ADMIN

**Request body:**
```json
{
  "name": "string (required, max 255)",
  "type": "GroupType (required)"
}
```

**Response `201`:** `GroupResponse`
```json
{
  "id":        1,
  "name":      "Support Team",
  "type":      "SUPPORT",
  "isActive":  true,
  "createdAt": "2026-03-27T10:00:00Z"
}
```

---

### `GET /api/groups`
List all **active** groups.

**Auth:** VIEWER+

**Response `200`:** `GroupSummaryResponse[]`
```json
[{ "id": 1, "name": "Support Team", "type": "SUPPORT", "isActive": true }]
```

---

### `GET /api/groups/{id}`
Get group by ID.

**Auth:** VIEWER+

**Response `200`:** `GroupResponse`
**Response `404`:** `GROUP_NOT_FOUND`

---

### `GET /api/groups/by-type?type={GroupType}`
List groups by type.

**Auth:** VIEWER+

**Response `200`:** `GroupSummaryResponse[]`

---

### `PUT /api/groups/{id}`
Update group name and type.

**Auth:** AGENT+

**Request body:** same as create (`name`, `type`)

**Response `200`:** `GroupResponse`

---

### `PATCH /api/groups/{id}/activate`
Activate a group.

**Auth:** ADMIN
**Response `204`:** no body

---

### `PATCH /api/groups/{id}/deactivate`
Deactivate a group.

**Auth:** ADMIN
**Response `204`:** no body

---

## Notes `/api/notes`

### `POST /api/notes`
Add a note to a ticket.

**Auth:** AGENT+

**Request body:**
```json
{
  "ticketId":  1,
  "content":   "string (required)",
  "type":      "NoteType (required)",
  "createdBy": 456
}
```

**Response `201`:** `NoteResponse`
```json
{
  "id":        1,
  "ticketId":  1,
  "content":   "Investigated the issue. Root cause found.",
  "type":      "INVESTIGATION",
  "createdBy": 456,
  "createdAt": "2026-03-27T10:00:00Z"
}
```

---

### `GET /api/notes/{id}`
Get a note by ID.

**Auth:** VIEWER+

**Response `200`:** `NoteResponse`
**Response `404`:** `NOTE_NOT_FOUND`

---

### `GET /api/notes/by-ticket/{ticketId}`
List all notes for a ticket, ordered by creation time.

**Auth:** VIEWER+

**Response `200`:** `NoteResponse[]`

---

## Assignments `/api/assignments`

### `POST /api/assignments/assign`
Assign a ticket to a user and/or group. At least one of `assignedUserId` or `assignedGroupId` should be provided.

**Auth:** AGENT+

**Request body:**
```json
{
  "ticketId":        1,
  "assignedUserId":  456,
  "assignedGroupId": 2,
  "assignedBy":      789
}
```

**Response `200`:** `AssignmentResponse`
```json
{
  "id":              1,
  "ticketId":        1,
  "assignedUserId":  456,
  "assignedGroupId": 2,
  "assignedBy":      789,
  "assignedAt":      "2026-03-27T10:00:00Z",
  "unassignedAt":    null,
  "active":          true
}
```

**Response `409`:** `ACTIVE_ASSIGNMENT_ALREADY_EXISTS` (ticket already assigned — use reassign)

---

### `POST /api/assignments/reassign`
Reassign a ticket that already has an active assignment.

**Auth:** AGENT+

**Request body:**
```json
{
  "ticketId":     1,
  "newUserId":    500,
  "newGroupId":   3,
  "reassignedBy": 789
}
```

**Response `200`:** `AssignmentResponse` (the new active assignment)

---

### `POST /api/assignments/unassign`
Remove the active assignment from a ticket.

**Auth:** AGENT+

**Request body:**
```json
{
  "ticketId":    1,
  "performedBy": 789
}
```

**Response `204`:** no body

---

### `GET /api/assignments/by-ticket/{ticketId}`
Get the current active assignment for a ticket.

**Auth:** VIEWER+

**Response `200`:** `AssignmentResponse`
**Response `404`:** no body (no active assignment)

---

## Transfers `/api/transfers`

### `POST /api/transfers`
Transfer a ticket from one group to another.

**Auth:** AGENT+

**Request body:**
```json
{
  "ticketId":      1,
  "fromGroupId":   2,
  "toGroupId":     3,
  "transferredBy": 456,
  "reason":        "string (optional)",
  "clearAssignee": false
}
```

> `clearAssignee: true` removes the current user assignment after the transfer.

**Response `201`:** `TransferResponse`
```json
{
  "id":             1,
  "ticketId":       1,
  "fromGroupId":    2,
  "toGroupId":      3,
  "transferredBy":  456,
  "transferredAt":  "2026-03-27T10:00:00Z",
  "reason":         "Escalated to operations"
}
```

---

### `GET /api/transfers/by-ticket/{ticketId}`
Get the full transfer history for a ticket.

**Auth:** VIEWER+

**Response `200`:** `TransferSummaryResponse[]`
```json
[
  {
    "id":            1,
    "ticketId":      1,
    "fromGroupId":   2,
    "toGroupId":     3,
    "transferredAt": "2026-03-27T10:00:00Z"
  }
]
```

---

## Emails `/api/emails`

Read-only — email documents are ingested by the backend, not created via API.

### `GET /api/emails/{id}`
Get an email document by its MongoDB document ID.

**Auth:** VIEWER+

**Response `200`:** `EmailDocumentResponse`
```json
{
  "id":         "665f1a2b3c4d5e6f7a8b9c0d",
  "messageId":  "<msg-id@mail.example.com>",
  "threadKey":  "string",
  "subject":    "Issue with account",
  "from":       "alice@acme.com",
  "to":         ["support@company.com"],
  "cc":         [],
  "receivedAt": "2026-03-27T09:00:00Z",
  "parsedAt":   "2026-03-27T09:00:05Z",
  "ticketId":   1
}
```

**Response `404`:** no body

---

### `GET /api/emails/by-ticket/{ticketId}`
List all emails linked to a ticket.

**Auth:** VIEWER+

**Response `200`:** `EmailDocumentSummaryResponse[]`
```json
[
  {
    "id":         "665f1a2b3c4d5e6f7a8b9c0d",
    "messageId":  "<msg-id@mail.example.com>",
    "subject":    "Issue with account",
    "from":       "alice@acme.com",
    "receivedAt": "2026-03-27T09:00:00Z",
    "ticketId":   1
  }
]
```

---

### `GET /api/emails/by-thread/{threadKey}`
List all emails in a thread (grouped by `threadKey`).

**Auth:** VIEWER+

**Response `200`:** `EmailDocumentSummaryResponse[]`

---

## Health & Meta

| Endpoint | Auth | Description |
|---|---|---|
| `GET /actuator/health` | None | Full stack health `{"status":"UP"}` |
| `GET /actuator/health/readiness` | None | K8s readiness probe |
| `GET /actuator/health/liveness` | None | K8s liveness probe |
| `GET /actuator/info` | None | App info |
| `GET /swagger-ui.html` | None | Interactive API explorer |
| `GET /v3/api-docs` | None | OpenAPI JSON spec |

---

## Seed Data (dev profile)

Start with `SPRING_PROFILES_ACTIVE=dev` to load:

| Type | Records |
|---|---|
| Groups | `Support` (SUPPORT) · `Operations` (OPERATIONS) · `Trade` (TRADE) |
| Users | `alice` (id 1) · `bob` (id 2) · `carol` (id 3) |
| Customers | `ACME Corp` (id 1) · `Globex` (id 2) |
| Contacts | 3 contacts linked to the two customers |
| Tickets | 3 tickets in various statuses |
| Notes | 2 notes on ticket id 1 |
| Email | 1 sample email document |

---

## Common HTTP Status Codes

| Code | Meaning |
|---|---|
| `200` | OK |
| `201` | Created |
| `204` | No Content (activate/deactivate/unassign) |
| `400` | Bad Request / validation failed |
| `401` | Unauthorized (missing or wrong credentials) |
| `403` | Forbidden (role insufficient) |
| `404` | Resource not found |
| `409` | Conflict (duplicate / state conflict) |
| `422` | Unprocessable — invalid state transition |
| `500` | Internal server error |
