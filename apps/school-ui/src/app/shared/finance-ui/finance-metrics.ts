import {
  ActivityItem,
  ChartSlice,
  FinanceAlert,
  FinanceKpi,
  OutstandingRow,
  PaymentModeCard,
} from './finance-dashboard.models';

function num(v: unknown): number {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
}

function isSameDay(iso: string | undefined, ref: Date): boolean {
  if (!iso) return false;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return false;
  return (
    d.getFullYear() === ref.getFullYear() &&
    d.getMonth() === ref.getMonth() &&
    d.getDate() === ref.getDate()
  );
}

function isSameMonth(iso: string | undefined, ref: Date): boolean {
  if (!iso) return false;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return false;
  return d.getFullYear() === ref.getFullYear() && d.getMonth() === ref.getMonth();
}

function money(n: number): string {
  return `₹ ${n.toLocaleString('en-IN', { maximumFractionDigits: 0 })}`;
}

export function formatInr(n: number): string {
  return money(n);
}

/** Build KPIs from collections, finance txs, and student directory summary. */
export function buildFinanceKpis(input: {
  collections: any[];
  transactions: any[];
  studentTotal: number;
  studentActive: number;
  concessionsCount: number;
  gatewayOn: boolean;
}): FinanceKpi[] {
  const now = new Date();
  const prev = new Date(now.getFullYear(), now.getMonth() - 1, 1);

  const approved = input.collections.filter((c) => String(c.status).toUpperCase() === 'APPROVED');
  const pending = input.collections.filter((c) => String(c.status).toUpperCase() !== 'APPROVED');

  const sumAnswers = (rows: any[]) =>
    rows.reduce((s, r) => s + num(r?.answers?.amount ?? r?.netAmount), 0);

  const totalCollected = sumAnswers(approved);
  const pendingFees = sumAnswers(pending);
  const todayCollection = sumAnswers(
    approved.filter((c) => isSameDay(c.updatedAt || c.createdAt, now)),
  );
  const monthCollection = sumAnswers(
    approved.filter((c) => isSameMonth(c.updatedAt || c.createdAt, now)),
  );
  const prevMonthCollection = sumAnswers(
    approved.filter((c) => isSameMonth(c.updatedAt || c.createdAt, prev)),
  );

  const onlineTx = input.transactions.filter((t) =>
    ['CAPTURED', 'SUCCESS', 'PAID'].includes(String(t.status || '').toUpperCase()),
  );
  const onlineAmount = onlineTx.reduce((s, t) => s + num(t.netAmount), 0);
  const cashRows = approved.filter((c) =>
    String(c.answers?.paymentMode || '')
      .toUpperCase()
      .includes('CASH'),
  );
  const cashAmount = sumAnswers(cashRows);

  const monthDelta =
    prevMonthCollection > 0
      ? Math.round(((monthCollection - prevMonthCollection) / prevMonthCollection) * 100)
      : monthCollection > 0
        ? 100
        : 0;

  const dueStudents = new Set(
    pending.map((c) => String(c.answers?.admissionNo || c.id)).filter(Boolean),
  ).size;

  return [
    {
      key: 'total',
      label: 'Total fee collection',
      value: money(totalCollected),
      hint: `${approved.length} approved receipts`,
      trendLabel: `${monthDelta >= 0 ? '↑' : '↓'} ${Math.abs(monthDelta)}% vs last month`,
      trendUp: monthDelta >= 0,
      tone: 'ok',
      actionLabel: 'View collections',
      actionLink: ['/admin/fee'],
    },
    {
      key: 'pending',
      label: 'Pending fees',
      value: money(pendingFees),
      hint: `${pending.length} open collections`,
      tone: pendingFees > 0 ? 'warn' : 'neutral',
      actionLabel: 'Review dues',
      actionTab: 'collections',
    },
    {
      key: 'today',
      label: "Today's collection",
      value: money(todayCollection),
      tone: 'info',
      actionLabel: 'Collect fee',
      actionLink: ['/admin/fee'],
      actionQuery: { new: '1' },
    },
    {
      key: 'month',
      label: 'This month',
      value: money(monthCollection),
      trendLabel: `${monthDelta >= 0 ? '↑' : '↓'} ${Math.abs(monthDelta)}%`,
      trendUp: monthDelta >= 0,
      tone: 'ok',
      actionLabel: 'Open fee list',
      actionLink: ['/admin/fee'],
    },
    {
      key: 'students',
      label: 'Total students',
      value: String(input.studentActive || input.studentTotal || 0),
      hint: `${input.studentTotal} in directory`,
      tone: 'info',
      actionLabel: 'Open directory',
      actionLink: ['/admin/student-directory'],
    },
    {
      key: 'dueStudents',
      label: 'Students with dues',
      value: String(dueStudents),
      tone: dueStudents ? 'danger' : 'neutral',
      actionLabel: 'Review dues',
      actionTab: 'collections',
    },
    {
      key: 'concessions',
      label: 'Concessions configured',
      value: String(input.concessionsCount),
      tone: 'accent',
      actionLabel: 'Manage concessions',
      actionTab: 'concessions',
    },
    {
      key: 'refunds',
      label: 'Refunds processed',
      value: String(
        input.transactions.filter((t) =>
          String(t.transactionType || '')
            .toUpperCase()
            .includes('REFUND'),
        ).length,
      ),
      tone: 'neutral',
      actionLabel: 'View payments',
      actionTab: 'payments',
      actionQuery: { q: 'refund' },
    },
    {
      key: 'online',
      label: 'Online payments',
      value: money(onlineAmount),
      hint: input.gatewayOn ? `${onlineTx.length} gateway captures` : 'Gateway feature off',
      tone: 'info',
      actionLabel: input.gatewayOn ? 'View payments' : 'Gateway settings',
      actionTab: input.gatewayOn ? 'payments' : 'settings',
    },
    {
      key: 'cash',
      label: 'Cash collections',
      value: money(cashAmount),
      hint: `${cashRows.length} cash receipts`,
      tone: 'ok',
      actionLabel: 'View cash receipts',
      actionLink: ['/admin/fee'],
      actionQuery: { q: 'cash' },
    },
  ];
}

