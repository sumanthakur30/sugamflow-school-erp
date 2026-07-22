import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Subscription, combineLatest } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { PortalContextService } from './portal-context.service';

interface PortalRow extends Record<string, any> {
  id?: string;
  status?: string;
  studentName?: string;
  examName?: string;
  admissionNo?: string;
  marksObtained?: number | string;
  maxMarks?: number | string;
  answers?: Record<string, any>;
}

interface DisplayColumn {
  label: string;
  kind: 'text' | 'date' | 'currency' | 'status' | 'score';
  keys: string[];
}

const SECTION_DESCRIPTIONS: Record<string, string> = {
  attendance: 'Review daily attendance and absence records.',
  fees: 'View fee details, payment status, and available online payment options.',
  exams: 'Track examination results, marks, grades, and published outcomes.',
  students: 'Find students and view their class and contact information.',
};

const SECTION_COLUMNS: Record<string, DisplayColumn[]> = {
  attendance: [
    { label: 'Student', kind: 'text', keys: ['studentName', 'answers.studentName', 'answers.fullName'] },
    { label: 'Date', kind: 'date', keys: ['attendanceDate', 'date', 'answers.attendanceDate', 'answers.date'] },
    { label: 'Attendance', kind: 'status', keys: ['markStatus', 'attendanceStatus', 'status'] },
    { label: 'Class', kind: 'text', keys: ['classSection', 'className', 'answers.className', 'answers.classSection'] },
  ],
  fees: [
    { label: 'Fee', kind: 'text', keys: ['answers.feeHead', 'feeHead', 'title', 'description'] },
    { label: 'Due date', kind: 'date', keys: ['answers.dueDate', 'dueDate', 'date', 'createdAt'] },
    { label: 'Amount', kind: 'currency', keys: ['answers.amount', 'amount'] },
    { label: 'Payment status', kind: 'status', keys: ['status', 'paymentStatus'] },
  ],
  exams: [
    { label: 'Examination', kind: 'text', keys: ['examName', 'answers.examName', 'termName', 'termKey'] },
    { label: 'Subject', kind: 'text', keys: ['subjectName', 'subject', 'answers.subjectName'] },
    { label: 'Date', kind: 'date', keys: ['examDate', 'date', 'answers.examDate'] },
    { label: 'Score', kind: 'score', keys: ['marksObtained'] },
    { label: 'Grade', kind: 'text', keys: ['grade'] },
    { label: 'Status', kind: 'status', keys: ['status', 'resultStatus'] },
  ],
  students: [
    { label: 'Student', kind: 'text', keys: ['studentName', 'fullName', 'answers.studentName', 'answers.fullName'] },
    { label: 'Admission no.', kind: 'text', keys: ['admissionNo', 'answers.admissionNo'] },
    { label: 'Class', kind: 'text', keys: ['classSection', 'className', 'answers.className', 'answers.classSection'] },
    { label: 'Contact', kind: 'text', keys: ['guardianName', 'parentName', 'mobile', 'answers.mobile'] },
    { label: 'Status', kind: 'status', keys: ['status', 'studentStatus'] },
  ],
};

