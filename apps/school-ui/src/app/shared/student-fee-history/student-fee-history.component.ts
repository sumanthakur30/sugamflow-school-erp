import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from '../../core/api.service';
import { StudentLookupComponent } from '../student-lookup/student-lookup.component';
import { StudentLookupRow } from '../student-lookup/student-lookup.models';

@Component({
  selector: 'sf-student-fee-history',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, StudentLookupComponent],
  templateUrl: './student-fee-history.component.html',
  styleUrls: ['./student-fee-history.component.scss'],
})
export class StudentFeeHistoryComponent implements OnChanges {
  private readonly api = inject(ApiService);

  @Input() open = false;
  /** Prefill / lock to this admission number when opening. */
  @Input() admissionNo = '';
  @Output() closed = new EventEmitter<void>();
  @Output() collect = new EventEmitter<string>();
  @Output() openCollection = new EventEmitter<string>();

  loading = false;
  error = '';
  student: any = null;
  collections: any[] = [];
  summary: any = null;
  lookupNonce = 0;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open) {
      this.lookupNonce += 1;
      if (this.admissionNo?.trim()) {
        this.loadForAdmission(this.admissionNo.trim());
      } else {
        this.resetBody();
      }
    }
  }

  close(): void {
    this.closed.emit();
  }

  onStudentSelected(row: StudentLookupRow): void {
    const adm = String(row.admissionNo || '').trim();
    if (!adm) {
      this.error = 'Selected student has no admission number';
      return;
    }
    this.loadForAdmission(adm, row);
  }

  onStudentCleared(): void {
    this.resetBody();
  }

  private resetBody(): void {
    this.student = null;
    this.collections = [];
    this.summary = null;
    this.error = '';
    this.loading = false;
  }

  private loadForAdmission(admissionNo: string, seed?: StudentLookupRow): void {
    this.loading = true;
    this.error = '';
    forkJoin({
      identity: this.api
        .get<any>(`/api/student/students/by-admission/${encodeURIComponent(admissionNo)}/identity`)
        .pipe(
          catchError(() =>
            of({
              admissionNo,
              fullName: seed?.fullName || '',
              mobile: seed?.mobile || '',
              classSection: seed?.classSection || '',
              fromAnswersOnly: true,
            }),
          ),
        ),
      collections: this.api.getPage<any>('/api/fee/collections', 0, 200).pipe(
        catchError(() => of({ items: [] as any[] })),
      ),
      summary: this.api
        .get<any>(`/api/fee/clearance/${encodeURIComponent(admissionNo)}`)
        .pipe(catchError(() => of(null))),
    }).subscribe(({ identity, collections, summary }) => {
      this.loading = false;
      this.student = identity;
      const adm = String(admissionNo).toLowerCase();
      const items = (collections as any)?.items ?? [];
      this.collections = items
        .filter(
          (c: any) =>
            String(c.answers?.admissionNo || '')
              .trim()
              .toLowerCase() === adm,
        )
        .sort(
          (a: any, b: any) =>
            new Date(b.updatedAt || b.createdAt || 0).getTime() -
            new Date(a.updatedAt || a.createdAt || 0).getTime(),
        );
      this.summary = summary;
      if (!this.student?.fullName && this.collections[0]?.answers?.studentName) {
        this.student = {
          ...this.student,
          fullName: this.collections[0].answers.studentName,
          mobile: this.student?.mobile || this.collections[0].answers.mobile,
        };
      }
    });
  }

  paidTotal(): number {
    return this.collections
      .filter((c) => String(c.status).toUpperCase() === 'APPROVED')
      .reduce((s, c) => s + (Number(c.answers?.amount) || 0), 0);
  }

  pendingTotal(): number {
    return this.collections
      .filter((c) => String(c.status).toUpperCase() !== 'APPROVED')
      .reduce((s, c) => s + (Number(c.answers?.amount) || 0), 0);
  }

  formatMoney(raw: unknown): string {
    if (raw == null || raw === '') return '—';
    const n = Number(raw);
    if (Number.isNaN(n)) return String(raw);
    return `₹ ${n.toLocaleString('en-IN', { maximumFractionDigits: 0 })}`;
  }

  formatWhen(raw: unknown): string {
    if (!raw) return '—';
    const d = new Date(String(raw));
    if (Number.isNaN(d.getTime())) return String(raw);
    return d.toLocaleString(undefined, {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  statusClass(status: unknown): string {
    const s = String(status || '')
      .trim()
      .toUpperCase();
    if (s === 'APPROVED' || s === 'PAID') return 'badge badge-ok';
    if (s === 'REJECTED' || s === 'CANCELLED') return 'badge badge-bad';
    if (s === 'IN_PROGRESS' || s === 'PENDING' || s === 'INFO_REQUESTED') return 'badge badge-progress';
    return 'badge';
  }

  collectFee(): void {
    const adm = String(this.student?.admissionNo || this.admissionNo || '').trim();
    this.collect.emit(adm);
  }
}
