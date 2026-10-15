import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

interface StaffRow {
  staffId: string;
  staffName: string;
  status: string;
}

@Component({
  selector: 'sf-staff-attendance',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './staff-attendance.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class StaffAttendanceComponent implements OnInit {
  private readonly api = inject(ApiService);

  readonly statuses = ['PRESENT', 'ABSENT', 'LATE', 'LEAVE', 'HALF_DAY'];
  month = this.currentMonth();
  date = this.today();
  staff: StaffRow[] = [];
  sheetStatus = 'DRAFT';
  loading = true;
  busy = false;
  error = '';
  status = '';

  ngOnInit(): void {
    this.load();
  }

  get submitted(): boolean {
    return this.sheetStatus === 'SUBMITTED';
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>(`/api/attendance/staff/months/${this.month}`).subscribe({
      next: (sheet) => {
        this.sheetStatus = String(sheet?.status || 'DRAFT');
        this.api.get<any>('/api/staff/directory/staff?page=0&size=200').subscribe({
          next: (page) => {
            const items = page?.items || page?.content || [];
            const marks = Array.isArray(sheet?.marks) ? sheet.marks : [];
            this.staff = items.map((row: any) => {
              const staffId = String(row.id || row.staffId || '');
              const saved = marks.find(
                (mark: any) => mark.staffId === staffId && mark.date === this.date,
              );
              return {
                staffId,
                staffName: String(row.fullName || row.staffName || row.name || row.employeeCode || staffId),
                status: String(saved?.status || 'PRESENT'),
              };
            });
            this.loading = false;
          },
          error: (err) => {
            this.loading = false;
            this.error = err?.error?.message ?? 'Could not load staff';
          },
        });
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Could not load staff attendance';
      },
    });
  }

  save(): void {
    if (this.submitted || !this.staff.length) return;
    if (!this.date.startsWith(this.month)) {
      this.error = 'Choose a date inside the selected month';
      return;
    }
    this.busy = true;
    this.error = '';
    this.api
      .put<any>(`/api/attendance/staff/months/${this.month}`, {
        date: this.date,
        marks: this.staff.map((row) => ({
          staffId: row.staffId,
          staffName: row.staffName,
          status: row.status,
        })),
      })
      .subscribe({
        next: (sheet) => {
          this.busy = false;
          this.sheetStatus = String(sheet?.status || 'DRAFT');
          this.status = `Saved ${this.date}`;
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Could not save staff attendance';
        },
      });
  }

  submitMonth(): void {
    if (this.submitted) return;
    this.busy = true;
    this.error = '';
    this.api.post<any>(`/api/attendance/staff/months/${this.month}/submit`, {}).subscribe({
      next: (sheet) => {
        this.busy = false;
        this.sheetStatus = String(sheet?.status || 'SUBMITTED');
        this.status = `Staff attendance for ${this.month} is submitted. Payslips for this month can be finalized.`;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Could not submit staff attendance';
      },
    });
  }

  private currentMonth(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  }

  private today(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  }
}
