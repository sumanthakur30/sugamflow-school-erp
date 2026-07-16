import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';

type OpsTab = 'library' | 'hostel' | 'transport' | 'payroll';

@Component({
  selector: 'sf-ops',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './ops.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class OpsComponent implements OnInit {
  private readonly api = inject(ApiService);

  tab: OpsTab = 'library';
  loading = true;
  error = '';
  status = '';
  busy = false;

  library: any = null;
  hostel: any = null;
  transport: any = null;
  payroll: any = null;

  overdueDays = 10;
  bedsRequested = 1;
  occupiedBeds = 5;
  distanceKm = 12;
  basicPay = 25000;

  preview: any = null;

  ngOnInit(): void {
    this.reload();
  }

  setTab(t: OpsTab): void {
    this.tab = t;
    this.preview = null;
    this.error = '';
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    const paths = [
      this.api.get<any>('/api/library/ops/bootstrap'),
      this.api.get<any>('/api/hostel/ops/bootstrap'),
      this.api.get<any>('/api/transport/ops/bootstrap'),
      this.api.get<any>('/api/payroll/ops/bootstrap'),
    ];
    let done = 0;
    const finish = () => {
      done++;
      if (done === 4) {
        this.loading = false;
      }
    };
    paths[0].subscribe({
      next: (d) => {
        this.library = d;
        finish();
      },
      error: (err) => {
        this.library = null;
        this.error = err?.error?.message ?? 'Library ops bootstrap failed';
        finish();
      },
    });
    paths[1].subscribe({
      next: (d) => {
        this.hostel = d;
        finish();
      },
      error: () => {
        this.hostel = null;
        finish();
      },
    });
    paths[2].subscribe({
      next: (d) => {
        this.transport = d;
        finish();
      },
      error: () => {
        this.transport = null;
        finish();
      },
    });
    paths[3].subscribe({
      next: (d) => {
        this.payroll = d;
        finish();
      },
      error: () => {
        this.payroll = null;
        finish();
      },
    });
  }

  payload(def: any): any {
    return def?.payload || def || {};
  }

  previewLibraryFine(): void {
    this.busy = true;
    this.api.post<any>('/api/library/ops/fines/preview', { overdueDays: this.overdueDays }).subscribe({
      next: (p) => {
        this.busy = false;
        this.preview = p;
        this.status = `Library fine ${p.fineAmount} ${p.currency}`;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Fine preview failed';
      },
    });
  }

  previewHostelAllocation(): void {
    this.busy = true;
    this.api
      .post<any>('/api/hostel/ops/allocations/preview', {
        bedsRequested: this.bedsRequested,
        occupiedBeds: this.occupiedBeds,
      })
      .subscribe({
        next: (p) => {
          this.busy = false;
          this.preview = p;
          this.status = `Hostel canAllocate=${p.canAllocate} available=${p.availableBeds}`;
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Allocation preview failed';
        },
      });
  }

  previewTransportFare(): void {
    this.busy = true;
    this.api.post<any>('/api/transport/ops/fares/preview', { distanceKm: this.distanceKm }).subscribe({
      next: (p) => {
        this.busy = false;
        this.preview = p;
        this.status = `Transport fare ${p.fareAmount} ${p.currency}`;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Fare preview failed';
      },
    });
  }

  previewPayrollPayslip(): void {
    this.busy = true;
    this.api.post<any>('/api/payroll/ops/payslips/preview', { basicPay: this.basicPay }).subscribe({
      next: (p) => {
        this.busy = false;
        this.preview = p;
        this.status = `Payroll net ${p.netPay} ${p.currency}`;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Payslip preview failed';
      },
    });
  }
}
