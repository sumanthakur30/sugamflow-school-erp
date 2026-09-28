import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';

type UdiseColumn = { key: string; label: string; selected: boolean };

@Component({
  selector: 'sf-udise-export',
  standalone: true,
  imports: [CommonModule, RouterLink],
  styleUrls: ['../../shared/admin-page.scss'],
  styles: [
    `
      .page-head {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 1rem;
      }
      a.secondary {
        display: inline-flex;
        align-items: center;
        min-height: 2.25rem;
        padding: 0.4rem 0.85rem;
        border-radius: 8px;
        background: #fff;
        border: 1px solid var(--sf-border, #dce4df);
        color: var(--sf-text, #26352e);
        text-decoration: none;
        white-space: nowrap;
      }
    `,
  ],
  templateUrl: './udise-export.component.html',
})
export class UdiseExportComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  saving = false;
  error = '';
  statusMsg = '';
  usingDefaults = true;
  columns: UdiseColumn[] = [];

  ngOnInit(): void {
    this.api.get<{ columns: UdiseColumn[]; usingDefaults: boolean }>('/api/student/directory/udise-setting').subscribe({
      next: (res) => {
        this.columns = res?.columns ?? [];
        this.usingDefaults = !!res?.usingDefaults;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Could not load UDISE+ columns';
      },
    });
  }

  get allSelected(): boolean {
    return this.columns.length > 0 && this.columns.every((c) => c.selected);
  }

  toggle(column: UdiseColumn, checked: boolean): void {
    column.selected = checked;
  }

  toggleAll(checked: boolean): void {
    for (const column of this.columns) {
      column.selected = checked;
    }
  }

  useOfficial(): void {
    this.toggleAll(true);
    this.save();
  }

  save(): void {
    const keys = this.columns.filter((c) => c.selected).map((c) => c.key);
    if (!keys.length) {
      this.error = 'Select at least one column.';
      return;
    }
    this.saving = true;
    this.error = '';
    this.statusMsg = '';
    this.api.put<{ columns: UdiseColumn[]; usingDefaults: boolean }>('/api/student/directory/udise-setting', { keys }).subscribe({
      next: (res) => {
        this.columns = res?.columns ?? this.columns;
        this.usingDefaults = !!res?.usingDefaults;
        this.saving = false;
        this.statusMsg = 'UDISE+ columns saved. Download the file from Student Directory.';
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message ?? 'Could not save UDISE+ columns';
      },
    });
  }
}
