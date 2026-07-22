import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-student-360',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './student-360.component.html',
  styleUrls: ['../../shared/admin-page.scss', './student-360.component.scss'],
})
export class Student360Component implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  loading = true;
  error = '';
  tab = 'profile';
  data: any = null;
  studentId = '';

  ngOnInit(): void {
    this.route.paramMap.subscribe((pm) => {
      this.studentId = pm.get('id') || '';
      if (this.studentId) {
        this.load();
      }
    });
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>(`/api/student/students/${this.studentId}/360`).subscribe({
      next: (d) => {
        this.data = d;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? err?.message ?? 'Failed to load Student 360';
      },
    });
  }

  setTab(t: string): void {
    this.tab = t;
  }

  houseName(): string {
    return String(this.data?.summary?.house || this.data?.student?.answers?.house || '').trim();
  }

  houseClass(): string {
    const value = this.houseName().toLowerCase();
    return ['red', 'blue', 'green', 'yellow'].includes(value) ? `house-${value}` : 'house-other';
  }

  admissionNo(): string {
    return String(
      this.data?.summary?.admissionNo ||
        this.data?.student?.admissionNo ||
        this.data?.student?.answers?.admissionNo ||
        '',
    ).trim();
  }

  admissionQuery(): Record<string, string> {
    const admissionNo = this.admissionNo();
    return admissionNo ? { admissionNo } : {};
  }

  profileEntries(): Array<{ label: string; value: string }> {
    const answers = this.data?.student?.answers ?? {};
    return Object.entries(answers)
      .filter(([, value]) => value != null && value !== '' && typeof value !== 'object')
      .map(([key, value]) => ({
        label: this.prettyLabel(key),
        value: typeof value === 'boolean' ? (value ? 'Yes' : 'No') : String(value),
      }));
  }

  private prettyLabel(key: string): string {
    const map: Record<string, string> = {
      fullName: 'Full name',
      studentName: 'Student name',
      admissionNo: 'Admission No',
      classSection: 'Class / section',
      rollNo: 'Roll No',
      parentName: 'Parent',
      email: 'Email',
      mobile: 'Mobile',
      gender: 'Gender',
      house: 'House',
      dob: 'Date of birth',
    };
    if (map[key]) return map[key];
    return key
      .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
      .replace(/[_-]+/g, ' ')
      .replace(/^./, (c) => c.toUpperCase());
  }
}
