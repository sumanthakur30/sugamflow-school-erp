import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-students',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './students.component.html',
  styleUrls: ['../../shared/admin-page.scss', './students.component.scss'],
})
export class StudentsComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  error = '';
  featureEnabled = false;
  formKey = 'student_master';
  parentFormKey = 'parent_master';
  guardiansAnswerKey = 'guardians';
  parentFields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
  students: any[] = [];
  selected: any = null;
  guardianDraft: Record<string, unknown> = {};
  savingGuardians = false;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/student/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.formKey = boot.formKey;
        this.parentFormKey = boot.parentFormKey ?? 'parent_master';
        this.guardiansAnswerKey = boot.guardiansAnswerKey ?? 'guardians';
        this.parentFields = this.extractFields(boot.parentForm);
        this.resetGuardianDraft();
        this.loading = false;
        this.loadStudents();
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? err?.message ?? 'Student bootstrap failed';
        this.featureEnabled = false;
      },
    });
  }

  loadStudents(): void {
    this.api.getItems<any>('/api/student/students').subscribe({
      next: (list) => (this.students = list),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load students'),
    });
  }

  select(row: any): void {
    this.api.get<any>(`/api/student/students/${row.id}`).subscribe({
      next: (full) => {
        this.selected = full;
        this.resetGuardianDraft();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load student'),
    });
  }

  guardians(): any[] {
    const fromDto = this.selected?.guardians;
    if (Array.isArray(fromDto)) {
      return fromDto;
    }
    const fromAnswers = this.selected?.answers?.[this.guardiansAnswerKey];
    return Array.isArray(fromAnswers) ? fromAnswers : [];
  }

  addGuardian(): void {
    if (!this.selected?.id) {
      return;
    }
    const next = [...this.guardians(), this.normalizeGuardian(this.guardianDraft)];
    this.saveGuardians(next, true);
  }

  removeGuardian(index: number): void {
    if (!this.selected?.id) {
      return;
    }
    const next = this.guardians().filter((_: any, i: number) => i !== index);
    this.saveGuardians(next, false);
  }

  private saveGuardians(guardians: any[], clearDraft: boolean): void {
    this.savingGuardians = true;
    this.error = '';
    this.api
      .put<any>(`/api/student/students/${this.selected.id}/guardians`, { guardians })
      .subscribe({
        next: (full) => {
          this.savingGuardians = false;
          this.selected = full;
          if (clearDraft) {
            this.resetGuardianDraft();
          }
          this.loadStudents();
        },
        error: (err) => {
          this.savingGuardians = false;
          this.error = err?.error?.message ?? 'Failed to save guardians';
        },
      });
  }

  private resetGuardianDraft(): void {
    const draft: Record<string, unknown> = {};
    for (const f of this.parentFields) {
      draft[f.key] = f.type === 'CHECKBOX' ? false : '';
    }
    if (draft['isPrimary'] === false && this.guardians().length === 0) {
      draft['isPrimary'] = true;
    }
    this.guardianDraft = draft;
  }

  private normalizeGuardian(raw: Record<string, unknown>): Record<string, unknown> {
    const out: Record<string, unknown> = {};
    for (const f of this.parentFields) {
      let v = raw[f.key];
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
