import { Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';
import { StudentLookupComponent } from '../../shared/student-lookup/student-lookup.component';
import { StudentLookupRow } from '../../shared/student-lookup/student-lookup.models';

interface FeeHeadOption {
  key: string;
  label: string;
  defaultAmount: number | null;
}

interface SelectedHead {
  key: string;
  label: string;
  amount: string;
  custom: boolean;
}

interface PayRow {
  mode: string;
  amount: string;
  reference: string;
}

@Component({
  selector: 'sf-fee-counter',
  standalone: true,
  imports: [CommonModule, FormsModule, StudentLookupComponent],
  templateUrl: './fee-counter.component.html',
  styleUrls: ['../../shared/admin-page.scss', './fee-counter.component.scss'],
})
export class FeeCounterComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);

  @Output() collected = new EventEmitter<void>();

  readonly modes = [
    { value: 'CASH', label: 'Cash' },
    { value: 'UPI', label: 'UPI' },
    { value: 'CARD', label: 'Card' },
    { value: 'BANK_TRANSFER', label: 'Bank transfer' },
    { value: 'CHEQUE', label: 'Cheque' },
  ];

  student: StudentLookupRow | null = null;
  demandOutstanding: number | null = null;
  demandNote = '';
  heads: FeeHeadOption[] = [];
  headQuery = '';
  selected: SelectedHead[] = [];
  description = '';
  discount = '';
  lateFee = '';
  payRows: PayRow[] = [{ mode: 'CASH', amount: '', reference: '' }];
  paymentDate = '';
  remarks = '';
  previouslyPaid = 0;
  balanceReady = true;
  saving = false;
  error = '';
  status = '';
  receiptId = '';
  receiptNo = '';
  addingHead = false;
  newHeadLabel = '';
  newHeadAmount = '';
  savingHead = false;

  private structures: any[] = [];
  private structureAmounts = new Map<string, number>();
  private clientRequestId = '';
  private balanceTicket = 0;

  ngOnInit(): void {
    this.paymentDate = this.today();
    this.loadCatalog();
  }

  get sessionId(): string {
    return this.auth.getSessionId();
  }

  get canConfigureHeads(): boolean {
    const role = (this.auth.getRole() || '').toUpperCase();
    if (!role) return true;
    return /OWNER|ADMIN|PRINCIPAL|ACCOUNT|CLERK|FEE/.test(role);
  }

  get filteredHeads(): FeeHeadOption[] {
    const q = this.headQuery.trim().toLowerCase();
    if (!q) return this.heads;
    return this.heads.filter(
      (head) => head.label.toLowerCase().includes(q) || head.key.toLowerCase().includes(q),
    );
  }

  get feeAmount(): number {
    return this.selected.reduce((sum, line) => sum + this.money(line.amount), 0);
  }

  get discountAmount(): number {
    return this.money(this.discount);
  }

  get lateFeeAmount(): number {
    return this.money(this.lateFee);
  }

  get netPayable(): number {
    return this.round(this.feeAmount + this.lateFeeAmount - this.discountAmount);
  }

  get currentPayment(): number {
    return this.round(this.payRows.reduce((sum, row) => sum + this.money(row.amount), 0));
  }

  get balanceDue(): number {
    return this.round(this.netPayable - this.previouslyPaid);
  }

  get remaining(): number {
    return this.round(this.balanceDue - this.currentPayment);
  }

  get paymentStatus(): 'PAID' | 'PARTIAL' | 'PENDING' {
    const paid = this.round(this.previouslyPaid + this.currentPayment);
    if (paid <= 0) return 'PENDING';
    if (this.remaining <= 0 && this.netPayable > 0) return 'PAID';
    return 'PARTIAL';
  }

  onStudent(row: StudentLookupRow): void {
    this.student = row;
    this.receiptId = '';
    this.receiptNo = '';
    this.error = '';
    this.status = '';
    this.clientRequestId = '';
    this.demandOutstanding = null;
    this.demandNote = '';
    this.applyStructureAmounts();
    this.refreshPreviouslyPaid();
    const admissionNo = row.admissionNo;
    if (!admissionNo) return;
    this.api
      .get<any>(`/api/fee/students/fee-summary?admissionNo=${encodeURIComponent(admissionNo)}`)
      .subscribe({
        next: (summary) => {
          const pending = Number(summary?.pendingAmount);
          this.demandOutstanding = Number.isFinite(pending) ? pending : null;
          this.demandNote =
            this.demandOutstanding == null
              ? 'No demand balance on file. The fee heads below are billed on this receipt.'
              : '';
        },
        error: () => {
          this.demandNote = 'The demand balance could not be loaded. You can still collect this receipt.';
        },
      });
  }

  isSelected(key: string): boolean {
    return this.selected.some((line) => line.key === key);
  }

  toggleHead(head: FeeHeadOption): void {
    if (this.isSelected(head.key)) {
      this.selected = this.selected.filter((line) => line.key !== head.key);
    } else {
      const known = this.amountFor(head.key);
      this.selected = [
        ...this.selected,
        {
          key: head.key,
          label: head.label,
          amount: known == null ? '' : String(known),
          custom: false,
        },
      ];
    }
    this.clientRequestId = '';
    this.refreshPreviouslyPaid();
  }

  markCustom(line: SelectedHead, value: string): void {
    const known = this.amountFor(line.key);
    line.custom = known == null || String(known) !== String(value ?? '').trim();
    this.touch();
  }

  touch(): void {
    this.clientRequestId = '';
  }

  addPayRow(): void {
    this.payRows = [...this.payRows, { mode: 'UPI', amount: '', reference: '' }];
  }

  removePayRow(index: number): void {
    if (this.payRows.length === 1) {
      this.payRows = [{ mode: 'CASH', amount: '', reference: '' }];
      return;
    }
    this.payRows = this.payRows.filter((_, i) => i !== index);
  }

  payFullBalance(): void {
    if (this.balanceDue <= 0) return;
    const mode = this.payRows[0]?.mode || 'CASH';
    this.payRows = [{ mode, amount: String(this.balanceDue), reference: '' }];
  }

  needsReference(mode: string): boolean {
    return String(mode || '').toUpperCase() !== 'CASH';
  }

  amountHint(head: FeeHeadOption): string {
    const amount = this.amountFor(head.key);
    return amount == null ? 'Amount not set' : this.formatInr(amount);
  }

  collect(): void {
    if (!this.student?.admissionNo) {
      this.error = 'Select a student first.';
      return;
    }
    if (!this.selected.length) {
      this.error = 'Select at least one fee head.';
      return;
    }
    if (this.selected.some((line) => this.money(line.amount) <= 0)) {
      this.error = 'Enter an amount for each selected fee.';
      return;
    }
    if (this.discountAmount < 0 || this.lateFeeAmount < 0) {
      this.error = 'Discount and late fee cannot be negative.';
      return;
    }
    if (this.netPayable < 0) {
      this.error = 'Discount cannot be more than the fee plus late fee.';
      return;
    }
    if (!this.balanceReady) {
      this.error = 'Earlier payments are still loading.';
      return;
    }
    if (this.balanceDue <= 0) {
      this.error = 'This fee is already paid.';
      return;
    }
    const payments = this.payRows
      .map((row) => ({
        mode: row.mode,
        amount: this.money(row.amount),
        reference: row.reference.trim(),
      }))
      .filter((row) => row.amount > 0);
    if (!payments.length) {
      this.error = 'Enter the amount being paid.';
      return;
    }
    const missingRef = payments.find((row) => this.needsReference(row.mode) && !row.reference);
    if (missingRef) {
      this.error = `Enter a reference for ${this.modeLabel(missingRef.mode)}.`;
      return;
    }
    if (this.currentPayment > this.balanceDue) {
      this.error = 'Payment is more than the balance due.';
      return;
    }
    if (!this.clientRequestId) {
      this.clientRequestId = this.newRequestId();
    }
    this.saving = true;
    this.error = '';
    this.status = '';
    const month = new Date(this.paymentDate || this.today()).toLocaleString('en-IN', {
      month: 'long',
      year: 'numeric',
    });
    this.api
      .post<any>('/api/fee/collections', {
        formKey: 'fee_collection',
        answers: {
          studentName: this.student.fullName || '',
          admissionNo: this.student.admissionNo,
          classSection: this.student.classSection || '',
          rollNo: this.student.rollNo || '',
          mobile: this.student.mobile || '',
          email: this.student.email || '',
          feeHead: this.selected.map((line) => line.label).join(', '),
          feeLines: this.selected.map((line) => ({
            key: line.key,
            label: line.label,
            amount: this.money(line.amount),
          })),
          feeMonth: month,
          description: this.description.trim(),
          academicSession: this.sessionId,
          feeAmount: this.feeAmount,
          discount: this.discountAmount,
          lateFee: this.lateFeeAmount,
          payments,
          amount: this.currentPayment,
          paymentMode: payments.length === 1 ? payments[0].mode : 'SPLIT',
          paymentDate: this.paymentDate || this.today(),
          remarks: this.remarks.trim(),
          billKey: this.billKey(),
          clientRequestId: this.clientRequestId,
        },
      })
      .subscribe({
        next: (row) => {
          this.saving = false;
          this.clientRequestId = '';
          this.receiptId = String(row?.id || '');
          this.receiptNo = String(row?.answers?.receiptNo || '');
          this.status =
            row?.status === 'APPROVED'
              ? 'Payment saved. The receipt is ready.'
              : 'Payment saved and is waiting for approval.';
          this.payRows = [{ mode: 'CASH', amount: '', reference: '' }];
          this.remarks = '';
          this.refreshPreviouslyPaid();
          this.collected.emit();
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message || 'The payment was not saved.';
        },
      });
  }

  printReceipt(): void {
    this.openReceipt(false);
  }

  downloadReceipt(): void {
    this.openReceipt(true);
  }

  saveHead(): void {
    const label = this.newHeadLabel.trim();
    if (!label) {
      this.error = 'Enter a name for the fee head.';
      return;
    }
    const amount = this.money(this.newHeadAmount);
    const key = label
      .toUpperCase()
      .replace(/[^A-Z0-9]+/g, '_')
      .replace(/^_|_$/g, '');
    if (!key) {
      this.error = 'Enter a name for the fee head.';
      return;
    }
    this.savingHead = true;
    this.error = '';
    const body: Record<string, unknown> = { label };
    if (amount > 0) body['defaultAmount'] = amount;
    this.api.put(`/api/fee/finance/heads/${encodeURIComponent(key)}`, body).subscribe({
      next: () => {
        this.savingHead = false;
        this.addingHead = false;
        this.newHeadLabel = '';
        this.newHeadAmount = '';
        this.status = `${label} is available in the fee list.`;
        this.loadCatalog();
      },
      error: (err) => {
        this.savingHead = false;
        this.error = err?.error?.message || 'The fee head was not saved.';
      },
    });
  }

  formatInr(value: number | null): string {
    if (value == null || Number.isNaN(value)) return '—';
    return value.toLocaleString('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2,
    });
  }

  private loadCatalog(): void {
    forkJoin({
      heads: this.api.get<any[]>('/api/fee/finance/heads').pipe(catchError(() => of([] as any[]))),
      structures: this.api
        .get<any[]>('/api/fee/finance/structures')
        .pipe(catchError(() => of([] as any[]))),
    }).subscribe(({ heads, structures }) => {
      const list = Array.isArray(heads) ? heads : [];
      this.heads = list
        .map((row) => this.readHead(row))
        .filter((head): head is FeeHeadOption => !!head)
        .sort((a, b) => a.label.localeCompare(b.label));
      this.structures = Array.isArray(structures) ? structures : [];
      this.applyStructureAmounts();
    });
  }

  private readHead(row: any): FeeHeadOption | null {
    const payload = row?.payload && typeof row.payload === 'object' ? row.payload : {};
    const key = String(row?.definitionKey || payload.definitionKey || payload.code || '').trim();
    const label = String(payload.label || row?.label || payload.name || key).trim();
    const active = payload.active !== false && payload.status !== 'INACTIVE' && row?.status !== 'INACTIVE';
    if (!key || !label || !active) return null;
    const raw = Number(payload.defaultAmount ?? payload.amount);
    return {
      key,
      label,
      defaultAmount: Number.isFinite(raw) && raw > 0 ? raw : null,
    };
  }

  private applyStructureAmounts(): void {
    this.structureAmounts = this.indexStructures(
      this.structures,
      this.student?.classSection || '',
      this.sessionId,
    );
    this.selected = this.selected.map((line) => {
      if (line.custom) return line;
      const known = this.amountFor(line.key);
      return known == null ? line : { ...line, amount: String(known) };
    });
  }

  private indexStructures(rows: any[], classSection: string, sessionId: string): Map<string, number> {
    const scored: Array<{ score: number; row: any }> = [];
    for (const row of rows) {
      const payload = row?.payload || {};
      const eligibility = payload.eligibility || {};
      const cls = String(eligibility.classSection || payload.classSection || '')
        .trim()
        .toLowerCase();
      const session = String(eligibility.academicSessionId || '').trim();
      const student = classSection.trim().toLowerCase();
      let score = 0;
      if (!cls && !session) score = 1;
      else if (cls && student && (student === cls || student.includes(cls) || cls.includes(student))) {
        score = !session || session === sessionId ? 3 : 2;
      }
      if (score > 0) scored.push({ score, row });
    }
    scored.sort((a, b) => b.score - a.score);
    const map = new Map<string, number>();
    for (const item of scored) {
      const lines = item.row?.payload?.lines;
      if (!Array.isArray(lines)) continue;
      for (const line of lines) {
        if (line?.active === false || String(line?.status || '').toUpperCase() === 'INACTIVE') continue;
        const key = String(line?.headKey || '')
          .trim()
          .toUpperCase();
        const amount = Number(line?.amount);
        if (key && Number.isFinite(amount) && amount > 0 && !map.has(key)) {
          map.set(key, amount);
        }
      }
    }
    return map;
  }

  private amountFor(key: string): number | null {
    const structured = this.structureAmounts.get(key.toUpperCase());
    if (structured != null && structured > 0) return structured;
    const head = this.heads.find((item) => item.key.toUpperCase() === key.toUpperCase());
    return head?.defaultAmount ?? null;
  }

  private refreshPreviouslyPaid(): void {
    const admissionNo = this.student?.admissionNo || '';
    const billKey = this.billKey();
    if (!admissionNo || !billKey) {
      this.previouslyPaid = 0;
      this.balanceReady = true;
      return;
    }
    const ticket = ++this.balanceTicket;
    this.balanceReady = false;
    const params = new URLSearchParams({ admissionNo, billKey });
    this.api.get<any>(`/api/fee/collections/bill-balance?${params.toString()}`).subscribe({
      next: (row) => {
        if (ticket !== this.balanceTicket) return;
        const paid = Number(row?.previouslyPaid);
        this.previouslyPaid = Number.isFinite(paid) ? this.round(paid) : 0;
        this.balanceReady = true;
      },
      error: () => {
        if (ticket !== this.balanceTicket) return;
        this.previouslyPaid = 0;
        this.balanceReady = true;
        this.demandNote = 'Earlier payments for this fee could not be loaded. Check the balance before saving.';
      },
    });
  }

  private billKey(): string {
    const keys = this.selected
      .map((line) => line.key.trim().toUpperCase())
      .filter(Boolean)
      .sort();
    if (!keys.length || !this.student?.admissionNo) return '';
    const raw = `${this.sessionId.trim()}|${this.student.admissionNo.trim()}|${keys.join(',')}`;
    return raw.length > 240 ? raw.slice(0, 240) : raw;
  }

  private openReceipt(download: boolean): void {
    if (!this.receiptId) return;
    this.api.getBlob(`/api/fee/collections/${this.receiptId}/receipt`).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        if (download) {
          const link = document.createElement('a');
          link.href = url;
          link.download = `${this.receiptNo || 'fee-receipt'}.pdf`;
          link.click();
        } else {
          window.open(url, '_blank', 'noopener');
        }
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => {
        this.error = 'The receipt file is not ready yet.';
      },
    });
  }

  private modeLabel(mode: string): string {
    return this.modes.find((item) => item.value === mode)?.label || mode;
  }

  private money(raw: string): number {
    const n = Number(String(raw ?? '').trim());
    if (!Number.isFinite(n) || n < 0) return 0;
    return this.round(n);
  }

  private round(value: number): number {
    return Math.round((value + Number.EPSILON) * 100) / 100;
  }

  private today(): string {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${now.getFullYear()}-${month}-${day}`;
  }

  private newRequestId(): string {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID();
    return `fee-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }
}
