/**
 * CaseFlow — API Type Definitions
 * Auto-derived from backend Java records.
 * All date fields are ISO 8601 strings (e.g. "2026-03-27T10:00:00Z").
 */

// ─────────────────────────────────────────────────────────────────────────────
// Auth
// ─────────────────────────────────────────────────────────────────────────────

export interface LoginRequest {
  username: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;   // milliseconds
  tokenType: string;   // always "Bearer"
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface MeResponse {
  id: number;
  username: string;
  email: string;
  fullName: string;
  role: 'ADMIN' | 'AGENT' | 'VIEWER';
}

// ─────────────────────────────────────────────────────────────────────────────
// Pagination
// ─────────────────────────────────────────────────────────────────────────────

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// Enums
// ─────────────────────────────────────────────────────────────────────────────

export type TicketStatus =
  | 'NEW'
  | 'TRIAGED'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'WAITING_CUSTOMER'
  | 'RESOLVED'
  | 'CLOSED'
  | 'REOPENED';

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type NoteType = 'INFO' | 'INVESTIGATION' | 'ESCALATION' | 'INTERNAL';

export type GroupType = 'TRADE' | 'OPERATIONS' | 'SUPPORT';

// ─────────────────────────────────────────────────────────────────────────────
// Error
// ─────────────────────────────────────────────────────────────────────────────

export interface FieldViolation {
  field: string;
  message: string;
  rejectedValue: unknown;
  code: string;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  details: FieldViolation[];
  requestId: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Tickets
// ─────────────────────────────────────────────────────────────────────────────

export interface CreateTicketRequest {
  subject: string;           // required, max 255
  description?: string;
  priority: TicketPriority;  // required
  customerId?: number;
  // createdBy resolved from JWT — do not send
}

export interface UpdateTicketRequest {
  subject: string;           // required, max 255
  description?: string;
  priority: TicketPriority;  // required
}

export interface ChangeTicketStatusRequest {
  status: TicketStatus;  // required
  // performedBy resolved from JWT
}

export interface CloseTicketRequest {}  // empty body — performedBy from JWT

export interface ReopenTicketRequest {}  // empty body — performedBy from JWT

export interface TicketResponse {
  id: number;
  ticketNo: string;
  subject: string;
  description: string | null;
  status: TicketStatus;
  priority: TicketPriority;
  customerId: number | null;
  assignedUserId: number | null;
  assignedGroupId: number | null;
  createdAt: string;
  updatedAt: string;
  closedAt: string | null;
}

export interface TicketSummaryResponse {
  id: number;
  ticketNo: string;
  subject: string;
  status: TicketStatus;
  priority: TicketPriority;
  assignedUserId: number | null;
  assignedGroupId: number | null;
  createdAt: string;
}

export interface AttachmentMetadataResponse {
  id: number;
  ticketId: number;
  emailId: string | null;
  fileName: string;
  objectKey: string;
  contentType: string;
  size: number;
  uploadedAt: string;
}

export interface HistoryResponse {
  id: number;
  ticketId: number;
  actionType: string;
  performedBy: number | null;
  performedAt: string;
  details: string | null;
}

export interface HistorySummaryResponse {
  id: number;
  actionType: string;
  performedBy: number | null;
  performedAt: string;
}

export interface TicketDetailResponse {
  id: number;
  ticketNo: string;
  subject: string;
  description: string | null;
  status: TicketStatus;
  priority: TicketPriority;
  customerId: number | null;
  assignedUserId: number | null;
  assignedGroupId: number | null;
  createdAt: string;
  updatedAt: string;
  closedAt: string | null;
  attachments: AttachmentMetadataResponse[];
  history: HistorySummaryResponse[];
}

// ─────────────────────────────────────────────────────────────────────────────
// Customers
// ─────────────────────────────────────────────────────────────────────────────

export interface CreateCustomerRequest {
  name: string;   // required, max 255
  code: string;   // required, max 100
}

export interface UpdateCustomerRequest {
  name: string;   // required, max 255
  code: string;   // required, max 100
}

export interface CustomerResponse {
  id: number;
  name: string;
  code: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerSummaryResponse {
  id: number;
  name: string;
  code: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Contacts
// ─────────────────────────────────────────────────────────────────────────────

export interface CreateContactRequest {
  customerId: number;  // required
  email: string;       // required, valid email, max 255
  name: string;        // required, max 255
  isPrimary: boolean;
}

export interface UpdateContactRequest {
  name: string;        // required, max 255
  isPrimary: boolean;
  isActive: boolean;
}

export interface ContactResponse {
  id: number;
  customerId: number;
  email: string;
  name: string;
  isPrimary: boolean;
  isActive: boolean;
  createdAt: string;
}

export interface ContactSummaryResponse {
  id: number;
  customerId: number;
  email: string;
  name: string;
  isPrimary: boolean;
}

// ─────────────────────────────────────────────────────────────────────────────
// Users
// ─────────────────────────────────────────────────────────────────────────────

export interface CreateUserRequest {
  username: string;   // required, max 100
  email: string;      // required, valid email, max 255
  fullName: string;   // required, max 255
}

export interface UpdateUserRequest {
  email: string;      // required, valid email, max 255
  fullName: string;   // required, max 255
}

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  fullName: string;
  isActive: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface UserSummaryResponse {
  id: number;
  username: string;
  fullName: string;
  isActive: boolean;
}

// ─────────────────────────────────────────────────────────────────────────────
// Groups
// ─────────────────────────────────────────────────────────────────────────────

export interface CreateGroupRequest {
  name: string;       // required, max 255
  type: GroupType;    // required
}

export interface UpdateGroupRequest {
  name: string;       // required, max 255
  type: GroupType;    // required
}

export interface GroupResponse {
  id: number;
  name: string;
  type: GroupType;
  isActive: boolean;
  createdAt: string;
}

export interface GroupSummaryResponse {
  id: number;
  name: string;
  type: GroupType;
  isActive: boolean;
}

// ─────────────────────────────────────────────────────────────────────────────
// Notes
// ─────────────────────────────────────────────────────────────────────────────

export interface AddNoteRequest {
  ticketId: number;  // required
  content: string;   // required
  type: NoteType;    // required
  // createdBy resolved from JWT
}

export interface NoteResponse {
  id: number;
  ticketId: number;
  content: string;
  type: NoteType;
  createdBy: number;
  createdAt: string;
}

export interface NoteSummaryResponse {
  id: number;
  ticketId: number;
  type: NoteType;
  createdBy: number;
  createdAt: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Assignments
// ─────────────────────────────────────────────────────────────────────────────

export interface AssignTicketRequest {
  ticketId: number;          // required
  assignedUserId?: number;   // at least one of user/group required
  assignedGroupId?: number;
  // assignedBy resolved from JWT
}

export interface ReassignTicketRequest {
  ticketId: number;   // required
  newUserId?: number;
  newGroupId?: number;
  // reassignedBy resolved from JWT
}

export interface UnassignTicketRequest {
  ticketId: number;  // required
  // performedBy resolved from JWT
}

export interface AssignmentResponse {
  id: number;
  ticketId: number;
  assignedUserId: number | null;
  assignedGroupId: number | null;
  assignedBy: number;
  assignedAt: string;
  unassignedAt: string | null;
  active: boolean;
}

export interface AssignmentSummaryResponse {
  id: number;
  ticketId: number;
  assignedUserId: number | null;
  assignedGroupId: number | null;
  assignedAt: string;
  active: boolean;
}

// ─────────────────────────────────────────────────────────────────────────────
// Transfers
// ─────────────────────────────────────────────────────────────────────────────

export interface TransferTicketRequest {
  ticketId: number;       // required
  fromGroupId: number;    // required
  toGroupId: number;      // required
  reason?: string;
  clearAssignee: boolean; // true = removes current user assignment
  // transferredBy resolved from JWT
}

export interface TransferResponse {
  id: number;
  ticketId: number;
  fromGroupId: number;
  toGroupId: number;
  transferredBy: number;
  transferredAt: string;
  reason: string | null;
}

export interface TransferSummaryResponse {
  id: number;
  ticketId: number;
  fromGroupId: number;
  toGroupId: number;
  transferredAt: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Emails
// ─────────────────────────────────────────────────────────────────────────────

export interface IngestEmailRequest {
  messageId: string;
  inReplyTo?: string;
  references?: string[];
  subject: string;
  from: string;
  to: string[];
  cc?: string[];
  textBody?: string;
  htmlBody?: string;
  receivedAt: string;  // ISO 8601
}

export interface EmailDocumentResponse {
  id: string;          // MongoDB ObjectId string
  messageId: string;
  threadKey: string;
  subject: string;
  from: string;
  to: string[];
  cc: string[];
  receivedAt: string;
  parsedAt: string;
  ticketId: number | null;
}

export interface EmailDocumentSummaryResponse {
  id: string;          // MongoDB ObjectId string
  messageId: string;
  subject: string;
  from: string;
  receivedAt: string;
  ticketId: number | null;
}
