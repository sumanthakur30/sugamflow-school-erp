import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';
import { matchFeeStructure } from './fee-structure-match';

interface FeeCategory {
  headKey: string;
  label: string;
  frequency: string;
}

interface SchoolClass {
  name: string;
  sequenceNo: number;
}

interface AmountCell {
  amount: string;
  active: boolean;
}

interface KnownHead {
  key: string;
  label: string;
  frequency: string;
}

const STANDARD_FEES: FeeCategory[] = [
  { headKey: 'ADMISSION', label: 'Admission Fee', frequency: 'OT' },
  { headKey: 'TUITION', label: 'Tuition Fee', frequency: 'M' },
  { headKey: 'ANNUAL', label: 'Annual Fee', frequency: 'Y' },
  { headKey: 'EXAM', label: 'Examination Fee', frequency: 'HY' },
  { headKey: 'COMPUTER', label: 'Computer Fee', frequency: 'Y' },
  { headKey: 'LIBRARY', label: 'Library Fee', frequency: 'Y' },
  { headKey: 'TRANSPORT', label: 'Transport Fee', frequency: 'M' },
  { headKey: 'SPORTS', label: 'Sports Fee', frequency: 'Y' },
  { headKey: 'ACTIVITY', label: 'Activity Fee', frequency: 'Y' },
  { headKey: 'DEVELOPMENT', label: 'Development Fee', frequency: 'Y' },
  { headKey: 'MISC', label: 'Other Fees', frequency: 'OT' },
];

