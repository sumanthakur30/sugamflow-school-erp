import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { ApiService, PageResult } from '../../core/api.service';

export type LeadStatus =
  | 'INTERESTED'
  | 'NOT_INTERESTED'
  | 'ADMIN'
  | 'FOLLOW_UP'
  | 'CALL_BACK'
  | 'BLACKLISTED'
  | 'ADMISSION_CREATED';

export interface Lead {
  id: string;
  studentName: string;
  fatherName?: string;
  motherName?: string;
  phone: string;
  fatherPhone?: string;
  motherPhone?: string;
  address?: string;
  classAppliedFor?: string;
  admissionNo?: string;
  createdBy?: string;
  createdAt?: string;
  scheduledAt?: string;
  status: LeadStatus;
  statusLabel?: string;
  remark?: string;
  assignedTo?: string;
}

export interface LeadWrite {
  studentName: string;
  fatherName: string;
  motherName: string;
  phone: string;
  fatherPhone: string;
  motherPhone: string;
  address: string;
  classAppliedFor: string;
  admissionNo: string;
  scheduledAt: string;
  status: LeadStatus;
  remark: string;
  assignedTo: string;
}

interface LeadSummary {
  total: number;
  counts: Record<string, number>;
}

@Component({
  selector: 'sf-leads',
  standalone: true,
  imports: [CommonModule, FormsModule],
  styleUrls: ['../../shared/admin-page.scss'],
  templateUrl: './leads.component.html',
  styles: [
    `
      .toolbar {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        align-items: flex-start;
        flex-wrap: wrap;
      }
      .icon-row {
        display: flex;
        gap: 0.45rem;
        align-items: center;
      }
      .icon-btn {
        width: 2.4rem;
        min-height: 2.4rem;
        padding: 0;
        border-radius: 10px;
      }
      .total-badge {
        margin-left: auto;
        background: #fff;
        border: 1px solid var(--sf-border, #dce4df);
        border-radius: 10px;
        padding: 0.45rem 0.8rem;
        font-weight: 700;
      }
      .search {
        width: min(420px, 100%);
        margin: 0.2rem 0 0.8rem;
      }
      .pills {
        display: flex;
        flex-wrap: wrap;
        gap: 0.45rem;
        margin-bottom: 0.9rem;
      }
      .pill {
        border: 0;
        color: #fff;
        border-radius: 6px;
        min-height: 1.8rem;
        padding: 0.2rem 0.55rem;
        font-size: 0.78rem;
        font-weight: 700;
      }
      .pill.active {
        outline: 2px solid var(--sf-text, #26352e);
        outline-offset: 2px;
      }
      .pill.interested { background: #16a34a; }
      .pill.not { background: #dc2626; }
      .pill.admin { background: #65a30d; }
      .pill.follow { background: #4f46e5; }
      .pill.callback { background: #db2777; }
      .pill.black { background: #111827; }
      .pill.admitted { background: #14532d; }
      .stack { display: flex; flex-direction: column; gap: 0.1rem; line-height: 1.35; }
      .name {
        background: none;
        border: 0;
        color: #1d4ed8;
        font-weight: 700;
        padding: 0;
        min-height: 0;
        text-align: left;
      }
      .muted { color: var(--sf-text-muted, #64736a); font-size: 0.84rem; }
      .wa {
        margin-left: 0.25rem;
        color: #15803d;
        text-decoration: none;
        font-weight: 700;
      }
      table { width: 100%; border-collapse: collapse; }
      th, td {
        text-align: left;
        vertical-align: top;
        padding: 0.7rem 0.55rem;
        border-bottom: 1px solid var(--sf-border, #dce4df);
        font-size: 0.9rem;
      }
      th { background: var(--sf-table-head, #e8efeb); font-size: 0.82rem; }
      .filters {
        display: grid;
        grid-template-columns: repeat(4, minmax(0, 1fr));
        gap: 0.7rem;
        margin-bottom: 0.9rem;
      }
      .sk {
        height: 0.8rem;
        border-radius: 6px;
        background: #e8efeb;
        margin: 0.25rem 0;
      }
      .modal-back {
        position: fixed;
        inset: 0;
        background: rgba(22, 56, 44, 0.35);
        display: flex;
        align-items: flex-start;
        justify-content: center;
        z-index: 6000;
        padding: 1.25rem 1rem 1.5rem;
        overflow: auto;
      }
      .modal {
        width: min(760px, 100%);
        margin: auto;
        background: #fff;
        border-radius: 12px;
        padding: 1.1rem 1.2rem 1.2rem;
        color: var(--sf-text, #26352e);
      }
      .modal h3 {
        margin: 0 0 0.85rem;
        color: var(--sf-text, #26352e);
      }
      .modal label {
        color: var(--sf-text, #26352e);
      }
      .fab {
        position: fixed;
        right: 1.4rem;
        bottom: 1.4rem;
        width: 3.4rem;
        height: 3.4rem;
        border-radius: 999px;
        font-size: 1.6rem;
        box-shadow: 0 8px 20px rgba(23, 107, 69, 0.28);
        z-index: 20;
      }
      .pager { display: flex; gap: 0.5rem; align-items: center; margin-top: 0.8rem; }
      .file { display: none; }
      @media (max-width: 900px) {
        .filters { grid-template-columns: 1fr 1fr; }
        .table-wrap { overflow-x: auto; }
      }
    `,
  ],
})
export class LeadsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly search$ = new Subject<string>();

  readonly statuses: { key: LeadStatus; label: string; tone: string }[] = [
    { key: 'INTERESTED', label: 'Interested', tone: 'interested' },
    { key: 'NOT_INTERESTED', label: 'Not Interested', tone: 'not' },
    { key: 'ADMIN', label: 'Admin', tone: 'admin' },
    { key: 'FOLLOW_UP', label: 'Follow-up', tone: 'follow' },
    { key: 'CALL_BACK', label: 'Call back', tone: 'callback' },
    { key: 'BLACKLISTED', label: 'Blacklisted', tone: 'black' },
    { key: 'ADMISSION_CREATED', label: 'Admission Created', tone: 'admitted' },
  ];

  loading = true;
  saving = false;
  error = '';
  statusMsg = '';
  q = '';
  status: LeadStatus | '' = '';
  filtersOpen = false;
  classAppliedFor = '';
  assignedTo = '';
  scheduledFrom = '';
  scheduledTo = '';
  page = 0;
  totalPages = 1;
  total = 0;
  counts: Record<string, number> = {};
  items: Lead[] = [];
  formOpen = false;
  private emptyFormPrompted = false;
  editingId = '';
  form: LeadWrite = this.blank();

  ngOnInit(): void {
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.page = 0;
        this.load();
      });
    this.refresh();
  }

  onSearch(value: string): void {
    this.q = value;
    this.search$.next(value.trim());
  }

  toggleStatus(key: LeadStatus): void {
    this.status = this.status === key ? '' : key;
    this.page = 0;
    this.load();
  }

  applyFilters(): void {
    this.page = 0;
    this.load();
  }

  refresh(): void {
    this.api.get<LeadSummary>('/api/admission/leads/summary').subscribe({
      next: (summary) => {
        this.total = summary?.total ?? 0;
        this.counts = summary?.counts ?? {};
      },
      error: () => {
        this.total = 0;
      },
    });
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api
      .getPage<Lead>('/api/admission/leads', this.page, 20, {
        q: this.q.trim(),
        status: this.status,
        classAppliedFor: this.classAppliedFor.trim(),
        assignedTo: this.assignedTo.trim(),
        scheduledFrom: this.scheduledFrom,
        scheduledTo: this.scheduledTo,
      })
      .subscribe({
        next: (page: PageResult<Lead>) => {
          this.items = page.items ?? [];
          this.totalPages = page.totalPages || 1;
          this.loading = false;
          const unfiltered =
            !this.q.trim() &&
            !this.status &&
            !this.classAppliedFor.trim() &&
            !this.assignedTo.trim() &&
            !this.scheduledFrom &&
            !this.scheduledTo &&
            this.page === 0;
          if (!this.emptyFormPrompted && unfiltered && this.items.length === 0) {
            this.emptyFormPrompted = true;
            this.openCreate();
          }
        },
        error: (err) => {
          this.loading = false;
          this.items = [];
          this.error = err?.error?.message ?? 'Could not load leads';
        },
      });
  }

  openCreate(): void {
    this.editingId = '';
    this.form = this.blank();
    this.formOpen = true;
    this.error = '';
  }

  openEdit(lead: Lead): void {
    this.editingId = lead.id;
    this.form = {
      studentName: lead.studentName ?? '',
      fatherName: lead.fatherName ?? '',
      motherName: lead.motherName ?? '',
      phone: lead.phone ?? '',
      fatherPhone: lead.fatherPhone ?? '',
      motherPhone: lead.motherPhone ?? '',
      address: lead.address ?? '',
      classAppliedFor: lead.classAppliedFor ?? '',
      admissionNo: lead.admissionNo ?? '',
      scheduledAt: this.toLocal(lead.scheduledAt),
      status: lead.status,
      remark: lead.remark ?? '',
      assignedTo: lead.assignedTo ?? '',
    };
    this.formOpen = true;
  }

  closeForm(): void {
    if (!this.saving) {
      this.formOpen = false;
    }
  }

  save(): void {
    if (!this.form.studentName.trim() || !this.form.phone.trim()) {
      this.error = 'Student name and mobile number are required.';
      return;
    }
    this.saving = true;
    this.error = '';
    const body = { ...this.form, scheduledAt: this.form.scheduledAt || null };
    const call = this.editingId
      ? this.api.put<Lead>(`/api/admission/leads/${this.editingId}`, body)
      : this.api.post<Lead>('/api/admission/leads', body);
    call.subscribe({
      next: () => {
        this.saving = false;
        this.formOpen = false;
        this.statusMsg = this.editingId ? 'Lead updated.' : 'Lead added.';
        this.refresh();
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message ?? 'Could not save the lead';
      },
    });
  }

  changeStatus(lead: Lead, status: LeadStatus): void {
    const previous = lead.status;
    lead.status = status;
    this.api.patch<Lead>(`/api/admission/leads/${lead.id}/status`, { status }).subscribe({
      next: (saved) => {
        lead.statusLabel = saved.statusLabel;
        this.refresh();
      },
      error: (err) => {
        lead.status = previous;
        this.error = err?.error?.message ?? 'Could not update status';
      },
    });
  }

  loadExample(): void {
    this.api.post<{ message?: string }>('/api/admission/leads/example', {}).subscribe({
      next: (res) => {
        this.statusMsg = res?.message ?? 'Sample leads loaded for this school.';
        this.refresh();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Could not load sample leads';
      },
    });
  }

  pickImport(input: HTMLInputElement): void {
    input.click();
  }

  importFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    const body = new FormData();
    body.append('file', file);
    this.api.post<{ created: number; skipped: number; errors: string[] }>('/api/admission/leads/import', body).subscribe({
      next: (res) => {
        const extra = res.errors?.length ? ` ${res.errors[0]}` : '';
        this.statusMsg = `Imported ${res.created} lead(s). Skipped ${res.skipped}.${extra}`;
        this.refresh();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Import failed';
      },
    });
  }

  exportExcel(): void {
    this.download('/api/admission/leads/export/excel', 'leads.xlsx');
  }

  exportPdf(): void {
    this.download('/api/admission/leads/export/pdf', 'leads.pdf');
  }

  count(key: string): number {
    return this.counts[key] ?? 0;
  }

  whatsapp(phone?: string): string {
    const digits = (phone ?? '').replace(/\D/g, '');
    if (digits.length < 10) {
      return '';
    }
    return `https://wa.me/${digits.length === 10 ? '91' + digits : digits}`;
  }

  formatWhen(iso?: string): string {
    if (!iso) {
      return '';
    }
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const pad = (n: number) => String(n).padStart(2, '0');
    const hour = date.getHours();
    const shown = hour % 12 || 12;
    const ampm = hour < 12 ? 'am' : 'pm';
    return `${pad(date.getDate())} ${months[date.getMonth()]}, ${date.getFullYear()} ${pad(shown)}:${pad(date.getMinutes())}:${pad(date.getSeconds())}${ampm}`;
  }

  tone(status: string): string {
    return this.statuses.find((item) => item.key === status)?.tone ?? 'interested';
  }

  label(status: string): string {
    return this.statuses.find((item) => item.key === status)?.label ?? status;
  }

  prev(): void {
    if (this.page > 0) {
      this.page -= 1;
      this.load();
    }
  }

  next(): void {
    if (this.page + 1 < this.totalPages) {
      this.page += 1;
      this.load();
    }
  }

  private download(path: string, filename: string): void {
    const params = new URLSearchParams();
    if (this.q.trim()) params.set('q', this.q.trim());
    if (this.status) params.set('status', this.status);
    if (this.classAppliedFor.trim()) params.set('classAppliedFor', this.classAppliedFor.trim());
    if (this.assignedTo.trim()) params.set('assignedTo', this.assignedTo.trim());
    if (this.scheduledFrom) params.set('scheduledFrom', this.scheduledFrom);
    if (this.scheduledTo) params.set('scheduledTo', this.scheduledTo);
    const query = params.toString();
    this.api.getBlob(path + (query ? `?${query}` : '')).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.error = 'Could not download the file';
      },
    });
  }

  private toLocal(iso?: string): string {
    if (!iso) {
      return '';
    }
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  private blank(): LeadWrite {
    return {
      studentName: '',
      fatherName: '',
      motherName: '',
      phone: '',
      fatherPhone: '',
      motherPhone: '',
      address: '',
      classAppliedFor: '',
      admissionNo: '',
      scheduledAt: '',
      status: 'INTERESTED',
      remark: '',
      assignedTo: '',
    };
  }
}
