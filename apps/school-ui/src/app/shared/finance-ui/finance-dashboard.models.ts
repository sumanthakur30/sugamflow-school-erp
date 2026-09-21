export type FinanceTone = 'ok' | 'info' | 'warn' | 'danger' | 'neutral' | 'accent';

export interface FinanceKpi {
  key: string;
  label: string;
  value: string;
  hint?: string;
  trendLabel?: string;
  trendUp?: boolean | null;
  tone?: FinanceTone;
  actionLabel?: string;
  actionTab?: string;
  actionLink?: string | any[];
  actionQuery?: Record<string, string>;
}

export interface ChartSlice {
  key: string;
  label: string;
  value: number;
  color?: string;
}

export interface OutstandingRow {
  id: string;
  studentName: string;
  admissionNo: string;
  classSection: string;
  parent: string;
  mobile: string;
  dueAmount: number;
  pendingDays: number;
  status: string;
  paymentMode: string;
  updatedAt?: string;
}

export interface ActivityItem {
  id: string;
  title: string;
  detail: string;
  at: string;
  tone: FinanceTone;
}

export interface FinanceAlert {
  id: string;
  title: string;
  detail: string;
  tone: FinanceTone;
  actionLabel?: string;
  actionTab?: string;
  actionLink?: string | any[];
  actionQuery?: Record<string, string>;
}

export interface PaymentModeCard {
  mode: string;
  today: number;
  month: number;
  count: number;
  /** Filter token used when opening Payments / Fee list */
  filterKey?: string;
}
