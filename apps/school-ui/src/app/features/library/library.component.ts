import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-library',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './library.component.html',
  styleUrls: ['../../shared/admin-page.scss', './library.component.scss'],
})
export class LibraryComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  error = '';
  featureEnabled = false;
  formKey = 'library_issue';
  workflowKey = 'library';
  fields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
  answers: Record<string, unknown> = {};
  records: any[] = [];
  selectedId: string | null = null;
  selected: any = null;
  actionComment = '';
  submitting = false;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/library/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.formKey = boot.formKey;
        this.workflowKey = boot.workflowKey;
        this.fields = this.extractFields(boot.form);
        for (const f of this.fields) {
          if (this.answers[f.key] === undefined) {
            this.answers[f.key] = f.type === 'CHECKBOX' ? false : f.type === 'NUMBER' ? 0 : '';
          }
        }
        this.loading = false;
        this.loadRecords();
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? err?.message ?? 'Library bootstrap failed';
        this.featureEnabled = false;
      },
    });
  }

  loadRecords(): void {
    this.api.getItems<any>('/api/library/records').subscribe({
      next: (list) => (this.records = list),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load records'),
    });
  }

  submit(): void {
    this.submitting = true;
    this.error = '';
    this.api
      .post<any>('/api/library/records', {
        formKey: this.formKey,
        workflowKey: this.workflowKey,
        answers: this.normalizeAnswers(),
      })
      .subscribe({
        next: (row) => {
          this.submitting = false;
          this.selectedId = row.id;
          this.selected = row;
          this.loadRecords();
        },
        error: (err) => {
          this.submitting = false;
          this.error = err?.error?.message ?? 'Submit failed';
        },
      });
  }

  select(row: any): void {
    this.selectedId = row.id;
    this.api.get<any>(`/api/library/records/${row.id}`).subscribe({
      next: (full) => (this.selected = full),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load record'),
    });
  }

  act(action: 'APPROVE' | 'REJECT' | 'REQUEST_INFO'): void {
    if (!this.selectedId) {
      return;
    }
    this.submitting = true;
    this.api
      .post<any>(`/api/library/records/${this.selectedId}/actions`, {
        action,
        comment: this.actionComment || undefined,
      })
      .subscribe({
        next: (row) => {
          this.submitting = false;
          this.selected = row;
          this.actionComment = '';
          this.loadRecords();
        },
        error: (err) => {
          this.submitting = false;
          this.error = err?.error?.message ?? 'Action failed';
        },
      });
  }

  latestApproveDelivery(): any[] {
    const intents = this.selected?.notificationIntents ?? [];
    for (let i = intents.length - 1; i >= 0; i--) {
      if (intents[i]?.intent === 'LIBRARY_APPROVED') {
        return intents[i].delivery ?? [];
      }
    }
    return [];
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
      out[f.key] = v;
    }
    return out;
  }

  private extractFields(
    form: any,
  ): Array<{ key: string; label: string; type: string; mandatory: boolean }> {
    const fields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
    for (const section of form?.sections ?? []) {
      for (const field of section.fields ?? []) {
        fields.push({
          key: field.key,
          label: field.label ?? field.key,
          type: field.type ?? 'TEXTBOX',
          mandatory: !!field.mandatory,
        });
      }
    }
    return fields;
  }
}
