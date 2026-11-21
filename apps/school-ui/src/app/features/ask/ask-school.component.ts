import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-ask-school',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './ask-school.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class AskSchoolComponent {
  private readonly api = inject(ApiService);

  question = '';
  asking = false;
  answer = '';
  columns: string[] = [];
  rows: Array<Record<string, unknown>> = [];
  error = '';

  readonly examples = ['Outstanding fees', 'Low attendance'];

  ask(text?: string): void {
    const question = (text ?? this.question).trim();
    this.question = question;
    this.answer = '';
    this.columns = [];
    this.rows = [];
    this.error = '';
    if (!question) {
      this.error = 'Type a question. This screen only reads records. It does not change them.';
      return;
    }
    const name = this.namedQuery(question);
    if (name === 'outstanding-fees') {
      this.answerFees(question);
      return;
    }
    if (name === 'low-attendance') {
      this.answerAttendance();
      return;
    }
    this.answer =
      'I can answer outstanding fees and low attendance from the records you are allowed to see. I cannot post a receipt or change marks.';
  }

  private namedQuery(question: string): 'outstanding-fees' | 'low-attendance' | null {
    const lower = question.toLowerCase();
    if (lower.includes('attendance')) {
      return 'low-attendance';
    }
    if (lower.includes('fee') || lower.includes('outstanding') || lower.includes('due')) {
      return 'outstanding-fees';
    }
    return null;
  }

  private answerFees(question: string): void {
    this.asking = true;
    const named = this.api.get<any[]>('/api/fee/ask/outstanding-fees').pipe(catchError(() => of(null)));
    const desk = this.api.get<any[]>('/api/fee/defaulters').pipe(catchError(() => of([])));
    forkJoin({ named, desk }).subscribe({
      next: ({ named: namedRows, desk: deskRows }) => {
        this.asking = false;
        const source = namedRows ?? (deskRows || []).map((row) => this.feeFigure(row));
        const classHint = question.match(/class\s+([0-9a-z-]+)/i)?.[1];
        const list = classHint
          ? source.filter((row) =>
              String(row.classSection || '')
                .toLowerCase()
                .includes(classHint.toLowerCase()),
            )
          : source;
        this.columns = ['Student', 'Admission no', 'Class', 'Head', 'Due', 'Amount'];
        this.rows = list.map((row) => ({
          Student: row.studentName || 'Student',
          'Admission no': row.admissionNo || '',
          Class: row.classSection || '',
          Head: row.feeHead || '',
          Due: row.dueDate || '',
          Amount: row.amount ?? '',
        }));
        this.answer = this.rows.length
          ? 'These are the open dues on the fee desk for this login.'
          : 'The fee desk has no open dues for this login.';
      },
      error: () => {
        this.asking = false;
        this.error = 'Fee records could not be read.';
      },
    });
  }

  private answerAttendance(): void {
    this.asking = true;
    const named = this.api.get<any[]>('/api/attendance/ask/low-attendance').pipe(catchError(() => of(null)));
    const desk = this.api.get<any[]>('/api/attendance/exceptions').pipe(catchError(() => of([])));
    forkJoin({ named, desk }).subscribe({
      next: ({ named: namedRows, desk: deskRows }) => {
        this.asking = false;
        const source = namedRows ?? (deskRows || []).map((row) => this.attendanceFigure(row));
        this.columns = ['Student', 'Admission no', 'Status', 'Percent'];
        this.rows = source.map((row) => ({
          Student: row.studentName || 'Student',
          'Admission no': row.admissionNo || '',
          Status: row.status || '',
          Percent: row.percent != null ? row.percent : '',
        }));
        this.answer = this.rows.length
          ? 'These are the students on the low-attendance list for this login.'
          : 'The low-attendance list is empty for this login.';
      },
      error: () => {
        this.asking = false;
        this.error = 'Attendance records could not be read.';
      },
    });
  }

  private feeFigure(row: any): Record<string, unknown> {
    return {
      studentName: row?.studentName,
      admissionNo: row?.admissionNo,
      classSection: row?.classSection,
      feeHead: row?.feeHead,
      dueDate: row?.dueDate,
      amount: row?.amount,
    };
  }

  private attendanceFigure(row: any): Record<string, unknown> {
    return {
      studentName: row?.studentName,
      admissionNo: row?.admissionNo,
      status: row?.status,
      percent: row?.percent,
    };
  }
}
