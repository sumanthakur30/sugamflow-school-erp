import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-fee',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './fee.component.html',
  styleUrls: ['../../shared/admin-page.scss', './fee.component.scss'],
})
export class FeeComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  error = '';
  featureEnabled = false;
  formKey = 'fee_collection';
  workflowKey = 'fee';
  fields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
  answers: Record<string, unknown> = {};
  collections: any[] = [];
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
    this.api.get<any>('/api/fee/bootstrap').subscribe({
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
        this.loadCollections();
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? err?.message ?? 'Fee bootstrap failed';
        this.featureEnabled = false;
      },
    });
  }

  loadCollections(): void {
    this.api.getItems<any>('/api/fee/collections').subscribe({
      next: (list) => (this.collections = list),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load collections'),
    });
  }

  submit(): void {
    this.submitting = true;
    this.error = '';
    this.api
      .post<any>('/api/fee/collections', {
        formKey: this.formKey,
        workflowKey: this.workflowKey,
        answers: this.normalizeAnswers(),
      })
      .subscribe({
        next: (row) => {
          this.submitting = false;
          this.selectedId = row.id;
          this.selected = row;
          this.loadCollections();
        },
        error: (err) => {
          this.submitting = false;
          this.error = err?.error?.message ?? 'Submit failed';
        },
      });
  }

  select(row: any): void {
    this.selectedId = row.id;
    this.api.get<any>(`/api/fee/collections/${row.id}`).subscribe({
      next: (full) => (this.selected = full),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load collection'),
    });
  }

  act(action: 'APPROVE' | 'REJECT' | 'REQUEST_INFO'): void {
    if (!this.selectedId) {
      return;
    }
    this.submitting = true;
    this.api
      .post<any>(`/api/fee/collections/${this.selectedId}/actions`, {
        action,
        comment: this.actionComment || undefined,
      })
      .subscribe({
        next: (row) => {
          this.submitting = false;
          this.selected = row;
          this.actionComment = '';
          this.loadCollections();
        },
        error: (err) => {
          this.submitting = false;
          this.error = err?.error?.message ?? 'Action failed';
        },
      });
  }

  downloadReceipt(): void {
    if (!this.selectedId) {
      return;
    }
    this.api.getBlob(`/api/fee/collections/${this.selectedId}/receipt`).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `fee-receipt-${this.selectedId}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => (this.error = err?.error?.message ?? 'Receipt download failed'),
    });
  }

  latestApproveDelivery(): any[] {
    const intents = this.selected?.notificationIntents ?? [];
    for (let i = intents.length - 1; i >= 0; i--) {
      if (intents[i]?.intent === 'FEE_APPROVED') {
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
