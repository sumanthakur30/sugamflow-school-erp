import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Subscription, combineLatest } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { PortalContextService } from './portal-context.service';

@Component({
  selector: 'sf-portal-section',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './portal-section.component.html',
  styleUrls: ['../../shared/admin-page.scss', './portal-section.component.scss'],
})
export class PortalSectionComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);
  private readonly portalCtx = inject(PortalContextService);
  private sub?: Subscription;

  sectionKey = '';
  title = '';
  emptyMessage = 'No records.';
  loading = true;
  error = '';
  rows: any[] = [];

  ngOnInit(): void {
    this.sub = combineLatest([this.route.data, this.portalCtx.bootstrap$]).subscribe(
      ([data, boot]) => {
        this.sectionKey = String(data['sectionKey'] || '');
        const section = boot?.sections?.[this.sectionKey];
        this.title = section?.title || this.sectionKey;
        this.emptyMessage = section?.emptyMessage || 'No records.';
        const apiPath = section?.apiPath;
        if (!boot?.featureEnabled) {
          this.loading = false;
          this.rows = [];
          return;
        }
        if (!apiPath) {
          this.loading = false;
          this.error = 'Section is not configured (missing apiPath).';
          return;
        }
        this.load(apiPath);
      },
    );
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  private load(apiPath: string): void {
    this.loading = true;
    this.error = '';
    this.api.getItems<any>(apiPath).subscribe({
      next: (list) => {
        this.rows = list ?? [];
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.rows = [];
        this.error = err?.error?.message ?? 'Failed to load records';
      },
    });
  }

  rowTitle(row: any): string {
    const a = row?.answers || {};
    return (
      a.studentName ||
      a.fullName ||
      a.employeeName ||
      row?.admissionNo ||
      a.admissionNo ||
      row?.status ||
      row?.id ||
      'Record'
    );
  }

  rowMeta(row: any): string {
    const parts = [
      row?.status,
      row?.currentStepName,
      row?.answers?.feeHead,
      row?.answers?.examName,
      row?.answers?.className,
    ].filter(Boolean);
    return parts.join(' · ');
  }
}
