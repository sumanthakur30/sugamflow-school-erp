import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  ComplianceApiService,
  InfrastructureAsset,
  InfrastructureSummary,
} from './compliance-api.service';

@Component({
  selector: 'sf-compliance-infrastructure',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  styleUrls: ['../../shared/admin-page.scss'],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h2>Infrastructure</h2>
          <p>Classrooms, labs, toilets, safety and other CBSE facility inventory.</p>
        </div>
        <a routerLink="/admin/compliance" class="btn">Dashboard</a>
      </div>

      @if (error) {
        <div class="panel" style="border-color: #c45c5c"><p>{{ error }}</p></div>
      }

      @if (summary) {
        <div class="grid two" style="margin-bottom: 1rem">
          <div class="panel">
            <div class="field-title">Inventory</div>
            <p>Assets: <strong>{{ summary.totalAssets }}</strong></p>
            <p>Total quantity: <strong>{{ summary.totalQuantity }}</strong></p>
          </div>
          <div class="panel">
            <div class="field-title">Missing recommended</div>
            @if (!summary.missingRecommendedCategories?.length) {
              <p class="muted">All recommended categories present.</p>
            } @else {
              <p>{{ summary.missingRecommendedCategories.join(', ') }}</p>
            }
          </div>
        </div>
      }

      <div class="panel" style="margin-bottom: 1rem">
        <h3 style="margin-top: 0; font-size: 1.1rem">{{ editingId ? 'Edit asset' : 'Add asset' }}</h3>
        <form class="grid" [formGroup]="form" (ngSubmit)="save()">
          <div class="grid two">
            <label>
              <span class="field-title">Category</span>
              <select formControlName="category">
                @for (c of categories; track c) {
                  <option [value]="c">{{ c }}</option>
                }
              </select>
            </label>
            <label>
              <span class="field-title">Name</span>
              <input formControlName="name" maxlength="255" />
            </label>
          </div>
          <div class="grid two">
            <label>
              <span class="field-title">Quantity</span>
              <input type="number" min="0" formControlName="quantity" />
            </label>
            <label>
              <span class="field-title">Capacity</span>
              <input type="number" min="0" formControlName="capacity" />
            </label>
          </div>
          <div class="grid two">
            <label>
              <span class="field-title">Condition</span>
              <select formControlName="conditionCode">
                <option value="GOOD">GOOD</option>
                <option value="FAIR">FAIR</option>
                <option value="POOR">POOR</option>
                <option value="UNDER_REPAIR">UNDER_REPAIR</option>
              </select>
            </label>
            <label>
              <span class="field-title">Location</span>
              <input formControlName="locationNote" />
            </label>
          </div>
          <label>
            <span class="field-title">Notes</span>
            <textarea formControlName="notes" rows="2"></textarea>
          </label>
          <div style="display: flex; gap: 0.5rem">
            <button class="btn primary" type="submit" [disabled]="form.invalid || saving">
              {{ saving ? 'Saving…' : editingId ? 'Update' : 'Add' }}
            </button>
            @if (editingId) {
              <button class="btn" type="button" (click)="resetForm()">Cancel</button>
            }
          </div>
        </form>
      </div>

      <div class="panel">
        <h3 style="margin-top: 0; font-size: 1.1rem">Assets</h3>
        @if (loading) {
          <p class="muted">Loading…</p>
        } @else if (!assets.length) {
          <p class="muted">No infrastructure assets yet.</p>
        } @else {
          <table class="data">
            <thead>
              <tr>
                <th>Category</th>
                <th>Name</th>
                <th>Qty</th>
                <th>Condition</th>
                <th>Location</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (a of assets; track a.id) {
                <tr>
                  <td>{{ a.category }}</td>
                  <td>{{ a.name }}</td>
                  <td>{{ a.quantity }}</td>
                  <td>{{ a.conditionCode }}</td>
                  <td>{{ a.locationNote || '—' }}</td>
                  <td style="white-space: nowrap">
                    <button class="btn" type="button" (click)="edit(a)">Edit</button>
                    <button class="btn" type="button" (click)="remove(a)">Remove</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>
    </section>
  `,
})
export class ComplianceInfrastructureComponent implements OnInit {
  private readonly api = inject(ComplianceApiService);
  private readonly fb = inject(FormBuilder);

  readonly categories = [
    'CLASSROOM',
    'SCIENCE_LAB',
    'COMPUTER_LAB',
    'LIBRARY',
    'TOILET_BOYS',
    'TOILET_GIRLS',
    'DRINKING_WATER',
    'CCTV',
    'FIRE_SAFETY',
    'PLAYGROUND',
    'MEDICAL_ROOM',
    'BUS',
    'HOSTEL',
    'OTHER',
  ];

  form = this.fb.nonNullable.group({
    category: ['CLASSROOM', Validators.required],
    name: ['', Validators.required],
    quantity: [1, Validators.required],
    capacity: [null as number | null],
    conditionCode: ['GOOD', Validators.required],
    locationNote: [''],
    notes: [''],
  });

  loading = true;
  saving = false;
  error = '';
  assets: InfrastructureAsset[] = [];
  summary: InfrastructureSummary | null = null;
  editingId: number | null = null;

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading = true;
    this.error = '';
    this.api.infrastructureSummary().subscribe({
      next: (s) => (this.summary = s),
      error: () => {},
    });
    this.api.listInfrastructure().subscribe({
      next: (rows) => {
        this.assets = rows || [];
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message || err?.message || 'Unable to load infrastructure.';
      },
    });
  }

  edit(a: InfrastructureAsset): void {
    this.editingId = a.id ?? null;
    this.form.patchValue({
      category: a.category,
      name: a.name,
      quantity: a.quantity,
      capacity: a.capacity ?? null,
      conditionCode: a.conditionCode || 'GOOD',
      locationNote: a.locationNote || '',
      notes: a.notes || '',
    });
  }

  resetForm(): void {
    this.editingId = null;
    this.form.reset({
      category: 'CLASSROOM',
      name: '',
      quantity: 1,
      capacity: null,
      conditionCode: 'GOOD',
      locationNote: '',
      notes: '',
    });
  }

  save(): void {
    if (this.form.invalid) return;
    this.saving = true;
    const v = this.form.getRawValue();
    const body: InfrastructureAsset = {
      category: v.category,
      name: v.name,
      quantity: Number(v.quantity) || 0,
      capacity: v.capacity == null || v.capacity === ('' as unknown) ? undefined : Number(v.capacity),
      conditionCode: v.conditionCode,
      locationNote: v.locationNote || undefined,
      notes: v.notes || undefined,
    };
    this.api.saveInfrastructure(body, this.editingId ?? undefined).subscribe({
      next: () => {
        this.saving = false;
        this.resetForm();
        this.refresh();
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message || err?.message || 'Save failed.';
      },
    });
  }

  remove(a: InfrastructureAsset): void {
    if (!a.id || !confirm(`Remove ${a.name}?`)) return;
    this.api.deleteInfrastructure(a.id).subscribe({
      next: () => this.refresh(),
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Delete failed.';
      },
    });
  }
}
