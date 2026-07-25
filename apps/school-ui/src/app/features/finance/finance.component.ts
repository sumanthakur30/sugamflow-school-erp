import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from '../../core/api.service';
import { ListToolbarComponent } from '../../shared/list-toolbar/list-toolbar.component';
import { ListPagerComponent } from '../../shared/list-toolbar/list-pager.component';
import { ListSortOption, sortRows } from '../../shared/list-toolbar/list-controls';
import { StudentLookupComponent } from '../../shared/student-lookup/student-lookup.component';
import { StudentLookupRow } from '../../shared/student-lookup/student-lookup.models';
import { FinanceKpiCardComponent } from '../../shared/finance-ui/finance-kpi-card.component';
import { FinanceBarChartComponent } from '../../shared/finance-ui/finance-bar-chart.component';
import { FinanceDonutChartComponent } from '../../shared/finance-ui/finance-donut-chart.component';
import {
  ActivityItem,
  ChartSlice,
  FinanceAlert,
  FinanceKpi,
  OutstandingRow,
  PaymentModeCard,
} from '../../shared/finance-ui/finance-dashboard.models';
import {
  buildActivity,
  buildAlerts,
  buildFinanceKpis,
  buildOutstanding,
  feeHeadSlices,
  formatInr,
  monthlyCollectionSeries,
  paymentModeCards,
  paymentModeSlices,
  pendingByClass,
} from '../../shared/finance-ui/finance-metrics';

export type FinanceTab =
  | 'overview'
  | 'collections'
  | 'heads'
  | 'structures'
  | 'concessions'
  | 'demand'
  | 'payments'
  | 'settings';

