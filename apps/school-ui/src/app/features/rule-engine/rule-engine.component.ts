import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-rule-engine',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rule-engine.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class RuleEngineComponent implements OnInit {
  private readonly api = inject(ApiService);

  rules: any[] = [];
  selectedId = '';
  draft: any = null;
  isNew = false;
  busy = false;
  status = '';
  error = '';
  evalResult: any = null;
  evalContext = `{
  "attendance": { "percent": 70 },
  "fees": { "pendingDays": 100 },
  "student": { "isBirthday": true }
}`;

  readonly ops = ['EQ', 'LT', 'GT', 'LTE', 'GTE'];

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.api.get<any[]>('/api/rules').subscribe({
      next: (r) => {
        this.rules = r ?? [];
        if (!this.selectedId && this.rules.length) {
          this.select(this.rules[0].id);
        } else if (this.selectedId) {
          this.select(this.selectedId);
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load rules'),
    });
  }

  select(id: string): void {
    this.isNew = false;
    this.error = '';
    this.status = '';
    this.selectedId = id;
    this.api.get<any>(`/api/rules/${encodeURIComponent(id)}`).subscribe({
      next: (rule) => {
        this.draft = this.normalize(rule);
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
      name: 'New rule',
      enabled: true,
      when: { field: 'attendance.percent', op: 'LT', value: 75 },
      then: { action: 'BLOCK_EXAM' },
    };
  }

  save(): void {
    if (!this.draft) return;
    const id = String(this.draft.id || '').trim();
    if (!id) {
      this.error = 'Rule id is required';
      return;
    }
    if (!this.draft.when?.field || !this.draft.then?.action) {
      this.error = 'when.field and then.action are required';
      return;
    }
    this.busy = true;
    this.error = '';
    const body = this.clone(this.draft);
    body.id = id;
    // Coerce numeric-looking values for LT/GT ops
    if (['LT', 'GT', 'LTE', 'GTE'].includes(body.when?.op)) {
      const n = Number(body.when.value);
      if (!Number.isNaN(n)) body.when.value = n;
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
        this.status = `Saved ${this.selectedId}`;
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
      this.error = 'Evaluate context must be valid JSON';
      return;
    }
    this.api.post('/api/rules/evaluate', ctx).subscribe({
      next: (r) => (this.evalResult = r),
      error: (err) => (this.error = err?.error?.message ?? 'Evaluate failed'),
    });
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
