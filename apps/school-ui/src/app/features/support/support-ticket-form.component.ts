import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  SCHOOL_ISSUE_TYPES,
  SUPPORT_PRIORITIES,
  SchoolSupportIssueType,
  SupportTicketPriority,
  SupportTicketService,
} from './support-ticket.service';

@Component({
  selector: 'sf-support-ticket-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  styleUrls: ['../../shared/admin-page.scss'],
  template: `
    <section class="page">
      <div class="page-head">
        <h2>Report issue</h2>
        <p>Raise a ticket with the SugamFlow school support team.</p>
      </div>

      <div class="panel">
        <form class="grid" [formGroup]="form" (ngSubmit)="submit()">
          <div class="grid two">
            <label>
              <span class="field-title">Issue type <span class="req">*</span></span>
              <select formControlName="issueType">
                @for (opt of issueTypes; track opt.value) {
                  <option [value]="opt.value">{{ opt.label }}</option>
                }
              </select>
            </label>
            <label>
              <span class="field-title">Priority</span>
              <select formControlName="priority">
                @for (opt of priorities; track opt.value) {
                  <option [value]="opt.value">{{ opt.label }}</option>
                }
              </select>
            </label>
          </div>

          <label>
            <span class="field-title">Subject <span class="req">*</span></span>
            <input type="text" formControlName="subject" maxlength="255" placeholder="Short summary" />
          </label>

          <label>
            <span class="field-title">Description <span class="req">*</span></span>
            <textarea
              formControlName="description"
              rows="6"
              maxlength="10000"
              placeholder="What happened, steps to reproduce, and expected result"
            ></textarea>
          </label>

          <div class="grid two">
            <label>
              <span class="field-title">Contact email</span>
              <input type="email" formControlName="contactEmail" maxlength="255" />
            </label>
            <label>
              <span class="field-title">Contact mobile</span>
              <input type="tel" formControlName="contactMobile" maxlength="20" placeholder="+91…" />
            </label>
          </div>

          <label>
            <span class="field-title">Module</span>
            <input
              type="text"
              formControlName="moduleName"
              maxlength="100"
              placeholder="e.g. Fees, Attendance, Admissions"
            />
          </label>

          <label>
            <span class="field-title">Attachments</span>
            <input
              type="file"
              multiple
              accept="image/png,image/jpeg,image/webp,image/gif,application/pdf,.png,.jpg,.jpeg,.webp,.gif,.pdf"
              (change)="onFilesSelected($event)"
            />
            <span class="muted">Optional — up to {{ support.maxAttachments }} files, 5MB each (PNG, JPEG, WebP, GIF, PDF).</span>
          </label>

          @if (files.length) {
            <ul class="file-list">
              @for (file of files; track file.name + file.size; let i = $index) {
                <li>
                  <span>{{ file.name }} ({{ formatSize(file.size) }})</span>
                  <button type="button" class="tiny secondary" (click)="removeFile(i)">Remove</button>
                </li>
              }
            </ul>
          }

          @if (error) {
            <p class="error">{{ error }}</p>
          }

          <div class="actions">
            <button type="submit" [disabled]="form.invalid || busy">
              {{ busy ? 'Submitting…' : 'Submit ticket' }}
            </button>
            <a routerLink="/admin/support/tickets" class="secondary-link">My tickets</a>
          </div>
        </form>
      </div>
    </section>
  `,
  styles: `
    .file-list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }
    .file-list li {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
      font-size: 0.9rem;
    }
    .secondary-link {
      align-self: center;
      color: var(--sf-primary);
      font-weight: 600;
      text-decoration: none;
    }
    textarea {
      resize: vertical;
      min-height: 7rem;
    }
  `,
})
export class SupportTicketFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  readonly support = inject(SupportTicketService);

  readonly issueTypes = SCHOOL_ISSUE_TYPES;
  readonly priorities = SUPPORT_PRIORITIES;

  busy = false;
  error = '';
  files: File[] = [];

  readonly form = this.fb.nonNullable.group({
    issueType: this.fb.nonNullable.control<SchoolSupportIssueType>('BUG_REPORT', Validators.required),
    priority: this.fb.nonNullable.control<SupportTicketPriority>('MEDIUM'),
    subject: ['', [Validators.required, Validators.maxLength(255)]],
    description: ['', [Validators.required, Validators.maxLength(10000)]],
    contactEmail: ['', [Validators.email, Validators.maxLength(255)]],
    contactMobile: ['', [Validators.maxLength(20), Validators.pattern(/^[+]?[0-9\s-]{7,20}$|^$/)]],
    moduleName: ['', Validators.maxLength(100)],
  });

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const selected = Array.from(input.files ?? []);
    const next = [...this.files, ...selected].slice(0, this.support.maxAttachments);
    const err = this.support.validateAttachments(next);
    if (err) {
      this.error = err;
      input.value = '';
      return;
    }
    this.error = '';
    this.files = next;
    input.value = '';
  }

  removeFile(index: number): void {
    this.files = this.files.filter((_, i) => i !== index);
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  submit(): void {
    if (this.form.invalid || this.busy) {
      this.form.markAllAsTouched();
      return;
    }
    const attachErr = this.support.validateAttachments(this.files);
    if (attachErr) {
      this.error = attachErr;
      return;
    }

    this.busy = true;
    this.error = '';
    const v = this.form.getRawValue();
    this.support
      .create(
        {
          product: 'SCHOOL',
          issueType: v.issueType,
          priority: v.priority,
          subject: v.subject.trim(),
          description: v.description.trim(),
          contactEmail: v.contactEmail.trim() || undefined,
          contactMobile: v.contactMobile.trim() || undefined,
          moduleName: v.moduleName.trim() || undefined,
        },
        this.files,
      )
      .subscribe({
        next: (ticket) => {
          this.busy = false;
          void this.router.navigate(['/admin/support/tickets', ticket.id]);
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? err?.message ?? 'Could not create ticket.';
        },
      });
  }
}
