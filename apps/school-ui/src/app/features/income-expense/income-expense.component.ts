import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subscription, catchError, forkJoin, of, timeout } from 'rxjs';
import { ApiService, PageResult } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';
import { FinanceKpiCardComponent } from '../../shared/finance-ui/finance-kpi-card.component';
import { FinanceDonutChartComponent } from '../../shared/finance-ui/finance-donut-chart.component';
import { FinanceBarChartComponent } from '../../shared/finance-ui/finance-bar-chart.component';
import {
  FinanceColumnChartComponent,
  ColumnPoint,
} from '../../shared/finance-ui/finance-column-chart.component';
import { ChartSlice, FinanceKpi } from '../../shared/finance-ui/finance-dashboard.models';

type AmountRow = { key: string; label: string; amount: number };
type TxnRow = {
  id: string;
  type: 'INCOME' | 'EXPENSE';
  date?: string;
  voucherNo?: string;
  description?: string;
  category?: string;
  amount?: number;
  paymentMode?: string;
  createdBy?: string;
  branchId?: string;
  drillPath?: string;
};

@Component({
  selector: 'sf-income-expense',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    FinanceKpiCardComponent,
    FinanceDonutChartComponent,
    FinanceBarChartComponent,
    FinanceColumnChartComponent,
  ],
  templateUrl: './income-expense.component.html',
  styleUrls: ['../../shared/admin-page.scss', './income-expense.component.scss'],
})
export class IncomeExpenseComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly router = inject(Router);
  private sub?: Subscription;

  loading = true;
  error = '';
  status = '';
  report: any = null;
  salary: any = null;
  activeTab: 'overview' | 'income' | 'expense' = 'overview';
  ledgerLoading = false;
  incomePage: PageResult<any> = this.emptyPage();
  expensePage: PageResult<any> = this.emptyPage();

  branches: Array<{ branchKey: string; name: string }> = [];
  canViewAllBranches = true;

  // Filters
  branchIds = 'ALL';
  datePreset = 'THIS_MONTH';
  fromDate = '';
  toDate = '';
  academicSessionId = '';
  feeType = '';
  expenseCategory = '';
  paymentMode = '';
  collectedBy = '';

  openIncome = true;
  openExpense = true;
  openFee = true;
  openSalary = true;
  showExpenseForm = false;
  showIncomeForm = false;
  savingExpense = false;
  savingIncome = false;

  /** When set, Overview shows only this KPI's drill-down instead of the full report. */
  kpiFocus: string | null = null;
  focusLoading = false;
  pendingFeeRows: any[] = [];
  salaryRows: any[] = [];
  approvedFeeRows: any[] = [];

  incomeDraft: Record<string, any> = {
    source: 'Donation',
    description: '',
    amount: '',
    paymentMode: 'CASH',
    incomeDate: new Date().toISOString().slice(0, 10),
  };

  expenseDraft: Record<string, any> = {
    category: 'Electricity',
    description: '',
    amount: '',
    paymentMode: 'CASH',
    expenseDate: new Date().toISOString().slice(0, 10),
  };

  readonly datePresets = [
    { value: 'TODAY', label: 'Today' },
    { value: 'YESTERDAY', label: 'Yesterday' },
    { value: 'THIS_WEEK', label: 'This Week' },
    { value: 'THIS_MONTH', label: 'This Month' },
    { value: 'LAST_MONTH', label: 'Last Month' },
    { value: 'QUARTER', label: 'Quarter' },
    { value: 'FINANCIAL_YEAR', label: 'Financial Year' },
    { value: 'CUSTOM', label: 'Custom Date' },
  ];

  readonly paymentModes = ['CASH', 'UPI', 'CARD', 'CHEQUE', 'BANK', 'NEFT', 'RTGS', 'ONLINE'];

  readonly chartColors = [
    '#15803d',
    '#2563eb',
    '#c2410c',
    '#7c3aed',
    '#0f766e',
    '#b45309',
    '#be123c',
    '#475569',
  ];

  ngOnInit(): void {
    this.academicSessionId = this.auth.getSessionId();
    this.loadBranches();
    this.reload();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  loadBranches(): void {
    this.api.get<any>('/api/config/branches/bootstrap').subscribe({
      next: (boot) => {
        this.branches = (boot?.branches ?? []).map((b: any) => ({
          branchKey: b.branchKey || b.code || b.id,
          name: b.name || b.branchKey || 'Branch',
        }));
      },
      error: () => {
        this.branches = [{ branchKey: 'main', name: 'Main Campus' }];
      },
    });
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.status = '';
    const q = this.queryParams();
    this.sub?.unsubscribe();
    this.sub = forkJoin({
      report: this.api.get<any>(`/api/fee/finance/income-expense?${q}`).pipe(
        timeout(20000),
        catchError((err) => {
          this.error = err?.error?.message ?? 'Failed to load income & expense report';
          return of(null);
        }),
      ),
      salary: this.api.get<any>(`/api/payroll/reports/salary-summary?${q}`).pipe(
        timeout(12000),
        catchError(() => of(null)),
      ),
    }).subscribe({
      next: ({ report, salary }) => {
        this.report = report;
        this.salary = salary;
        this.canViewAllBranches = !!report?.canViewAllBranches;
        if (report && !this.canViewAllBranches && this.branchIds === 'ALL') {
          this.branchIds = this.auth.getBranchId() || 'main';
        }
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to load report';
      },
    });
  }

  applyFilters(): void {
    this.clearKpiFocus();
    this.reload();
  }

  selectTab(tab: 'overview' | 'income' | 'expense'): void {
    this.activeTab = tab;
    this.error = '';
    this.status = '';
    this.showIncomeForm = false;
    this.showExpenseForm = false;
    if (tab !== 'overview') this.clearKpiFocus();
    if (tab === 'income') this.loadIncome(0);
    if (tab === 'expense') this.loadExpenses(0);
  }

  loadIncome(page = 0): void {
    this.ledgerLoading = true;
    this.api.getPage<any>('/api/fee/incomes', page, 20).subscribe({
      next: (result) => {
        this.incomePage = result;
        this.ledgerLoading = false;
      },
      error: (err) => {
        this.ledgerLoading = false;
        this.error = err?.error?.message ?? 'Could not load income entries';
      },
    });
  }

  loadExpenses(page = 0): void {
    this.ledgerLoading = true;
    this.api.getPage<any>('/api/fee/expenses', page, 20).subscribe({
      next: (result) => {
        this.expensePage = result;
        this.ledgerLoading = false;
      },
      error: (err) => {
        this.ledgerLoading = false;
        this.error = err?.error?.message ?? 'Could not load expense entries';
      },
    });
  }

  newIncome(): void {
    this.showIncomeForm = true;
    this.status = '';
    this.error = '';
  }

  newExpense(): void {
    this.showExpenseForm = true;
    this.status = '';
    this.error = '';
  }

  get kpis(): FinanceKpi[] {
    const k = this.mergedKpis();
    const profit = Number(k.netProfit || 0);
    return [
      {
        key: 'income',
        label: 'Total Income',
        value: this.money(k.totalIncome),
        hint: 'Fee collections & other income',
        tone: 'ok',
        actionLabel: 'View income',
        actionTab: 'income',
      },
      {
        key: 'expense',
        label: 'Total Expenses',
        value: this.money(k.totalExpense),
        hint: 'Operating + salary',
        tone: 'danger',
        actionLabel: 'View expenses',
        actionTab: 'expense',
      },
      {
        key: 'profit',
        label: profit >= 0 ? 'Net Profit' : 'Net Deficit',
        value: this.money(Math.abs(profit)),
        hint: `${Number(k.profitPercent || 0).toFixed(2)}% of income`,
        tone: profit >= 0 ? 'ok' : 'danger',
        actionLabel: 'View profit',
        actionTab: 'profit',
      },
      {
        key: 'fee',
        label: 'Fee Collection',
        value: this.money(k.feeIncome),
        hint: 'Approved receipts in period',
        tone: 'info',
        actionLabel: 'View fees',
        actionTab: 'fee',
      },
      {
        key: 'pending',
        label: 'Pending Fees',
        value: this.money(k.pendingFees),
        hint: 'Outstanding student dues',
        tone: 'warn',
        actionLabel: 'View pending',
        actionTab: 'pending',
      },
      {
        key: 'salary',
        label: 'Salary Paid',
        value: this.money(k.salaryPaid),
        hint: 'Approved payroll net pay',
        tone: 'accent',
        actionLabel: 'View salary',
        actionTab: 'salary',
      },
      {
        key: 'cash',
        label: 'Cash In Hand',
        value: this.money(k.cashInHand),
        hint: 'Cash income − cash expense',
        tone: 'neutral',
        actionLabel: 'View cash',
        actionTab: 'cash',
      },
      {
        key: 'bank',
        label: 'Bank Balance',
        value: this.money(k.bankBalance),
        hint: 'Bank / UPI / card / cheque net',
        tone: 'info',
        actionLabel: 'View bank',
        actionTab: 'bank',
      },
    ];
  }

  get incomeRows(): AmountRow[] {
    return (this.report?.incomeBySource ?? []) as AmountRow[];
  }

  get expenseRows(): AmountRow[] {
    const rows = [...((this.report?.expenseByCategory ?? []) as AmountRow[])];
    const salary = Number(this.salary?.salaryPaid || 0);
    if (salary > 0 && !rows.some((r) => /salary/i.test(r.label))) {
      rows.unshift({ key: 'Staff Salary', label: 'Staff Salary', amount: salary });
    }
    return rows.sort((a, b) => Number(b.amount) - Number(a.amount));
  }

  get monthlyPoints(): ColumnPoint[] {
    const points = (this.report?.monthlyTrend ?? []).map((m: any) => {
      const salaryMonth =
        (this.salary?.byMonth ?? []).find((x: any) => x.month === m.month)?.amount || 0;
      return {
        key: m.month,
        label: m.label,
        income: Number(m.income || 0),
        expense: Number(m.expense || 0) + Number(salaryMonth || 0),
      };
    });
    return points;
  }

  get expenseSlices(): ChartSlice[] {
    return this.expenseRows.slice(0, 8).map((r, i) => ({
      key: r.key,
      label: r.label,
      value: Number(r.amount || 0),
      color: this.chartColors[i % this.chartColors.length],
    }));
  }

  get incomeModeSlices(): ChartSlice[] {
    return (this.report?.incomeByPaymentMode ?? []).map((r: any, i: number) => ({
      key: r.key,
      label: r.label,
      value: Number(r.amount || 0),
      color: this.chartColors[i % this.chartColors.length],
    }));
  }

  get feeTypeBars(): ChartSlice[] {
    return this.incomeRows.map((r, i) => ({
      key: r.key,
      label: r.label,
      value: Number(r.amount || 0),
      color: this.chartColors[i % this.chartColors.length],
    }));
  }

  get recent(): TxnRow[] {
    return (this.report?.recentTransactions ?? []) as TxnRow[];
  }

  get topExpenses(): TxnRow[] {
    return (this.report?.topExpenses ?? []) as TxnRow[];
  }

  get branchRows(): any[] {
    return this.report?.branchComparison ?? [];
  }

  get feeAnalysis(): any {
    return this.report?.feeAnalysis ?? {};
  }

  get salaryAnalysis(): any {
    return (
      this.salary ?? {
        teachingStaffSalary: 0,
        nonTeachingSalary: 0,
        pendingSalary: 0,
        bonus: 0,
        overtime: 0,
        salaryDeduction: 0,
      }
    );
  }

  get profitSummary(): { income: number; expense: number; net: number; pct: number } {
    const k = this.mergedKpis();
    return {
      income: Number(k.totalIncome || 0),
      expense: Number(k.totalExpense || 0),
      net: Number(k.netProfit || 0),
      pct: Number(k.profitPercent || 0),
    };
  }

  get focusLabel(): string {
    return this.kpis.find((k) => k.key === this.kpiFocus)?.label || 'Selected metric';
  }

  get focusAmount(): string {
    return this.kpis.find((k) => k.key === this.kpiFocus)?.value || this.money(0);
  }

  get focusedTransactions(): TxnRow[] {
    const rows = this.recent;
    switch (this.kpiFocus) {
      case 'income':
      case 'fee':
        return rows.filter((r) => r.type === 'INCOME');
      case 'expense':
        return rows.filter((r) => r.type === 'EXPENSE');
      case 'cash':
        return rows.filter((r) => String(r.paymentMode || '').toUpperCase() === 'CASH');
      case 'bank':
        return rows.filter((r) => this.isBankMode(r.paymentMode));
      case 'profit':
        return rows;
      default:
        return rows;
    }
  }

  get pendingTotal(): number {
    return this.pendingFeeRows.reduce((sum, row) => sum + Number(row.amount || 0), 0);
  }

  get salaryTotal(): number {
    return this.salaryRows.reduce((sum, row) => sum + Number(row.netPay || row.amount || 0), 0);
  }

  onKpiAction(kpi: FinanceKpi): void {
    const focus = String(kpi.actionTab || kpi.key || '');
    if (!focus) return;
    this.activeTab = 'overview';
    if (this.kpiFocus === focus) {
      this.clearKpiFocus();
      return;
    }
    this.kpiFocus = focus;
    this.status = `Showing ${kpi.label}`;
    this.error = '';
    this.loadFocusData(focus);
  }

  clearKpiFocus(): void {
    this.kpiFocus = null;
    this.focusLoading = false;
    this.pendingFeeRows = [];
    this.salaryRows = [];
    this.approvedFeeRows = [];
    this.status = '';
  }

  openPendingFee(row: any): void {
    void this.router.navigate(['/admin/fee'], { queryParams: { id: row.id } });
  }

  openSalaryRow(row: any): void {
    void this.router.navigate(['/admin/payroll'], { queryParams: { id: row.id } });
  }

  openTxn(row: TxnRow): void {
    if (row.drillPath) {
      void this.router.navigateByUrl(row.drillPath);
      return;
    }
    if (row.type === 'INCOME') {
      void this.router.navigate(['/admin/fee'], { queryParams: { id: row.id } });
    }
  }

  saveIncome(): void {
    const amount = Number(this.incomeDraft['amount']);
    if (!this.incomeDraft['source'] || !(amount > 0)) {
      this.error = 'Income source and a positive amount are required';
      return;
    }
    this.savingIncome = true;
    this.error = '';
    const body = {
      ...this.incomeDraft,
      amount,
      branchId: this.branchIds === 'ALL' ? this.auth.getBranchId() : this.branchIds.split(',')[0],
      academicSessionId: this.academicSessionId || this.auth.getSessionId(),
    };
    this.api.post('/api/fee/incomes', body).subscribe({
      next: () => {
        this.savingIncome = false;
        this.showIncomeForm = false;
        this.status = 'Income entry recorded';
        this.incomeDraft = {
          source: 'Donation',
          description: '',
          amount: '',
          paymentMode: 'CASH',
          incomeDate: new Date().toISOString().slice(0, 10),
        };
        this.loadIncome(0);
        this.reloadSummary();
      },
      error: (err) => {
        this.savingIncome = false;
        this.error = err?.error?.message ?? 'Could not save income';
      },
    });
  }

  saveExpense(): void {
    const amount = Number(this.expenseDraft['amount']);
    if (!this.expenseDraft['category'] || !(amount > 0)) {
      this.error = 'Category and a positive amount are required';
      return;
    }
    this.savingExpense = true;
    this.error = '';
    const body = {
      ...this.expenseDraft,
      amount,
      branchId: this.branchIds === 'ALL' ? this.auth.getBranchId() : this.branchIds.split(',')[0],
      academicSessionId: this.academicSessionId || this.auth.getSessionId(),
    };
    this.api.post('/api/fee/expenses', body).subscribe({
      next: () => {
        this.savingExpense = false;
        this.showExpenseForm = false;
        this.status = 'Expense recorded';
        this.expenseDraft = {
          category: 'Electricity',
          description: '',
          amount: '',
          paymentMode: 'CASH',
          expenseDate: new Date().toISOString().slice(0, 10),
        };
        this.loadExpenses(0);
        this.reloadSummary();
      },
      error: (err) => {
        this.savingExpense = false;
        this.error = err?.error?.message ?? 'Could not save expense';
      },
    });
  }

  deleteIncome(row: any): void {
    if (!confirm(`Remove income entry ${row.voucherNo || ''}?`)) return;
    this.api.delete(`/api/fee/incomes/${row.id}`).subscribe({
      next: () => {
        this.status = 'Income entry removed';
        this.loadIncome(this.incomePage.page);
        this.reloadSummary();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not remove income entry'),
    });
  }

  deleteExpense(row: any): void {
    if (!confirm(`Remove expense ${row.voucherNo || ''}?`)) return;
    this.api.delete(`/api/fee/expenses/${row.id}`).subscribe({
      next: () => {
        this.status = 'Expense removed';
        this.loadExpenses(this.expensePage.page);
        this.reloadSummary();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not remove expense'),
    });
  }

  exportCsv(): void {
    const rows: string[][] = [
      ['Type', 'Date', 'Voucher', 'Description', 'Category', 'Amount', 'Payment Mode', 'Branch', 'Created By'],
    ];
    for (const t of this.recent) {
      rows.push([
        t.type,
        this.fmtDate(t.date),
        String(t.voucherNo || ''),
        String(t.description || ''),
        String(t.category || ''),
        String(t.amount || 0),
        String(t.paymentMode || ''),
        String(t.branchId || ''),
        String(t.createdBy || ''),
      ]);
    }
    const csv = rows.map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n');
    this.downloadBlob(csv, 'text/csv;charset=utf-8', `income-expense-${this.datePreset.toLowerCase()}.csv`);
  }

  exportExcel(): void {
    // Spreadsheet-friendly CSV with BOM for Excel.
    const k = this.mergedKpis();
    const lines = [
      ['Metric', 'Amount'],
      ['Total Income', k.totalIncome],
      ['Total Expenses', k.totalExpense],
      ['Net Profit', k.netProfit],
      ['Pending Fees', k.pendingFees],
      ['Salary Paid', k.salaryPaid],
      [],
      ['Income Source', 'Amount'],
      ...this.incomeRows.map((r) => [r.label, r.amount]),
      [],
      ['Expense Category', 'Amount'],
      ...this.expenseRows.map((r) => [r.label, r.amount]),
    ];
    const csv =
      '\uFEFF' +
      lines
        .map((r) => r.map((c) => `"${String(c ?? '').replace(/"/g, '""')}"`).join(','))
        .join('\n');
    this.downloadBlob(csv, 'application/vnd.ms-excel', `income-expense-${this.datePreset.toLowerCase()}.xls`);
  }

  printReport(): void {
    window.print();
  }

  exportPdf(): void {
    // Browser print-to-PDF keeps formatting without a heavy client PDF lib.
    this.status = 'Use Print → Save as PDF for a formatted PDF export';
    setTimeout(() => window.print(), 250);
  }

  abs(v: unknown): number {
    return Math.abs(Number(v || 0));
  }

  money(v: unknown): string {
    const n = Number(v || 0);
    return '₹' + n.toLocaleString('en-IN', { maximumFractionDigits: 0 });
  }

  fmtDate(v?: string): string {
    if (!v) return '—';
    const d = new Date(v);
    if (Number.isNaN(d.getTime())) return String(v).slice(0, 10);
    return d.toLocaleDateString('en-IN');
  }

  private mergedKpis(): any {
    const k = { ...(this.report?.kpis ?? {}) };
    const salaryPaid = Number(this.salary?.salaryPaid || k.salaryPaid || 0);
    const operating = Number(k.operatingExpense ?? k.totalExpense ?? 0);
    const totalIncome = Number(k.totalIncome || 0);
    const totalExpense = operating + salaryPaid;
    const net = totalIncome - totalExpense;
    const profitPercent = totalIncome > 0 ? Math.round((net * 10000) / totalIncome) / 100 : 0;
    return {
      ...k,
      salaryPaid,
      totalExpense,
      netProfit: net,
      profitPercent,
    };
  }

  private queryParams(): string {
    const params = new URLSearchParams();
    params.set('preset', this.datePreset);
    if (this.datePreset === 'CUSTOM') {
      if (this.fromDate) params.set('fromDate', this.fromDate);
      if (this.toDate) params.set('toDate', this.toDate);
    }
    params.set('branchIds', this.branchIds || 'ALL');
    if (this.academicSessionId) params.set('academicSessionId', this.academicSessionId);
    if (this.feeType) params.set('feeType', this.feeType);
    if (this.expenseCategory) params.set('expenseCategory', this.expenseCategory);
    if (this.paymentMode) params.set('paymentMode', this.paymentMode);
    if (this.collectedBy) params.set('collectedBy', this.collectedBy);
    return params.toString();
  }

  private reloadSummary(): void {
    this.reload();
  }

  private loadFocusData(focus: string): void {
    if (focus === 'pending') {
      this.focusLoading = true;
      this.api.getPage<any>('/api/fee/collections', 0, 200).subscribe({
        next: (page) => {
          this.pendingFeeRows = (page.items ?? [])
            .filter((row) => {
              const status = String(row.status || '').toUpperCase();
              return status === 'IN_PROGRESS' || status === 'INFO_REQUESTED';
            })
            .map((row) => {
              const answers = row.answers || {};
              return {
                id: row.id,
                date: row.updatedAt || row.createdAt,
                studentName: answers.studentName || row.studentName || 'Student',
                admissionNo: answers.admissionNo || answers.admissionNumber || '—',
                classSection: answers.classSection || answers.className || '—',
                category: answers.feeHead || 'Fee',
                paymentMode: answers.paymentMode || '—',
                amount: Number(answers.amount ?? row.amount ?? 0),
                status: row.status,
              };
            });
          this.focusLoading = false;
        },
        error: (err) => {
          this.focusLoading = false;
          this.error = err?.error?.message ?? 'Could not load pending fees';
        },
      });
      return;
    }

    if (focus === 'fee') {
      this.focusLoading = true;
      this.api.getPage<any>('/api/fee/collections', 0, 200).subscribe({
        next: (page) => {
          this.approvedFeeRows = (page.items ?? [])
            .filter((row) => String(row.status || '').toUpperCase() === 'APPROVED')
            .map((row) => {
              const answers = row.answers || {};
              return {
                id: row.id,
                date: row.updatedAt || row.createdAt,
                studentName: answers.studentName || row.studentName || 'Student',
                category: answers.feeHead || 'Fee',
                paymentMode: answers.paymentMode || '—',
                amount: Number(answers.amount ?? row.amount ?? 0),
                voucherNo: 'FEE-' + String(row.id || '').slice(0, 8).toUpperCase(),
              };
            });
          this.focusLoading = false;
        },
        error: (err) => {
          this.focusLoading = false;
          this.error = err?.error?.message ?? 'Could not load fee collections';
        },
      });
      return;
    }

    if (focus === 'salary') {
      this.focusLoading = true;
      this.api.getPage<any>('/api/payroll/records', 0, 100).subscribe({
        next: (page) => {
          this.salaryRows = (page.items ?? [])
            .filter((row) => {
              const status = String(row.status || '').toUpperCase();
              return status === 'APPROVED' || status === 'PAID' || status === 'POSTED';
            })
            .map((row) => {
              const answers = row.answers || {};
              return {
                id: row.id,
                date: row.updatedAt || row.createdAt || answers.month,
                employeeName: answers.employeeName || answers.staffName || 'Staff',
                month: answers.month || '—',
                paymentMode: answers.paymentMode || '—',
                netPay: Number(answers.netPay ?? row.netPay ?? 0),
                status: row.status,
              };
            });
          this.focusLoading = false;
        },
        error: (err) => {
          this.focusLoading = false;
          this.error = err?.error?.message ?? 'Could not load salary records';
        },
      });
    }
  }

  private isBankMode(mode?: string): boolean {
    const m = String(mode || '').toUpperCase();
    return ['BANK', 'NEFT', 'RTGS', 'UPI', 'CARD', 'ONLINE', 'CHEQUE'].includes(m);
  }

  private emptyPage<T>(): PageResult<T> {
    return {
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    };
  }

  private downloadBlob(content: string, type: string, filename: string): void {
    const blob = new Blob([content], { type });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}
