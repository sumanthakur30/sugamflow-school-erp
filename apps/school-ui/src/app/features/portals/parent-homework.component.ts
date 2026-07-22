import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-parent-homework',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './parent-homework.component.html',
  styleUrls: ['../../shared/admin-page.scss', './parent-homework.component.scss'],
})
export class ParentHomeworkComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  busy = false;
  error = '';
  status = '';
  items: any[] = [];
  drafts: Record<string, string> = {};

  ngOnInit(): void {
    this.load();
  }

  rowKey(item: any): string {
    return `${item.id}|${item.studentId || item.admissionNo || ''}`;
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any[]>('/api/exam/homework/mine').subscribe({
      next: (rows) => {
        this.items = rows ?? [];
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to load homework';
      },
    });
  }

  submit(item: any): void {
    const key = this.rowKey(item);
    const body = (this.drafts[key] || '').trim();
    if (!body) {
      this.error = 'Write a short response before submitting';
      return;
    }
    this.busy = true;
    this.error = '';
    this.status = '';
    this.api
      .post<any>(`/api/exam/homework/${item.id}/submissions/mine`, {
        studentId: item.studentId,
        admissionNo: item.admissionNo,
        body,
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.status = 'Submitted';
          this.drafts[key] = '';
          this.load();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Submit failed';
        },
      });
  }
}
