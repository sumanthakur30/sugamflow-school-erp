import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from '../../core/api.service';
import { TenantContextService } from '../../core/tenant-context.service';
import { ModuleBootstrapService } from '../../core/module-bootstrap.service';

@Component({
  selector: 'sf-academic',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './academic.component.html',
  styleUrls: ['../../shared/admin-page.scss', './academic.component.scss'],
})
export class AcademicComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly tenantContext = inject(TenantContextService);
  private readonly modules = inject(ModuleBootstrapService);
  private campusReadySub?: Subscription;

  loading = true;
  canManage = false;
  error = '';
  status = '';
  busy = false;

  classesError = '';
  sectionsError = '';
  subjectsError = '';
  assignmentsError = '';

  classes: any[] = [];
  /** Sections for the current class filter (table). */
  sections: any[] = [];
  /** All sections (assignment picker + labels). */
  allSections: any[] = [];
  subjects: any[] = [];
  assignments: any[] = [];

  selectedClassId: string | null = null;

  editingClassId: string | null = null;
  editingSectionId: string | null = null;
  editingSubjectId: string | null = null;

  classDraft = { name: '', code: '', sequenceNo: 0 };
  sectionDraft = {
    name: '',
    code: '',
    studentLabel: '',
    classTeacherUsername: '',
    room: '',
    capacity: null as number | null,
  };
  subjectDraft = { name: '', code: '', subjectType: 'CORE' };
  assignmentDraft = {
    sectionId: '',
    subjectId: '',
    teacherUsername: '',
    classTeacher: false,
  };

  ngOnInit(): void {
    this.campusReadySub = this.tenantContext.whenCampusReady().subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.campusReadySub?.unsubscribe();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.classesError = '';
    this.sectionsError = '';
    this.subjectsError = '';
    this.assignmentsError = '';
    const path = '/api/academic/bootstrap';
    const apply = (boot: any) => {
      this.canManage = !!boot.canManage;
      this.loadLists();
    };
    const peeked = this.modules.peek(path);
    if (peeked) {
      apply(peeked);
      return;
    }
    this.modules.load(path).subscribe({
      next: apply,
      error: (err) => {
        this.loading = false;
        this.error = ModuleBootstrapService.errorMessage(
          err,
          'Failed to load academic structure. Is academic-structure-service running?',
        );
      },
    });
  }

  private loadLists(): void {
    forkJoin({
      classes: this.api.get<any[]>('/api/academic/classes').pipe(
        catchError((err) => {
          this.classesError = err?.error?.message ?? 'Failed to load classes';
          return of([] as any[]);
        }),
      ),
      subjects: this.api.get<any[]>('/api/academic/subjects').pipe(
        catchError((err) => {
          this.subjectsError = err?.error?.message ?? 'Failed to load subjects';
          return of([] as any[]);
        }),
      ),
      sections: this.api.get<any[]>('/api/academic/sections').pipe(
        catchError((err) => {
          this.sectionsError = err?.error?.message ?? 'Failed to load sections';
          return of([] as any[]);
        }),
      ),
      assignments: this.api.get<any[]>('/api/academic/assignments').pipe(
        catchError((err) => {
          this.assignmentsError = err?.error?.message ?? 'Failed to load assignments';
          return of([] as any[]);
        }),
      ),
    }).subscribe({
      next: ({ classes, subjects, sections, assignments }) => {
        this.classes = classes ?? [];
        this.subjects = subjects ?? [];
        this.allSections = sections ?? [];
        this.assignments = assignments ?? [];
        this.applySectionFilter();
        this.loading = false;
        this.error = [
          this.classesError,
          this.sectionsError,
          this.subjectsError,
          this.assignmentsError,
        ]
          .filter(Boolean)
          .join(' · ');
      },
      error: () => {
        this.loading = false;
        this.error = 'Failed to load academic lists';
      },
    });
  }

  private applySectionFilter(): void {
    if (!this.selectedClassId) {
      this.sections = [...this.allSections];
      return;
    }
    this.sections = this.allSections.filter((s) => s.classId === this.selectedClassId);
  }

  refreshSections(): void {
    this.sectionsError = '';
    this.api.get<any[]>('/api/academic/sections').subscribe({
      next: (items) => {
        this.allSections = items ?? [];
        this.applySectionFilter();
      },
      error: (err) => {
        this.sectionsError = err?.error?.message ?? 'Failed to load sections';
        this.error = this.sectionsError;
      },
    });
  }

  loadAssignments(): void {
    this.assignmentsError = '';
    this.api.get<any[]>('/api/academic/assignments').subscribe({
      next: (items) => (this.assignments = items ?? []),
      error: (err) => {
        this.assignmentsError = err?.error?.message ?? 'Failed to load assignments';
        this.error = this.assignmentsError;
      },
    });
  }

  selectClass(id: string | null): void {
    this.selectedClassId = id;
    this.editingSectionId = null;
    this.applySectionFilter();
  }

  startEditClass(c: any): void {
    this.editingClassId = c.id;
    this.classDraft = {
      name: c.name || '',
      code: c.code || '',
      sequenceNo: Number(c.sequenceNo) || 0,
    };
  }

  cancelEditClass(): void {
    this.editingClassId = null;
    this.classDraft = { name: '', code: '', sequenceNo: 0 };
  }

  saveClass(): void {
    if (!this.classDraft.name.trim()) {
      this.error = 'Class name is required';
      return;
    }
    this.busy = true;
    this.error = '';
    const body = {
      name: this.classDraft.name.trim(),
      code: this.classDraft.code.trim() || undefined,
      sequenceNo: this.classDraft.sequenceNo || 0,
    };
    const req = this.editingClassId
      ? this.api.put(`/api/academic/classes/${this.editingClassId}`, body)
      : this.api.post('/api/academic/classes', body);
    req.subscribe({
      next: () => {
        this.busy = false;
        this.status = this.editingClassId
          ? `Class ${this.classDraft.name} updated`
          : `Class ${this.classDraft.name} created`;
        this.cancelEditClass();
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Save class failed';
      },
    });
  }

  deleteClass(id: string): void {
    if (!confirm('Delete this class? Sections must be removed first.')) return;
    this.busy = true;
    this.api.delete(`/api/academic/classes/${id}`).subscribe({
      next: () => {
        this.busy = false;
        this.status = 'Class deleted';
        if (this.selectedClassId === id) this.selectedClassId = null;
        if (this.editingClassId === id) this.cancelEditClass();
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Delete class failed';
      },
    });
  }

  startEditSection(s: any): void {
    this.editingSectionId = s.id;
    this.selectedClassId = s.classId || this.selectedClassId;
    this.sectionDraft = {
      name: s.name || '',
      code: s.code || '',
      studentLabel: s.studentLabel || '',
      classTeacherUsername: s.classTeacherUsername || '',
      room: s.room || '',
      capacity: s.capacity ?? null,
    };
  }

  cancelEditSection(): void {
    this.editingSectionId = null;
    this.sectionDraft = {
      name: '',
      code: '',
      studentLabel: '',
      classTeacherUsername: '',
      room: '',
      capacity: null,
    };
  }

  saveSection(): void {
    if (!this.selectedClassId && !this.editingSectionId) {
      this.error = 'Select a class first';
      return;
    }
    if (!this.sectionDraft.name.trim()) {
      this.error = 'Section name is required';
      return;
    }
    this.busy = true;
    this.error = '';
    const classId =
      this.editingSectionId
        ? this.allSections.find((x) => x.id === this.editingSectionId)?.classId || this.selectedClassId
        : this.selectedClassId;
    const label =
      this.sectionDraft.studentLabel.trim() ||
      `${this.className(classId)}-${this.sectionDraft.name.trim()}`;
    const body = {
      classId,
      name: this.sectionDraft.name.trim(),
      code: this.sectionDraft.code.trim() || undefined,
      studentLabel: label,
      classTeacherUsername: this.sectionDraft.classTeacherUsername.trim() || undefined,
      room: this.sectionDraft.room.trim() || undefined,
      capacity: this.sectionDraft.capacity,
    };
    const req = this.editingSectionId
      ? this.api.put(`/api/academic/sections/${this.editingSectionId}`, body)
      : this.api.post('/api/academic/sections', body);
    req.subscribe({
      next: () => {
        this.busy = false;
        this.status = this.editingSectionId
          ? `Section ${this.sectionDraft.name} updated`
          : `Section ${this.sectionDraft.name} created · studentLabel=${label}`;
        this.cancelEditSection();
        this.refreshSections();
        this.loadAssignments();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Save section failed';
      },
    });
  }

  deleteSection(id: string): void {
    if (!confirm('Delete this section and its teaching assignments?')) return;
    this.busy = true;
    this.api.delete(`/api/academic/sections/${id}`).subscribe({
      next: () => {
        this.busy = false;
        this.status = 'Section deleted';
        if (this.editingSectionId === id) this.cancelEditSection();
        this.refreshSections();
        this.loadAssignments();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Delete section failed';
      },
    });
  }

  startEditSubject(s: any): void {
    this.editingSubjectId = s.id;
    this.subjectDraft = {
      name: s.name || '',
      code: s.code || '',
      subjectType: s.subjectType || 'CORE',
    };
  }

  cancelEditSubject(): void {
    this.editingSubjectId = null;
    this.subjectDraft = { name: '', code: '', subjectType: 'CORE' };
  }

  saveSubject(): void {
    if (!this.subjectDraft.name.trim()) {
      this.error = 'Subject name is required';
      return;
    }
    this.busy = true;
    this.error = '';
    const body = {
      name: this.subjectDraft.name.trim(),
      code: this.subjectDraft.code.trim() || undefined,
      subjectType: this.subjectDraft.subjectType || 'CORE',
    };
    const req = this.editingSubjectId
      ? this.api.put(`/api/academic/subjects/${this.editingSubjectId}`, body)
      : this.api.post('/api/academic/subjects', body);
    req.subscribe({
      next: () => {
        this.busy = false;
        this.status = this.editingSubjectId
          ? `Subject ${this.subjectDraft.name} updated`
          : `Subject ${this.subjectDraft.name} created`;
        this.cancelEditSubject();
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Save subject failed';
      },
    });
  }

  deleteSubject(id: string): void {
    if (!confirm('Delete this subject?')) return;
    this.busy = true;
    this.api.delete(`/api/academic/subjects/${id}`).subscribe({
      next: () => {
        this.busy = false;
        this.status = 'Subject deleted';
        if (this.editingSubjectId === id) this.cancelEditSubject();
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Delete subject failed';
      },
    });
  }

  createAssignment(): void {
    if (!this.assignmentDraft.sectionId || !this.assignmentDraft.teacherUsername.trim()) {
      this.error = 'Section and teacher username are required';
      return;
    }
    this.busy = true;
    this.error = '';
    this.api
      .post('/api/academic/assignments', {
        sectionId: this.assignmentDraft.sectionId,
        subjectId: this.assignmentDraft.subjectId || undefined,
        teacherUsername: this.assignmentDraft.teacherUsername.trim(),
        classTeacher: !!this.assignmentDraft.classTeacher,
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.status = 'Teaching assignment created';
          this.assignmentDraft = {
            sectionId: '',
            subjectId: '',
            teacherUsername: '',
            classTeacher: false,
          };
          this.loadAssignments();
          this.refreshSections();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Create assignment failed';
        },
      });
  }

  deleteAssignment(id: string): void {
    if (!confirm('Remove this teaching assignment?')) return;
    this.busy = true;
    this.api.delete(`/api/academic/assignments/${id}`).subscribe({
      next: () => {
        this.busy = false;
        this.status = 'Assignment removed';
        this.loadAssignments();
        this.refreshSections();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Delete assignment failed';
      },
    });
  }

  className(id: string | null | undefined): string {
    if (!id) return '';
    return this.classes.find((c) => c.id === id)?.name ?? '';
  }

  sectionLabel(id: string): string {
    const s = this.allSections.find((x) => x.id === id) || this.sections.find((x) => x.id === id);
    return s ? s.studentLabel || s.name : id;
  }

  subjectName(id: string | null): string {
    if (!id) return '—';
    return this.subjects.find((s) => s.id === id)?.name ?? id;
  }

  suggestedLabel(): string {
    if (!this.selectedClassId || !this.sectionDraft.name.trim()) return '';
    return `${this.className(this.selectedClassId)}-${this.sectionDraft.name.trim()}`;
  }
}
