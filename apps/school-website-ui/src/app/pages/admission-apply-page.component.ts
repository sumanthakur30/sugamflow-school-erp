import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WebsiteApiService } from '../core/website-api.service';

@Component({
  selector: 'app-admission-apply-page',
  standalone: true,
  imports: [FormsModule, NgIf, RouterLink],
  template: `
    <section class="wrap">
      <header>
        <h1>Online Admission</h1>
        <p>Submit an enquiry. Our admission team will follow up from the school ERP.</p>
      </header>

      <form *ngIf="!submitted; else done" (ngSubmit)="submit()" class="card">
        <label>Full name <input name="fullName" [(ngModel)]="fullName" required /></label>
        <label>Mobile <input name="mobile" [(ngModel)]="mobile" required /></label>
        <label>Email <input name="email" type="email" [(ngModel)]="email" /></label>
        <label>Class / Grade applied
          <input name="classApplied" [(ngModel)]="classApplied" required placeholder="e.g. Class 5" />
        </label>
        <label>Age (optional) <input name="age" [(ngModel)]="age" /></label>
        <label>Message
          <textarea name="message" rows="4" [(ngModel)]="message"></textarea>
        </label>
        <p class="error" *ngIf="error">{{ error }}</p>
        <button type="submit" class="primary" [disabled]="submitting">
          {{ submitting ? 'Submitting…' : 'Submit application' }}
        </button>
      </form>

      <ng-template #done>
        <div class="card success">
          <h2>Application received</h2>
          <p>Reference: <strong>{{ referenceId }}</strong></p>
          <p>Status: {{ status }}</p>
          <a routerLink="/admission">Back to admission info</a>
        </div>
      </ng-template>
    </section>
  `,
  styles: [
    `
      .wrap {
        max-width: 640px;
      }
      header p {
        color: #64748b;
      }
      .card {
        background: #fff;
        border: 1px solid #e6e9f0;
        border-radius: 14px;
        padding: 1.25rem;
        display: grid;
        gap: 0.75rem;
      }
      label {
        display: grid;
        gap: 0.35rem;
        font-size: 0.92rem;
      }
      input,
      textarea {
        border: 1px solid #cbd5e1;
        border-radius: 8px;
        padding: 0.55rem 0.65rem;
        font: inherit;
      }
      button.primary {
        background: var(--sf-primary, #0b3d91);
        color: #fff;
        border: 0;
        border-radius: 8px;
        padding: 0.7rem 1rem;
        font-weight: 600;
        cursor: pointer;
      }
      button:disabled {
        opacity: 0.7;
      }
      .error {
        color: #b91c1c;
      }
      .success a {
        color: var(--sf-primary, #0b3d91);
      }
    `,
  ],
})
export class AdmissionApplyPageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);

  fullName = '';
  mobile = '';
  email = '';
  classApplied = '';
  age = '';
  message = '';
  submitting = false;
  submitted = false;
  error = '';
  referenceId = '';
  status = '';

  ngOnInit(): void {
    this.api.resolve().subscribe();
  }

  submit(): void {
    this.error = '';
    this.submitting = true;
    this.api
      .applyAdmission({
        fullName: this.fullName.trim(),
        mobile: this.mobile.trim(),
        email: this.email.trim() || undefined,
        classApplied: this.classApplied.trim(),
        age: this.age.trim() || undefined,
        message: this.message.trim() || undefined,
      })
      .subscribe({
        next: (res) => {
          this.submitting = false;
          this.submitted = true;
          this.referenceId = String(res['id'] || res['applicationId'] || '');
          this.status = String(res['status'] || 'IN_PROGRESS');
        },
        error: (err) => {
          this.submitting = false;
          this.error =
            err?.error?.message ||
            err?.error?.data?.message ||
            err?.message ||
            'Submission failed. Please try again.';
        },
      });
  }
}
