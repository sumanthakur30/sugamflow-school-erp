import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

interface FieldOption {
  path: string;
  label: string;
  module: string;
  type: 'number' | 'boolean' | 'text';
}

interface ActionOption {
  code: string;
  label: string;
  kind: 'block' | 'notify' | 'other';
}

const CUSTOM = '__custom__';

@Component({
  selector: 'sf-rule-engine',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rule-engine.component.html',
  styleUrls: ['../../shared/admin-page.scss', './rule-engine.component.scss'],
})
export class RuleEngineComponent implements OnInit {
  private readonly api = inject(ApiService);

  rules: any[] = [];
  ruleFilter = '';
  selectedId = '';
  draft: any = null;
  isNew = false;
  busy = false;
  status = '';
  error = '';
  testOpen = false;
  evalResult: any = null;
  evalContext = `{
  "attendance": { "percent": 70 },
  "fees": { "pendingDays": 100 },
  "student": { "isBirthday": true }
}`;

  /** Fields the rule engine receives from each module at runtime. */
  readonly fieldOptions: FieldOption[] = [
    { path: 'attendance.percent', label: 'Attendance percentage', module: 'Attendance', type: 'number' },
    { path: 'attendance.attendancePercent', label: 'Attendance % while marking', module: 'Attendance', type: 'number' },
    { path: 'application.age', label: 'Applicant age (years)', module: 'Admission', type: 'number' },
    { path: 'application.documentsComplete', label: 'Application documents complete', module: 'Admission', type: 'boolean' },
    { path: 'fees.pendingDays', label: 'Fee pending days', module: 'Fees', type: 'number' },
    { path: 'fees.pendingAmount', label: 'Fee pending amount', module: 'Fees', type: 'number' },
    { path: 'payment.amount', label: 'Payment amount', module: 'Fees', type: 'number' },
    { path: 'payment.pendingDays', label: 'Payment pending days', module: 'Fees', type: 'number' },
    { path: 'exam.marksObtained', label: 'Exam marks obtained', module: 'Exams', type: 'number' },
    { path: 'student.isBirthday', label: "Student's birthday today", module: 'Students', type: 'boolean' },
    { path: 'library.bookId', label: 'Library book ID', module: 'Library', type: 'text' },
    { path: 'library.dueDays', label: 'Library due days', module: 'Library', type: 'number' },
    { path: 'library.outstandingBooks', label: 'Library outstanding books', module: 'Library', type: 'number' },
    { path: 'hostel.bedNo', label: 'Hostel bed number', module: 'Hostel', type: 'number' },
    { path: 'hostel.pendingFee', label: 'Hostel pending fee', module: 'Hostel', type: 'number' },
    { path: 'transport.distanceKm', label: 'Transport distance (km)', module: 'Transport', type: 'number' },
    { path: 'payroll.netPay', label: 'Payroll net pay', module: 'Payroll', type: 'number' },
  ];

  /** Actions the modules understand when a rule matches. */
  readonly actionOptions: ActionOption[] = [
    { code: 'BLOCK_ADMISSION', label: 'Block the admission', kind: 'block' },
    { code: 'BLOCK_ATTENDANCE', label: 'Block attendance marking', kind: 'block' },
    { code: 'BLOCK_EXAM', label: 'Block the exam entry', kind: 'block' },
    { code: 'BLOCK_FEE', label: 'Block the fee collection', kind: 'block' },
    { code: 'BLOCK_HOSTEL', label: 'Block the hostel allocation', kind: 'block' },
    { code: 'BLOCK_LIBRARY', label: 'Block the library issue', kind: 'block' },
    { code: 'BLOCK_PAYROLL', label: 'Block the payroll run', kind: 'block' },
    { code: 'BLOCK_TC', label: 'Block the transfer certificate', kind: 'block' },
    { code: 'BLOCK_TRANSPORT', label: 'Block the transport route', kind: 'block' },
    { code: 'NOTIFY_ADMISSION', label: 'Notify the admission desk', kind: 'notify' },
    { code: 'NOTIFY_ATTENDANCE', label: 'Notify the attendance desk', kind: 'notify' },
    { code: 'NOTIFY_EXAM', label: 'Notify the exam desk', kind: 'notify' },
    { code: 'NOTIFY_FEE', label: 'Notify the fee desk', kind: 'notify' },
    { code: 'NOTIFY_HOSTEL', label: 'Notify the hostel desk', kind: 'notify' },
    { code: 'NOTIFY_LIBRARY', label: 'Notify the library desk', kind: 'notify' },
    { code: 'NOTIFY_PAYROLL', label: 'Notify the payroll desk', kind: 'notify' },
    { code: 'NOTIFY_TRANSPORT', label: 'Notify the transport desk', kind: 'notify' },
    { code: 'DISABLE_ID_CARD', label: 'Disable the ID card', kind: 'other' },
    { code: 'SEND_WHATSAPP', label: 'Send a WhatsApp message', kind: 'other' },
  ];

