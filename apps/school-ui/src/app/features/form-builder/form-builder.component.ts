import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-form-builder',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './form-builder.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class FormBuilderComponent implements OnInit {
  private readonly api = inject(ApiService);

  forms: any[] = [];
  fieldTypes: string[] = [];
  selectedKey = '';
  draft: any = null;
  isNew = false;
  busy = false;
  status = '';
  error = '';

  ngOnInit(): void {
    this.reload();
    this.api.get<string[]>('/api/forms/field-types').subscribe({
      next: (t) => (this.fieldTypes = t ?? []),
      error: () => (this.fieldTypes = ['TEXTBOX', 'NUMBER', 'CHECKBOX', 'EMAIL', 'DROPDOWN']),
    });
  }

  reload(): void {
    this.api.get<any[]>('/api/forms').subscribe({
      next: (f) => {
        this.forms = f ?? [];
        if (!this.selectedKey && this.forms.length) {
          this.select(this.forms[0].formKey);
        } else if (this.selectedKey) {
          const still = this.forms.find((x) => x.formKey === this.selectedKey);
          if (still) {
            this.select(this.selectedKey);
          }
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load forms'),
    });
  }

  select(formKey: string): void {
    this.isNew = false;
    this.error = '';
    this.status = '';
    this.selectedKey = formKey;
    this.api.get<any>(`/api/forms/${encodeURIComponent(formKey)}`).subscribe({
      next: (form) => (this.draft = this.clone(form)),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load form'),
    });
  }

  startNew(): void {
    this.isNew = true;
    this.selectedKey = '';
    this.status = '';
    this.error = '';
    this.draft = {
      formKey: '',
      title: 'New form',
      sections: [
        {
          id: 'main',
          title: 'Main',
          repeatable: false,
          fields: [
            { key: 'field1', label: 'Field 1', type: 'TEXTBOX', mandatory: false, showInReports: true, showOnIdCard: false },
          ],
        },
      ],
      validationRules: [],
      conditionalVisibility: [],
    };
  }

  addSection(): void {
    if (!this.draft) return;
    if (!Array.isArray(this.draft.sections)) this.draft.sections = [];
    const n = this.draft.sections.length + 1;
    this.draft.sections.push({
      id: `section_${n}`,
      title: `Section ${n}`,
      repeatable: false,
      fields: [],
    });
  }

  removeSection(si: number): void {
    this.draft?.sections?.splice(si, 1);
  }

  addField(si: number): void {
    const section = this.draft?.sections?.[si];
    if (!section) return;
    if (!Array.isArray(section.fields)) section.fields = [];
    const n = section.fields.length + 1;
    section.fields.push({
      key: `field_${n}`,
      label: `Field ${n}`,
      type: this.fieldTypes[0] || 'TEXTBOX',
      mandatory: false,
      showInReports: true,
      showOnIdCard: false,
    });
  }

  removeField(si: number, fi: number): void {
    this.draft?.sections?.[si]?.fields?.splice(fi, 1);
  }

  moveField(si: number, fi: number, dir: -1 | 1): void {
    const fields = this.draft?.sections?.[si]?.fields;
    if (!fields) return;
    const to = fi + dir;
    if (to < 0 || to >= fields.length) return;
    const tmp = fields[fi];
    fields[fi] = fields[to];
    fields[to] = tmp;
  }

  save(): void {
    if (!this.draft) return;
    const key = String(this.draft.formKey || '').trim();
    if (!key) {
      this.error = 'formKey is required';
      return;
    }
    if (!this.draft.title?.trim()) {
      this.error = 'title is required';
      return;
    }
    this.busy = true;
    this.error = '';
    const body = this.clone(this.draft);
    body.formKey = key;
    const req = this.isNew
      ? this.api.post<any>('/api/forms', body)
      : this.api.put<any>(`/api/forms/${encodeURIComponent(key)}`, body);
    req.subscribe({
      next: (saved) => {
        this.busy = false;
        this.isNew = false;
        this.selectedKey = saved.formKey || key;
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

  private clone<T>(v: T): T {
    return JSON.parse(JSON.stringify(v ?? null));
  }
}
