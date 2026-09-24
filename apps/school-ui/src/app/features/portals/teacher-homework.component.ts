import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-teacher-homework',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './teacher-homework.component.html',
  styleUrls: ['../../shared/admin-page.scss', './teacher-homework.component.scss'],
})
export class TeacherHomeworkComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  busy = false;
  error = '';
  status = '';

  sections: any[] = [];
  sectionId = '';
  items: any[] = [];
  selectedId = '';
  submissions: any[] = [];

  form = {
    title: '',
    description: '',
    subjectKey: 'general',
    dueDate: '',
  };

  ngOnInit(): void {
    this.api.get<any[]>('/api/academic/sections').subscribe({
      next: (items) => {
        this.sections = items ?? [];
        if (this.sections.length) {
          this.sectionId = this.sections[0].id;
        }
        this.loading = false;
        this.refresh();
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to load sections';
      },
    });
  }

  sectionLabel(id: string): string {
    const s = this.sections.find((x) => x.id === id);
    return s?.studentLabel || s?.name || id;
  }

  refresh(): void {
    this.error = '';
    let path = '/api/exam/homework?status=PUBLISHED';
    if (this.sectionId) {
      path += `&sectionId=${encodeURIComponent(this.sectionId)}`;
    }
    this.api.get<any[]>(path).subscribe({
      next: (rows) => {
        this.items = rows ?? [];
        if (this.selectedId && !this.items.some((i) => i.id === this.selectedId)) {
          this.selectedId = '';
          this.submissions = [];
        }
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Failed to load homework';
      },
    });
  }

  create(): void {
    if (!this.form.title.trim()) {
      this.error = 'Title is required';
      return;
    }
    this.busy = true;
    this.error = '';
    this.status = '';
    this.api
      .post<any>('/api/exam/homework', {
        title: this.form.title.trim(),
        description: this.form.description.trim() || null,
        subjectKey: this.form.subjectKey || 'general',
        sectionId: this.sectionId || null,
        classSection: this.sectionLabel(this.sectionId),
        dueDate: this.form.dueDate || null,
        status: 'PUBLISHED',
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.status = 'Homework published';
          this.form = { title: '', description: '', subjectKey: 'general', dueDate: '' };
          this.refresh();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Create failed';
        },
      });
  }

  open(item: any): void {
    this.selectedId = item.id;
    this.submissions = [];
    this.api.get<any[]>(`/api/exam/homework/${item.id}/submissions`).subscribe({
      next: (rows) => {
        this.submissions = rows ?? [];
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Failed to load submissions';
      },
    });
  }

  grade(sub: any): void {
    const marks = Number(sub._marks);
    this.busy = true;
    this.api
      .post<any>(`/api/exam/homework/submissions/${sub.id}/grade`, {
        marks: Number.isFinite(marks) ? marks : null,
        feedback: sub._feedback || null,
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.status = 'Graded';
          this.open({ id: this.selectedId });
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Grade failed';
        },
      });
  }
}
