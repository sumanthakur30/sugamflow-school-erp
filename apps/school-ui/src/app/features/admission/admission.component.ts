import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiService, PageResult } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';
import { ListToolbarComponent } from '../../shared/list-toolbar/list-toolbar.component';
import { ListPagerComponent } from '../../shared/list-toolbar/list-pager.component';
import {
  ListSortOption,
  ListStatusOption,
  headerSortIndicator,
  nextHeaderSort,
  pageMeta,
} from '../../shared/list-toolbar/list-controls';
import { parseListViewParams } from '../../shared/list-toolbar/list-view-route';

type FormField = {
  key: string;
  label: string;
  type: string;
  mandatory: boolean;
  options: string[];
  sectionId: string;
};

@Component({
  selector: 'sf-admission',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ListToolbarComponent, ListPagerComponent],
  templateUrl: './admission.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/list-toolbar.component.scss',
    '../../shared/list-toolbar/sortable-table.scss',
    './admission.component.scss',
  ],
})
export class AdmissionComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private routeSub?: Subscription;

  loading = true;
  error = '';
  statusMsg = '';
  featureEnabled = false;
  formKey = 'admission_form';
  workflowKey = 'admission';
  fields: FormField[] = [];
  formSections: Array<{ id: string; title: string }> = [];
  answers: Record<string, unknown> = {};
  fieldErrors: Record<string, string> = {};
  readonly houseOptions = ['Red', 'Blue', 'Green', 'Yellow'];
  /** Grade 1–10 + section letters for Class Applied picker. */
  readonly defaultGrades = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10'];
  readonly defaultSections = ['A', 'B', 'C'];
  gradeChoices: string[] = [...this.defaultGrades];
  sectionChoices: string[] = [...this.defaultSections];
  classGrade = '';
  classSectionLetter = '';
  applications: any[] = [];
  selectedId: string | null = null;
  selected: any = null;
  /** List-first: form/detail driven by ?new=1 / ?id= */
  formOpen = false;
  actionComment = '';
  submitting = false;
  activeFormSectionId = '';
  /** Sync lock — blocks double-click before Angular re-renders disabled state. */
  private submitLocked = false;
  private lastSubmitFingerprint = '';
  private lastSubmitAt = 0;

  listQ = '';
  listStatus = '';
  sortBy = 'updatedAt';
  sortDir: 'ASC' | 'DESC' = 'DESC';
  pageIndex = 0;
  pageSize = 50;
  searching = false;
  page: PageResult<any> = {
    items: [],
    page: 0,
    size: 50,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
  };
  readonly sortOptions: ListSortOption[] = [
    { key: 'updatedAt', label: 'Updated' },
    { key: 'fullName', label: 'Applicant Name' },
    { key: 'status', label: 'Status' },
  ];
  readonly statusOptions: ListStatusOption[] = [
    { value: 'IN_PROGRESS', label: 'Under review' },
    { value: 'INFO_REQUESTED', label: 'More info needed' },
    { value: 'APPROVED', label: 'Approved' },
    { value: 'REJECTED', label: 'Rejected' },
  ];

  ngOnInit(): void {
    this.routeSub = this.route.queryParamMap.subscribe((params) => this.syncFromRoute(params));
    this.reload();
    this.loadClassOptions();
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.submitting) return;
    if (this.formOpen) {
      this.closeForm();
      return;
    }
    if (this.selected) {
      this.closeDetail();
    }
  }

  openForm(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { new: '1' },
    });
  }

  closeForm(): void {
    if (this.submitting) return;
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  closeDetail(): void {
    this.actionComment = '';
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  private syncFromRoute(params: import('@angular/router').ParamMap): void {
    const { mode, id } = parseListViewParams(params);
    if (mode === 'new') {
      this.formOpen = true;
      this.selected = null;
      this.selectedId = null;
      this.error = '';
      this.statusMsg = '';
      this.fieldErrors = {};
      this.resetClassPicker();
      const visible = this.visibleFormSections();
      this.activeFormSectionId = visible[0]?.id || '';
      return;
    }
    this.formOpen = false;
    if (mode === 'detail' && id) {
      if (this.selectedId !== id || !this.selected) {
        this.loadDetail(id);
      }
      return;
    }
    this.selected = null;
    this.selectedId = null;
    this.actionComment = '';
  }

  private loadDetail(id: string): void {
    this.selectedId = id;
    this.api.get<any>(`/api/admission/applications/${id}`).subscribe({
      next: (full) => (this.selected = full),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load application'),
    });
  }

  sectionFields(sectionId: string): FormField[] {
    return this.fields.filter((f) => f.sectionId === sectionId);
  }

  applicantFields(): FormField[] {
    return this.fields.filter((f) => !this.isGuardianKey(f.key));
  }

  guardianFields(): FormField[] {
    return this.fields.filter((f) => this.isGuardianKey(f.key));
  }

  private isGuardianKey(key: string): boolean {
    const k = key.toLowerCase();
    return k.includes('guardian') || k === 'relation';
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/admission/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.formKey = boot.formKey;
        this.workflowKey = boot.workflowKey;
        this.fields = this.extractFields(boot.form);
        this.formSections = this.extractSections(boot.form);
        const visible = this.visibleFormSections();
        this.activeFormSectionId = visible[0]?.id || '';
        for (const f of this.fields) {
          if (this.answers[f.key] === undefined) {
            this.answers[f.key] = f.type === 'CHECKBOX' ? false : '';
          }
        }
        this.loading = false;
        this.loadApplications();
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? err?.message ?? 'Admission bootstrap failed';
        this.featureEnabled = false;
      },
    });
  }

  loadApplications(): void {
    this.searching = true;
    this.api
      .getPage<any>('/api/admission/applications', this.pageIndex, this.pageSize, {
        q: this.listQ || undefined,
        status: this.listStatus || undefined,
        sortBy: this.sortBy || undefined,
        sortDir: this.sortDir || undefined,
      })
      .subscribe({
        next: (p) => {
          this.page = p;
          this.applications = p.items || [];
          this.searching = false;
        },
        error: (err) => {
          this.searching = false;
          this.error = err?.error?.message ?? 'Failed to load applications';
        },
      });
  }

  get showingFrom(): number {
    return pageMeta(this.page.page, this.page.size || this.pageSize, this.page.totalElements || 0)
      .from;
  }

  get showingTo(): number {
    return pageMeta(this.page.page, this.page.size || this.pageSize, this.page.totalElements || 0)
      .to;
  }

  get listClearEnabled(): boolean {
    return (
      !!this.listQ ||
      !!this.listStatus ||
      this.sortBy !== 'updatedAt' ||
      this.sortDir !== 'DESC' ||
      this.pageSize !== 50
    );
  }

  get hasActiveFilters(): boolean {
    return !!this.listQ?.trim() || !!this.listStatus;
  }

  clearListFilters(): void {
    this.listQ = '';
    this.listStatus = '';
    this.sortBy = 'updatedAt';
    this.sortDir = 'DESC';
    this.pageSize = 50;
    this.pageIndex = 0;
    this.loadApplications();
  }

  onPageChange(index: number): void {
    this.pageIndex = index;
    this.loadApplications();
  }

  isSortableColumn(key: string): boolean {
    return this.sortOptions.some((option) => option.key === key);
  }

  sortByColumn(key: string): void {
    if (!this.isSortableColumn(key)) {
      return;
    }
    const next = nextHeaderSort(this.sortBy, this.sortDir, key);
    this.sortBy = next.sortBy;
    this.sortDir = next.sortDir;
    this.pageIndex = 0;
    this.loadApplications();
  }

  sortIndicator(key: string): string {
    return headerSortIndicator(this.sortBy, this.sortDir, key);
  }

  submit(): void {
    if (this.submitLocked || this.submitting) {
      return;
    }

    this.error = '';
    this.statusMsg = '';
    this.fieldErrors = {};

    if (!this.validateForm()) {
      this.error = 'Please fix the highlighted fields before submitting.';
      return;
    }

    const answers = this.normalizeAnswers();
    const fingerprint = this.fingerprint(answers);
    const now = Date.now();
    if (fingerprint === this.lastSubmitFingerprint && now - this.lastSubmitAt < 15000) {
      this.error =
        'This application was already submitted a moment ago. Check the inbox to avoid duplicates.';
      return;
    }

    this.submitLocked = true;
    this.submitting = true;
    this.statusMsg = 'Saving application… please wait. Do not click Submit again.';

    const payload = {
      formKey: this.formKey,
      workflowKey: this.workflowKey,
      answers,
      clientRequestId: crypto.randomUUID?.() ?? `${Date.now()}-${Math.random()}`,
    };

    this.api.post<any>('/api/admission/applications', payload).subscribe({
      next: (app) => {
        this.lastSubmitFingerprint = fingerprint;
        this.lastSubmitAt = Date.now();
        this.submitting = false;
        this.submitLocked = false;
        this.statusMsg = `Application saved successfully${app?.id ? ` (${app.id})` : ''}.`;
        this.resetAnswers();
        this.pageIndex = 0;
        this.loadApplications();
        if (app?.id) {
          void this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { id: app.id },
          });
        } else {
          void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
        }
      },
      error: (err) => {
        this.submitting = false;
        this.submitLocked = false;
        this.statusMsg = '';
        this.error = err?.error?.message ?? 'Submit failed';
      },
    });
  }

  select(app: any): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { id: app.id },
    });
  }

  canRunAction(action: 'APPROVE' | 'REJECT' | 'REQUEST_INFO' | 'RESUME'): boolean {
    if (!this.selected || this.submitting || this.submitLocked) {
      return false;
    }
    if (this.selected.terminal) {
      return false;
    }
    const allowed: string[] = this.selected.allowedActions ?? [];
    if (allowed.length) {
      return allowed.includes(action);
    }
    return !!this.selected.canAct;
  }

  act(action: 'APPROVE' | 'REJECT' | 'REQUEST_INFO' | 'RESUME'): void {
    if (!this.selectedId || !this.canRunAction(action)) {
      return;
    }
    this.submitting = true;
    this.submitLocked = true;
    const labels: Record<string, string> = {
      APPROVE: 'Approving',
      REJECT: 'Rejecting',
      REQUEST_INFO: 'Requesting info',
      RESUME: 'Resuming',
    };
    this.statusMsg = `${labels[action] ?? 'Processing'}… please wait.`;
    this.error = '';
    this.api
      .post<any>(`/api/admission/applications/${this.selectedId}/actions`, {
        action,
        comment: this.actionComment || undefined,
      })
      .subscribe({
        next: (app) => {
          this.submitting = false;
          this.submitLocked = false;
          this.statusMsg = `Action ${action} completed.`;
          this.selected = app;
          this.actionComment = '';
          this.loadApplications();
        },
        error: (err) => {
          this.submitting = false;
          this.submitLocked = false;
          this.statusMsg = '';
          this.error = err?.error?.message ?? 'Action failed';
        },
      });
  }

  downloadOfferLetter(): void {
    if (!this.selectedId) {
      return;
    }
    this.api.getBlob(`/api/admission/applications/${this.selectedId}/offer-letter`).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `offer-letter-${this.selectedId}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => (this.error = err?.error?.message ?? 'Offer letter download failed'),
    });
  }

  downloadRegister(format: 'PDF' | 'EXCEL' | 'CSV' = 'PDF'): void {
    const qs = new URLSearchParams();
    if (this.listStatus) qs.set('status', this.listStatus);
    if (this.listQ) qs.set('q', this.listQ);
    qs.set('format', format);
    this.api.get<any>(`/api/admission/register?${qs.toString()}`).subscribe({
      next: (res) => {
        const b64 = String(res?.contentBase64 || '');
        if (!b64) {
          this.error = 'Register export returned empty content';
          return;
        }
        const bin = atob(b64);
        const bytes = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        const blob = new Blob([bytes], { type: res.contentType || 'application/octet-stream' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download =
          res.fileName ||
          `admission-register.${format === 'EXCEL' ? 'xlsx' : format.toLowerCase()}`;
        a.click();
        URL.revokeObjectURL(url);
        this.statusMsg = `Admission register (${format}) downloaded`;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Register export failed'),
    });
  }

  selectFormSection(id: string): void {
    this.activeFormSectionId = id;
  }

  visibleFormSections(): Array<{ id: string; title: string }> {
    return this.formSections.filter((s) => this.sectionFields(s.id).length > 0);
  }

  latestApproveDelivery(): any[] {
    const intents = this.selected?.notificationIntents ?? [];
    for (let i = intents.length - 1; i >= 0; i--) {
      if (intents[i]?.intent === 'ADMISSION_APPROVED') {
        return intents[i].delivery ?? [];
      }
    }
    return [];
  }

  answer(app: any, key: string): string {
    return this.formatAnswer(app?.answers?.[key]);
  }

  firstAnswer(app: any, ...keys: string[]): string {
    for (const key of keys) {
      const raw = app?.answers?.[key];
      if (raw === true) return 'Yes';
      if (raw === false) continue;
      if (raw != null && String(raw).trim() !== '') {
        return this.formatAnswer(raw);
      }
    }
    return '—';
  }

  private formatAnswer(v: unknown): string {
    if (v === true) return 'Yes';
    if (v === false) return 'No';
    if (v == null || String(v).trim() === '') return '—';
    return String(v);
  }

  applicantName(app: any): string {
    const name = this.firstAnswer(app, 'fullName', 'studentName');
    return name === '—' ? String(app?.id || '—') : name;
  }

  statusLabel(status: unknown, fallbackLabel?: unknown): string {
    if (fallbackLabel != null && String(fallbackLabel).trim()) {
      return String(fallbackLabel);
    }
    const s = String(status || '')
      .trim()
      .toUpperCase();
    if (s === 'APPROVED') return 'Approved';
    if (s === 'REJECTED') return 'Rejected';
    if (s === 'IN_PROGRESS') return 'Under review';
    if (s === 'INFO_REQUESTED') return 'More information needed';
    return String(status || '—');
  }

  statusClass(status: unknown): string {
    const s = String(status || '')
      .trim()
      .toUpperCase();
    if (s === 'APPROVED') return 'badge badge-ok';
    if (s === 'REJECTED') return 'badge badge-bad';
    if (s === 'IN_PROGRESS') return 'badge badge-progress';
    if (s === 'INFO_REQUESTED') return 'badge badge-warn';
    return 'badge';
  }

  formatWhen(raw: unknown): string {
    if (!raw) return '—';
    const d = new Date(String(raw));
    if (Number.isNaN(d.getTime())) return String(raw);
    return d.toLocaleString(undefined, {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  fieldInvalid(key: string): boolean {
    return !!this.fieldErrors[key];
  }

  clearFieldError(key: string): void {
    if (this.fieldErrors[key]) {
      const next = { ...this.fieldErrors };
      delete next[key];
      this.fieldErrors = next;
    }
  }

  isClassField(key: string): boolean {
    const normalized = key.replace(/[^a-z0-9]/gi, '').toLowerCase();
    return normalized === 'classapplied' || normalized === 'classsection' || normalized === 'class';
  }

  isDropdownField(key: string): boolean {
    return key === 'house';
  }

  dropdownOptions(field: FormField): string[] {
    if (field.options?.length) {
      return field.options;
    }
    if (field.key === 'house') {
      return this.houseOptions;
    }
    return [];
  }

  selectClassGrade(grade: string): void {
    this.classGrade = grade;
    this.syncClassAppliedAnswer();
  }

  selectClassSection(section: string): void {
    if (!this.classGrade) {
      return;
    }
    this.classSectionLetter = section;
    this.syncClassAppliedAnswer();
  }

  classAppliedLabel(grade = this.classGrade, section = this.classSectionLetter): string {
    if (!grade || !section) {
      return '';
    }
    return `Grade ${grade}-${section}`;
  }

  private syncClassAppliedAnswer(): void {
    const label = this.classAppliedLabel();
    for (const f of this.fields) {
      if (!this.isClassField(f.key)) continue;
      this.answers[f.key] = label;
      this.clearFieldError(f.key);
    }
    this.answers['classGrade'] = this.classGrade;
    this.answers['sectionLetter'] = this.classSectionLetter;
    this.answers['section'] = this.classSectionLetter;
    this.clearFieldError('classGrade');
    this.clearFieldError('sectionLetter');
  }

  /** Grade/section part keys are written by the class picker — hide as standalone inputs. */
  isClassPartField(key: string): boolean {
    const normalized = key.replace(/[^a-z0-9]/gi, '').toLowerCase();
    return (
      normalized === 'classgrade' ||
      normalized === 'grade' ||
      normalized === 'sectionletter' ||
      normalized === 'section' ||
      normalized === 'sectionname'
    );
  }

  private resetClassPicker(): void {
    this.classGrade = '';
    this.classSectionLetter = '';
    for (const f of this.fields) {
      if (this.isClassField(f.key)) {
        this.answers[f.key] = '';
      }
    }
  }

  private loadClassOptions(): void {
    this.api.get<any[]>('/api/academic/sections').subscribe({
      next: (sections) => {
        const grades = new Set(this.defaultGrades);
        const letters = new Set(this.defaultSections);
        for (const s of sections ?? []) {
          const label = String(s.studentLabel || '').trim();
          const parsed = this.parseClassLabel(label);
          if (parsed) {
            grades.add(parsed.grade);
            letters.add(parsed.section);
          }
          const sectionName = String(s.name || '')
            .trim()
            .toUpperCase();
          if (/^[A-Z]$/.test(sectionName)) {
            letters.add(sectionName);
          }
        }
        this.gradeChoices = [...grades].sort((a, b) => Number(a) - Number(b));
        this.sectionChoices = [...letters].sort((a, b) => a.localeCompare(b));
      },
      error: () => {
        this.gradeChoices = [...this.defaultGrades];
        this.sectionChoices = [...this.defaultSections];
      },
    });
  }

  private parseClassLabel(label: string): { grade: string; section: string } | null {
    const m = label.match(/^(?:grade\s*)?(\d{1,2})\s*[-–]?\s*([A-Za-z])$/i);
    if (!m) return null;
    const grade = String(Number(m[1]));
    if (Number(grade) < 1 || Number(grade) > 12) return null;
    return { grade, section: m[2].toUpperCase() };
  }

  currentRole(): string {
    return (this.auth.getRole() || '').toUpperCase();
  }

  private validateForm(): boolean {
    const errors: Record<string, string> = {};
    for (const f of this.fields) {
      const raw = this.answers[f.key];
      if (f.type === 'CHECKBOX') {
        if (f.mandatory && !raw) {
          errors[f.key] = `${f.label} is required`;
        }
        continue;
      }
      const value = raw == null ? '' : String(raw).trim();
      if (f.mandatory && !value) {
        errors[f.key] = `${f.label} is required`;
        continue;
      }
      if (!value) continue;
      if (f.type === 'EMAIL' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
        errors[f.key] = 'Enter a valid email address';
      }
      if (f.type === 'NUMBER') {
        const n = Number(value);
        if (Number.isNaN(n)) {
          errors[f.key] = `${f.label} must be a number`;
        } else if (f.key.toLowerCase().includes('age') && (n < 3 || n > 25)) {
          errors[f.key] = 'Age must be between 3 and 25';
        }
      }
                  if (
                    f.type === 'PHONE' ||
                    f.key === 'mobile' ||
                    f.key === 'guardianMobile' ||
                    f.key.toLowerCase().includes('mobile') ||
                    f.key.toLowerCase().includes('phone')
                  ) {
                    const digits = value.replace(/\D/g, '');
                    if (digits.length < 10) {
                      errors[f.key] = 'Enter a valid 10-digit mobile number';
                    }
                  }
                  if (f.key === 'aadhaar' || f.key === 'aadhaarNumber') {
                    const digits = value.replace(/\D/g, '');
                    if (digits && digits.length !== 12) {
                      errors[f.key] = 'Aadhaar must be exactly 12 digits';
                    }
                  }
    }
    this.fieldErrors = errors;
    return Object.keys(errors).length === 0;
  }

  private fingerprint(answers: Record<string, unknown>): string {
    const keys = ['fullName', 'mobile', 'email', 'classApplied', 'age'];
    return keys.map((k) => String(answers[k] ?? '').trim().toLowerCase()).join('|');
  }

  private resetAnswers(): void {
    const next: Record<string, unknown> = {};
    for (const f of this.fields) {
      next[f.key] = f.type === 'CHECKBOX' ? false : '';
    }
    this.answers = next;
    this.fieldErrors = {};
    this.classGrade = '';
    this.classSectionLetter = '';
  }

  private normalizeAnswers(): Record<string, unknown> {
    const out: Record<string, unknown> = {};
    for (const f of this.fields) {
      let v = this.answers[f.key];
      if (f.type === 'NUMBER' && v !== '' && v != null) {
        v = Number(v);
      }
      if (f.type === 'CHECKBOX') {
        v = !!v;
      }
      if (typeof v === 'string') {
        v = v.trim();
      }
      if ((f.key === 'aadhaar' || f.key === 'aadhaarNumber') && typeof v === 'string') {
        v = v.replace(/\D/g, '');
      }
      out[f.key] = v;
    }
    // Always persist grade/section parts even if form keys were added after answers init.
    if (this.classGrade) out['classGrade'] = this.classGrade;
    if (this.classSectionLetter) {
      out['sectionLetter'] = this.classSectionLetter;
      out['section'] = this.classSectionLetter;
    }
    return out;
  }

  private extractSections(form: any): Array<{ id: string; title: string }> {
    const sections = form?.sections ?? [];
    return sections.map((section: any, index: number) => ({
      id: String(section.id ?? `section-${index}`),
      title: String(section.title ?? `Section ${index + 1}`),
    }));
  }

  private extractFields(form: any): FormField[] {
    const fields: FormField[] = [];
    const sections = form?.sections ?? [];
    sections.forEach((section: any, index: number) => {
      const sectionId = String(section.id ?? `section-${index}`);
      for (const field of section.fields ?? []) {
        const optionsRaw = field.options ?? field.choices ?? [];
        const options = Array.isArray(optionsRaw)
          ? optionsRaw.map((o: unknown) => String(o)).filter(Boolean)
          : [];
        fields.push({
          key: field.key,
          label: field.label ?? field.key,
          type: String(field.type ?? 'TEXTBOX').toUpperCase(),
          mandatory: !!field.mandatory,
          options,
          sectionId,
        });
      }
    });
    return fields;
  }
}
