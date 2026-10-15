import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

interface Notice {
  noticeCode: string;
  version: string;
  title: string;
  body: string;
}

interface Purpose {
  purposeCode: string;
  legalGround: string;
  description: string;
  withdrawable: boolean;
  allowed: boolean;
}

interface PrivacyRequest {
  id: string;
  requestNumber: string;
  dataCategory: string;
  subjectLabel: string;
  requestType: string;
  status: string;
  detail?: string;
  actionTaken: string;
  responseNote?: string;
  createdAt?: string;
}

@Component({
  selector: 'app-school-privacy',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './school-privacy.component.html',
  styleUrl: './school-privacy.component.scss',
})
export class SchoolPrivacyComponent implements OnInit {
  private readonly api = inject(ApiService);

  notice: Notice | null = null;
  purposes: Purpose[] = [];
  requests: PrivacyRequest[] = [];
  error = '';
  statusMsg = '';
  fieldError = '';
  loading = true;
  busy = false;
  statusFilter = 'ALL';
  draft = {
    dataCategory: 'STUDENT',
    requestType: 'ACCESS',
    subjectLabel: '',
    detail: '',
  };

  readonly statuses = [
    { value: 'ALL', label: 'All' },
    { value: 'OPEN', label: 'Pending' },
    { value: 'IN_REVIEW', label: 'Under review' },
    { value: 'COMPLETED', label: 'Completed' },
    { value: 'REJECTED', label: 'Rejected' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ];

  ngOnInit(): void {
    this.reload();
  }

  get visibleRequests(): PrivacyRequest[] {
    if (this.statusFilter === 'ALL') {
      return this.requests;
    }
    return this.requests.filter((row) => row.status === this.statusFilter);
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    let pending = 3;
    const done = () => {
      pending -= 1;
      if (pending === 0) {
        this.loading = false;
      }
    };
    this.api.get<Notice>('/api/student/privacy/notice').subscribe({
      next: (notice) => {
        this.notice = notice;
        done();
      },
      error: (err) => {
        this.error = this.message(err, 'The privacy notice could not be loaded.');
        done();
      },
    });
    this.api.get<Purpose[]>('/api/student/privacy/purposes').subscribe({
      next: (purposes) => {
        this.purposes = purposes ?? [];
        done();
      },
      error: (err) => {
        this.error = this.message(err, 'Privacy purposes could not be loaded.');
        done();
      },
    });
    this.api.get<PrivacyRequest[]>('/api/student/privacy/requests/organization').subscribe({
      next: (rows) => {
        this.requests = rows ?? [];
        done();
      },
      error: (err) => {
        this.error = this.message(err, 'Privacy requests could not be loaded.');
        done();
      },
    });
  }

  setConsent(purpose: Purpose, action: 'GRANT' | 'WITHDRAW'): void {
    if (action === 'WITHDRAW' && !confirm('Withdraw optional school updates? School operations stay on.')) {
      return;
    }
    this.busy = true;
    this.error = '';
    this.api.post('/api/student/privacy/consents', { purposeCode: purpose.purposeCode, action }).subscribe({
      next: () => {
        this.busy = false;
        this.statusMsg = action === 'GRANT' ? 'Optional updates allowed.' : 'Optional updates withdrawn.';
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = this.message(err, 'The preference could not be saved.');
      },
    });
  }

  submit(): void {
    this.fieldError = '';
    this.error = '';
    const label = this.draft.subjectLabel.trim();
    if (!/^[A-Za-z0-9][A-Za-z0-9 ./_-]{0,79}$/.test(label)) {
      this.fieldError = 'Enter an admission number or a short parent reference. Do not paste a document.';
      return;
    }
    this.busy = true;
    this.api
      .post('/api/student/privacy/requests', {
        ...this.draft,
        subjectLabel: label,
        detail: this.draft.detail.trim(),
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.statusMsg = 'Request recorded. Student, fee, and exam records were not deleted.';
          this.draft = { dataCategory: 'STUDENT', requestType: 'ACCESS', subjectLabel: '', detail: '' };
          this.reload();
        },
        error: (err) => {
          this.busy = false;
          this.error = this.message(err, 'The request could not be saved.');
        },
      });
  }

  setStatus(row: PrivacyRequest, status: string): void {
    const label = this.statusLabel(status);
    const extra =
      status === 'COMPLETED' ? ' Completing it records the request and does not delete school records.' : '';
    if (!confirm(`Mark ${row.requestNumber} as ${label}?${extra}`)) {
      return;
    }
    this.busy = true;
    this.error = '';
    this.api.post(`/api/student/privacy/requests/${row.id}/status`, { status, note: `Marked ${label} by the school office.` }).subscribe({
      next: () => {
        this.busy = false;
        this.statusMsg =
          status === 'COMPLETED'
            ? `${row.requestNumber} completed. No student records were deleted.`
            : `${row.requestNumber} marked ${label}.`;
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = this.message(err, 'The request status could not be updated.');
      },
    });
  }

  statusLabel(status: string): string {
    return this.statuses.find((item) => item.value === status)?.label ?? status;
  }

  whoLabel(category: string): string {
    return category === 'PARENT' ? 'Parent or guardian' : 'Student';
  }

  purposeTitle(code: string): string {
    if (code === 'SCHOOL_OPERATIONS') {
      return 'School operations';
    }
    if (code === 'OPTIONAL_SCHOOL_UPDATES') {
      return 'Optional school updates';
    }
    return code;
  }

  private message(err: { error?: { message?: string } | string; message?: string; status?: number }, fallback: string): string {
    const body = err?.error;
    if (body && typeof body === 'object' && body.message) {
      return body.message;
    }
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    if (err?.status === 0) {
      return 'The privacy service could not be reached.';
    }
    return fallback;
  }
}
