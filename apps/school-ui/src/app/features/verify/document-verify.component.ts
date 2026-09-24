import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

@Component({
  selector: 'sf-document-verify',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <main class="verify-page">
      <header>
        <p class="brand">SugamFlow School</p>
        <h1>Document verification</h1>
        <p class="sub">Confirm whether an ID card or certificate is authentic.</p>
      </header>

      @if (loading) {
        <p class="panel">Checking document…</p>
      } @else if (error) {
        <p class="panel error" role="alert">{{ error }}</p>
      } @else if (result) {
        <section class="panel result" [class.ok]="result.valid" [class.bad]="!result.valid">
          <p class="status-line">{{ result.valid ? 'Valid document' : 'Not valid' }}</p>
          <p>{{ result.message }}</p>
          <dl>
            <div><dt>Type</dt><dd>{{ result.documentType }}</dd></div>
            <div><dt>Reference</dt><dd>{{ result.referenceNo }}</dd></div>
            <div><dt>Student</dt><dd>{{ result.studentName || '—' }}</dd></div>
            <div><dt>Admission</dt><dd>{{ result.admissionNo || '—' }}</dd></div>
            <div><dt>Class</dt><dd>{{ result.classSection || '—' }}</dd></div>
            <div><dt>Status</dt><dd>{{ result.status }}</dd></div>
            <div><dt>Issued</dt><dd>{{ result.issuedAt }}</dd></div>
            <div><dt>Organization</dt><dd>{{ result.organizationId }}</dd></div>
          </dl>
        </section>
      }

      <p class="footer"><a routerLink="/login">Staff login</a></p>
    </main>
  `,
  styles: [
    `
      .verify-page {
        min-height: 100vh;
        padding: 2rem 1.25rem 3rem;
        max-width: 640px;
        margin: 0 auto;
        background:
          radial-gradient(circle at top left, #dcefe6, transparent 40%),
          linear-gradient(180deg, #f7faf8, #eef3f0);
        color: #0f172a;
        font-family: 'Segoe UI', system-ui, sans-serif;
      }
      .brand {
        margin: 0;
        font-weight: 800;
        letter-spacing: 0.04em;
        text-transform: uppercase;
        color: #0b6e4f;
      }
      h1 {
        margin: 0.35rem 0;
      }
      .sub,
      .footer {
        color: #64748b;
      }
      .panel {
        margin-top: 1.25rem;
        padding: 1rem 1.1rem;
        border-radius: 12px;
        background: #fff;
        border: 1px solid #d7e3dc;
      }
      .error {
        color: #991b1b;
        background: #fef2f2;
      }
      .result.ok {
        border-color: #86efac;
      }
      .result.bad {
        border-color: #fca5a5;
      }
      .status-line {
        font-size: 1.15rem;
        font-weight: 800;
        margin: 0 0 0.5rem;
      }
      dl {
        display: grid;
        gap: 0.55rem;
        margin: 1rem 0 0;
      }
      dl div {
        display: grid;
        grid-template-columns: 7.5rem 1fr;
        gap: 0.5rem;
      }
      dt {
        color: #64748b;
        font-size: 0.85rem;
      }
      dd {
        margin: 0;
        font-weight: 600;
      }
      a {
        color: #0b6e4f;
        font-weight: 700;
      }
    `,
  ],
})
export class DocumentVerifyComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);

  loading = true;
  error = '';
  result: any = null;

  ngOnInit(): void {
    const token = this.route.snapshot.paramMap.get('token');
    if (!token) {
      this.loading = false;
      this.error = 'Missing verification token.';
      return;
    }
    this.http
      .get<{ data?: any; success?: boolean } | any>(
        `${environment.apiBaseUrl}/api/student/public/documents/verify/${encodeURIComponent(token)}`,
      )
      .subscribe({
        next: (body) => {
          this.loading = false;
          this.result = body?.data ?? body;
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.error?.message ?? 'Document could not be verified.';
        },
      });
  }
}