  readonly ops = [
    { code: 'EQ', label: 'is equal to' },
    { code: 'LT', label: 'is less than' },
    { code: 'GT', label: 'is greater than' },
    { code: 'LTE', label: 'is at most' },
    { code: 'GTE', label: 'is at least' },
  ];

  readonly customValue = CUSTOM;
  whenFieldChoice = '';
  actionChoice = '';

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.api.get<any[]>('/api/rules').subscribe({
      next: (r) => {
        this.rules = r ?? [];
        if (!this.selectedId && !this.draft && this.rules.length) {
          this.select(this.rules[0].id);
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load rules'),
    });
  }

  get moduleGroups(): Array<{ module: string; rules: any[] }> {
    const filter = this.ruleFilter.trim().toLowerCase();
    const groups = new Map<string, any[]>();
    for (const rule of this.rules) {
      if (filter) {
        const hay = `${rule.name || ''} ${rule.id || ''} ${this.ruleSentence(rule)}`.toLowerCase();
        if (!hay.includes(filter)) continue;
      }
      const moduleName = this.moduleOf(rule);
      if (!groups.has(moduleName)) groups.set(moduleName, []);
      groups.get(moduleName)!.push(rule);
    }
    return [...groups.entries()]
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([module, rules]) => ({ module, rules }));
  }

  get enabledCount(): number {
    return this.rules.filter((rule) => rule.enabled !== false).length;
  }

  moduleOf(rule: any): string {
    const path = String(rule?.when?.field || '');
    const known = this.fieldOptions.find((f) => f.path === path);
    if (known) return known.module;
    const prefix = path.split('.')[0] || '';
    return prefix ? prefix.charAt(0).toUpperCase() + prefix.slice(1) : 'Other';
  }

  fieldLabel(path: string): string {
    return this.fieldOptions.find((f) => f.path === path)?.label || path || 'a value';
  }

  actionLabel(code: string): string {
    return this.actionOptions.find((a) => a.code === code)?.label || code || 'do nothing';
  }

  actionKind(code: string): string {
    return this.actionOptions.find((a) => a.code === code)?.kind || 'other';
  }

  opLabel(code: string): string {
    return this.ops.find((o) => o.code === code)?.label || code;
  }

  valueLabel(value: unknown): string {
    if (value === true || value === 'true') return 'Yes';
    if (value === false || value === 'false') return 'No';
    if (value === '' || value == null) return '(blank)';
    return String(value);
  }

  ruleSentence(rule: any): string {
    const when = rule?.when || {};
    const then = rule?.then || {};
    return `If ${this.fieldLabel(when.field)} ${this.opLabel(when.op)} ${this.valueLabel(when.value)}, then ${this.actionLabel(then.action).toLowerCase()}.`;
  }

  selectedFieldType(): 'number' | 'boolean' | 'text' {
    const path = String(this.draft?.when?.field || '');
    return this.fieldOptions.find((f) => f.path === path)?.type ?? 'text';
  }

