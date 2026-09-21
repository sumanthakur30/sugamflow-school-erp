import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-workflow-builder',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './workflow-builder.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class WorkflowBuilderComponent implements OnInit {
  private readonly api = inject(ApiService);

  workflows: any[] = [];
  selectedKey = '';
  draft: any = null;
  isNew = false;
  busy = false;
  status = '';
  error = '';

  readonly roles = [
    'RECEPTION',
    'PRINCIPAL',
    'ACCOUNTANT',
    'MANAGEMENT',
    'TEACHER',
    'CASHIER',
    'LIBRARIAN',
    'HR',
    'ADMIN',
  ];

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.api.get<any[]>('/api/workflows').subscribe({
      next: (w) => {
        this.workflows = w ?? [];
        if (!this.selectedKey && this.workflows.length) {
          this.select(this.workflows[0].workflowKey);
        } else if (this.selectedKey) {
          this.select(this.selectedKey);
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load workflows'),
    });
  }

  select(key: string): void {
    this.isNew = false;
    this.error = '';
    this.status = '';
    this.selectedKey = key;
    this.api.get<any>(`/api/workflows/${encodeURIComponent(key)}`).subscribe({
      next: (wf) => {
        this.draft = this.clone(wf);
        if (!Array.isArray(this.draft.steps)) this.draft.steps = [];
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load workflow'),
    });
  }

  startNew(): void {
    this.isNew = true;
    this.selectedKey = '';
    this.status = '';
    this.error = '';
    this.draft = {
      workflowKey: '',
      name: 'New workflow',
      steps: [
        {
          sequence: 1,
          name: 'Reception',
          assignRole: 'RECEPTION',
          slaHours: 24,
          autoApprove: false,
        },
      ],
      autoApproveRules: [],
      rejectRules: [],
      escalationRules: [],
      notificationRules: [],
    };
  }

  addStep(): void {
    if (!this.draft) return;
    if (!Array.isArray(this.draft.steps)) this.draft.steps = [];
    const seq = this.draft.steps.length + 1;
    this.draft.steps.push({
      sequence: seq,
      name: `Step ${seq}`,
      assignRole: 'PRINCIPAL',
      slaHours: 24,
      autoApprove: false,
    });
  }

  removeStep(i: number): void {
    this.draft?.steps?.splice(i, 1);
    this.renumber();
  }

  moveStep(i: number, dir: -1 | 1): void {
    const steps = this.draft?.steps;
    if (!steps) return;
    const to = i + dir;
    if (to < 0 || to >= steps.length) return;
    const tmp = steps[i];
    steps[i] = steps[to];
    steps[to] = tmp;
    this.renumber();
  }

  save(): void {
    if (!this.draft) return;
    const key = String(this.draft.workflowKey || '').trim();
    if (!key) {
      this.error = 'workflowKey is required';
      return;
    }
    this.renumber();
    this.busy = true;
    this.error = '';
    const body = this.clone(this.draft);
    body.workflowKey = key;
    const req = this.isNew
      ? this.api.post<any>('/api/workflows', body)
      : this.api.put<any>(`/api/workflows/${encodeURIComponent(key)}`, body);
    req.subscribe({
      next: (saved) => {
        this.busy = false;
        this.isNew = false;
        this.selectedKey = saved.workflowKey || key;
        this.draft = this.clone(saved);
        this.status = `Saved ${this.selectedKey}`;
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Save failed';
      },
    });
  }

  private renumber(): void {
    if (!this.draft?.steps) return;
    this.draft.steps.forEach((s: any, idx: number) => (s.sequence = idx + 1));
  }

  private clone<T>(v: T): T {
    return JSON.parse(JSON.stringify(v ?? null));
  }
}
