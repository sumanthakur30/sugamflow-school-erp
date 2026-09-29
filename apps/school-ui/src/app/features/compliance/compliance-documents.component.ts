import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  ComplianceApiService,
  ComplianceDocument,
  DocumentAudit,
  DocumentCategory,
  DocumentFolder,
  DocumentVaultSummary,
  DocumentVersion,
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
          <p>Manage school documents, certificates, policies and compliance records.</p>
        </div>
        <div style="display: flex; gap: 0.5rem; flex-wrap: wrap">
          <a routerLink="/admin/compliance" class="btn">Board Compliance</a>
          <button class="btn" type="button" (click)="showFolder = !showFolder">Create Folder</button>
          <button class="btn primary" type="button" (click)="openUpload()">+ Upload Document</button>
        </div>
      </div>

      @if (error) {
        <div class="panel" style="border-color: #c45c5c"><p>{{ error }}</p></div>
      }
      @if (notice) {
        <div class="panel"><p>{{ notice }}</p></div>
      }

      @if (summary) {
        <div class="grid" style="grid-template-columns: repeat(6, minmax(0, 1fr)); margin-bottom: 1rem; gap: 0.5rem">
          @for (card of cards; track card.label) {
            <button class="panel" type="button" style="text-align: left; cursor: pointer" (click)="quick(card.status)">
              <div class="muted" style="font-size: 0.75rem">{{ card.label }}</div>
              <div style="font-size: 1.35rem; font-weight: 700; color: #14532d">{{ card.value }}</div>
            </button>
          }
        </div>
        <p class="muted" style="margin-top: -0.5rem">
          Storage {{ storageLabel }}
            @if (summary.missingRecommendedTypes.length) {
            · Missing: {{ summary.missingRecommendedTypes.join(', ') }}
          }
        </p>
      }

      <div class="panel" style="margin-bottom: 1rem">
        <div class="grid two">
          <label>
            <span class="field-title">Search</span>
            <input
              [value]="query"
              placeholder="Search documents by name, category, document number, keyword..."
              (input)="onQuery($event)"
            />
          </label>
          <div class="grid two">
            <label>
              <span class="field-title">Category</span>
              <select [value]="categoryFilter" (change)="onSelect('category', $event)">
                <option value="">All categories</option>
                @for (c of categories; track c.code) {
                  <option [value]="c.code">{{ c.name }}</option>
                }
              </select>
            </label>
            <label>
              <span class="field-title">Status</span>
              <select [value]="statusFilter" (change)="onSelect('status', $event)">
                <option value="">All</option>
                <option value="APPROVED">Approved</option>
                <option value="PENDING_REVIEW">Pending review</option>
                <option value="DRAFT">Draft</option>
                <option value="EXPIRING">Expiring soon</option>
                <option value="EXPIRED">Expired</option>
                <option value="REJECTED">Rejected</option>
                <option value="ARCHIVED">Archived</option>
              </select>
            </label>
          </div>
        </div>
      </div>

      @if (showFolder) {
        <form class="panel" style="margin-bottom: 1rem" [formGroup]="folderForm" (ngSubmit)="saveFolder()">
          <h3 style="margin-top: 0">Create folder</h3>
          <div class="grid two">
            <label>
              <span class="field-title">Folder name</span>
              <input formControlName="name" />
            </label>
            <label>
              <span class="field-title">Parent folder</span>
              <select formControlName="parentId">
                <option value="">None</option>
                @for (f of folders; track f.id) {
                  <option [value]="f.id">{{ f.name }}</option>
                }
              </select>
            </label>
          </div>
          <label>
            <span class="field-title">Description</span>
            <input formControlName="description" />
          </label>
          <button class="btn primary" type="submit" [disabled]="folderForm.invalid">Save folder</button>
        </form>
      }

      @if (showUpload) {
        <form class="panel" style="margin-bottom: 1rem" [formGroup]="form" (ngSubmit)="save()">
          <h3 style="margin-top: 0">{{ editingId ? 'Edit metadata' : 'Upload document' }}</h3>
          <div
            style="border: 1px dashed #14532d; border-radius: 8px; padding: 1.25rem; text-align: center; margin-bottom: 1rem"
            (dragover)="$event.preventDefault()"
            (drop)="onDrop($event)"
          >
            <p style="margin: 0 0 0.5rem">Drag and drop a document here</p>
            <label class="btn" style="cursor: pointer">
              Browse files
              <input type="file" hidden accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png" (change)="onPick($event)" />
            </label>
            <p class="muted">PDF, DOC, DOCX, XLS, XLSX, JPG, PNG. Maximum 25 MB.</p>
            @if (picked) {
              <p><strong>{{ picked.name }}</strong> · {{ (picked.size / 1048576) | number: '1.1-1' }} MB</p>
            }
          </div>
          <div class="grid two">
            <label>
              <span class="field-title">Document name</span>
              <input formControlName="title" />
            </label>
            <label>
              <span class="field-title">Document type</span>
              <select formControlName="docType">
                @for (t of docTypes; track t) {
                  <option [value]="t">{{ t }}</option>
                }
              </select>
            </label>
            <label>
              <span class="field-title">Category</span>
              <select formControlName="categoryCode">
                <option value="">Select</option>
                @for (c of categories; track c.code) {
                  <option [value]="c.code">{{ c.name }}</option>
                }
              </select>
            </label>
            <label>
              <span class="field-title">Folder</span>
              <select formControlName="folderId">
                <option value="">None</option>
                @for (f of folders; track f.id) {
                  <option [value]="f.id">{{ f.name }}</option>
                }
              </select>
            </label>
            <label>
              <span class="field-title">Compliance area</span>
              <input formControlName="complianceArea" placeholder="FIRE, BUILDING, BOARD..." />
            </label>
            <label>
              <span class="field-title">Document number</span>
              <input formControlName="referenceNo" />
            </label>
            <label>
              <span class="field-title">Issue date</span>
              <input type="date" formControlName="issuedOn" />
            </label>
            <label>
              <span class="field-title">Expiry date</span>
              <input type="date" formControlName="expiresOn" />
            </label>
            <label>
              <span class="field-title">Issued by</span>
              <input formControlName="issuer" />
            </label>
            <label>
              <span class="field-title">Visibility</span>
              <select formControlName="visibility">
                <option value="SCHOOL">School-wide</option>
                <option value="CAMPUS">Campus-specific</option>
                <option value="COMPLIANCE">Compliance team</option>
                <option value="ADMIN">Admin only</option>
                <option value="RESTRICTED">Restricted</option>
              </select>
            </label>
            <label>
              <span class="field-title">Academic session</span>
              <input formControlName="academicSessionId" placeholder="Leave blank if not applicable" />
            </label>
            <label>
              <span class="field-title">Tags</span>
              <input formControlName="tags" placeholder="Fire, Safety, 2026-27" />
            </label>
          </div>
          <label>
            <span class="field-title">Description</span>
            <textarea formControlName="description" rows="2"></textarea>
          </label>
          <div style="display: flex; gap: 0.5rem">
            <button class="btn primary" type="submit" [disabled]="form.invalid || saving">
              {{ saving ? 'Saving…' : editingId ? 'Save metadata' : 'Save document' }}
            </button>
            <button class="btn" type="button" (click)="resetForm()">Cancel</button>
          </div>
        </form>
      }

      <div class="panel">
        @if (selectedIds.size) {
          <div style="display: flex; gap: 0.5rem; margin-bottom: 0.75rem">
            <span class="muted">{{ selectedIds.size }} selected</span>
            <button class="btn" type="button" (click)="archiveSelected()">Archive selected</button>
          </div>
        }
        @if (loading) {
          <p class="muted">Loading…</p>
        } @else if (!docs.length) {
          <p class="muted">No documents match these filters.</p>
        } @else {
          <table class="data">
            <thead>
              <tr>
                <th></th>
                <th>Document</th>
                <th>Category</th>
                <th>Area</th>
                <th>Document no.</th>
                <th>Expiry</th>
                <th>Status</th>
                <th>Version</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (d of docs; track d.id) {
                <tr>
                  <td>
                    <input type="checkbox" [checked]="selectedIds.has(d.id!)" (change)="toggle(d)" />
                  </td>
                  <td>
                    <button class="btn" type="button" (click)="openDetail(d)">{{ d.title }}</button>
                    <div class="muted">{{ d.publicCode }}</div>
                  </td>
                  <td>{{ categoryName(d.categoryCode) }}</td>
                  <td>{{ d.complianceArea || d.docType }}</td>
                  <td>{{ d.referenceNo || '—' }}</td>
                  <td>
                    {{ d.expiresOn || '—' }}
                    @if (d.daysUntilExpiry != null && d.daysUntilExpiry >= 0 && d.daysUntilExpiry <= 90) {
                      <div class="muted">Expiring in {{ d.daysUntilExpiry }} days</div>
                    }
                  </td>
                  <td>{{ d.status }}</td>
                  <td>v{{ d.versionNo || 1 }}</td>
                  <td style="white-space: nowrap">
                    @if (d.hasFile) {
                      <button class="btn" type="button" (click)="download(d)">Download</button>
                      @if (canPreview(d)) {
                        <button class="btn" type="button" (click)="preview(d)">Preview</button>
                      }
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>

      @if (detail) {
        <div class="panel" style="margin-top: 1rem">
          <div class="page-head">
            <div>
              <h3 style="margin: 0">{{ detail.title }}</h3>
              <p class="muted">{{ detail.publicCode }} · {{ detail.status }} · v{{ detail.versionNo || 1 }}</p>
            </div>
            <button class="btn" type="button" (click)="detail = null">Close</button>
          </div>
          @if (detail.rejectionReason) {
            <p>Rejected: {{ detail.rejectionReason }}</p>
          }
          <p>
            {{ categoryName(detail.categoryCode) }} · {{ detail.complianceArea || detail.docType }} ·
            Issued {{ detail.issuedOn || '—' }} · Expires {{ detail.expiresOn || 'Not applicable' }}
          </p>
          <div style="display: flex; gap: 0.5rem; flex-wrap: wrap">
            <button class="btn" type="button" (click)="edit(detail)">Edit metadata</button>
            <button class="btn" type="button" (click)="act(detail, 'submit')">Submit for review</button>
            <button class="btn" type="button" (click)="act(detail, 'approve')">Approve</button>
            <button class="btn" type="button" (click)="reject(detail)">Reject</button>
            <button class="btn" type="button" (click)="renew(detail)">Upload new version</button>
            <button class="btn" type="button" (click)="act(detail, 'archive')">Archive</button>
            @if (!detail.active) {
              <button class="btn" type="button" (click)="act(detail, 'restore')">Restore</button>
            }
          </div>
          <h4>Versions</h4>
          @if (!versions.length) {
            <p class="muted">No stored versions yet.</p>
          } @else {
            <ul>
              @for (v of versions; track v.id) {
                <li>
                  v{{ v.versionNo }} — {{ v.fileName }} — {{ v.uploadedAt }}
                  {{ v.current ? '(current)' : '' }}
                  <button class="btn" type="button" (click)="download(detail, v.versionNo)">Download</button>
                </li>
              }
            </ul>
          }
          <h4>Audit</h4>
          <ul>
            @for (a of audits; track a.id) {
              <li>{{ a.action }} · {{ a.actorUserId || 'user' }} · {{ a.createdAt }} · {{ a.detail }}</li>
            }
          </ul>
        </div>
      }
    </section>
  `,
})
export class ComplianceDocumentsComponent implements OnInit {
  private readonly api = inject(ComplianceApiService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);

  readonly docTypes = [
    'AFFILIATION_LETTER',
    'RECOGNITION',
    'FIRE_NOC',
    'BUILDING_SAFETY',
    'STRUCTURAL_SAFETY',
    'HEALTH_SANITATION',
    'DRINKING_WATER',
    'LAND_OWNERSHIP',
    'TRUST_SOCIETY',
    'INSURANCE',
    'TRANSPORT_PERMIT',
    'POLICY',
    'OTHER',
  ];

  form = this.fb.nonNullable.group({
    docType: ['AFFILIATION_LETTER', Validators.required],
    title: ['', Validators.required],
    categoryCode: [''],
    folderId: [''],
    complianceArea: [''],
    referenceNo: [''],
    issuer: [''],
    issuedOn: [''],
    expiresOn: [''],
    visibility: ['SCHOOL'],
    academicSessionId: [''],
    tags: [''],
    description: [''],
  });

  folderForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    parentId: [''],
    description: [''],
  });

  loading = true;
  saving = false;
  error = '';
  notice = '';
  docs: ComplianceDocument[] = [];
  summary: DocumentVaultSummary | null = null;
  categories: DocumentCategory[] = [];
  folders: DocumentFolder[] = [];
  versions: DocumentVersion[] = [];
  audits: DocumentAudit[] = [];
  editingId: number | null = null;
  renewing: ComplianceDocument | null = null;
  detail: ComplianceDocument | null = null;
  picked: File | null = null;
  showUpload = false;
  showFolder = false;
  query = '';
  statusFilter = '';
  categoryFilter = '';
  selectedIds = new Set<number>();

  ngOnInit(): void {
    const preset = this.route.snapshot.queryParamMap.get('docType');
    if (preset) {
      this.form.patchValue({ docType: preset });
      this.showUpload = true;
    }
    this.api.documentCategories().subscribe({ next: (rows) => (this.categories = rows || []) });
    this.api.documentFolders().subscribe({ next: (rows) => (this.folders = rows || []) });
    this.refresh();
  }

  get cards() {
    const s = this.summary;
    return [
      { label: 'Total', value: s?.totalDocuments ?? 0, status: '' },
      { label: 'Approved', value: s?.approvedCount ?? s?.validCount ?? 0, status: 'APPROVED' },
      { label: 'Pending review', value: s?.pendingCount ?? 0, status: 'PENDING_REVIEW' },
      { label: 'Expiring soon', value: s?.expiringCount ?? 0, status: 'EXPIRING' },
      { label: 'Expired', value: s?.expiredCount ?? 0, status: 'EXPIRED' },
      { label: 'Draft', value: s?.draftCount ?? 0, status: 'DRAFT' },
    ];
  }

  get storageLabel(): string {
    const used = this.summary?.storageUsedBytes ?? 0;
    if (used < 1024 * 1024) return `${(used / 1024).toFixed(0)} KB used`;
    return `${(used / (1024 * 1024)).toFixed(1)} MB used`;
  }

  categoryName(code?: string): string {
    return this.categories.find((c) => c.code === code)?.name || code || '—';
  }

  refresh(): void {
    this.loading = true;
    this.error = '';
    this.api.documentsSummary().subscribe({
      next: (s) => (this.summary = s),
      error: () => {},
    });
    this.api
      .listDocuments({
        status: this.statusFilter || undefined,
        q: this.query || undefined,
        category: this.categoryFilter || undefined,
      })
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

  quick(status: string): void {
    this.statusFilter = status;
    this.refresh();
  }

  onQuery(event: Event): void {
    this.query = (event.target as HTMLInputElement).value;
    this.refresh();
  }

  onSelect(which: 'category' | 'status', event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    if (which === 'category') this.categoryFilter = value;
    else this.statusFilter = value;
    this.refresh();
  }

  openUpload(): void {
    this.resetForm();
    this.showUpload = true;
  }

  onPick(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.picked = file;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    const file = event.dataTransfer?.files?.[0];
    if (file) this.picked = file;
  }

  edit(d: ComplianceDocument): void {
    this.editingId = d.id ?? null;
    this.renewing = null;
    this.showUpload = true;
    this.form.patchValue({
      docType: d.docType,
      title: d.title,
      categoryCode: d.categoryCode || '',
      folderId: d.folderId ? String(d.folderId) : '',
      complianceArea: d.complianceArea || '',
      referenceNo: d.referenceNo || '',
      issuer: d.issuer || '',
      issuedOn: d.issuedOn || '',
      expiresOn: d.expiresOn || '',
      visibility: d.visibility || 'SCHOOL',
      academicSessionId: d.academicSessionId || '',
      tags: d.tags || '',
      description: d.description || d.notes || '',
    });
  }

  renew(d: ComplianceDocument): void {
    this.renewing = d;
    this.editingId = d.id ?? null;
    this.picked = null;
    this.showUpload = true;
    this.notice = `Upload a new version of ${d.title}. The previous file stays in history.`;
    this.edit(d);
    this.renewing = d;
  }

  resetForm(): void {
    this.editingId = null;
    this.renewing = null;
    this.picked = null;
    this.showUpload = false;
    this.form.reset({
      docType: 'AFFILIATION_LETTER',
      title: '',
      categoryCode: '',
      folderId: '',
      complianceArea: '',
      referenceNo: '',
      issuer: '',
      issuedOn: '',
      expiresOn: '',
      visibility: 'SCHOOL',
      academicSessionId: '',
      tags: '',
      description: '',
    });
  }

  save(): void {
    if (this.form.invalid) return;
    this.saving = true;
    const v = this.form.getRawValue();
    const body: ComplianceDocument = {
      docType: v.docType,
      title: v.title,
      categoryCode: v.categoryCode || undefined,
      folderId: v.folderId ? Number(v.folderId) : undefined,
      complianceArea: v.complianceArea || undefined,
      referenceNo: v.referenceNo || undefined,
      issuer: v.issuer || undefined,
      issuedOn: v.issuedOn || undefined,
      expiresOn: v.expiresOn || undefined,
      visibility: v.visibility,
      academicSessionId: v.academicSessionId || undefined,
      tags: v.tags || undefined,
      description: v.description || undefined,
      notes: v.description || undefined,
    };
    const renew = this.renewing;
    const file = this.picked;
    this.api.saveDocument(body, this.editingId ?? undefined).subscribe({
      next: (saved) => {
        if (file && saved.id) {
          this.api
            .uploadDocumentFile(saved.id, file, {
              asNewVersion: true,
              reason: renew ? 'Renewal' : 'Initial upload',
            })
            .subscribe({
              next: () => this.afterSave(),
              error: (err) => this.failSave(err, 'Upload failed.'),
            });
          return;
        }
        this.afterSave();
      },
      error: (err) => this.failSave(err, 'Save failed.'),
    });
  }

  saveFolder(): void {
    const v = this.folderForm.getRawValue();
    this.api
      .createFolder({
        name: v.name,
        description: v.description || undefined,
        parentId: v.parentId ? Number(v.parentId) : undefined,
      })
      .subscribe({
        next: () => {
          this.showFolder = false;
          this.folderForm.reset({ name: '', parentId: '', description: '' });
          this.api.documentFolders().subscribe({ next: (rows) => (this.folders = rows || []) });
        },
        error: (err) => {
          this.error = err?.error?.message || 'Could not create folder.';
        },
      });
  }

  openDetail(d: ComplianceDocument): void {
    this.detail = d;
    this.versions = [];
    this.audits = [];
    if (!d.id) return;
    this.api.documentVersions(d.id).subscribe({ next: (rows) => (this.versions = rows || []) });
    this.api.documentAudit(d.id).subscribe({ next: (rows) => (this.audits = rows || []) });
  }

  act(d: ComplianceDocument, action: 'submit' | 'approve' | 'archive' | 'restore'): void {
    if (!d.id) return;
    if (action === 'archive' && !confirm(`Archive ${d.title}? It can be restored later.`)) return;
    this.api.documentAction(d.id, action).subscribe({
      next: () => this.refresh(),
      error: (err) => {
        this.error = err?.error?.message || 'Action failed.';
      },
    });
  }

  reject(d: ComplianceDocument): void {
    if (!d.id) return;
    const reason = prompt('Rejection reason');
    if (!reason) return;
    this.api.rejectDocument(d.id, reason).subscribe({
      next: () => this.refresh(),
      error: (err) => {
        this.error = err?.error?.message || 'Reject failed.';
      },
    });
  }

  toggle(d: ComplianceDocument): void {
    if (!d.id) return;
    if (this.selectedIds.has(d.id)) this.selectedIds.delete(d.id);
    else this.selectedIds.add(d.id);
  }

  archiveSelected(): void {
    if (!confirm(`Archive ${this.selectedIds.size} documents?`)) return;
    const ids = [...this.selectedIds];
    ids.forEach((id) => this.api.documentAction(id, 'archive').subscribe({ next: () => this.refresh() }));
    this.selectedIds.clear();
  }

  canPreview(d: ComplianceDocument): boolean {
    const type = (d.contentType || '').toLowerCase();
    return type.includes('pdf') || type.startsWith('image/');
  }

  download(d: ComplianceDocument, version?: number): void {
    if (!d.id) return;
    this.api.downloadDocumentFile(d.id, version).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = d.fileName || `document-${d.id}`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.error = 'Download failed.';
      },
    });
  }

  preview(d: ComplianceDocument): void {
    if (!d.id) return;
    this.api.downloadDocumentFile(d.id, undefined, true).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank', 'noopener');
      },
      error: () => {
        this.error = 'Preview failed.';
      },
    });
  }

  private afterSave(): void {
    this.saving = false;
    this.notice = '';
    this.resetForm();
    this.refresh();
  }

  private failSave(err: { error?: { message?: string }; message?: string }, fallback: string): void {
    this.saving = false;
    this.error = err?.error?.message || err?.message || fallback;
  }
}