@Component({
  selector: 'sf-finance',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    ListToolbarComponent,
    ListPagerComponent,
    StudentLookupComponent,
    FinanceKpiCardComponent,
    FinanceBarChartComponent,
    FinanceDonutChartComponent,
  ],
  templateUrl: './finance.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/list-toolbar.component.scss',
    '../../shared/list-toolbar/inbox-list.scss',
    './finance.component.scss',
  ],
})
export class FinanceComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  loading = true;
  error = '';
  status = '';
  busy = false;
  tab: FinanceTab = 'overview';
  globalQ = '';

  paymentGatewayEnabled = false;
  accountingEnabled = false;
  paymentMode = 'simulate';
  availableAdapters: string[] = [];
  heads: any[] = [];
  structures: any[] = [];
  concessions: any[] = [];
  providers: any[] = [];
  transactions: any[] = [];
  collections: any[] = [];
  studentTotal = 0;
  studentActive = 0;

  kpis: FinanceKpi[] = [];
  outstanding: OutstandingRow[] = [];
  monthlySeries: ChartSlice[] = [];
  modeSlices: ChartSlice[] = [];
  headSlices: ChartSlice[] = [];
  pendingClassSlices: ChartSlice[] = [];
  modeCards: PaymentModeCard[] = [];
  alerts: FinanceAlert[] = [];
  activity: ActivityItem[] = [];

  headsQ = '';
  structuresQ = '';
  dueQ = '';
  duePageIndex = 0;
  duePageSize = 10;

  listQ = '';
  sortBy = 'createdAt';
  sortDir: 'ASC' | 'DESC' = 'DESC';
  pageSize = 20;
  pageIndex = 0;
  readonly sortOptions: ListSortOption[] = [
    { key: 'createdAt', label: 'Created' },
    { key: 'transactionType', label: 'Type' },
    { key: 'status', label: 'Status' },
    { key: 'referenceNo', label: 'Reference No' },
  ];

  structureKey = 'grade_8_annual';
  concessionKey = '';
  studentRef = '';
  studentName = '';
  classSection = '';
  periodKey = '';
  pendingDays = 0;
  hostelMonthlyFee: number | null = null;
  transportFare: number | null = null;
  demand: any = null;
  lastDemandTxn: any = null;
  bulkResult: any = null;
  selectedStudent: StudentLookupRow | null = null;

  headDraft: {
    definitionKey: string;
    label: string;
    category: string;
    refundable: boolean;
    enabled: boolean;
    frequency: string;
    gstRate: number;
    taxable: boolean;
  } = {
    definitionKey: '',
    label: '',
    category: 'ACADEMIC',
    refundable: true,
    enabled: true,
    frequency: 'M',
    gstRate: 0,
    taxable: false,
  };
  editingHeadKey: string | null = null;

  structureDraft = {
    definitionKey: '',
    name: '',
    currency: 'INR',
    classSection: '',
    academicSessionId: '',
    notes: '',
    linesText: 'TUITION,12000,M\nLIBRARY,500,Y',
  };
  editingStructureKey: string | null = null;

  adjustDraft = {
    type: 'waive' as 'waive' | 'refund',
    amount: 0,
    headKey: 'MISC',
    reason: '',
    studentRef: '',
  };

  intentAmount = 1000;
  providerKey = 'simulated';
  lastIntent: any = null;

  readonly tabs: Array<{ id: FinanceTab; label: string }> = [
    { id: 'overview', label: 'Overview' },
    { id: 'collections', label: 'Collections' },
    { id: 'heads', label: 'Fee heads' },
    { id: 'structures', label: 'Structures' },
    { id: 'concessions', label: 'Concessions' },
    { id: 'demand', label: 'Demand' },
    { id: 'payments', label: 'Payments' },
    { id: 'settings', label: 'Settings' },
  ];

  readonly quickActions: Array<{
    label: string;
    tab?: FinanceTab;
    link?: any[];
    query?: Record<string, string>;
  }> = [
    { label: 'Collect fee', link: ['/admin/fee'], query: { new: '1' } },
    { label: 'Generate demand', tab: 'demand' },
    { label: 'Create fee head', tab: 'heads' },
    { label: 'Fee structure', tab: 'structures' },
    { label: 'Concession', tab: 'concessions' },
    { label: 'Reports', link: ['/admin/reports'] },
    { label: 'Payment gateway', tab: 'settings' },
    { label: 'Students', link: ['/admin/student-directory'] },
  ];

  ngOnInit(): void {
    this.reload();
  }

  setTab(id: FinanceTab): void {
    this.tab = id;
    this.error = '';
    queueMicrotask(() =>
      document.querySelector('.fin-sticky-head')?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
    );
  }

  onKpiAction(kpi: FinanceKpi): void {
    this.openDestination({
      tab: kpi.actionTab as FinanceTab | undefined,
      link: kpi.actionLink,
      query: kpi.actionQuery,
    });
  }

  onAlertAction(alert: FinanceAlert): void {
    this.openDestination({
      tab: alert.actionTab as FinanceTab | undefined,
      link: alert.actionLink,
      query: alert.actionQuery,
    });
  }

  openModeCard(card: PaymentModeCard): void {
    const q = (card.filterKey || card.mode || '').trim();
    if (!q) {
      this.setTab('payments');
      return;
    }
    void this.router.navigate(['/admin/fee'], { queryParams: { q } });
  }

  openActivity(ev: ActivityItem): void {
    if (ev.id.startsWith('c-')) {
      const id = ev.id.slice(2);
      void this.router.navigate(['/admin/fee'], { queryParams: { id } });
      return;
    }
    if (ev.id.startsWith('t-')) {
      this.listQ = '';
      this.setTab('payments');
      return;
    }
    if (ev.id.startsWith('h-')) {
      this.headsQ = '';
      this.setTab('heads');
    }
  }

  openDestination(dest: {
    tab?: FinanceTab;
    link?: string | any[];
    query?: Record<string, string>;
  }): void {
    if (dest.link) {
      const link = Array.isArray(dest.link) ? dest.link : [dest.link];
      void this.router.navigate(link, { queryParams: dest.query });
      return;
    }
    if (dest.tab) {
      if (dest.query?.['q']) {
        if (dest.tab === 'payments') this.listQ = dest.query['q'];
        if (dest.tab === 'collections') this.dueQ = dest.query['q'];
        if (dest.tab === 'heads') this.headsQ = dest.query['q'];
      }
      this.setTab(dest.tab);
    }
  }

  runQuick(action: { label: string; tab?: FinanceTab; link?: any[]; query?: Record<string, string> }): void {
    if (action.tab) {
      this.setTab(action.tab);
      if (action.tab === 'heads') {
        queueMicrotask(() =>
          document.getElementById('fin-head-form')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }),
        );
      }
      return;
    }
    if (action.link) {
      void this.router.navigate(action.link, { queryParams: action.query });
    }
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    forkJoin({
      boot: this.api.get<any>('/api/fee/finance/bootstrap').pipe(
        catchError((err) => {
          this.error = err?.error?.message ?? 'Finance bootstrap failed';
          return of(null);
        }),
      ),
      transactions: this.api
        .get<any[]>('/api/fee/finance/transactions')
        .pipe(catchError(() => of([] as any[]))),
      collections: this.api
        .getPage<any>('/api/fee/collections', 0, 200)
        .pipe(catchError(() => of({ items: [] as any[] }))),
      students: this.api
        .get<any>('/api/student/directory/summary')
        .pipe(catchError(() => of({ total: 0, active: 0 }))),
    }).subscribe(({ boot, transactions, collections, students }) => {
      if (boot) {
        this.paymentGatewayEnabled = !!boot.paymentGatewayEnabled;
        this.accountingEnabled = !!boot.accountingEnabled;
        this.paymentMode = boot.paymentMode || 'simulate';
        this.availableAdapters = boot.availableAdapters ?? [];
        this.heads = boot.heads ?? [];
        this.structures = boot.structures ?? [];
        this.concessions = boot.concessions ?? [];
        this.providers = boot.providers ?? [];
        if (this.providers.length) {
          const keys = this.providers.map((p: any) => p.definitionKey || p.payload?.definitionKey);
          if (!keys.includes(this.providerKey)) {
            this.providerKey = keys[0] || 'simulated';
          }
        }
        if (this.structures.length) {
          const keys = this.structures.map((s: any) => s.definitionKey);
          if (!keys.includes(this.structureKey)) {
            this.structureKey = keys[0] || this.structureKey;
          }
        }
      }
      this.transactions = transactions ?? [];
      this.collections = (collections as any)?.items ?? [];
      this.studentTotal = Number(students?.total) || 0;
      this.studentActive = Number(students?.active) || 0;
      this.recomputeDashboard();
      this.loading = false;
    });
  }

  private recomputeDashboard(): void {
    this.kpis = buildFinanceKpis({
      collections: this.collections,
      transactions: this.transactions,
      studentTotal: this.studentTotal,
      studentActive: this.studentActive,
      concessionsCount: this.concessions.length,
      gatewayOn: this.paymentGatewayEnabled,
    });
    this.outstanding = buildOutstanding(this.collections);
    this.monthlySeries = monthlyCollectionSeries(this.collections);
    this.modeSlices = paymentModeSlices(this.collections);
    this.headSlices = feeHeadSlices(this.collections);
    this.pendingClassSlices = pendingByClass(this.collections);
    this.modeCards = paymentModeCards(this.collections);
    this.alerts = buildAlerts({
      pendingCount: this.outstanding.length,
      pendingAmount: this.outstanding.reduce((s, r) => s + r.dueAmount, 0),
      gatewayOn: this.paymentGatewayEnabled,
      structuresCount: this.structures.length,
      dueStudents: new Set(this.outstanding.map((r) => r.admissionNo)).size,
    });
    this.activity = buildActivity(this.collections, this.transactions, this.heads);
  }

  applyGlobalSearch(): void {
    const q = this.globalQ.trim().toLowerCase();
    if (!q) return;
    const inHeads = this.heads.some((h) => {
      const p = this.payload(h);
      return (
        String(p.label || '')
          .toLowerCase()
          .includes(q) ||
        String(h.definitionKey || '')
          .toLowerCase()
          .includes(q)
      );
    });
    if (inHeads) {
      this.headsQ = this.globalQ;
      this.setTab('heads');
      return;
    }
    const inDue = this.outstanding.some(
      (r) =>
        r.studentName.toLowerCase().includes(q) ||
        r.admissionNo.toLowerCase().includes(q) ||
        r.mobile.toLowerCase().includes(q),
    );
    if (inDue) {
      this.dueQ = this.globalQ;
      this.setTab('collections');
      return;
    }
    this.listQ = this.globalQ;
    this.setTab('payments');
  }

  payload(def: any): any {
    return def?.payload || def || {};
  }

  get filteredHeads(): any[] {
    const q = this.headsQ.trim().toLowerCase();
    if (!q) return this.heads;
    return this.heads.filter((h) => {
      const p = this.payload(h);
      return (
        String(p.label || '')
          .toLowerCase()
          .includes(q) ||
        String(p.code || h.definitionKey || '')
          .toLowerCase()
          .includes(q) ||
        String(p.category || '')
          .toLowerCase()
          .includes(q)
      );
    });
  }

  get filteredStructures(): any[] {
    const q = this.structuresQ.trim().toLowerCase();
    if (!q) return this.structures;
    return this.structures.filter((s) => {
      const p = this.payload(s);
      return (
        String(p.name || '')
          .toLowerCase()
          .includes(q) ||
        String(s.definitionKey || '')
          .toLowerCase()
          .includes(q)
      );
    });
  }

  get filteredOutstanding(): OutstandingRow[] {
    let rows = this.outstanding;
    const q = this.dueQ.trim().toLowerCase();
    if (q) {
      rows = rows.filter(
        (r) =>
          r.studentName.toLowerCase().includes(q) ||
          r.admissionNo.toLowerCase().includes(q) ||
          r.classSection.toLowerCase().includes(q) ||
          r.mobile.toLowerCase().includes(q),
      );
    }
    const start = this.duePageIndex * this.duePageSize;
    return rows.slice(start, start + this.duePageSize);
  }

  get outstandingTotal(): number {
    const q = this.dueQ.trim().toLowerCase();
    if (!q) return this.outstanding.length;
    return this.outstanding.filter(
      (r) =>
        r.studentName.toLowerCase().includes(q) ||
        r.admissionNo.toLowerCase().includes(q) ||
        r.classSection.toLowerCase().includes(q) ||
        r.mobile.toLowerCase().includes(q),
    ).length;
  }

  onStudentSelected(row: StudentLookupRow): void {
    this.selectedStudent = row;
    this.studentName = row.fullName || '';
    this.studentRef = row.admissionNo || row.id || '';
    this.classSection = row.classSection || this.classSection;
  }

  onStudentCleared(): void {
    this.selectedStudent = null;
    this.studentName = '';
    this.studentRef = '';
    this.classSection = '';
  }

  editHead(h: any): void {
    const p = this.payload(h);
    this.editingHeadKey = h.definitionKey || p.definitionKey || null;
    this.headDraft = {
      definitionKey: String(this.editingHeadKey || ''),
      label: String(p.label || ''),
      category: String(p.category || 'ACADEMIC'),
      refundable: p.refundable !== false,
      enabled: p.enabled !== false && h.enabled !== false,
      frequency: String(p.frequency || 'M'),
      gstRate: Number(p.gstRate || 0),
      taxable: !!p.taxable,
    };
    this.setTab('heads');
    queueMicrotask(() =>
      document.getElementById('fin-head-form')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }),
    );
  }

  resetHeadDraft(): void {
    this.editingHeadKey = null;
    this.headDraft = {
      definitionKey: '',
      label: '',
      category: 'ACADEMIC',
      refundable: true,
      enabled: true,
      frequency: 'M',
      gstRate: 0,
      taxable: false,
    };
  }

  demandBody(): Record<string, unknown> {
    const body: Record<string, unknown> = {
      structureKey: this.structureKey,
      concessionKey: this.concessionKey || undefined,
      studentRef: this.studentRef || undefined,
      studentName: this.studentName || undefined,
      classSection: this.classSection || undefined,
      periodKey: this.periodKey || undefined,
      pendingDays: this.pendingDays || 0,
    };
    if (this.hostelMonthlyFee != null && this.hostelMonthlyFee > 0) {
      body['hostelMonthlyFee'] = this.hostelMonthlyFee;
    }
    if (this.transportFare != null && this.transportFare > 0) {
      body['transportFare'] = this.transportFare;
    }
    return body;
  }

  previewDemand(): void {
    this.busy = true;
    this.error = '';
    this.api.post<any>('/api/fee/finance/demands/preview', this.demandBody()).subscribe({
      next: (d) => {
        this.busy = false;
        this.demand = d;
        if (d?.netAmount != null) {
          this.intentAmount = Number(d.netAmount) || this.intentAmount;
        }
        this.status = `Preview net ${this.formatMoney(d.netAmount)}`;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Demand preview failed';
      },
    });
  }

  generateDemand(): void {
    if (!this.studentRef?.trim()) {
      this.error = 'Select a student before generating a demand';
      return;
    }
    this.busy = true;
    this.error = '';
    this.api.post<any>('/api/fee/finance/demands', this.demandBody()).subscribe({
      next: (txn) => {
        this.busy = false;
        this.lastDemandTxn = txn;
        this.demand = txn.demand || this.demand;
        this.status = `Demand ${txn.referenceNo || ''} saved (${txn.status || 'OPEN'})`;
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Generate demand failed';
      },
    });
  }

  generateBulkDemands(): void {
    if (!this.classSection?.trim()) {
      this.error = 'Class / section is required for bulk demand';
      return;
    }
    this.busy = true;
    this.error = '';
    this.api
      .post<any>('/api/fee/finance/demands/bulk', {
        ...this.demandBody(),
        classSection: this.classSection,
      })
      .subscribe({
        next: (res) => {
          this.busy = false;
          this.bulkResult = res;
          this.status = `Bulk demand: ${res.created || 0} created, ${res.failed || 0} failed`;
          this.reload();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Bulk demand failed';
        },
      });
  }

  editStructure(s: any): void {
    const p = this.payload(s);
    this.editingStructureKey = s.definitionKey || p.definitionKey || null;
    const elig = p.eligibility || {};
    const lines = Array.isArray(p.lines) ? p.lines : [];
    this.structureDraft = {
      definitionKey: String(this.editingStructureKey || ''),
      name: String(p.name || ''),
      currency: String(p.currency || 'INR'),
      classSection: String(elig.classSection || ''),
      academicSessionId: String(elig.academicSessionId || ''),
      notes: String(p.notes || ''),
      linesText: lines
        .map((l: any) => `${l.headKey},${l.amount},${l.frequency || 'Y'}`)
        .join('\n'),
    };
    this.setTab('structures');
    queueMicrotask(() =>
      document.getElementById('fin-structure-form')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }),
    );
  }

  resetStructureDraft(): void {
    this.editingStructureKey = null;
    this.structureDraft = {
      definitionKey: '',
      name: '',
      currency: 'INR',
      classSection: '',
      academicSessionId: '',
      notes: '',
      linesText: 'TUITION,12000,M\nLIBRARY,500,Y',
    };
  }

  saveStructure(): void {
    const key = (this.structureDraft.definitionKey || '').trim().toUpperCase().replace(/\s+/g, '_');
    if (!key || !this.structureDraft.name?.trim()) {
      this.error = 'Structure key and name are required';
      return;
    }
    const lines = this.structureDraft.linesText
      .split(/\r?\n/)
      .map((row) => row.trim())
      .filter(Boolean)
      .map((row) => {
        const [headKey, amount, frequency] = row.split(',').map((x) => x.trim());
        return {
          headKey: (headKey || '').toUpperCase(),
          amount: Number(amount || 0),
          frequency: (frequency || 'Y').toUpperCase(),
          optional: false,
        };
      })
      .filter((l) => l.headKey && l.amount > 0);
    if (!lines.length) {
      this.error = 'Add at least one line as HEAD,amount,frequency';
      return;
    }
    this.busy = true;
    this.error = '';
    this.api
      .put(`/api/fee/finance/structures/${encodeURIComponent(key)}`, {
        definitionKey: key,
        name: this.structureDraft.name.trim(),
        currency: this.structureDraft.currency || 'INR',
        notes: this.structureDraft.notes || '',
        eligibility: {
          classSection: this.structureDraft.classSection || undefined,
          academicSessionId: this.structureDraft.academicSessionId || undefined,
        },
        lines,
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.status = `Saved structure ${key}`;
          this.resetStructureDraft();
          this.reload();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Save structure failed';
        },
      });
  }

  postAdjustment(): void {
    const amount = Number(this.adjustDraft.amount || 0);
    if (amount <= 0 || !this.adjustDraft.reason?.trim()) {
      this.error = 'Adjustment amount and reason are required';
      return;
    }
    const path =
      this.adjustDraft.type === 'refund' ? '/api/fee/finance/refund' : '/api/fee/finance/waive';
    this.busy = true;
    this.error = '';
    this.api
      .post(path, {
        amount,
        reason: this.adjustDraft.reason.trim(),
        headKey: this.adjustDraft.headKey || 'MISC',
        studentRef: this.adjustDraft.studentRef || this.studentRef || undefined,
      })
      .subscribe({
        next: (txn: any) => {
          this.busy = false;
          this.status = `${this.adjustDraft.type} posted ${txn.referenceNo || ''}`;
          this.adjustDraft = { ...this.adjustDraft, amount: 0, reason: '' };
          this.reload();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Adjustment failed';
        },
      });
  }

  duplicateHead(h: any): void {
    const p = this.payload(h);
    const base = String(h.definitionKey || p.code || 'HEAD');
    this.editingHeadKey = null;
    this.headDraft = {
      definitionKey: `${base}_COPY`,
      label: `${p.label || base} (Copy)`,
      category: String(p.category || 'ACADEMIC'),
      refundable: p.refundable !== false,
      enabled: true,
      frequency: String(p.frequency || 'M'),
      gstRate: Number(p.gstRate || 0),
      taxable: !!p.taxable,
    };
    this.setTab('heads');
    this.status = `Drafted duplicate of ${base}. Review key/label and save.`;
  }

  collectFor(row: OutstandingRow): void {
    void this.router.navigate(['/admin/fee'], {
      queryParams: { new: '1', admissionNo: row.admissionNo !== '—' ? row.admissionNo : undefined },
    });
  }

  openCollection(row: OutstandingRow): void {
    void this.router.navigate(['/admin/fee'], { queryParams: { id: row.id } });
  }

  reminderStub(row: OutstandingRow, channel: string): void {
    this.status = `${channel} reminder queued for ${row.studentName} (${row.mobile}) — wire notification template next.`;
  }

  saveHead(): void {
    const key = String(this.headDraft.definitionKey || '')
      .trim()
      .toUpperCase();
    if (!key || !String(this.headDraft.label || '').trim()) {
      this.error = 'Head key and label are required';
      return;
    }
    this.busy = true;
    this.error = '';
    this.api
      .put(`/api/fee/finance/heads/${encodeURIComponent(key)}`, {
        ...this.headDraft,
        definitionKey: key,
        gstRate: Number(this.headDraft.gstRate || 0),
        taxable: !!this.headDraft.taxable,
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.status = `Saved head ${key}`;
          this.resetHeadDraft();
          this.reload();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Save head failed';
        },
      });
  }

  createIntent(): void {
    if (!this.paymentGatewayEnabled) {
      this.error = 'FEATURE_MULTI_PAYMENT_GATEWAY is off';
      return;
    }
    this.busy = true;
    this.error = '';
    this.api
      .post<any>('/api/fee/finance/payments/intents', {
        amount: this.demand?.netAmount ?? this.intentAmount,
        studentRef: this.studentRef || 'WALK-IN',
        providerKey: this.providerKey || 'simulated',
        mode: 'UPI',
        idempotencyKey: crypto.randomUUID(),
        demand: this.demand,
      })
      .subscribe({
        next: (intent) => {
          this.busy = false;
          this.lastIntent = intent;
          this.status = `Intent ${intent.referenceNo} ${intent.status} (${intent.adapter || '—'})`;
          this.reload();
          if (intent.checkoutMode === 'RAZORPAY_CHECKOUT' && intent.checkout) {
            this.openRazorpayCheckout(intent);
          }
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Create intent failed';
        },
      });
  }

  openRazorpayCheckout(intent: any): void {
    const checkout = intent.checkout || {};
    const key = intent.publicKey || checkout.razorpayKeyId;
    const orderId = checkout.razorpayOrderId || intent.gatewayOrderId;
    if (!key || !orderId) {
      this.status = 'Razorpay checkout fields missing — check fee.payment.razorpay keys';
      return;
    }
    const w = window as any;
    const launch = () => {
      const rzp = new w.Razorpay({
        key,
        amount: checkout.amountPaise,
        currency: checkout.currency || intent.currency || 'INR',
        name: checkout.name || 'School Fee Payment',
        order_id: orderId,
        handler: (response: any) => {
          this.busy = true;
          this.api
            .post(`/api/fee/finance/payments/intents/${intent.id}/confirm`, {
              razorpayPaymentId: response.razorpay_payment_id,
              razorpayOrderId: response.razorpay_order_id,
              gatewayTxnId: response.razorpay_payment_id,
            })
            .subscribe({
              next: (captured: any) => {
                this.busy = false;
                this.lastIntent = captured;
                this.status = `Captured ${captured.referenceNo}`;
                this.reload();
              },
              error: (err) => {
                this.busy = false;
                this.error = err?.error?.message ?? 'Confirm capture failed';
              },
            });
        },
      });
      rzp.open();
    };
    if (w.Razorpay) {
      launch();
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.onload = () => launch();
    script.onerror = () => {
      this.error = 'Failed to load Razorpay checkout.js';
    };
    document.body.appendChild(script);
  }

  simulateCapture(): void {
    if (!this.lastIntent?.id) return;
    this.busy = true;
    this.api
      .post<any>(`/api/fee/finance/payments/intents/${this.lastIntent.id}/simulate-capture`, {})
      .subscribe({
        next: (intent: any) => {
          this.busy = false;
          this.lastIntent = intent;
          this.status = `Captured ${intent.referenceNo}`;
          this.reload();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Capture failed';
        },
      });
  }

  get filteredTransactions(): any[] {
    let rows = this.transactions;
    if (this.listQ.trim()) {
      const q = this.listQ.trim().toLowerCase();
      rows = rows.filter(
        (t) =>
          String(t.transactionType ?? '')
            .toLowerCase()
            .includes(q) ||
          String(t.status ?? '')
            .toLowerCase()
            .includes(q) ||
          String(t.referenceNo ?? '')
            .toLowerCase()
            .includes(q) ||
          String(t.studentRef ?? '')
            .toLowerCase()
            .includes(q),
      );
    }
    rows = sortRows(rows, this.sortBy, this.sortDir);
    const start = this.pageIndex * this.pageSize;
    return rows.slice(start, start + this.pageSize);
  }

  get filteredTransactionsTotal(): number {
    if (!this.listQ.trim()) return this.transactions.length;
    const q = this.listQ.trim().toLowerCase();
    return this.transactions.filter(
      (t) =>
        String(t.transactionType ?? '')
          .toLowerCase()
          .includes(q) ||
        String(t.status ?? '')
          .toLowerCase()
          .includes(q) ||
        String(t.referenceNo ?? '')
          .toLowerCase()
          .includes(q) ||
        String(t.studentRef ?? '')
          .toLowerCase()
          .includes(q),
    ).length;
  }

  get listClearEnabled(): boolean {
    return (
      !!this.listQ ||
      this.sortBy !== 'createdAt' ||
      this.sortDir !== 'DESC' ||
      this.pageSize !== 20
    );
  }

  clearListFilters(): void {
    this.listQ = '';
    this.sortBy = 'createdAt';
    this.sortDir = 'DESC';
    this.pageSize = 20;
    this.pageIndex = 0;
  }

  onPageChange(index: number): void {
    this.pageIndex = index;
  }

  onDuePageChange(index: number): void {
    this.duePageIndex = index;
  }

  formatMoney(raw: unknown): string {
    if (raw == null || raw === '') return '—';
    const n = Number(raw);
    if (Number.isNaN(n)) return String(raw);
    return formatInr(n).replace('₹ ', '₹ ');
  }

  formatWhen(raw: unknown): string {
    if (!raw) return '—';
    const d = new Date(String(raw));
    if (Number.isNaN(d.getTime())) return String(raw);
    return d.toLocaleString(undefined, {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  statusClass(status: unknown): string {
    const s = String(status || '')
      .trim()
      .toUpperCase();
    if (s === 'CAPTURED' || s === 'SUCCESS' || s === 'PAID' || s === 'APPROVED' || s === 'ACTIVE')
      return 'badge badge-ok';
    if (s === 'FAILED' || s === 'CANCELLED' || s === 'REJECTED') return 'badge badge-bad';
    if (s === 'PENDING' || s === 'CREATED' || s === 'IN_PROGRESS') return 'badge badge-progress';
    return 'badge';
  }

  structureTotal(s: any): number {
    const lines = this.payload(s).lines || [];
    return lines.reduce((sum: number, line: any) => sum + (Number(line.amount) || 0), 0);
  }

  dueTone(days: number): string {
    if (days >= 30) return 'overdue';
    if (days >= 7) return 'late';
    return 'ok';
  }

  recentApproved(): any[] {
    return this.collections
      .filter((c) => String(c.status).toUpperCase() === 'APPROVED')
      .slice(0, 8);
  }

  exportHeadsCsv(): void {
    const lines = ['name,code,category,version'];
    for (const h of this.filteredHeads) {
      const p = this.payload(h);
      lines.push(
        `"${p.label || ''}","${p.code || h.definitionKey || ''}","${p.category || ''}",${h.version || 1}`,
      );
    }
    const blob = new Blob([lines.join('\n')], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'fee-heads.csv';
    a.click();
    URL.revokeObjectURL(url);
  }
}
