import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ReportCardInsightsComponent } from '../../shared/report-card-insights/report-card-insights.component';

@Component({
  selector: 'sf-parent-report-cards',
  standalone: true,
  imports: [CommonModule, ReportCardInsightsComponent],
  templateUrl: './parent-report-cards.component.html',
  styleUrls: ['../../shared/admin-page.scss', './parent-report-cards.component.scss'],
})
export class ParentReportCardsComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  busy = false;
  error = '';
  cards: any[] = [];
  insights: Record<string, any> = {};
  insightLoading = new Set<string>();
  expandedInsightKey = '';

  ngOnInit(): void {
    this.load();
  }

  cardKey(card: any): string {
    const student = card?.student || {};
    return `${card?.sectionId}|${card?.termKey}|${student.studentId || student.admissionNo || ''}`;
  }

  insightFor(card: any): any {
    return this.insights[this.cardKey(card)];
  }

  isInsightLoading(card: any): boolean {
    return this.insightLoading.has(this.cardKey(card));
  }

  toggleInsights(card: any): void {
    const key = this.cardKey(card);
    if (this.expandedInsightKey === key) {
      this.expandedInsightKey = '';
      return;
    }
    this.expandedInsightKey = key;
    if (this.insights[key] || this.insightLoading.has(key)) return;

    const student = card.student || {};
    const studentParam = student.studentId
      ? `studentId=${encodeURIComponent(student.studentId)}`
      : `admissionNo=${encodeURIComponent(student.admissionNo)}`;
    const path =
      `/api/exam/report-cards/mine/insights?sectionId=${encodeURIComponent(card.sectionId)}` +
      `&termKey=${encodeURIComponent(card.termKey)}&${studentParam}`;
    this.insightLoading.add(key);
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

  load(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any[]>('/api/exam/report-cards/mine').subscribe({
      next: (items) => {
        this.cards = items ?? [];
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.cards = [];
        this.error = err?.error?.message ?? 'Failed to load report cards';
      },
    });
  }

  download(card: any): void {
    const student = card.student || {};
    const key = student.studentId
      ? `studentId=${encodeURIComponent(student.studentId)}`
      : `admissionNo=${encodeURIComponent(student.admissionNo)}`;
    const path =
      `/api/exam/report-cards/mine.pdf?sectionId=${encodeURIComponent(card.sectionId)}` +
      `&termKey=${encodeURIComponent(card.termKey)}&${key}`;
    const fileName = `report-card-${student.admissionNo || 'student'}-${card.termKey}.pdf`;
    this.busy = true;
    this.error = '';
    this.api.getBlob(path).subscribe({
      next: (blob) => {
        this.busy = false;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.busy = false;
        this.error = 'Could not download report card PDF';
      },
    });
  }
}
