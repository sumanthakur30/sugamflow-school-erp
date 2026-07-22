import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-teacher-gradebook',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './teacher-gradebook.component.html',
  styleUrls: ['../../shared/admin-page.scss', './teacher-gradebook.component.scss'],
})
export class TeacherGradebookComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  busy = false;
  error = '';
  status = '';

  sections: any[] = [];
  subjects: any[] = [];
  definitions: any[] = [];

  sectionId = '';
  subjectId = '';
  examDefinitionId = '';

  createDraft = { name: '', termKey: 'TERM1', maxMarks: 100 };

  gradebook: any = null;
  marksDraft: Record<string, { marksObtained: number | null; grade: string }> = {};

  ngOnInit(): void {
    let pending = 2;
    const done = () => {
      pending -= 1;
      if (pending <= 0) {
        this.loading = false;
        this.refreshDefinitions();
      }
    };
    this.api.get<any[]>('/api/academic/sections').subscribe({
      next: (items) => {
        this.sections = items ?? [];
        if (this.sections.length) {
          this.sectionId = this.sections[0].id;
        }
        done();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Failed to load sections';
        done();
      },
    });
    this.api.get<any[]>('/api/academic/subjects').subscribe({
      next: (items) => {
        this.subjects = items ?? [];
        if (this.subjects.length) {
          this.subjectId = this.subjects[0].id;
        }
        done();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Failed to load subjects';
        done();
      },
    });
  }

  refreshDefinitions(): void {
    if (!this.sectionId) {
      this.definitions = [];
      return;
    }
    let path = `/api/exam/definitions?sectionId=${encodeURIComponent(this.sectionId)}`;
    if (this.subjectId) {
      path += `&subjectId=${encodeURIComponent(this.subjectId)}`;
    }
    this.api.get<any[]>(path).subscribe({
      next: (items) => {
        this.definitions = items ?? [];
        if (this.definitions.length) {
          const still =
            this.examDefinitionId &&
            this.definitions.some((d) => d.id === this.examDefinitionId);
          if (!still) {
            this.examDefinitionId = this.definitions[0].id;
          }
          this.loadGradebook();
        } else {
          this.examDefinitionId = '';
          this.gradebook = null;
        }
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Failed to load exam definitions';
      },
    });
  }

  createExam(): void {
    if (!this.sectionId || !this.subjectId || !this.createDraft.name.trim()) {
      this.error = 'Section, subject and exam name are required';
      return;
    }
    this.busy = true;
    this.error = '';
    this.api
      .post<any>('/api/exam/definitions', {
        sectionId: this.sectionId,
        subjectId: this.subjectId,
        name: this.createDraft.name.trim(),
        termKey: this.createDraft.termKey || 'TERM1',
        maxMarks: this.createDraft.maxMarks || 100,
      })
      .subscribe({
        next: (def) => {
          this.busy = false;
          this.status = `Created ${def.name}`;
          this.createDraft = { name: '', termKey: 'TERM1', maxMarks: 100 };
          this.examDefinitionId = def.id;
          this.refreshDefinitions();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Create exam failed';
        },
      });
  }

  loadGradebook(): void {
    if (!this.examDefinitionId) {
      this.gradebook = null;
      return;
    }
    this.busy = true;
    this.error = '';
    this.api
      .get<any>(
        `/api/exam/gradebook?examDefinitionId=${encodeURIComponent(this.examDefinitionId)}`,
      )
      .subscribe({
        next: (data) => {
          this.gradebook = data;
          this.marksDraft = {};
          for (const s of data?.students ?? []) {
            const key = s.studentId || s.admissionNo;
            if (key) {
              this.marksDraft[key] = {
                marksObtained: s.marksObtained ?? null,
                grade: s.grade || '',
              };
            }
          }
          this.busy = false;
        },
        error: (err) => {
          this.busy = false;
          this.gradebook = null;
          this.error = err?.error?.message ?? 'Failed to load gradebook';
        },
      });
  }

  saveMarks(): void {
    if (!this.examDefinitionId || !this.gradebook) {
      return;
    }
    const marks = (this.gradebook.students ?? []).map((s: any) => {
      const key = s.studentId || s.admissionNo;
      const d = this.marksDraft[key] || { marksObtained: null, grade: '' };
      return {
        studentId: s.studentId,
        admissionNo: s.admissionNo,
        studentName: s.studentName,
        marksObtained: d.marksObtained,
        grade: d.grade || null,
      };
    });
    this.busy = true;
    this.error = '';
    this.api
      .put<any>('/api/exam/gradebook/bulk', {
        examDefinitionId: this.examDefinitionId,
        marks,
      })
      .subscribe({
        next: (data) => {
          this.busy = false;
          this.status = 'Marks saved';
          this.gradebook = data;
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Save marks failed';
        },
      });
  }

  publish(): void {
    if (!this.examDefinitionId) {
      return;
    }
    this.busy = true;
    this.error = '';
    this.api.post(`/api/exam/definitions/${this.examDefinitionId}/publish`, {}).subscribe({
      next: () => {
        this.busy = false;
        this.status = 'Published to parents';
        this.refreshDefinitions();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Publish failed';
      },
    });
  }

  readOnly(): boolean {
    const st = String(this.gradebook?.exam?.status || '').toUpperCase();
    return st === 'LOCKED' || st === 'PUBLISHED';
  }
}
