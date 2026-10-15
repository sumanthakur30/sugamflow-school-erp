import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-portal-extra',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './portal-extra.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class PortalExtraComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  mode = 'study';
  title = 'Study';
  items: any[] = [];
  siblings: any[] = [];
  error = '';
  message = '';
  loading = false;
  admissionNo = '';
  answer = '';
  selectedQuizId = '';
  leave = { subjectRef: '', subjectName: '', title: 'Leave', note: '' };

  readonly packs = [
    { key: 'id_card', label: 'ID card' },
    { key: 'transfer_certificate', label: 'Transfer certificate' },
  ];
  reportCards: any[] = [];
  admissionNos: string[] = [];
  selectedAdmission = '';
  busy = false;

  ngOnInit(): void {
    this.route.data.subscribe((data) => {
      this.mode = String(data['mode'] || 'study');
      this.title = String(data['title'] || 'Portal');
      this.load();
    });
  }

  load(): void {
    this.error = '';
    this.items = [];
    this.loading = true;
    if (this.mode === 'study' || this.mode === 'quiz' || this.mode === 'calendar' || this.mode === 'lessons' || this.mode === 'gallery') {
      const kind =
        this.mode === 'study'
          ? 'STUDY_MATERIAL'
          : this.mode === 'quiz'
            ? 'QUIZ'
            : this.mode === 'calendar'
              ? 'CALENDAR'
              : this.mode === 'gallery'
                ? 'GALLERY'
                : 'LESSON_PLAN';
      this.api.get<any[]>(`/api/exam/classroom/${kind}`).subscribe({
        next: (rows) => {
          this.items = rows || [];
          this.selectedQuizId = this.items[0]?.id || '';
          this.loading = false;
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.error?.message ?? 'Could not load this list';
        },
      });
      return;
    }
    if (this.mode === 'timetable') {
      this.api.get<any[]>('/api/academic/timetable/slots').subscribe({
        next: (rows) => {
          this.items = rows || [];
          this.loading = false;
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.error?.message ?? 'Timetable is not available for this login';
        },
      });
      return;
    }
    if (this.mode === 'notices') {
      this.api.get<any>('/api/school/notification-config/comms/bootstrap').subscribe({
        next: (boot) => {
          const rows = boot?.announcements || [];
          this.items = rows.filter((row: any) => !row.channel || row.channel === 'IN_APP' || row.channel === 'SMS' || row.channel === 'WHATSAPP');
          this.loading = false;
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.error?.message ?? 'Notices are not available for this login';
        },
      });
      return;
    }
    if (this.mode === 'documents') {
      this.api.get<any>('/api/student/access-scope').subscribe({
        next: (scope) => {
          this.admissionNos = Array.isArray(scope?.admissionNos) ? scope.admissionNos : [];
          this.selectedAdmission = this.admissionNos[0] || '';
        },
      });
      this.api.get<any[]>('/api/exam/report-cards/mine').subscribe({
        next: (cards) => {
          this.reportCards = cards || [];
          this.loading = false;
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.error?.message ?? 'Report cards are not available for this login';
        },
      });
      return;
    }
    this.loading = false;
  }

  downloadReport(card: any): void {
    const student = card?.student || {};
    const sectionId = card?.sectionId;
    const termKey = card?.termKey;
    if (!sectionId || !termKey) {
      this.error = 'This report card is missing its class or term';
      return;
    }
    const key = student.studentId
      ? `studentId=${encodeURIComponent(student.studentId)}`
      : `admissionNo=${encodeURIComponent(student.admissionNo || '')}`;
    this.busy = true;
    this.error = '';
    this.api
      .getBlob(
        `/api/exam/report-cards/mine.pdf?sectionId=${encodeURIComponent(sectionId)}&termKey=${encodeURIComponent(termKey)}&${key}`,
      )
      .subscribe({
        next: (blob) => {
          this.busy = false;
          this.saveBlob(blob, `report-card-${student.admissionNo || 'student'}-${termKey}.pdf`);
        },
        error: () => {
          this.busy = false;
          this.error = 'Could not download the report card';
        },
      });
  }

  downloadPack(templateKey: string): void {
    if (!this.selectedAdmission) {
      this.error = 'No child is linked to this login';
      return;
    }
    this.busy = true;
    this.error = '';
    this.api
      .post<any>(`/api/reports/mine/${templateKey}`, {
        format: 'PDF',
        admissionNo: this.selectedAdmission,
        data: {
          admissionNo: this.selectedAdmission,
          student: { admissionNo: this.selectedAdmission, studentName: this.selectedAdmission },
        },
      })
      .subscribe({
        next: (file) => {
          this.busy = false;
          const encoded = String(file?.contentBase64 || '');
          if (!encoded) {
            this.error = 'The school has not published this document yet';
            return;
          }
          const bytes = Uint8Array.from(atob(encoded), (ch) => ch.charCodeAt(0));
          this.saveBlob(
            new Blob([bytes], { type: 'application/pdf' }),
            String(file.fileName || `${templateKey}.pdf`),
          );
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Could not download this document';
        },
      });
  }

  private saveBlob(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(url);
  }

  submitQuiz(): void {
    if (!this.selectedQuizId) {
      this.error = 'No quiz is published';
      return;
    }
    this.api
      .post(`/api/exam/classroom/items/${this.selectedQuizId}/responses`, {
        admissionNo: this.admissionNo,
        payload: { answer: this.answer },
      })
      .subscribe({
        next: () => {
          this.message = 'Quiz submitted';
          this.answer = '';
        },
        error: (err) => {
          this.error = err?.error?.message ?? 'Quiz submit failed';
        },
      });
  }

  applyLeave(): void {
    this.api.post('/api/student/desk/LEAVE', this.leave).subscribe({
      next: () => {
        this.message = 'Leave request sent';
        this.leave = { subjectRef: '', subjectName: '', title: 'Leave', note: '' };
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Leave request failed';
      },
    });
  }

  loadSiblings(): void {
    this.api.get<any>(`/api/student/households?admissionNo=${encodeURIComponent(this.admissionNo)}`).subscribe({
      next: (house) => {
        this.siblings = house?.students || [];
        this.message = this.siblings.length ? '' : 'No siblings linked for that admission number';
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Could not load the household';
      },
    });
  }
}
