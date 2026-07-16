import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-admission',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './admission.component.html',
  styleUrls: ['../../shared/admin-page.scss', './admission.component.scss'],
})
export class AdmissionComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  error = '';
  featureEnabled = false;
  formKey = 'admission_form';
  workflowKey = 'admission';
  fields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
  answers: Record<string, unknown> = {};
  applications: any[] = [];
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
    this.api.get<any>('/api/admission/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.formKey = boot.formKey;
        this.workflowKey = boot.workflowKey;
        this.fields = this.extractFields(boot.form);
        for (const f of this.fields) {
          if (this.answers[f.key] === undefined) {
            this.answers[f.key] = f.type === 'CHECKBOX' ? true : '';
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
    this.api.getItems<any>('/api/admission/applications').subscribe({
      next: (list) => (this.applications = list),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load applications'),
    });
  }

  submit(): void {
    this.submitting = true;
    this.error = '';
    const payload = {
      formKey: this.formKey,
      workflowKey: this.workflowKey,
      answers: this.normalizeAnswers(),
    };
    this.api.post<any>('/api/admission/applications', payload).subscribe({
      next: (app) => {
        this.submitting = false;
        this.selectedId = app.id;
        this.selected = app;
        this.loadApplications();
      },
      error: (err) => {
        this.submitting = false;
        this.error = err?.error?.message ?? 'Submit failed';
      },
    });
  }

  select(app: any): void {
    this.selectedId = app.id;
    this.api.get<any>(`/api/admission/applications/${app.id}`).subscribe({
      next: (full) => (this.selected = full),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load application'),
    });
  }

  act(action: 'APPROVE' | 'REJECT' | 'REQUEST_INFO'): void {
    if (!this.selectedId) {
      return;
    }
    this.submitting = true;
    this.api
      .post<any>(`/api/admission/applications/${this.selectedId}/actions`, {
        action,
        comment: this.actionComment || undefined,
      })
      .subscribe({
        next: (app) => {
          this.submitting = false;
          this.selected = app;
          this.actionComment = '';
          this.loadApplications();
        },
        error: (err) => {
          this.submitting = false;
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

  latestApproveDelivery(): any[] {
    const intents = this.selected?.notificationIntents ?? [];
    for (let i = intents.length - 1; i >= 0; i--) {
      if (intents[i]?.intent === 'ADMISSION_APPROVED') {
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

  private extractFields(form: any): Array<{ key: string; label: string; type: string; mandatory: boolean }> {
    const fields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
    const sections = form?.sections ?? [];
    for (const section of sections) {
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
