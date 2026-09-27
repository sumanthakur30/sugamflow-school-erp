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

  readonly documents = [
    { key: 'id_card', label: 'ID card' },
    { key: 'admit_card', label: 'Admit card' },
    { key: 'transfer_certificate', label: 'Transfer certificate' },
  ];

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
    this.loading = false;
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
