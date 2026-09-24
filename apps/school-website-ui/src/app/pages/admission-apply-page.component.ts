import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgFor, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WebsiteApiService } from '../core/website-api.service';
import { environment } from '../../environments/environment';

declare global {
  interface Window {
    grecaptcha?: {
      ready: (cb: () => void) => void;
      execute: (siteKey: string, opts: { action: string }) => Promise<string>;
      render?: (el: string | HTMLElement, opts: Record<string, unknown>) => number;
    };
  }
}

@Component({
  selector: 'app-admission-apply-page',
  standalone: true,
  imports: [FormsModule, NgIf, NgFor, RouterLink],
  template: `
    <section class="wrap">
      <header>
        <h1>Online Admission</h1>
        <p>Submit an enquiry. Our admission team will follow up from the school ERP.</p>
      </header>

      <form *ngIf="!submitted; else done" (ngSubmit)="submit()" class="card">
        <ng-container *ngIf="fields.length; else fixedFields">
          <label *ngFor="let field of fields">
            {{ field.label }}<span *ngIf="field.mandatory"> *</span>
            <select
              *ngIf="field.type === 'DROPDOWN' || field.type === 'SELECT'"
              [name]="field.key"
              [(ngModel)]="values[field.key]"
              [required]="field.mandatory"
            >
              <option value="">Select</option>
              <option *ngFor="let option of field.options" [value]="option">{{ option }}</option>
            </select>
            <textarea
              *ngIf="field.type === 'TEXTAREA'"
              [name]="field.key"
              rows="4"
              [(ngModel)]="values[field.key]"
              [required]="field.mandatory"
            ></textarea>
            <input
              *ngIf="field.type !== 'DROPDOWN' && field.type !== 'SELECT' && field.type !== 'TEXTAREA'"
              [name]="field.key"
              [type]="inputType(field.type)"
              [(ngModel)]="values[field.key]"
              [required]="field.mandatory"
              [placeholder]="field.placeholder || ''"
            />
          </label>
        </ng-container>
        <ng-template #fixedFields>
          <label>Full name <input name="fullName" [(ngModel)]="fullName" required /></label>
          <label>Mobile <input name="mobile" [(ngModel)]="mobile" required /></label>
          <label>Email <input name="email" type="email" [(ngModel)]="email" /></label>
          <label
            >Class / Grade applied
            <input name="classApplied" [(ngModel)]="classApplied" required placeholder="e.g. Class 5" />
          </label>
          <label>Age (optional) <input name="age" [(ngModel)]="age" /></label>
          <label
            >Message
            <textarea name="message" rows="4" [(ngModel)]="message"></textarea>
          </label>
        </ng-template>
        <p class="hint" *ngIf="captchaEnabled">Protected by reCAPTCHA.</p>
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
      .hint {
        color: #64748b;
        font-size: 0.85rem;
      }
      .success a {
        color: var(--sf-primary, #0b3d91);
      }
    `,
  ],
})
export class AdmissionApplyPageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);

  readonly captchaEnabled = !!(environment.captchaSiteKey && environment.captchaSiteKey.trim());

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
  fields: Array<{
    key: string;
    label: string;
    type: string;
    mandatory: boolean;
    placeholder?: string;
    options: string[];
  }> = [];
  values: Record<string, string> = {};

  ngOnInit(): void {
    this.api.resolve().subscribe(() => this.loadForm());
    if (this.captchaEnabled) {
      this.ensureRecaptchaScript(environment.captchaSiteKey);
    }
  }

  async submit(): Promise<void> {
    this.error = '';
    this.submitting = true;
    try {
      const captchaToken = this.captchaEnabled
        ? await this.executeCaptcha(environment.captchaSiteKey)
        : undefined;
      const answers = this.fields.length ? { ...this.values } : undefined;
      this.api
        .applyAdmission({
          fullName: this.fieldValue('fullName', this.fullName),
          mobile: this.fieldValue('mobile', this.mobile),
          email: this.fieldValue('email', this.email) || undefined,
          classApplied: this.fieldValue('classApplied', this.classApplied),
          age: this.fieldValue('age', this.age) || undefined,
          message: this.fieldValue('message', this.message) || undefined,
          answers,
          captchaToken,
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
    } catch (e: any) {
      this.submitting = false;
      this.error = e?.message || 'Captcha failed. Please try again.';
    }
  }

  inputType(type: string): string {
    const kind = (type || '').toUpperCase();
    if (kind === 'EMAIL') return 'email';
    if (kind === 'PHONE' || kind === 'MOBILE') return 'tel';
    if (kind === 'NUMBER') return 'number';
    if (kind === 'DATE') return 'date';
    return 'text';
  }

  private loadForm(): void {
    this.api.admissionForm().subscribe({
      next: (schema) => {
        const sections = Array.isArray(schema['sections']) ? schema['sections'] : [];
        const fields: AdmissionApplyPageComponent['fields'] = [];
        for (const section of sections) {
          const rawFields = (section as { fields?: unknown }).fields;
          if (!Array.isArray(rawFields)) continue;
          for (const raw of rawFields) {
            const field = raw as Record<string, unknown>;
            const key = String(field['key'] || '').trim();
            if (!key) continue;
            const options = Array.isArray(field['options'])
              ? field['options'].map((option) =>
                  typeof option === 'string'
                    ? option
                    : String((option as { label?: string; value?: string }).label || (option as { value?: string }).value || '')
                )
              : [];
            fields.push({
              key,
              label: String(field['label'] || key),
              type: String(field['type'] || 'TEXTBOX').toUpperCase(),
              mandatory: field['mandatory'] === true,
              placeholder: field['placeholder'] == null ? '' : String(field['placeholder']),
              options: options.filter(Boolean),
            });
            if (this.values[key] == null) this.values[key] = '';
          }
        }
        this.fields = fields;
      },
      error: () => {
        this.fields = [];
      },
    });
  }

  private fieldValue(key: string, fallback: string): string {
    if (this.fields.length) {
      return (this.values[key] || '').trim();
    }
    return fallback.trim();
  }

  private ensureRecaptchaScript(siteKey: string): void {
    if (document.getElementById('sf-recaptcha')) {
      return;
    }
    const script = document.createElement('script');
    script.id = 'sf-recaptcha';
    script.src = `https://www.google.com/recaptcha/api.js?render=${encodeURIComponent(siteKey)}`;
    script.async = true;
    document.head.appendChild(script);
  }

  private executeCaptcha(siteKey: string): Promise<string> {
    return new Promise((resolve, reject) => {
      const run = () => {
        if (!window.grecaptcha?.execute) {
          reject(new Error('reCAPTCHA not loaded'));
          return;
        }
        window.grecaptcha.ready(() => {
          window
            .grecaptcha!.execute(siteKey, { action: 'admission_apply' })
            .then(resolve)
            .catch(() => reject(new Error('reCAPTCHA failed')));
        });
      };
      if (window.grecaptcha) {
        run();
      } else {
        setTimeout(run, 800);
      }
    });
  }
}
