import { Component, HostListener, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';
import { ThemeService } from '../../core/theme.service';
import { StudentLookupComponent, StudentLookupRow } from '../../shared/student-lookup';
import {
  EMPTY_FILTERS,
  GateFilters,
  GatePass,
  GatePayload,
  GateStats,
  PassType,
  STUDENT_PURPOSES,
  VISITOR_CATEGORIES,
  VISITOR_PURPOSES,
  canApprove,
  kolkataToday,
  maskPhone,
  matches,
  passTypeOf,
  statsFor,
  statusLabel,
} from './gate-pass.model';

interface ClassRow {
  id: string;
  name: string;
}

interface SectionRow {
  id: string;
  classId: string;
  name: string;
}

@Component({
  selector: 'sf-gate-pass',
  standalone: true,
  imports: [CommonModule, FormsModule, StudentLookupComponent],
  templateUrl: './gate-pass.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/sortable-table.scss',
    './gate-pass.component.scss',
  ],
})
export class GatePassComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly theme = inject(ThemeService);

  readonly purposes = { STUDENT: STUDENT_PURPOSES, VISITOR: VISITOR_PURPOSES };
  readonly categories = VISITOR_CATEGORIES;
  readonly statusLabel = statusLabel;
  readonly maskPhone = maskPhone;
  readonly passTypeOf = passTypeOf;

  loading = true;
  saving = false;
  error = '';
  notice = '';
  rows: GatePass[] = [];
  passType: PassType = 'STUDENT';
  draft: GateFilters = { ...EMPTY_FILTERS };
  applied: GateFilters = { ...EMPTY_FILTERS };
  private readonly savedFilters: Record<PassType, { draft: GateFilters; applied: GateFilters; page: number }> = {
    STUDENT: { draft: { ...EMPTY_FILTERS }, applied: { ...EMPTY_FILTERS }, page: 1 },
    VISITOR: { draft: { ...EMPTY_FILTERS }, applied: { ...EMPTY_FILTERS }, page: 1 },
  };
  page = 1;
  pageSize = 10;
  sortKey = 'createdAt';
  sortDir: 'asc' | 'desc' = 'desc';
  classes: ClassRow[] = [];
  sections: SectionRow[] = [];
  formOpen = false;
  editingId: string | null = null;
  detail: GatePass | null = null;
  menuId: string | null = null;
  confirm: { id: string; action: string; title: string; body: string } | null = null;
  clientRequestId = '';
  requestApproval = false;
  student: StudentLookupRow | null = null;
  meetStudent: StudentLookupRow | null = null;
  formError = '';
  payload: GatePayload = {};

  ngOnInit(): void {
    this.reload();
    this.api.get<ClassRow[]>('/api/academic/classes').subscribe({
      next: (rows) => (this.classes = rows || []),
      error: () => (this.classes = []),
    });
    this.api.get<SectionRow[]>('/api/academic/sections').subscribe({
      next: (rows) => (this.sections = rows || []),
      error: () => (this.sections = []),
    });
  }

  get approver(): boolean {
    return canApprove(this.auth.getRole());
  }

  get stats(): GateStats {
    return statsFor(this.rows, kolkataToday());
  }

  get sectionOptions(): SectionRow[] {
    const selected = this.classes.find((row) => row.name === this.draft.className);
    if (!selected) {
      return [];
    }
    return this.sections.filter((row) => row.classId === selected.id);
  }

  get filtered(): GatePass[] {
    const rows = this.rows.filter((row) => matches(row, this.passType, this.applied));
    const dir = this.sortDir === 'asc' ? 1 : -1;
    return [...rows].sort((a, b) => dir * this.sortValue(a).localeCompare(this.sortValue(b)));
  }

  get pageCount(): number {
    return Math.max(1, Math.ceil(this.filtered.length / this.pageSize));
  }

  get pageRows(): GatePass[] {
    const start = (this.page - 1) * this.pageSize;
    return this.filtered.slice(start, start + this.pageSize);
  }

  selectType(type: PassType): void {
    if (type === this.passType) {
      return;
    }
    this.savedFilters[this.passType] = {
      draft: { ...this.draft },
      applied: { ...this.applied },
      page: this.page,
    };
    const next = this.savedFilters[type];
    this.passType = type;
    this.draft = { ...next.draft };
    this.applied = { ...next.applied };
    this.page = next.page;
    this.menuId = null;
    this.formOpen = false;
  }

  search(): void {
    this.applied = { ...this.draft };
    this.page = 1;
  }

  reset(): void {
    this.draft = { ...EMPTY_FILTERS };
    this.applied = { ...EMPTY_FILTERS };
    this.page = 1;
  }

  onClassChange(): void {
    this.draft.section = '';
  }

  sort(key: string): void {
    if (this.sortKey === key) {
      this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortKey = key;
      this.sortDir = 'asc';
    }
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<GatePass[]>('/api/student/desk/GATE_PASS').subscribe({
      next: (rows) => {
        this.rows = rows || [];
        this.loading = false;
        if (this.page > this.pageCount) {
          this.page = this.pageCount;
        }
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message || 'Could not load gate passes';
      },
    });
  }

  openCreate(): void {
    this.formOpen = true;
    this.editingId = null;
    this.student = null;
    this.meetStudent = null;
    this.formError = '';
    this.clientRequestId = crypto.randomUUID();
    this.requestApproval = false;
    this.payload = { passType: this.passType, purpose: '', category: 'GUEST', accompanying: 0 };
  }

  openEdit(row: GatePass): void {
    this.menuId = null;
    this.formOpen = true;
    this.editingId = row.id;
    this.formError = '';
    this.payload = { ...(row.payload || {}) };
    this.student = null;
    this.meetStudent = null;
  }

  closeForm(): void {
    if (!this.saving) {
      this.formOpen = false;
    }
  }

  onStudent(row: StudentLookupRow): void {
    this.student = row;
    this.payload.guardianName = this.payload.guardianName || row.parentName || '';
    this.payload.contact = this.payload.contact || row.mobile || '';
  }

  onMeetStudent(row: StudentLookupRow): void {
    this.meetStudent = row;
    this.payload.meetStudentId = row.id;
    this.payload.meetStudentName = row.fullName;
  }

  save(): void {
    this.formError = '';
    if (this.saving) {
      return;
    }
    if (!this.payload.purpose?.trim()) {
      this.formError = 'Purpose is required';
      return;
    }
    if (!this.editingId && this.passType === 'STUDENT' && !this.student?.id) {
      this.formError = 'Select a student';
      return;
    }
    if (!this.editingId && this.passType === 'VISITOR') {
      if (!this.payload.visitorName?.trim()) {
        this.formError = 'Guest name is required';
        return;
      }
      if ((this.payload.mobile || '').replace(/\D/g, '').length < 10) {
        this.formError = 'A valid mobile number is required';
        return;
      }
    }
    this.saving = true;
    const request = this.editingId
      ? this.api.post<GatePass>(`/api/student/desk/items/${this.editingId}/status`, {
          action: 'EDIT',
          payload: this.payload,
        })
      : this.api.post<GatePass>('/api/student/desk/GATE_PASS', {
          subjectType: this.passType,
          subjectRef: this.passType === 'STUDENT' ? this.student?.id : undefined,
          clientRequestId: this.clientRequestId,
          requestApproval: this.passType === 'STUDENT' && this.requestApproval,
          payload: { ...this.payload, passType: this.passType },
        });
    request.subscribe({
      next: () => {
        this.saving = false;
        this.formOpen = false;
        this.notice = this.editingId ? 'Gate pass updated' : 'Gate pass created';
        this.reload();
      },
      error: (err) => {
        this.saving = false;
        this.formError = err?.error?.message || 'Could not save the gate pass';
      },
    });
  }

  ask(row: GatePass, action: string): void {
    this.menuId = null;
    const titles: Record<string, string> = {
      CANCEL: 'Cancel this gate pass?',
      REJECT: 'Reject this gate pass?',
      EXIT: 'Record that the student has left?',
      RETURN: 'Record that the student has returned?',
      CHECK_OUT: 'Record guest check-out?',
      APPROVE: 'Approve this gate pass?',
    };
    this.confirm = {
      id: row.id,
      action,
      title: titles[action] || 'Continue?',
      body: 'This updates the saved gate pass.',
    };
  }

  runConfirm(): void {
    if (!this.confirm || this.saving) {
      return;
    }
    const pending = this.confirm;
    this.saving = true;
    this.api
      .post(`/api/student/desk/items/${pending.id}/status`, { action: pending.action })
      .subscribe({
        next: () => {
          this.saving = false;
          this.confirm = null;
          this.detail = null;
          this.notice = 'Gate pass updated';
          this.reload();
        },
        error: (err) => {
          this.saving = false;
          this.notice = '';
          this.error = err?.error?.message || 'Could not update the gate pass';
          this.confirm = null;
        },
      });
  }

  print(row: GatePass): void {
    this.menuId = null;
    const popup = window.open('', '_blank', 'noopener,width=720,height=900');
    if (!popup) {
      this.error = 'Allow pop-ups to print the gate pass';
      return;
    }
    popup.document.write(this.printHtml(row));
    popup.document.close();
    popup.focus();
    popup.print();
  }

  toggleMenu(id: string, event: Event): void {
    event.stopPropagation();
    this.menuId = this.menuId === id ? null : id;
  }

  @HostListener('document:click')
  closeMenu(): void {
    this.menuId = null;
  }

  actionsFor(row: GatePass): Array<{ id: string; label: string }> {
    const status = (row.status || '').toUpperCase();
    const type = passTypeOf(row);
    const actions: Array<{ id: string; label: string }> = [];
    if (type === 'STUDENT' && status === 'PENDING' && this.approver) {
      actions.push({ id: 'APPROVE', label: 'Approve' }, { id: 'REJECT', label: 'Reject' });
    }
    if (type === 'STUDENT' && (status === 'APPROVED' || status === 'OPEN')) {
      actions.push({ id: 'EXIT', label: 'Record exit' });
    }
    if (type === 'STUDENT' && status === 'OUT') {
      actions.push({ id: 'RETURN', label: 'Record return' });
    }
    if (type === 'VISITOR' && (status === 'INSIDE' || status === 'OPEN')) {
      actions.push({ id: 'CHECK_OUT', label: 'Check out' });
    }
    if (['PENDING', 'APPROVED', 'OPEN', 'INSIDE'].includes(status)) {
      actions.push({ id: 'CANCEL', label: 'Cancel' });
    }
    return actions;
  }

  editable(row: GatePass): boolean {
    return !['RETURNED', 'COMPLETED', 'CANCELLED', 'REJECTED'].includes((row.status || '').toUpperCase());
  }

  labelFor(row: GatePass): string {
    return statusLabel(row.status, passTypeOf(row));
  }

  initials(name?: string | null): string {
    const parts = (name || '').trim().split(/\s+/).filter(Boolean);
    if (!parts.length) {
      return '?';
    }
    return (parts[0].charAt(0) + (parts[1]?.charAt(0) || '')).toUpperCase();
  }

  categoryLabel(id?: string): string {
    return this.categories.find((item) => item.id === id)?.label || id || '';
  }

  primaryAction(row: GatePass): { id: string; label: string } | null {
    const preferred = ['EXIT', 'RETURN', 'CHECK_OUT', 'APPROVE'];
    return this.actionsFor(row).find((action) => preferred.includes(action.id)) ?? null;
  }

  menuActions(row: GatePass): Array<{ id: string; label: string }> {
    const primary = this.primaryAction(row)?.id;
    return this.actionsFor(row).filter((action) => action.id !== primary);
  }

  private sortValue(row: GatePass): string {
    const payload = row.payload || {};
    switch (this.sortKey) {
      case 'name':
        return (row.subjectName || '').toLowerCase();
      case 'class':
        return `${payload.className || ''} ${payload.section || ''}`.toLowerCase();
      case 'purpose':
        return (payload.purpose || '').toLowerCase();
      case 'status':
        return (row.status || '').toLowerCase();
      case 'pass':
        return (payload.passNumber || row.title || '').toLowerCase();
      default:
        return row.createdAt || '';
    }
  }

  private printHtml(row: GatePass): string {
    const branding = this.theme.theme()?.branding || {};
    const payload = row.payload || {};
    const type = passTypeOf(row);
    const school = this.escape(branding['schoolName'] || 'School');
    const address = this.escape(branding['address'] || branding['schoolAddress'] || '');
    const contact = this.escape(branding['phone'] || branding['contact'] || branding['email'] || '');
    const logo = branding['logo'] ? `<img src="${this.escape(branding['logo'])}" alt="" />` : '';
    const lines =
      type === 'STUDENT'
        ? [
            ['Student', row.subjectName],
            ['Admission no.', payload.admissionNo],
            ['Class / section', [payload.className, payload.section].filter(Boolean).join(' / ')],
            ['Roll no.', payload.rollNo],
            ['Guardian', payload.guardianName],
            ['Contact', payload.contact],
            ['Exit', payload.actualExit],
            ['Expected return', payload.expectedReturn],
            ['Returned', payload.actualReturn],
          ]
        : [
            ['Guest', payload.visitorName || row.subjectName],
            ['Mobile', payload.mobile],
            ['Category', payload.category],
            ['To meet', payload.personToMeet],
            ['Check-in', payload.checkIn],
            ['Expected departure', payload.expectedDeparture],
            ['Check-out', payload.checkOut],
          ];
    const rows = lines
      .filter(([, value]) => value)
      .map(([label, value]) => `<tr><th>${this.escape(String(label))}</th><td>${this.escape(String(value))}</td></tr>`)
      .join('');
    const approved = (row.status || '').toUpperCase();
    const approval =
      type === 'STUDENT' && ['APPROVED', 'OUT', 'RETURNED'].includes(approved)
        ? `<p class="ok">Status: ${this.escape(statusLabel(row.status, type))}</p>`
        : `<p>Status: ${this.escape(statusLabel(row.status, type))}</p>`;
    return `<!doctype html><html><head><title>${this.escape(payload.passNumber || 'Gate pass')}</title>
      <style>
        body { font-family: "Source Sans 3", sans-serif; color: #1f2933; margin: 24px; }
        header { display: flex; gap: 12px; align-items: center; border-bottom: 2px solid #176b45; padding-bottom: 8px; }
        img { height: 52px; }
        h1 { font-size: 18px; margin: 12px 0 4px; }
        table { width: 100%; border-collapse: collapse; margin-top: 12px; }
        th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid #d9e2dc; font-size: 14px; }
        th { width: 34%; color: #405549; font-weight: 600; }
        .signs { display: flex; justify-content: space-between; margin-top: 42px; }
        .signs div { width: 40%; border-top: 1px solid #1f2933; padding-top: 6px; font-size: 13px; }
        .ok { font-weight: 700; }
      </style></head><body>
      <header>${logo}<div><strong>${school}</strong><div>${address}</div><div>${contact}</div></div></header>
      <h1>${type === 'STUDENT' ? 'Student gate pass' : 'Guest gate pass'}</h1>
      <p>Pass no. <strong>${this.escape(payload.passNumber || row.title || '')}</strong> · ${this.escape(payload.passDate || '')}</p>
      <p>Purpose: ${this.escape(payload.purpose || '')}</p>
      ${payload.reason ? `<p>Reason: ${this.escape(payload.reason)}</p>` : ''}
      <table>${rows}</table>
      ${approval}
      <div class="signs"><div>Issued by</div><div>${type === 'STUDENT' ? 'Guardian / student' : 'Visitor'}</div></div>
      </body></html>`;
  }

  private escape(value: string | undefined): string {
    return String(value || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
}