export function buildOutstanding(collections: any[]): OutstandingRow[] {
  return collections
    .filter((c) => String(c.status || '').toUpperCase() !== 'APPROVED')
    .map((c) => ({
      id: String(c.id),
      studentName: String(c.answers?.studentName || '—'),
      admissionNo: String(c.answers?.admissionNo || '—'),
      classSection: String(c.answers?.classSection || c.answers?.classApplied || '—'),
      parent: String(c.answers?.fatherName || c.answers?.parentName || '—'),
      mobile: String(c.answers?.mobile || '—'),
      dueAmount: num(c.answers?.amount),
      pendingDays: num(c.answers?.pendingDays),
      status: String(c.status || 'PENDING'),
      paymentMode: String(c.answers?.paymentMode || '—'),
      updatedAt: c.updatedAt || c.createdAt,
    }))
    .sort((a, b) => b.dueAmount - a.dueAmount);
}

export function monthlyCollectionSeries(collections: any[], months = 6): ChartSlice[] {
  const now = new Date();
  const out: ChartSlice[] = [];
  for (let i = months - 1; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const key = `${d.getFullYear()}-${d.getMonth() + 1}`;
    const label = d.toLocaleString(undefined, { month: 'short' });
    const value = collections
      .filter((c) => String(c.status).toUpperCase() === 'APPROVED')
      .filter((c) => isSameMonth(c.updatedAt || c.createdAt, d))
      .reduce((s, c) => s + num(c.answers?.amount), 0);
    out.push({ key, label, value });
  }
  return out;
}

export function paymentModeSlices(collections: any[]): ChartSlice[] {
  const map = new Map<string, number>();
  for (const c of collections) {
    if (String(c.status).toUpperCase() !== 'APPROVED') continue;
    const mode = String(c.answers?.paymentMode || 'OTHER').trim() || 'OTHER';
    map.set(mode, (map.get(mode) || 0) + num(c.answers?.amount));
  }
  const colors = ['#0b6e4f', '#2563eb', '#c2410c', '#7c3aed', '#64748b', '#0891b2', '#b45309'];
  return [...map.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([label, value], i) => ({
      key: label,
      label,
      value,
      color: colors[i % colors.length],
    }));
}

export function feeHeadSlices(collections: any[]): ChartSlice[] {
  const map = new Map<string, number>();
  for (const c of collections) {
    if (String(c.status).toUpperCase() !== 'APPROVED') continue;
    const head = String(c.answers?.feeHead || 'Other').trim() || 'Other';
    map.set(head, (map.get(head) || 0) + num(c.answers?.amount));
  }
  return [...map.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 8)
    .map(([label, value]) => ({ key: label, label, value }));
}

