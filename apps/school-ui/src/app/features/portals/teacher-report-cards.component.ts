import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { ReportCardInsightsComponent } from '../../shared/report-card-insights/report-card-insights.component';

@Component({
  selector: 'sf-teacher-report-cards',
  standalone: true,
  imports: [CommonModule, FormsModule, ReportCardInsightsComponent],
  templateUrl: './teacher-report-cards.component.html',
  styleUrls: ['../../shared/admin-page.scss', './teacher-report-cards.component.scss'],
})
export class TeacherReportCardsComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  busy = false;
  error = '';
  status = '';

  sections: any[] = [];
  sectionId = '';
  termKey = 'TERM1';
  publishedOnly = true;

  report: any = null;
  insights: Record<string, any> = {};
  insightLoading = new Set<string>();
  expandedInsightKey = '';

  ngOnInit(): void {
    this.api.get<any[]>('/api/academic/sections').subscribe({
      next: (items) => {
        this.sections = items ?? [];
        if (this.sections.length) {
          this.sectionId = this.sections[0].id;
        }
        this.loading = false;
        if (this.sectionId) {
          this.load();
        }
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to load sections';
      },
    });
  }

  load(): void {
    if (!this.sectionId || !this.termKey.trim()) {
      return;
    }
    this.busy = true;
    this.error = '';
    this.status = '';
    const path =
      `/api/exam/report-cards?sectionId=${encodeURIComponent(this.sectionId)}` +
      `&termKey=${encodeURIComponent(this.termKey.trim())}` +
      `&publishedOnly=${this.publishedOnly}`;
    this.api.get<any>(path).subscribe({
      next: (data) => {
        this.report = data;
        this.insights = {};
        this.expandedInsightKey = '';
        this.busy = false;
      },
      error: (err) => {
        this.busy = false;
        this.report = null;
        this.error = err?.error?.message ?? 'Failed to load report cards';
      },
    });
  }

  subjectCell(student: any, subjectId: string): string {
    const s = (student?.subjects ?? []).find((x: any) => x.subjectId === subjectId);
    if (!s || s.marksObtained == null) {
      return '–';
    }
    return `${s.marksObtained}/${s.maxMarks ?? '?'}`;
  }

  insightKey(student: any): string {
    return String(student?.studentId || student?.admissionNo || '');
  }

  insightFor(student: any): any {
    return this.insights[this.insightKey(student)];
  }

  isInsightLoading(student: any): boolean {
    return this.insightLoading.has(this.insightKey(student));
  }

  toggleInsights(student: any): void {
    const key = this.insightKey(student);
    if (this.expandedInsightKey === key) {
      this.expandedInsightKey = '';
      return;
    }
    this.expandedInsightKey = key;
    if (this.insights[key] || this.insightLoading.has(key)) return;

    this.insightLoading.add(key);
    const studentParam = student.studentId
      ? `studentId=${encodeURIComponent(student.studentId)}`
      : `admissionNo=${encodeURIComponent(student.admissionNo)}`;
    const path =
      `/api/exam/report-cards/student/insights?sectionId=${encodeURIComponent(this.sectionId)}` +
      `&termKey=${encodeURIComponent(this.termKey.trim())}&${studentParam}` +
      `&publishedOnly=${this.publishedOnly}`;
    this.api.get<any>(path).subscribe({
      next: (insight) => {
        this.insights[key] = insight;
        this.insightLoading.delete(key);
      },
      error: (err) => {
        this.insightLoading.delete(key);
        this.expandedInsightKey = '';
        this.error = err?.error?.message ?? 'Could not generate report-card insights';
      },
    });
  }

  downloadPdf(student: any): void {
    const key = student.studentId
      ? `studentId=${encodeURIComponent(student.studentId)}`
      : `admissionNo=${encodeURIComponent(student.admissionNo)}`;
    const path =
      `/api/exam/report-cards/student.pdf?sectionId=${encodeURIComponent(this.sectionId)}` +
      `&termKey=${encodeURIComponent(this.termKey.trim())}&${key}` +
      `&publishedOnly=${this.publishedOnly}`;
    const fileName = `report-card-${student.admissionNo || 'student'}-${this.termKey.trim()}.pdf`;
    this.busy = true;
    this.error = '';
    this.api.getBlob(path).subscribe({
      next: (blob) => {
        this.busy = false;
        this.saveBlob(blob, fileName);
      },
      error: () => {
        this.busy = false;
        this.error = 'Could not download report card PDF';
      },
    });
  }

  private saveBlob(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }
}