@Component({
  selector: 'sf-standard-fee',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './standard-fee.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
  styles: [
    `
      .choices { display: flex; flex-wrap: wrap; gap: 0.4rem; margin: 0.6rem 0 1rem; }
      table { width: 100%; border-collapse: collapse; }
      th, td { text-align: left; padding: 0.45rem 0.35rem; border-bottom: 1px solid color-mix(in srgb, var(--sf-menu) 18%, transparent); vertical-align: middle; }
      td.amount, th.amount { width: 9rem; }
      td.use, th.use { width: 3.5rem; }
      input[type='number'] { max-width: 8rem; }
      .add-line { display: flex; flex-wrap: wrap; gap: 0.75rem; align-items: end; }
      .add-line label { min-width: 12rem; }
    `,
  ],
})
export class StandardFeeComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);

  readonly frequencies = [
    { value: 'OT', label: 'One-time' },
    { value: 'M', label: 'Monthly' },
    { value: 'Q', label: 'Quarterly' },
    { value: 'HY', label: 'Half-yearly' },
    { value: 'Y', label: 'Annually' },
  ];

  sessionId = '';
  // Class buttons rebuild the amount inputs so each class shows its own fees.
  view: 'class' | 'fee' = 'class';
  classes: SchoolClass[] = [];
  categories: FeeCategory[] = [];
  selectedClass = '';
  selectedFee = '';
  addKey = '';
  customName = '';
  customFrequency = 'Y';
  loading = true;
  busy = false;
  error = '';
  status = '';
  private knownHeads: KnownHead[] = [];
  private readonly cells = new Map<string, AmountCell>();

  ngOnInit(): void {
    this.sessionId = this.auth.getSessionId();
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    forkJoin({
      classes: this.api.get<any[]>('/api/academic/classes').pipe(catchError(() => of(null))),
      heads: this.api.get<any[]>('/api/fee/finance/heads').pipe(catchError(() => of([]))),
      structures: this.api.get<any[]>('/api/fee/finance/structures').pipe(catchError(() => of([]))),
    }).subscribe(({ classes, heads, structures }) => {
      this.loading = false;
      if (!classes) {
        this.error = 'Classes could not be loaded.';
        return;
      }
      this.classes = (classes || [])
        .map((row) => ({
          name: String(row?.name || '').trim(),
          sequenceNo: Number(row?.sequenceNo || 0),
          status: String(row?.status || 'ACTIVE').toUpperCase(),
        }))
        .filter((row) => row.name && row.status !== 'INACTIVE' && row.status !== 'SUGGESTED')
        .sort((a, b) => a.sequenceNo - b.sequenceNo || a.name.localeCompare(b.name))
        .map(({ name, sequenceNo }) => ({ name, sequenceNo }));
      this.knownHeads = (heads || []).map((row) => {
        const payload = row?.payload || {};
        return {
          key: String(row?.definitionKey || payload.code || '').trim().toUpperCase(),
          label: String(payload.label || row?.definitionKey || '').trim(),
          frequency: String(payload.frequency || '').trim().toUpperCase(),
        };
      }).filter((row) => row.key);
      this.cells.clear();
      this.categories = this.categoriesFrom(structures || []);
      this.selectedClass = this.classes[0]?.name || '';
      this.selectedFee = this.categories[0]?.headKey || '';
      this.bindCells();
    });
  }

  feesToAdd(): FeeCategory[] {
    const selected = new Set(this.categories.map((fee) => fee.headKey));
    const standard = STANDARD_FEES.filter((fee) => !selected.has(fee.headKey));
    const extras = this.knownHeads
      .filter((head) => !selected.has(head.key) && !STANDARD_FEES.some((fee) => fee.headKey === head.key))
      .map((head) => ({ headKey: head.key, label: head.label || head.key, frequency: head.frequency || 'Y' }));
    return [...standard, ...extras];
  }

  frequencyLabel(value: string): string {
    return this.frequencies.find((item) => item.value === value)?.label || value;
  }

  cell(className: string, headKey: string): AmountCell {
    const key = `${className}::${headKey}`;
    let cell = this.cells.get(key);
    if (!cell) {
      cell = { amount: '', active: false };
      this.cells.set(key, cell);
    }
    return cell;
  }

  classTotal(className: string): number {
    return this.categories.reduce((sum, fee) => {
      const cell = this.cell(className, fee.headKey);
      if (!cell.active) return sum;
      const amount = Number(cell.amount);
      return sum + (Number.isFinite(amount) ? amount : 0);
    }, 0);
  }

  money(amount: number): string {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2,
    }).format(amount);
  }

  addSelected(): void {
    const fee = this.feesToAdd().find((item) => item.headKey === this.addKey);
    if (!fee) return;
    const known = this.knownHeads.find((head) => head.key === fee.headKey);
    this.categories = [
      ...this.categories,
      {
        headKey: fee.headKey,
        label: known?.label || fee.label,
        frequency: known?.frequency || fee.frequency,
      },
    ];
    this.addKey = '';
    this.selectedFee = this.selectedFee || fee.headKey;
    this.bindCells();
  }

  addCustom(): void {
    const label = this.customName.trim().replace(/\s+/g, ' ');
    if (!label) {
      this.error = 'Enter a name for the fee.';
      return;
    }
    if (this.categories.some((fee) => fee.label.toLowerCase() === label.toLowerCase())) {
      this.error = `${label} is already in the list.`;
      return;
    }
    const known = this.knownHeads.find((head) => head.label.toLowerCase() === label.toLowerCase());
    let headKey = known?.key || label.toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_|_$/g, '') || 'CUSTOM';
    while (this.categories.some((fee) => fee.headKey === headKey)) {
      headKey = `${headKey}_2`;
    }
    this.categories = [...this.categories, { headKey, label, frequency: this.customFrequency || 'Y' }];
    this.customName = '';
    this.selectedFee = headKey;
    this.error = '';
    this.bindCells();
  }

  removeFee(headKey: string): void {
    this.categories = this.categories.filter((fee) => fee.headKey !== headKey);
    if (this.selectedFee === headKey) {
      this.selectedFee = this.categories[0]?.headKey || '';
    }
  }

  save(): void {
    this.error = '';
    this.status = '';
    for (const cls of this.classes) {
      for (const fee of this.categories) {
        const cell = this.cell(cls.name, fee.headKey);
        const amount = cell.amount.trim() === '' ? 0 : Number(cell.amount);
        if (!Number.isFinite(amount) || amount < 0) {
          this.error = `Amount for ${fee.label} in ${cls.name} must be a number.`;
          return;
        }
        if (cell.active && amount <= 0) {
          this.error = `${fee.label} for ${cls.name} needs an amount, or turn it off.`;
          this.selectedClass = cls.name;
          this.view = 'class';
          return;
        }
      }
    }
    const body = {
      academicSessionId: this.sessionId,
      categories: this.categories.map((fee) => ({
        headKey: fee.headKey,
        label: fee.label,
        frequency: fee.frequency,
      })),
      classes: this.classes.map((cls) => ({
        className: cls.name,
        lines: this.categories
          .map((fee) => {
            const cell = this.cell(cls.name, fee.headKey);
            const amount = cell.amount.trim() === '' ? 0 : Number(cell.amount);
            return { headKey: fee.headKey, amount, active: cell.active };
          })
          .filter((line) => line.active || line.amount > 0),
      })),
    };
    this.busy = true;
    this.api.put<{ message?: string }>('/api/fee/finance/standard-fees', body).subscribe({
      next: (saved) => {
        this.busy = false;
        this.status = saved?.message || 'Saved. Fee receipts and dues already recorded are unchanged.';
        this.load();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message || 'The fee structure was not saved.';
      },
    });
  }

  private categoriesFrom(structures: any[]): FeeCategory[] {
    const used = new Map<string, string>();
    for (const cls of this.classes) {
      const key = matchFeeStructure(structures, cls.name, this.sessionId);
      const structure = (structures || []).find((row) => row?.definitionKey === key);
      const lines = structure?.payload?.lines;
      if (!Array.isArray(lines)) continue;
      for (const line of lines) {
        const headKey = String(line?.headKey || '').trim().toUpperCase();
        if (!headKey) continue;
        if (!used.has(headKey) && line?.frequency) {
          used.set(headKey, String(line.frequency).trim().toUpperCase());
        } else if (!used.has(headKey)) {
          used.set(headKey, '');
        }
        const cell = this.cell(cls.name, headKey);
        cell.amount = line?.amount === 0 || line?.amount ? String(line.amount) : '';
        cell.active = line?.active !== false && String(line?.status || '').toUpperCase() !== 'INACTIVE';
      }
    }
    const order = new Map(STANDARD_FEES.map((fee, index) => [fee.headKey, index]));
    return [...used.keys()]
      .sort((a, b) => (order.get(a) ?? 100) - (order.get(b) ?? 100) || a.localeCompare(b))
      .map((headKey) => {
        const standard = STANDARD_FEES.find((fee) => fee.headKey === headKey);
        const known = this.knownHeads.find((head) => head.key === headKey);
        return {
          headKey,
          label: known?.label || standard?.label || headKey,
          frequency: used.get(headKey) || known?.frequency || standard?.frequency || 'Y',
        };
      });
  }

  private bindCells(): void {
    for (const cls of this.classes) {
      for (const fee of this.categories) {
        this.cell(cls.name, fee.headKey);
      }
    }
  }
}
