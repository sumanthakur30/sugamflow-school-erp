import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  ComplianceApiService,
  ComplianceDocument,
  DocumentVaultSummary,
} from './compliance-api.service';

@Component({
  selector: 'sf-compliance-documents',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  styleUrls: ['../../shared/admin-page.scss'],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h2>Documents Vault</h2>
          <p>NOCs, affiliation letters, and certificates with expiry tracking.</p>
        </div>
        <a routerLink="/admin/compliance" class="btn">Dashboard</a>
      </div>

      @if (error) {
        <div class="panel" style="border-color: #c45c5c"><p>{{ error }}</p></div>
      }

      @if (summary) {
        <div class="grid two" style="margin-bottom: 1rem">
          <div class="panel">
            <div class="field-title">Vault status</div>
            <p>Total: <strong>{{ summary.totalDocuments }}</strong></p>
            <p>
              Valid {{ summary.validCount }} · Expiring {{ summary.expiringCount }} · Expired
              {{ summary.expiredCount }}
            </p>
          </div>
          <div class="panel">
            <div class="field-title">Missing recommended types</div>
            @if (!summary.missingRecommendedTypes?.length) {
              <p class="muted">All recommended document types present.</p>
            } @else {
              <p>{{ summary.missingRecommendedTypes.join(', ') }}</p>
            }
          </div>
        </div>
      }

      <div class="panel" style="margin-bottom: 1rem">
        <h3 style="margin-top: 0; font-size: 1.1rem">
          {{ editingId ? 'Edit document' : 'Add document' }}
        </h3>
        <form class="grid" [formGroup]="form" (ngSubmit)="save()">
          <div class="grid two">
            <label>
              <span class="field-title">Type</span>
              <select formControlName="docType">
                @for (t of docTypes; track t) {
                  <option [value]="t">{{ t }}</option>
                }
              </select>
            </label>
            <label>
              <span class="field-title">Title</span>
              <input formControlName="title" maxlength="255" />
            </label>
          </div>
          <div class="grid two">
            <label>
              <span class="field-title">Reference no</span>
              <input formControlName="referenceNo" />
            </label>
            <label>
              <span class="field-title">Issuer</span>
              <input formControlName="issuer" />
            </label>
          </div>
          <div class="grid two">
            <label>
              <span class="field-title">Issued on</span>
              <input type="date" formControlName="issuedOn" />
            </label>
            <label>
              <span class="field-title">Expires on</span>
              <input type="date" formControlName="expiresOn" />
            </label>
          </div>
          <label>
            <span class="field-title">External URL (optional)</span>
            <input formControlName="externalUrl" />
          </label>
          <label>
            <span class="field-title">Notes</span>
            <textarea formControlName="notes" rows="2"></textarea>
          </label>
          <div style="display: flex; gap: 0.5rem">
            <button class="btn primary" type="submit" [disabled]="form.invalid || saving">
              {{ saving ? 'Saving…' : editingId ? 'Update' : 'Add' }}
            </button>
            @if (editingId) {
              <button class="btn" type="button" (click)="resetForm()">Cancel</button>
            }
          </div>
        </form>
      </div>

      <div class="panel">
        <div class="page-head" style="margin-bottom: 0.75rem">
          <h3 style="margin: 0; font-size: 1.1rem">Documents</h3>
          <select [value]="statusFilter" (change)="onStatusFilter($event)">
            <option value="">All statuses</option>
            <option value="VALID">Valid</option>
            <option value="EXPIRING">Expiring</option>
            <option value="EXPIRED">Expired</option>
          </select>
        </div>
        @if (loading) {
          <p class="muted">Loading…</p>
        } @else if (!docs.length) {
          <p class="muted">No documents in vault yet.</p>
        } @else {
          <table class="data">
            <thead>
              <tr>
                <th>Status</th>
                <th>Type</th>
                <th>Title</th>
                <th>Expires</th>
                <th>File</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (d of docs; track d.id) {
                <tr>
                  <td>{{ d.status }}</td>
                  <td>{{ d.docType }}</td>
                  <td>{{ d.title }}</td>
                  <td>{{ d.expiresOn || '—' }}</td>
                  <td>
                    @if (d.hasFile) {
                      <button class="btn" type="button" (click)="download(d)">Download</button>
                    } @else if (d.externalUrl) {
                      <a [href]="d.externalUrl" target="_blank" rel="noopener">Link</a>
                    } @else {
                      <label class="btn" style="cursor: pointer; display: inline-block">
                        Upload
                        <input
                          type="file"
                          hidden
                          (change)="onFile($event, d)"
                          accept=".pdf,.png,.jpg,.jpeg,.webp,.doc,.docx,.xls,.xlsx"
                        />
                      </label>
                    }
                  </td>
                  <td style="white-space: nowrap">
                    <button class="btn" type="button" (click)="edit(d)">Edit</button>
                    <button class="btn" type="button" (click)="remove(d)">Remove</button>
                    @if (d.hasFile) {
                      <label class="btn" style="cursor: pointer; display: inline-block">
                        Replace
                        <input
                          type="file"
                          hidden
                          (change)="onFile($event, d)"
                          accept=".pdf,.png,.jpg,.jpeg,.webp,.doc,.docx,.xls,.xlsx"
                        />
                      </label>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>
    </section>
  `,
})
export class ComplianceDocumentsComponent implements OnInit {
  private readonly api = inject(ComplianceApiService);
  private readonly fb = inject(FormBuilder);

  readonly docTypes = [
    'AFFILIATION_LETTER',
    'FIRE_NOC',
    'BUILDING_SAFETY',
    'HEALTH_SANITATION',
    'DRINKING_WATER',
    'LAND_OWNERSHIP',
    'TRUST_SOCIETY',
    'RECOGNITION',
    'TRANSPORT_PERMIT',
    'FEE_STRUCTURE',
    'OTHER',
  ];

  form = this.fb.nonNullable.group({
    docType: ['AFFILIATION_LETTER', Validators.required],
    title: ['', Validators.required],
    referenceNo: [''],
    issuer: [''],
    issuedOn: [''],
    expiresOn: [''],
    externalUrl: [''],
    notes: [''],
  });

  loading = true;
  saving = false;
  error = '';
  docs: ComplianceDocument[] = [];
  summary: DocumentVaultSummary | null = null;
  editingId: number | null = null;
  statusFilter = '';

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading = true;
    this.error = '';
    this.api.documentsSummary().subscribe({
      next: (s) => (this.summary = s),
      error: () => {},
    });
    this.api
      .listDocuments({ status: this.statusFilter || undefined })
      .subscribe({
        next: (rows) => {
          this.docs = rows || [];
          this.loading = false;
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.error?.message || err?.message || 'Unable to load documents.';
        },
      });
  }

  onStatusFilter(event: Event): void {
    this.statusFilter = (event.target as HTMLSelectElement).value;
    this.refresh();
  }

  download(d: ComplianceDocument): void {
    if (!d.id) return;
    this.api.downloadDocumentFile(d.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = d.fileName || `document-${d.id}`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Download failed.';
      },
    });
  }

  edit(d: ComplianceDocument): void {
    this.editingId = d.id ?? null;
    this.form.patchValue({
      docType: d.docType,
      title: d.title,
      referenceNo: d.referenceNo || '',
      issuer: d.issuer || '',
      issuedOn: d.issuedOn || '',
      expiresOn: d.expiresOn || '',
      externalUrl: d.externalUrl || '',
      notes: d.notes || '',
    });
  }

  resetForm(): void {
    this.editingId = null;
    this.form.reset({
      docType: 'AFFILIATION_LETTER',
      title: '',
      referenceNo: '',
      issuer: '',
      issuedOn: '',
      expiresOn: '',
      externalUrl: '',
      notes: '',
    });
  }

  save(): void {
    if (this.form.invalid) return;
    this.saving = true;
    const v = this.form.getRawValue();
    const body: ComplianceDocument = {
      docType: v.docType,
      title: v.title,
      referenceNo: v.referenceNo || undefined,
      issuer: v.issuer || undefined,
      issuedOn: v.issuedOn || undefined,
      expiresOn: v.expiresOn || undefined,
      externalUrl: v.externalUrl || undefined,
      notes: v.notes || undefined,
    };
    this.api.saveDocument(body, this.editingId ?? undefined).subscribe({
      next: () => {
        this.saving = false;
        this.resetForm();
        this.refresh();
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message || err?.message || 'Save failed.';
      },
    });
  }

  onFile(event: Event, doc: ComplianceDocument): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !doc.id) return;
    this.api.uploadDocumentFile(doc.id, file).subscribe({
      next: () => {
        input.value = '';
        this.refresh();
      },
      error: (err) => {
        input.value = '';
        this.error = err?.error?.message || err?.message || 'Upload failed.';
      },
    });
  }

  remove(d: ComplianceDocument): void {
    if (!d.id || !confirm(`Remove ${d.title}?`)) return;
    this.api.deleteDocument(d.id).subscribe({
      next: () => this.refresh(),
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Delete failed.';
      },
    });
  }
}
