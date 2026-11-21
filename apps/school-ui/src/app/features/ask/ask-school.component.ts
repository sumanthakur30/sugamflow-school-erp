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
  rows: Array<{ label: string; value: string }> = [];
  error = '';

  readonly examples = [
    'Show students with outstanding fees',
    'Which class has the lowest attendance this month?',
    'Show students whose names are on the fee follow-up list in Class 8',
  ];

  ask(text?: string): void {
    const question = (text ?? this.question).trim();
    this.question = question;
    this.answer = '';
    this.rows = [];
    this.error = '';
    if (!question) {
      this.error = 'Type a question. This screen only reads records. It does not change them.';
      return;
    }
    const lower = question.toLowerCase();
    this.asking = true;
    if (lower.includes('fee') || lower.includes('outstanding') || lower.includes('due')) {
      this.answerFees(lower);
      return;
    }
    if (lower.includes('attendance')) {
      this.answerAttendance();
      return;
    }
    this.asking = false;
    this.answer =
      'I can answer outstanding fees and low attendance from the records you are allowed to see. I cannot post a receipt or change marks.';
  }

  private answerFees(question: string): void {
    forkJoin({
      people: this.api.get<any[]>('/api/fee/defaulters').pipe(catchError(() => of([]))),
      finance: this.api
        .get<any>('/api/fee/finance/income-expense?preset=THIS_MONTH')
        .pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ people, finance }) => {
        this.asking = false;
        const pending = finance?.kpis?.pendingFees;
        const classHint = question.match(/class\s+([0-9a-z]+)/i)?.[1];
        let list = people || [];
        if (classHint) {
          list = list.filter((row) =>
            String(row.classSection || row.classApplied || '')
              .toLowerCase()
              .includes(classHint.toLowerCase()),
          );
        }
        this.rows = list.slice(0, 12).map((row) => ({
          label: String(row.studentName || row.admissionNo || 'Student'),
          value: `${row.classSection || 'Class not on the row'} · ₹${row.amount ?? '—'}`,
        }));
        const total =
          pending == null ? '' : ` The fee summary shows ₹${pending} still pending this period.`;
        this.answer = this.rows.length
          ? `These open fee rows match the question.${total} This list is unpaid collections, not a new balance I calculated.`
          : `No open fee rows matched.${total} Approved dues may still sit in the fee summary.`;
      },
      error: () => {
        this.asking = false;
        this.error = 'Fee records could not be read.';
      },
    });
  }

  private answerAttendance(): void {
    this.api.get<any[]>('/api/attendance/exceptions').subscribe({
      next: (rows) => {
        this.asking = false;
        const list = rows || [];
        const byClass = new Map<string, number>();
        for (const row of list) {
          const key = String(row.classSection || row.className || 'Class not on the row');
          byClass.set(key, (byClass.get(key) || 0) + 1);
        }
        let lowest = '';
        let count = -1;
        for (const [name, n] of byClass) {
          if (n > count) {
            lowest = name;
            count = n;
          }
        }
        this.rows = list.slice(0, 12).map((row) => ({
          label: String(row.studentName || row.admissionNo || 'Student'),
          value: `${row.classSection || row.status || 'Flagged'}${row.percent != null ? ' · ' + row.percent + '%' : ''}`,
        }));
        this.answer = list.length
          ? lowest
            ? `${lowest} has the most students on the low-attendance list (${count}). This is the exception list, not a full class percentage.`
            : 'These students are on the low-attendance list.'
          : 'No low-attendance rows were returned for this campus and session.';
      },
      error: () => {
        this.asking = false;
        this.error = 'Attendance records could not be read.';
      },
    });
  }
}
