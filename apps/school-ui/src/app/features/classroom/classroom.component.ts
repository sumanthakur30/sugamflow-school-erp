import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

type ClassroomTab =
  | 'STUDY_MATERIAL'
  | 'QUESTION'
  | 'QUIZ'
  | 'LESSON_PLAN'
  | 'CALENDAR'
  | 'GALLERY'
  | 'OFFLINE_TEST';

@Component({
  selector: 'sf-classroom',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './classroom.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class ClassroomComponent implements OnInit {
  private readonly api = inject(ApiService);

  readonly tabs: Array<{ id: ClassroomTab; label: string }> = [
    { id: 'STUDY_MATERIAL', label: 'Study material' },
    { id: 'QUESTION', label: 'Question bank' },
    { id: 'QUIZ', label: 'Quiz' },
    { id: 'LESSON_PLAN', label: 'Lesson plans' },
    { id: 'CALENDAR', label: 'Calendar' },
    { id: 'GALLERY', label: 'Gallery' },
    { id: 'OFFLINE_TEST', label: 'Offline tests' },
  ];

  tab: ClassroomTab = 'STUDY_MATERIAL';
  items: any[] = [];
  loading = false;
  error = '';
  message = '';
  marksItemId = '';
  csv = '';
  draft = this.emptyDraft();

  ngOnInit(): void {
    this.load();
  }

  select(tab: ClassroomTab): void {
    this.tab = tab;
    this.draft = this.emptyDraft();
    this.message = '';
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any[]>(`/api/exam/classroom/${this.tab}`).subscribe({
      next: (rows) => {
        this.items = rows || [];
        this.loading = false;
        if (!this.marksItemId && this.items[0]) {
          this.marksItemId = this.items[0].id;
        }
      },
      error: (err) => {
        this.loading = false;
        this.items = [];
        this.error = err?.error?.message ?? 'Could not load classroom items';
      },
    });
  }

  save(): void {
    this.message = '';
    this.error = '';
    const payload: Record<string, string> = {};
    if (this.draft.subject) payload['subject'] = this.draft.subject;
    if (this.draft.mediaType) payload['mediaType'] = this.draft.mediaType;
    if (this.draft.url) payload['url'] = this.draft.url;
    if (this.draft.eventDate) payload['eventDate'] = this.draft.eventDate;
    if (this.draft.classSection) payload['classSection'] = this.draft.classSection;
    this.api
      .post<any>(`/api/exam/classroom/${this.tab}`, {
        title: this.draft.title,
        note: this.draft.note,
        status: 'PUBLISHED',
        payload,
      })
      .subscribe({
        next: () => {
          this.draft = this.emptyDraft();
          this.message = 'Saved';
          this.load();
        },
        error: (err) => {
          this.error = err?.error?.message ?? 'Save failed';
        },
      });
  }

  importMarks(): void {
    if (!this.marksItemId) {
      this.error = 'Choose a test first';
      return;
    }
    this.api
      .post<any>(`/api/exam/classroom/items/${this.marksItemId}/marks`, { csv: this.csv })
      .subscribe({
        next: (result) => {
          this.message = `Imported ${result?.saved ?? 0} mark rows`;
          this.csv = '';
        },
        error: (err) => {
          this.error = err?.error?.message ?? 'Mark import failed';
        },
      });
  }

  private emptyDraft() {
    return {
      title: '',
      note: '',
      subject: '',
      mediaType: 'NOTE',
      url: '',
      eventDate: '',
      classSection: '',
    };
  }
}
