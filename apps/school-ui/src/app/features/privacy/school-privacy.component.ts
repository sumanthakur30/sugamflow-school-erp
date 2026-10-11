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
  template: `
    <div class="page">
      <header class="page-head toolbar">
        <div>
          <h2>Student privacy</h2>
          <p>Record access, correction, and erasure requests for student and parent information.</p>
        </div>
      </header>

      @if (error) {
        <p class="error panel" role="alert">{{ error }}</p>
      }
      @if (statusMsg) {
        <p class="status panel" role="status">{{ statusMsg }}</p>
      }

      @if (notice) {
        <section class="panel">
          <h3>{{ notice.title }}</h3>
          <p>{{ notice.body }}</p>
          <p>Notice {{ notice.noticeCode }} {{ notice.version }}</p>
        </section>
      }

      <section class="panel">
        <h3>Purposes</h3>
        @for (purpose of purposes; track purpose.purposeCode) {
          <div>
            <strong>{{ purpose.purposeCode }}</strong>
            <span> {{ purpose.legalGround }} · {{ purpose.allowed ? 'Allowed' : 'Not allowed' }}</span>
            <p>{{ purpose.description }}</p>
            @if (purpose.withdrawable) {
              <button type="button" (click)="setConsent(purpose.purposeCode, 'GRANT')">Allow updates</button>
              <button type="button" class="icon-btn secondary" (click)="setConsent(purpose.purposeCode, 'WITHDRAW')">
                Withdraw updates
              </button>
            }
          </div>
        }
      </section>

      <section class="panel">
        <h3>Record a request</h3>
        <p>Use an admission number or parent reference. Do not paste identity documents.</p>
        <label>
          Who
          <select [(ngModel)]="draft.dataCategory">
            <option value="STUDENT">Student</option>
            <option value="PARENT">Parent</option>
          </select>
        </label>
        <label>
          Request
          <select [(ngModel)]="draft.requestType">
            <option value="ACCESS">Access</option>
            <option value="CORRECTION">Correction</option>
            <option value="ERASURE">Erasure</option>
            <option value="GRIEVANCE">Grievance</option>
          </select>
        </label>
        <label>
          Admission or parent reference
          <input [(ngModel)]="draft.subjectLabel" maxlength="80" />
        </label>
        <label>
          Note
          <textarea [(ngModel)]="draft.detail" rows="3"></textarea>
        </label>
        <button type="button" (click)="submit()" [disabled]="busy">Save request</button>
      </section>

      <section class="panel">
        <h3>Requests</h3>
        <table>
          <thead>
            <tr>
              <th>Number</th>
              <th>Who</th>
              <th>Reference</th>
              <th>Type</th>
              <th>Status</th>
              <th>Action</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (row of requests; track row.id) {
              <tr>
                <td>{{ row.requestNumber }}</td>
                <td>{{ row.dataCategory }}</td>
                <td>{{ row.subjectLabel }}</td>
                <td>{{ row.requestType }}</td>
                <td>{{ row.status }}</td>
                <td>{{ row.actionTaken }}</td>
                <td>
                  @if (row.status !== 'COMPLETED') {
                    <button type="button" (click)="complete(row)" [disabled]="busy">Mark recorded</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </section>
    </div>
  `,
})
export class SchoolPrivacyComponent implements OnInit {
  private readonly api = inject(ApiService);

  notice: Notice | null = null;
  purposes: Purpose[] = [];
  requests: PrivacyRequest[] = [];
  error = '';
  statusMsg = '';
  busy = false;
  draft = {
    dataCategory: 'STUDENT',
    requestType: 'ACCESS',
    subjectLabel: '',
    detail: '',
  };

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.api.get<Notice>('/api/student/privacy/notice').subscribe({
      next: (notice) => (this.notice = notice),
      error: (err) => (this.error = this.message(err)),
    });
    this.api.get<Purpose[]>('/api/student/privacy/purposes').subscribe({
      next: (purposes) => (this.purposes = purposes ?? []),
      error: (err) => (this.error = this.message(err)),
    });
    this.api.get<PrivacyRequest[]>('/api/student/privacy/requests/organization').subscribe({
      next: (rows) => (this.requests = rows ?? []),
      error: (err) => (this.error = this.message(err)),
    });
  }

  setConsent(purposeCode: string, action: string): void {
    this.busy = true;
    this.error = '';
    this.api.post('/api/student/privacy/consents', { purposeCode, action }).subscribe({
      next: () => {
        this.busy = false;
        this.statusMsg = action === 'GRANT' ? 'Optional updates allowed.' : 'Optional updates withdrawn.';
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = this.message(err);
      },
    });
  }

  submit(): void {
    this.busy = true;
    this.error = '';
    this.api.post('/api/student/privacy/requests', this.draft).subscribe({
      next: () => {
        this.busy = false;
        this.statusMsg = 'Request recorded. Student, fee, and exam records were not deleted.';
        this.draft = { dataCategory: 'STUDENT', requestType: 'ACCESS', subjectLabel: '', detail: '' };
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = this.message(err);
      },
    });
  }

  complete(row: PrivacyRequest): void {
    this.busy = true;
    this.error = '';
    this.api.post(`/api/student/privacy/requests/${row.id}/complete`, { note: 'Recorded by the school office.' }).subscribe({
      next: () => {
        this.busy = false;
        this.statusMsg = 'Request marked recorded. No student records were deleted.';
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = this.message(err);
      },
    });
  }

  private message(err: { error?: { message?: string }; message?: string }): string {
    return err?.error?.message || err?.message || 'Privacy request failed.';
  }
}
