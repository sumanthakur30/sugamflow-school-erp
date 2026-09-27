import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { TenantContextService } from '../../core/tenant-context.service';

type DeskTab = 'defaulters' | 'followups' | 'demand';

@Component({
  selector: 'sf-fee-desk',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './fee-desk.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class FeeDeskComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly tenantContext = inject(TenantContextService);
  private campusReadySub?: Subscription;

  tab: DeskTab = 'defaulters';
  loading = false;
  busy = false;
  error = '';
  status = '';
  defaulters: any[] = [];
  followUps: any[] = [];
  structures: any[] = [];
  demand: any = null;

  structureKey = '';
  studentRef = '';
  studentName = '';
  classSection = '';
  periodKey = '';

  ngOnInit(): void {
    this.campusReadySub = this.tenantContext.whenCampusReady().subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.campusReadySub?.unsubscribe();
  }

  setTab(tab: DeskTab): void {
    this.tab = tab;
    this.error = '';
    this.status = '';
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any[]>('/api/fee/defaulters').subscribe({
      next: (rows) => {
        this.defaulters = rows || [];
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Could not load fee defaulters';
      },
    });
    this.api.get<any[]>('/api/fee/due-reminders/history').subscribe({
      next: (rows) => {
        this.followUps = rows || [];
      },
      error: () => {
        this.followUps = [];
      },
    });
    this.api.get<any>('/api/fee/finance/bootstrap').subscribe({
      next: (boot) => {
        this.structures = boot?.structures || boot?.feeStructures || [];
        if (!this.structureKey && this.structures.length) {
          this.structureKey = String(
            this.structures[0].definitionKey || this.structures[0].key || '',
          );
        }
      },
      error: () => {
        this.structures = [];
      },
    });
  }

  sendReminders(): void {
    this.busy = true;
    this.error = '';
    this.status = '';
    this.api.post<any>('/api/fee/due-reminders/run', {}).subscribe({
      next: (result) => {
        this.busy = false;
        this.status = `Reminders sent: ${result?.reminded ?? result?.sent ?? 0}`;
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Could not send fee reminders';
      },
    });
  }

  previewDemand(): void {
    this.busy = true;
    this.error = '';
    this.demand = null;
    this.api.post<any>('/api/fee/finance/demands/preview', this.demandBody()).subscribe({
      next: (row) => {
        this.busy = false;
        this.demand = row;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Demand preview failed';
      },
    });
  }

  createDemand(): void {
    this.busy = true;
    this.error = '';
    this.api.post<any>('/api/fee/finance/demands', this.demandBody()).subscribe({
      next: () => {
        this.busy = false;
        this.status = 'Demand bill created';
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Could not create demand bill';
      },
    });
  }

  amountOf(row: any): string {
    const answers = row?.answers || {};
    const value = row?.amount ?? answers.amount ?? row?.netAmount;
    return value == null ? '—' : String(value);
  }

  nameOf(row: any): string {
    const answers = row?.answers || {};
    return row?.studentName || answers.studentName || answers.fullName || row?.admissionNo || '—';
  }

  private demandBody(): Record<string, unknown> {
    return {
      structureKey: this.structureKey || undefined,
      studentRef: this.studentRef || undefined,
      studentName: this.studentName || undefined,
      classSection: this.classSection || undefined,
      periodKey: this.periodKey || undefined,
    };
  }
}