  select(id: string): void {
    this.isNew = false;
    this.error = '';
    this.status = '';
    this.selectedId = id;
    this.api.get<any>(`/api/rules/${encodeURIComponent(id)}`).subscribe({
      next: (rule) => {
        this.draft = this.normalize(rule);
        this.syncChoices();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load rule'),
    });
  }

  startNew(): void {
    this.isNew = true;
    this.selectedId = '';
    this.status = '';
    this.error = '';
    this.draft = {
      id: '',
      name: '',
      enabled: true,
      when: { field: 'attendance.percent', op: 'LT', value: 75 },
      then: { action: 'BLOCK_EXAM' },
    };
    this.syncChoices();
  }

  onWhenFieldChoice(): void {
    if (this.whenFieldChoice === CUSTOM) {
      this.draft.when.field = '';
      return;
    }
    this.draft.when.field = this.whenFieldChoice;
    const type = this.selectedFieldType();
    if (type === 'boolean') {
      this.draft.when.op = 'EQ';
      if (this.draft.when.value !== 'true' && this.draft.when.value !== 'false') {
        this.draft.when.value = 'true';
      }
    }
  }

  onActionChoice(): void {
    if (this.actionChoice === CUSTOM) {
      this.draft.then.action = '';
      return;
    }
    this.draft.then.action = this.actionChoice;
  }

  get isCustomField(): boolean {
    return this.whenFieldChoice === CUSTOM;
  }

  get isCustomAction(): boolean {
    return this.actionChoice === CUSTOM;
  }

  onNameChange(): void {
    if (this.isNew) {
      this.draft.id = this.slugify(this.draft.name);
    }
  }

  toggleRule(rule: any, event: Event): void {
    event.stopPropagation();
    const payload = this.clone(rule);
    payload.enabled = rule.enabled === false;
    this.api.put<any>(`/api/rules/${encodeURIComponent(rule.id)}`, payload).subscribe({
      next: () => {
        rule.enabled = payload.enabled;
        if (this.draft?.id === rule.id) this.draft.enabled = payload.enabled;
        this.status = `${rule.name || rule.id} turned ${payload.enabled ? 'on' : 'off'}`;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not update rule'),
    });
  }

  save(): void {
    if (!this.draft) return;
    const name = String(this.draft.name || '').trim();
    if (!name) {
      this.error = 'Give the rule a name so others can understand it';
      return;
    }
    if (this.isNew && !String(this.draft.id || '').trim()) {
      this.draft.id = this.slugify(name);
    }
    const id = String(this.draft.id || '').trim();
    if (!id) {
      this.error = 'Rule id is required';
      return;
    }
    if (!String(this.draft.when?.field || '').trim()) {
      this.error = 'Choose which value the rule should check';
      return;
    }
    if (!String(this.draft.then?.action || '').trim()) {
      this.error = 'Choose what should happen when the rule matches';
      return;
    }
    this.busy = true;
    this.error = '';
    const body = this.clone(this.draft);
    body.id = id;
    body.name = name;
    if (['LT', 'GT', 'LTE', 'GTE'].includes(body.when?.op)) {
      const n = Number(body.when.value);
      if (!Number.isNaN(n)) body.when.value = n;
    } else if (body.when?.op === 'EQ') {
      if (body.when.value === 'true') body.when.value = true;
      else if (body.when.value === 'false') body.when.value = false;
      else {
        const n = Number(body.when.value);
        if (body.when.value !== '' && !Number.isNaN(n)) body.when.value = n;
      }
    }
    const req = this.isNew
      ? this.api.post<any>('/api/rules', body)
      : this.api.put<any>(`/api/rules/${encodeURIComponent(id)}`, body);
    req.subscribe({
      next: (saved) => {
        this.busy = false;
        this.isNew = false;
        this.selectedId = saved.id || id;
        this.draft = this.normalize(saved);
        this.syncChoices();
        this.status = `Rule "${this.draft.name || this.selectedId}" saved`;
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Save failed';
      },
    });
  }

  evaluate(): void {
    this.error = '';
    let ctx: any;
    try {
      ctx = JSON.parse(this.evalContext);
    } catch {
      this.error = 'Test data must be valid JSON';
      return;
    }
    this.api.post('/api/rules/evaluate', ctx).subscribe({
      next: (r) => (this.evalResult = r),
      error: (err) => (this.error = err?.error?.message ?? 'Evaluate failed'),
    });
  }

  matchedActions(): string[] {
    return this.evalResult?.matchedActions ?? [];
  }

  private syncChoices(): void {
    const field = String(this.draft?.when?.field || '');
    this.whenFieldChoice = this.fieldOptions.some((f) => f.path === field) ? field : CUSTOM;
    const action = String(this.draft?.then?.action || '');
    this.actionChoice = this.actionOptions.some((a) => a.code === action) ? action : CUSTOM;
    // Fresh drafts use catalog defaults, never the custom mode.
    if (!field && this.isNew) this.whenFieldChoice = 'attendance.percent';
    if (!action && this.isNew) this.actionChoice = 'BLOCK_EXAM';
  }

  private slugify(raw: string): string {
    return String(raw || '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '')
      .slice(0, 60);
  }

  private normalize(rule: any): any {
    const d = this.clone(rule) || {};
    if (!d.when) d.when = { field: '', op: 'EQ', value: '' };
    if (!d.then) d.then = { action: '' };
    if (d.enabled == null) d.enabled = true;
    return d;
  }

  private clone<T>(v: T): T {
    return JSON.parse(JSON.stringify(v ?? null));
  }
}