export function pendingByClass(collections: any[]): ChartSlice[] {
  const map = new Map<string, number>();
  for (const c of collections) {
    if (String(c.status).toUpperCase() === 'APPROVED') continue;
    const cls = String(c.answers?.classSection || c.answers?.classApplied || 'Unassigned');
    map.set(cls, (map.get(cls) || 0) + num(c.answers?.amount));
  }
  return [...map.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 8)
    .map(([label, value]) => ({ key: label, label, value }));
}

export function paymentModeCards(collections: any[]): PaymentModeCard[] {
  const now = new Date();
  const modes = ['CASH', 'UPI', 'CARD', 'NET BANKING', 'CHEQUE', 'WALLET', 'QR'];
  return modes.map((mode) => {
    const filterKey = mode.split(' ')[0];
    const rows = collections.filter((c) => {
      if (String(c.status).toUpperCase() !== 'APPROVED') return false;
      return String(c.answers?.paymentMode || '')
        .toUpperCase()
        .includes(filterKey);
    });
    return {
      mode: mode
        .toLowerCase()
        .replace(/\b\w/g, (c) => c.toUpperCase()),
      filterKey,
      today: rows
        .filter((c) => isSameDay(c.updatedAt || c.createdAt, now))
        .reduce((s, c) => s + num(c.answers?.amount), 0),
      month: rows
        .filter((c) => isSameMonth(c.updatedAt || c.createdAt, now))
        .reduce((s, c) => s + num(c.answers?.amount), 0),
      count: rows.length,
    };
  });
}

export function buildAlerts(input: {
  pendingCount: number;
  pendingAmount: number;
  gatewayOn: boolean;
  structuresCount: number;
  dueStudents: number;
}): FinanceAlert[] {
  const alerts: FinanceAlert[] = [];
  if (input.dueStudents > 0) {
    alerts.push({
      id: 'dues',
      title: `${input.dueStudents} students have pending fees`,
      detail: `${formatInr(input.pendingAmount)} outstanding across ${input.pendingCount} collections`,
      tone: 'warn',
      actionLabel: 'Review dues',
      actionTab: 'collections',
    });
  }
  if (!input.gatewayOn) {
    alerts.push({
      id: 'gateway',
      title: 'Online payment gateway feature is off',
      detail: 'Enable FEATURE_MULTI_PAYMENT_GATEWAY to accept UPI / card intents.',
      tone: 'info',
      actionLabel: 'Open settings',
      actionTab: 'settings',
    });
  }
  if (input.structuresCount === 0) {
    alerts.push({
      id: 'structure',
      title: 'No fee structure configured',
      detail: 'Create a structure before generating class-wise demands.',
      tone: 'danger',
      actionLabel: 'Open structures',
      actionTab: 'structures',
    });
  }
  if (!alerts.length) {
    alerts.push({
      id: 'ok',
      title: 'Finance looks healthy',
      detail: 'No critical alerts right now.',
      tone: 'ok',
      actionLabel: 'View collections',
      actionLink: ['/admin/fee'],
    });
  }
  return alerts;
}

export function buildActivity(collections: any[], transactions: any[], heads: any[]): ActivityItem[] {
  const items: ActivityItem[] = [];
  for (const c of collections.slice(0, 8)) {
    items.push({
      id: `c-${c.id}`,
      title:
        String(c.status).toUpperCase() === 'APPROVED'
          ? 'Collection received'
          : 'Collection in progress',
      detail: `${c.answers?.studentName || 'Student'} · ${formatInr(num(c.answers?.amount))}`,
      at: c.updatedAt || c.createdAt || '',
      tone: String(c.status).toUpperCase() === 'APPROVED' ? 'ok' : 'warn',
    });
  }
  for (const t of transactions.slice(0, 5)) {
    items.push({
      id: `t-${t.id}`,
      title: String(t.transactionType || 'Payment').replace(/_/g, ' '),
      detail: `${t.referenceNo || '—'} · ${formatInr(num(t.netAmount))}`,
      at: t.createdAt || '',
      tone: String(t.status).toUpperCase() === 'CAPTURED' ? 'ok' : 'info',
    });
  }
  for (const h of heads.slice(0, 3)) {
    items.push({
      id: `h-${h.definitionKey || h.id}`,
      title: 'Fee head available',
      detail: String(h.payload?.label || h.definitionKey || 'Head'),
      at: h.updatedAt || h.createdAt || new Date().toISOString(),
      tone: 'neutral',
    });
  }
  return items
    .sort((a, b) => new Date(b.at).getTime() - new Date(a.at).getTime())
    .slice(0, 12);
}
