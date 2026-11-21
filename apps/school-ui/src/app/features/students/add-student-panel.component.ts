import { Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-add-student-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './add-student-panel.component.html',
})
export class AddStudentPanelComponent implements OnInit {
  private readonly api = inject(ApiService);

  @Output() readonly closed = new EventEmitter<void>();
  @Output() readonly created = new EventEmitter<string>();

  fullName = '';
  dateOfBirth = '';
  gender = '';
  className = '';
  section = '';
  parentName = '';
  mobile = '';
  classes: Array<{ id: string; name: string }> = [];
  sections: Array<{ id: string; name: string; classId?: string }> = [];
  saving = false;
  error = '';
  savedId = '';
  savedNo = '';

  ngOnInit(): void {
    this.api.get<any[]>('/api/academic/classes').subscribe({
      next: (rows) => {
        this.classes = (rows || [])
          .map((row) => ({
            id: String(row.id || ''),
            name: String(row.name || row.className || row.code || '').trim(),
          }))
          .filter((row) => row.name);
      },
      error: () => {
        this.classes = [];
      },
    });
    this.api.get<any[]>('/api/academic/sections').subscribe({
      next: (rows) => {
        this.sections = (rows || [])
          .map((row) => ({
            id: String(row.id || ''),
            name: String(row.name || row.sectionName || row.code || '').trim(),
            classId: row.classId ? String(row.classId) : undefined,
          }))
          .filter((row) => row.name);
      },
      error: () => {
        this.sections = [];
      },
    });
  }

  save(): void {
    this.error = '';
    if (!this.fullName.trim() || !this.className.trim() || !this.parentName.trim() || !this.mobile.trim()) {
      this.error = 'Student name, class, parent name, and mobile are required.';
      return;
    }
    this.saving = true;
    this.api
      .post<any>('/api/student/students', {
        fullName: this.fullName.trim(),
        className: this.className.trim(),
        section: this.section.trim(),
        parentName: this.parentName.trim(),
        mobile: this.mobile.trim(),
        dateOfBirth: this.dateOfBirth,
        gender: this.gender,
      })
      .subscribe({
        next: (row) => {
          this.saving = false;
          this.savedId = String(row?.id || '');
          this.savedNo = String(row?.admissionNo || '');
          if (this.savedId) {
            this.created.emit(this.savedId);
          }
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message || 'The student was not saved.';
        },
      });
  }
}
