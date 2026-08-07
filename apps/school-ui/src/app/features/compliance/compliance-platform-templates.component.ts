import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  ComplianceApiService,
  ComplianceTemplate,
  FieldMapRow,
  ValidationRuleRow,
} from './compliance-api.service';

@Component({
  selector: 'sf-compliance-platform-templates',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterLink],
  styleUrls: ['../../shared/admin-page.scss'],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h2>Platform templates</h2>
          <p>Versioned board packs and field/rule toggles (no school redeploy).</p>
        </div>
        <a routerLink="/admin/compliance" class="btn">Dashboard</a>
      </div>

      @if (error) {
        <div class="panel" style="border-color: #c45c5c"><p>{{ error }}</p></div>
      }
      @if (info) {
        <div class="panel"><p>{{ info }}</p></div>
      }

      <div class="panel" style="margin-bottom: 1rem">
        <h3 style="margin-top: 0; font-size: 1.1rem">New pack version</h3>
        <form class="grid two" [formGroup]="createForm" (ngSubmit)="create()">
          <label>
            <span class="field-title">Pack key</span>
            <input formControlName="packKey" placeholder="CBSE-2026.2" />
          </label>
          <label>
            <span class="field-title">Board</span>
            <select formControlName="boardCode">
              <option value="CBSE">CBSE</option>
              <option value="ICSE">ICSE</option>
              <option value="STATE">STATE</option>
            </select>
          </label>
          <label>
            <span class="field-title">Version</span>
            <input formControlName="versionLabel" placeholder="2026.2" />
          </label>
          <label>
            <span class="field-title">Title</span>
            <input formControlName="title" />
          </label>
          <label style="grid-column: 1 / -1">
            <span class="field-title">Description</span>
            <input formControlName="description" />
          </label>
          <div>
            <button class="btn primary" type="submit" [disabled]="createForm.invalid || busy">
              Create template
            </button>
          </div>
        </form>
      </div>

      <div class="panel" style="margin-bottom: 1rem">
        <h3 style="margin-top: 0; font-size: 1.1rem">Templates</h3>
        @if (loading) {
          <p class="muted">Loading…</p>
        } @else if (!templates.length) {
          <p class="muted">No templates.</p>
        } @else {
          <table class="data">
            <thead>
              <tr>
                <th>Pack</th>
                <th>Board</th>
                <th>Status</th>
                <th>Active</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (t of templates; track t.id) {
                <tr>
                  <td>
                    <strong>{{ t.packKey }}</strong>
                    <div class="muted">{{ t.title }}</div>
                  </td>
                  <td>{{ t.boardCode }}</td>
                  <td>{{ t.status }}</td>
                  <td>{{ t.active ? 'Yes' : 'No' }}</td>
                  <td>
                    <button class="btn" type="button" [disabled]="busy" (click)="toggleActive(t)">
                      {{ t.active ? 'Deactivate' : 'Activate' }}
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>

      <div class="panel" style="margin-bottom: 1rem">
        <div class="row" style="gap: 1rem; align-items: end; flex-wrap: wrap">
          <label>
            <span class="field-title">Edit field maps / rules for board</span>
            <select [(ngModel)]="editBoard" (ngModelChange)="loadPackConfig()">
              <option value="CBSE">CBSE</option>
              <option value="ICSE">ICSE</option>
              <option value="STATE">STATE</option>
            </select>
          </label>
          <button class="btn" type="button" [disabled]="busy" (click)="loadPackConfig()">
            Reload
          </button>
        </div>
      </div>

      <div class="grid two">
        <div class="panel">
          <h3 style="margin-top: 0; font-size: 1.1rem">Field maps ({{ editBoard }})</h3>
          @if (!fieldMaps.length) {
            <p class="muted">No field maps.</p>
          } @else {
            <table class="data">
              <thead>
                <tr>
                  <th>Entity</th>
                  <th>Field</th>
                  <th>Req</th>
                  <th>Sev</th>
                  <th>On</th>
                </tr>
              </thead>
              <tbody>
                @for (f of fieldMaps; track f.id) {
                  <tr>
                    <td>{{ f.entityType }}</td>
                    <td>
                      <div>{{ f.label }}</div>
                      <div class="muted">{{ f.fieldKey }}</div>
                    </td>
                    <td>
                      <input
                        type="checkbox"
                        [checked]="f.required"
                        (change)="setField(f, { required: $any($event.target).checked })"
                      />
                    </td>
                    <td>
                      <select
                        [ngModel]="f.severity"
                        (ngModelChange)="setField(f, { severity: $event })"
                      >
                        <option value="BLOCKER">BLOCKER</option>
                        <option value="WARN">WARN</option>
                      </select>
                    </td>
                    <td>
                      <input
                        type="checkbox"
                        [checked]="f.active"
                        (change)="setField(f, { active: $any($event.target).checked })"
                      />
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          }
        </div>

        <div class="panel">
          <h3 style="margin-top: 0; font-size: 1.1rem">Rules ({{ editBoard }})</h3>
          @if (!rules.length) {
            <p class="muted">No rules.</p>
          } @else {
            <table class="data">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Type</th>
                  <th>Sev</th>
                  <th>On</th>
                </tr>
              </thead>
              <tbody>
                @for (r of rules; track r.id) {
                  <tr>
                    <td>
                      <div>{{ r.ruleCode }}</div>
                      <div class="muted">{{ r.entityType }}</div>
                    </td>
                    <td>{{ r.ruleType }}</td>
                    <td>
                      <select
                        [ngModel]="r.severity"
                        (ngModelChange)="setRule(r, { severity: $event })"
                      >
                        <option value="BLOCKER">BLOCKER</option>
                        <option value="WARN">WARN</option>
                      </select>
                    </td>
                    <td>
                      <input
                        type="checkbox"
                        [checked]="r.active"
                        (change)="setRule(r, { active: $any($event.target).checked })"
                      />
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          }
        </div>
      </div>
    </section>
  `,
})
export class CompliancePlatformTemplatesComponent implements OnInit {
  private readonly api = inject(ComplianceApiService);
  private readonly fb = inject(FormBuilder);

  createForm = this.fb.nonNullable.group({
    packKey: ['', Validators.required],
    boardCode: ['CBSE', Validators.required],
    versionLabel: ['', Validators.required],
    title: ['', Validators.required],
    description: [''],
  });

  loading = true;
  busy = false;
  error = '';
  info = '';
  templates: ComplianceTemplate[] = [];
  editBoard = 'CBSE';
  fieldMaps: FieldMapRow[] = [];
  rules: ValidationRuleRow[] = [];

  ngOnInit(): void {
    this.refresh();
    this.loadPackConfig();
  }

  refresh(): void {
    this.loading = true;
    this.error = '';
    this.api.listPlatformTemplates().subscribe({
      next: (rows) => {
        this.templates = rows || [];
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error =
          err?.error?.message || err?.message || 'Unable to load platform templates (admin role required).';
      },
    });
  }

  create(): void {
    if (this.createForm.invalid) return;
    this.busy = true;
    this.info = '';
    this.error = '';
    this.api.createPlatformTemplate(this.createForm.getRawValue()).subscribe({
      next: () => {
        this.busy = false;
        this.info = 'Template created.';
        this.createForm.patchValue({
          packKey: '',
          versionLabel: '',
          title: '',
          description: '',
        });
        this.refresh();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message || 'Create failed';
      },
    });
  }

  toggleActive(t: ComplianceTemplate): void {
    this.busy = true;
    this.api.updatePlatformTemplate(t.id, { active: !t.active }).subscribe({
      next: () => {
        this.busy = false;
        this.refresh();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message || 'Update failed';
      },
    });
  }

  loadPackConfig(): void {
    this.api.listFieldMaps(this.editBoard).subscribe({
      next: (rows) => (this.fieldMaps = rows || []),
      error: (err) => {
        this.fieldMaps = [];
        this.error = err?.error?.message || 'Unable to load field maps';
      },
    });
    this.api.listRules(this.editBoard).subscribe({
      next: (rows) => (this.rules = rows || []),
      error: (err) => {
        this.rules = [];
        this.error = err?.error?.message || 'Unable to load rules';
      },
    });
  }

  setField(f: FieldMapRow, patch: Partial<FieldMapRow>): void {
    this.busy = true;
    this.api.updateFieldMap(f.id, patch).subscribe({
      next: (updated) => {
        this.busy = false;
        this.fieldMaps = this.fieldMaps.map((row) => (row.id === updated.id ? updated : row));
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message || 'Field map update failed';
        this.loadPackConfig();
      },
    });
  }

  setRule(r: ValidationRuleRow, patch: Partial<ValidationRuleRow>): void {
    this.busy = true;
    this.api.updateRule(r.id, patch).subscribe({
      next: (updated) => {
        this.busy = false;
        this.rules = this.rules.map((row) => (row.id === updated.id ? updated : row));
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message || 'Rule update failed';
        this.loadPackConfig();
      },
    });
  }
}
