import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../core/api.service';

type AddonTab =
  | 'INVENTORY'
  | 'PTM'
  | 'TASK'
  | 'TEACHER_DIARY'
  | 'DESK_SLIP'
  | 'ACTIVITY'
  | 'LECTURE'
  | 'BARCODE'
  | 'GPS'
  | 'PAPER'
  | 'PAYROLL';

@Component({
  selector: 'sf-addons',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './addons.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class AddonsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);

  readonly tabs: Array<{ id: AddonTab; label: string }> = [
    { id: 'INVENTORY', label: 'Inventory' },
    { id: 'PTM', label: 'PTM' },
    { id: 'TASK', label: 'Tasks' },
    { id: 'TEACHER_DIARY', label: 'Teacher diary' },
    { id: 'DESK_SLIP', label: 'Desk slip' },
    { id: 'ACTIVITY', label: 'Activity log' },
    { id: 'LECTURE', label: 'Lecture attendance' },
    { id: 'BARCODE', label: 'Library barcode' },
    { id: 'GPS', label: 'Transport GPS' },
    { id: 'PAPER', label: 'Paper generator' },
    { id: 'PAYROLL', label: 'Payroll structures' },
  ];

  tab: AddonTab = 'INVENTORY';
  items: any[] = [];
  error = '';
  message = '';
  draft = this.empty();
  tests: any[] = [];
  marksFile: File | null = null;

  ngOnInit(): void {
    this.load();
  }

  select(tab: AddonTab): void {
    this.tab = tab;
    this.draft = this.empty();
    this.message = '';
    this.error = '';
    this.load();
  }

  load(): void {
    this.items = [];
    if (this.tab === 'BARCODE' || this.tab === 'PAPER') {
      return;
    }
    if (this.tab === 'GPS') {
      this.api.get<any[]>('/api/transport/gps').subscribe({
        next: (rows) => (this.items = rows || []),
        error: (err) => (this.error = err?.error?.message ?? 'GPS list failed'),
      });
      return;
    }
    if (this.tab === 'PAYROLL') {
      this.api.get<any[]>('/api/payroll/ops/structures').subscribe({
        next: (rows) => (this.items = rows || []),
        error: (err) => (this.error = err?.error?.message ?? 'Payroll structures failed'),
      });
      return;
    }
    if (this.tab === 'ACTIVITY') {
      this.api.get<any>('/api/audit/config-changes').subscribe({
        next: (rows) => {
          this.items = Array.isArray(rows) ? rows : rows?.items || [];
        },
        error: () => {
          this.api.get<any[]>('/api/student/desk/ACTIVITY').subscribe({
            next: (desk) => (this.items = desk || []),
            error: (err) => (this.error = err?.error?.message ?? 'Activity log failed'),
          });
        },
      });
      return;
    }
    this.api.get<any[]>(`/api/student/desk/${this.tab}`).subscribe({
      next: (rows) => (this.items = rows || []),
      error: (err) => (this.error = err?.error?.message ?? 'Could not load this add-on'),
    });
    if (this.tab === 'LECTURE') {
      this.api.get<any[]>('/api/exam/classroom/OFFLINE_TEST').subscribe({
        next: (rows) => (this.tests = rows || []),
        error: () => (this.tests = []),
      });
    }
  }

  save(): void {
    this.error = '';
    if (this.tab === 'BARCODE') {
      this.api.get<any>(`/api/library/circulation/books/lookup?barcode=${encodeURIComponent(this.draft.title)}`).subscribe({
        next: (book) => {
          this.items = [book];
          this.message = book.title || 'Book found';
        },
        error: (err) => (this.error = err?.error?.message ?? 'Book not found'),
      });
      return;
    }
    if (this.tab === 'GPS') {
      this.api.post('/api/transport/gps', {
        vehicleNo: this.draft.title,
        latitude: this.draft.subjectRef,
        longitude: this.draft.note,
      }).subscribe({
        next: () => {
          this.message = 'Location saved';
          this.load();
        },
        error: (err) => (this.error = err?.error?.message ?? 'GPS save failed'),
      });
      return;
    }
    if (this.tab === 'PAPER') {
      this.api.post('/api/exam/classroom/paper-builder', { title: this.draft.title, note: this.draft.note }).subscribe({
        next: () => (this.message = 'Paper saved on the quiz list'),
        error: (err) => (this.error = err?.error?.message ?? 'Paper generator failed'),
      });
      return;
    }
    if (this.tab === 'PAYROLL') {
      const key = (this.draft.title || 'structure').toLowerCase().replace(/\s+/g, '_');
      this.api.put(`/api/payroll/ops/structures/${encodeURIComponent(key)}`, {
        name: this.draft.title,
        basic: this.draft.subjectRef,
        allowance: this.draft.note,
      }).subscribe({
        next: () => {
          this.message = 'Structure saved';
          this.load();
        },
        error: (err) => (this.error = err?.error?.message ?? 'Structure save failed'),
      });
      return;
    }
    this.api.post(`/api/student/desk/${this.tab}`, {
      title: this.draft.title,
      subjectRef: this.draft.subjectRef,
      subjectName: this.draft.subjectName,
      note: this.draft.note,
      status: this.tab === 'LECTURE' ? 'PRESENT' : undefined,
    }).subscribe({
      next: () => {
        this.message = 'Saved';
        this.draft = this.empty();
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Save failed'),
    });
  }

  onMarksFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.marksFile = input.files?.[0] ?? null;
  }

  uploadMarks(): void {
    const testId = this.draft.subjectRef;
    if (!testId || !this.marksFile) {
      this.error = 'Choose an offline test and an Excel file';
      return;
    }
    const body = new FormData();
    body.append('file', this.marksFile);
    this.http.post(`/api/exam/classroom/items/${testId}/marks-file`, body).subscribe({
      next: () => (this.message = 'Excel marks imported'),
      error: (err) => (this.error = err?.error?.message ?? 'Excel import failed'),
    });
  }

  private empty() {
    return { title: '', subjectRef: '', subjectName: '', note: '' };
  }
}
