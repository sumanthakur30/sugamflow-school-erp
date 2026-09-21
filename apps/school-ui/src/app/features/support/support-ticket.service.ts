import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../../core/api.service';

/** School UI subset of shared support issue types. */
export type SchoolSupportIssueType =
  | 'BUG_REPORT'
  | 'BILLING_ISSUE'
  | 'ADMISSION_ISSUE'
  | 'FEE_ISSUE'
  | 'ATTENDANCE_ISSUE'
  | 'ACADEMICS_ISSUE'
  | 'FEATURE_REQUEST'
  | 'PERFORMANCE_ISSUE'
  | 'OTHER';

export type SupportTicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type SupportTicketStatus =
  | 'OPEN'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'CLOSED'
  | 'REJECTED';

export interface SupportAttachment {
  id: number;
  fileName: string;
  contentType: string;
  fileSize: number;
  createdAt: string;
}

export interface SupportComment {
  id: number;
  body: string;
  author: string;
  staffResponse: boolean;
  createdAt: string;
}

export interface SupportTicket {
  id: number;
  ticketNumber: string;
  product: string;
  organizationId?: string;
  shopId?: string;
  issueType: SchoolSupportIssueType | string;
  subject: string;
  description: string;
  priority: SupportTicketPriority;
  status: SupportTicketStatus;
  contactEmail?: string;
  contactMobile?: string;
  moduleName?: string;
  appVersion?: string;
  deviceInfo?: string;
  submittedBy?: string;
  assignedTo?: string;
  slaDueAt?: string;
  firstResponseAt?: string;
  resolvedAt?: string;
  closedAt?: string;
  createdAt: string;
  updatedAt?: string;
  attachments?: SupportAttachment[];
  comments?: SupportComment[];
}

export interface CreateSupportTicketPayload {
  product: 'SCHOOL';
  issueType: SchoolSupportIssueType;
  subject: string;
  description: string;
  priority?: SupportTicketPriority;
  contactEmail?: string;
  contactMobile?: string;
  moduleName?: string;
  appVersion?: string;
  deviceInfo?: string;
}

export interface UpdateSupportTicketPayload {
  subject?: string;
  additionalDescription?: string;
  moduleName?: string;
  contactEmail?: string;
  contactMobile?: string;
}

/** Spring Data Page shape returned in `data`. */
export interface SupportTicketPage {
  content: SupportTicket[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const SCHOOL_ISSUE_TYPES: { value: SchoolSupportIssueType; label: string }[] = [
  { value: 'BUG_REPORT', label: 'Bug report' },
  { value: 'BILLING_ISSUE', label: 'Billing issue' },
  { value: 'ADMISSION_ISSUE', label: 'Admission issue' },
  { value: 'FEE_ISSUE', label: 'Fee issue' },
  { value: 'ATTENDANCE_ISSUE', label: 'Attendance issue' },
  { value: 'ACADEMICS_ISSUE', label: 'Academics issue' },
  { value: 'FEATURE_REQUEST', label: 'Feature request' },
  { value: 'PERFORMANCE_ISSUE', label: 'Performance issue' },
  { value: 'OTHER', label: 'Other' },
];

export const SUPPORT_PRIORITIES: { value: SupportTicketPriority; label: string }[] = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
  { value: 'CRITICAL', label: 'Critical' },
];

const MAX_ATTACHMENTS = 5;
const MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024;
const ALLOWED_ATTACHMENT_TYPES = new Set([
  'image/png',
  'image/jpeg',
  'image/webp',
  'image/gif',
  'application/pdf',
]);

@Injectable({ providedIn: 'root' })
export class SupportTicketService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/support/tickets`;

  private headers(): HttpHeaders {
    return new HttpHeaders({ 'X-Product': 'SCHOOL' });
  }

  private unwrap<T>(res: ApiResponse<T>): T {
    return res.data;
  }

  create(ticket: CreateSupportTicketPayload, files: File[] = []): Observable<SupportTicket> {
    const form = new FormData();
    form.append(
      'ticket',
      new Blob([JSON.stringify({ ...ticket, product: 'SCHOOL' })], {
        type: 'application/json',
      }),
    );
    for (const file of files) {
      form.append('attachments', file, file.name);
    }
    return this.http
      .post<ApiResponse<SupportTicket>>(this.base, form, { headers: this.headers() })
      .pipe(map((r) => this.unwrap(r)));
  }

  list(page = 0, size = 20): Observable<SupportTicketPage> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http
      .get<ApiResponse<SupportTicketPage>>(this.base, { headers: this.headers(), params })
      .pipe(map((r) => this.unwrap(r)));
  }

  get(ticketId: number | string): Observable<SupportTicket> {
    return this.http
      .get<ApiResponse<SupportTicket>>(`${this.base}/${ticketId}`, { headers: this.headers() })
      .pipe(map((r) => this.unwrap(r)));
  }

  comment(ticketId: number | string, body: string): Observable<SupportTicket> {
    return this.http
      .post<ApiResponse<SupportTicket>>(
        `${this.base}/${ticketId}/comments`,
        { body },
        { headers: this.headers() },
      )
      .pipe(map((r) => this.unwrap(r)));
  }

  close(ticketId: number | string): Observable<SupportTicket> {
    return this.http
      .post<ApiResponse<SupportTicket>>(
        `${this.base}/${ticketId}/close`,
        {},
        { headers: this.headers() },
      )
      .pipe(map((r) => this.unwrap(r)));
  }

  update(
    ticketId: number | string,
    payload: UpdateSupportTicketPayload,
  ): Observable<SupportTicket> {
    return this.http
      .put<ApiResponse<SupportTicket>>(`${this.base}/${ticketId}`, payload, {
        headers: this.headers(),
      })
      .pipe(map((r) => this.unwrap(r)));
  }

  addAttachment(ticketId: number | string, file: File): Observable<SupportAttachment> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http
      .post<ApiResponse<SupportAttachment>>(`${this.base}/${ticketId}/attachments`, form, {
        headers: this.headers(),
      })
      .pipe(map((r) => this.unwrap(r)));
  }

  download(ticketId: number | string, attachmentId: number | string): Observable<Blob> {
    return this.http.get(`${this.base}/${ticketId}/attachments/${attachmentId}`, {
      headers: this.headers(),
      responseType: 'blob',
    });
  }

  validateAttachments(files: File[]): string | null {
    if (files.length > MAX_ATTACHMENTS) {
      return `At most ${MAX_ATTACHMENTS} attachments allowed.`;
    }
    for (const file of files) {
      if (file.size > MAX_ATTACHMENT_BYTES) {
        return `${file.name} exceeds the 5MB limit.`;
      }
      if (!ALLOWED_ATTACHMENT_TYPES.has(file.type)) {
        return `${file.name}: use PNG, JPEG, WebP, GIF, or PDF.`;
      }
    }
    return null;
  }

  readonly maxAttachments = MAX_ATTACHMENTS;
  readonly maxAttachmentBytes = MAX_ATTACHMENT_BYTES;
}
