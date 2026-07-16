import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

type SettingKind = 'boolean' | 'number' | 'string' | 'string[]' | 'json';

interface SettingRow {
  key: string;
  kind: SettingKind;
  value: any;
  text?: string;
}

@Component({
  selector: 'sf-module-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './module-settings.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class ModuleSettingsComponent implements OnInit {
  private readonly api = inject(ApiService);

  modules: string[] = [];
  selected = 'admission';
  settingsDoc: any = null;
  rows: SettingRow[] = [];
  status = '';
  error = '';
  busy = false;

  ngOnInit(): void {
    this.api.get<string[]>('/api/config/modules').subscribe({
      next: (m) => {
        this.modules = m ?? [];
        this.load(this.selected);
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load modules'),
    });
  }

  load(key: string): void {
    this.selected = key;
    this.status = '';
    this.error = '';
    this.api.get<any>(`/api/config/modules/${key}`).subscribe({
      next: (s) => {
        this.settingsDoc = s;
        this.rows = this.toRows((s?.settings as Record<string, unknown>) || {});
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load module'),
    });
  }

  save(): void {
    if (!this.settingsDoc) return;
    const settings: Record<string, unknown> = {};
    for (const row of this.rows) {
      try {
        settings[row.key] = this.fromRow(row);
      } catch (e: any) {
        this.error = `Invalid value for ${row.key}: ${e?.message || e}`;
        return;
      }
    }
    this.busy = true;
    this.error = '';
    const body = {
      ...this.settingsDoc,
      moduleKey: this.selected,
      settings,
    };
    this.api.put<any>(`/api/config/modules/${this.selected}`, body).subscribe({
      next: (s) => {
        this.busy = false;
        this.settingsDoc = s;
        this.rows = this.toRows((s?.settings as Record<string, unknown>) || {});
        this.status = `${this.selected} settings saved`;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Save failed';
      },
    });
  }

  addKey(): void {
    const key = prompt('New setting key');
    if (!key?.trim()) return;
    if (this.rows.some((r) => r.key === key.trim())) {
      this.error = 'Key already exists';
      return;
    }
    this.rows.push({ key: key.trim(), kind: 'string', value: '', text: '' });
  }

  removeRow(i: number): void {
    this.rows.splice(i, 1);
  }

  private toRows(settings: Record<string, unknown>): SettingRow[] {
    return Object.keys(settings).map((key) => {
      const value = settings[key];
      if (typeof value === 'boolean') {
        return { key, kind: 'boolean', value };
      }
      if (typeof value === 'number') {
        return { key, kind: 'number', value };
      }
      if (Array.isArray(value) && value.every((v) => typeof v === 'string')) {
        return { key, kind: 'string[]', value, text: value.join(', ') };
      }
      if (typeof value === 'string') {
        return { key, kind: 'string', value, text: value };
      }
      return { key, kind: 'json', value, text: JSON.stringify(value, null, 2) };
    });
  }

  private fromRow(row: SettingRow): unknown {
    switch (row.kind) {
      case 'boolean':
        return !!row.value;
      case 'number':
        return Number(row.value);
      case 'string':
        return row.text ?? row.value ?? '';
      case 'string[]':
        return String(row.text || '')
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean);
      case 'json':
        return JSON.parse(String(row.text || 'null'));
      default:
        return row.value;
    }
  }
}