@Component({
  selector: 'sf-portal-section',
  standalone: true,
  imports: [CommonModule, FormsModule],
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
  status = '';
  busy = false;
  rows: PortalRow[] = [];
  searchQuery = '';
  statusFilter = '';
  private apiPath = '';

  ngOnInit(): void {
    this.sub = combineLatest([this.route.data, this.portalCtx.bootstrap$]).subscribe(
      ([data, boot]) => {
        this.sectionKey = String(data['sectionKey'] || '');
        const section = boot?.sections?.[this.sectionKey];
        this.title = section?.title || this.sectionKey;
        this.emptyMessage = section?.emptyMessage || this.defaultEmptyMessage;
        this.apiPath = section?.apiPath || '';
        if (!boot?.featureEnabled) {
          this.loading = false;
          this.rows = [];
          return;
        }
        if (!this.apiPath) {
          this.loading = false;
          this.error = 'This section is temporarily unavailable. Please try again later.';
          return;
        }
        this.load(this.apiPath);
      },
    );
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  get description(): string {
    return SECTION_DESCRIPTIONS[this.sectionKey] || 'View and manage the latest school records.';
  }

  get defaultEmptyMessage(): string {
    const messages: Record<string, string> = {
      attendance: 'No attendance records are available yet.',
      fees: 'No fee records are available.',
      exams: 'No examination results are available yet.',
      students: 'No students are available for this class.',
    };
    return messages[this.sectionKey] || 'No records are available.';
  }

  get columns(): DisplayColumn[] {
    return SECTION_COLUMNS[this.sectionKey] || SECTION_COLUMNS['students'];
  }

  get filteredRows(): PortalRow[] {
    const query = this.searchQuery.trim().toLocaleLowerCase();
    return this.rows.filter((row) => {
      const matchesStatus = !this.statusFilter || this.statusForRow(row) === this.statusFilter;
      if (!matchesStatus) {
        return false;
      }
      if (!query) {
        return true;
      }
      return this.columns.some((column) =>
        String(this.rawCellValue(row, column) ?? '').toLocaleLowerCase().includes(query),
      );
    });
  }

  get statusOptions(): string[] {
    return [...new Set(this.rows.map((row) => this.statusForRow(row)).filter(Boolean))].sort();
  }

  get hasActiveFilters(): boolean {
    return Boolean(this.searchQuery.trim() || this.statusFilter);
  }

  get canRefresh(): boolean {
    return Boolean(this.apiPath);
  }

  refresh(): void {
    if (this.apiPath && !this.loading) {
      this.load(this.apiPath);
    }
  }

  clearFilters(): void {
    this.searchQuery = '';
    this.statusFilter = '';
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
        this.error = err?.error?.message ?? 'We could not load these records. Please try again.';
      },
    });
  }

  canPay(row: PortalRow): boolean {
    if (this.sectionKey !== 'fees') {
      return false;
    }
    const status = String(row?.status || '').toUpperCase();
    return status !== 'APPROVED' && status !== 'REJECTED';
  }

  payOnline(row: PortalRow): void {
    const amount = Number(row?.answers?.['amount'] ?? 0);
    if (!amount || amount <= 0) {
      this.error = 'A payable amount is not available for this fee.';
      return;
    }
    this.busy = true;
    this.error = '';
    this.status = '';
    this.api
      .post<any>('/api/fee/finance/payments/intents', {
        amount,
        studentRef: row?.answers?.['admissionNo'] || row?.admissionNo,
        collectionId: row?.id,
        providerKey: 'simulated',
        mode: 'UPI',
        idempotencyKey: `fee-pay-${row.id}-${Date.now()}`,
      })
      .subscribe({
        next: (intent) => {
          if (intent.checkoutMode === 'RAZORPAY_CHECKOUT' && intent.checkout) {
            this.openRazorpay(intent, row);
            return;
          }
          // Simulate path (local/dev): capture immediately so parent sees APPROVED + receipt.
          this.api
            .post(`/api/fee/finance/payments/intents/${intent.id}/simulate-capture`, {})
            .subscribe({
              next: () => {
                this.busy = false;
                this.status = `Payment successful. Reference: ${intent.referenceNo}`;
                if (this.apiPath) {
                  this.load(this.apiPath);
                }
              },
              error: (err) => {
                this.busy = false;
                this.error = err?.error?.message ?? 'The payment could not be completed. Please try again.';
              },
            });
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'We could not start the payment. Please try again.';
        },
      });
  }

  private openRazorpay(intent: any, row: PortalRow): void {
    const checkout = intent.checkout || {};
    const key = intent.publicKey || checkout.razorpayKeyId;
    const orderId = checkout.razorpayOrderId || intent.gatewayOrderId;
    const w = window as any;
    const finish = (paymentId: string) => {
      this.api
        .post(`/api/fee/finance/payments/intents/${intent.id}/confirm`, {
          razorpayPaymentId: paymentId,
          gatewayTxnId: paymentId,
        })
        .subscribe({
          next: () => {
            this.busy = false;
            this.status = `Payment successful. Reference: ${intent.referenceNo}`;
            if (this.apiPath) {
              this.load(this.apiPath);
            }
          },
          error: (err) => {
            this.busy = false;
            this.error = err?.error?.message ?? 'We could not confirm the payment. Please contact the school office.';
          },
        });
    };
    const launch = () => {
      const rzp = new w.Razorpay({
        key,
        amount: checkout.amountPaise,
        currency: checkout.currency || 'INR',
        name: 'School Fee Payment',
        description: this.rowTitle(row),
        order_id: orderId,
        handler: (response: any) => finish(response.razorpay_payment_id),
        modal: {
          ondismiss: () => {
            this.busy = false;
          },
        },
      });
      rzp.open();
    };
    if (!key || !orderId) {
      this.busy = false;
      this.error = 'Online payment is temporarily unavailable. Please contact the school office.';
      return;
    }
    if (w.Razorpay) {
      launch();
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.onload = () => launch();
    script.onerror = () => {
      this.busy = false;
      this.error = 'The secure payment window could not be loaded. Please try again.';
    };
    document.body.appendChild(script);
  }

  rowTitle(row: PortalRow): string {
    const a = row?.answers || {};
    return (
      row?.studentName ||
      row?.examName ||
      a['studentName'] ||
      a['fullName'] ||
      a['employeeName'] ||
      row?.admissionNo ||
      a['admissionNo'] ||
      this.valueAt(row, 'answers.feeHead') ||
      'School record'
    );
  }

  displayValue(row: PortalRow, column: DisplayColumn): string {
    const value = this.rawCellValue(row, column);
    if (column.kind === 'date') {
      return this.formatDate(value);
    }
    if (column.kind === 'currency') {
      return this.formatCurrency(value);
    }
    if (column.kind === 'status') {
      return this.readableStatus(value);
    }
    if (column.kind === 'score') {
      const maximum = row?.maxMarks ?? row?.answers?.['maxMarks'];
      return value != null && value !== '' ? `${value}${maximum != null ? ` / ${maximum}` : ''}` : '—';
    }
    return value != null && String(value).trim() ? String(value) : '—';
  }

  statusForRow(row: PortalRow): string {
    const statusColumn = this.columns.find((column) => column.kind === 'status');
    return statusColumn ? this.readableStatus(this.rawCellValue(row, statusColumn)) : '';
  }

  statusTone(value: unknown): string {
    const status = String(value ?? '').toUpperCase();
    if (['APPROVED', 'PAID', 'PRESENT', 'ACTIVE', 'PASSED', 'PUBLISHED', 'COMPLETED'].some((item) => status.includes(item))) {
      return 'positive';
    }
    if (['REJECTED', 'FAILED', 'ABSENT', 'OVERDUE', 'CANCELLED', 'INACTIVE'].some((item) => status.includes(item))) {
      return 'negative';
    }
    if (['PENDING', 'DUE', 'LATE', 'PROCESSING', 'PARTIAL'].some((item) => status.includes(item))) {
      return 'warning';
    }
    return 'neutral';
  }

  trackRow(_index: number, row: PortalRow): unknown {
    return row?.id || row?.admissionNo || row?.answers?.['admissionNo'] || row;
  }

  private rawCellValue(row: PortalRow, column: DisplayColumn): unknown {
    for (const key of column.keys) {
      const value = this.valueAt(row, key);
      if (value != null && value !== '') {
        return value;
      }
    }
    return null;
  }

  private valueAt(row: PortalRow, path: string): unknown {
    return path.split('.').reduce<unknown>((value, key) => {
      if (value && typeof value === 'object') {
        return (value as Record<string, unknown>)[key];
      }
      return undefined;
    }, row);
  }

  private readableStatus(value: unknown): string {
    if (value == null || String(value).trim() === '') {
      return 'Not available';
    }
    return String(value)
      .trim()
      .replace(/[_-]+/g, ' ')
      .toLocaleLowerCase()
      .replace(/\b\w/g, (letter) => letter.toLocaleUpperCase());
  }

  private formatDate(value: unknown): string {
    if (!value) {
      return '—';
    }
    const date = new Date(String(value));
    return Number.isNaN(date.getTime())
      ? String(value)
      : new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }).format(date);
  }

  private formatCurrency(value: unknown): string {
    const amount = Number(value);
    return Number.isFinite(amount)
      ? new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(amount)
      : '—';
  }
}
