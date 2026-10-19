export type PassType = 'STUDENT' | 'VISITOR';

export interface GatePayload {
  passType?: PassType | string;
  passNumber?: string;
  passDate?: string;
  purpose?: string;
  reason?: string;
  notes?: string;
  destination?: string;
  expectedReturn?: string;
  actualExit?: string;
  actualReturn?: string;
  actualExitAt?: string;
  actualReturnAt?: string;
  admissionNo?: string;
  studentName?: string;
  className?: string;
  section?: string;
  rollNo?: string;
  photoUrl?: string;
  guardianName?: string;
  relationship?: string;
  contact?: string;
  pickupPerson?: string;
  visitorName?: string;
  mobile?: string;
  category?: string;
  organization?: string;
  idType?: string;
  idRefLast4?: string;
  idRef?: string;
  personToMeet?: string;
  department?: string;
  meetStudentId?: string;
  meetStudentName?: string;
  meetClassName?: string;
  meetSection?: string;
  checkIn?: string;
  checkOut?: string;
  checkInAt?: string;
  checkOutAt?: string;
  vehicleNo?: string;
  accompanying?: number;
  expectedDeparture?: string;
  issuedAt?: string;
  audit?: Array<{ at?: string; by?: string; action?: string; status?: string }>;
}

export interface GatePass {
  id: string;
  status: string;
  subjectType?: string;
  subjectRef?: string;
  subjectName?: string;
  title?: string;
  note?: string;
  payload?: GatePayload;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
}

export interface GateFilters {
  q: string;
  className: string;
  section: string;
  status: string;
  purpose: string;
  category: string;
  person: string;
  from: string;
  to: string;
}

export const EMPTY_FILTERS: GateFilters = {
  q: '',
  className: '',
  section: '',
  status: '',
  purpose: '',
  category: '',
  person: '',
  from: '',
  to: '',
};

export const STUDENT_PURPOSES = ['Medical', 'Family emergency', 'Early leave', 'Appointment', 'Other'];
export const VISITOR_PURPOSES = ['Meeting', 'Delivery', 'Enquiry', 'Official work', 'Other'];
export const VISITOR_CATEGORIES = [
  { id: 'PARENT', label: 'Parent / guardian' },
  { id: 'VENDOR', label: 'Vendor' },
  { id: 'GUEST', label: 'Guest' },
  { id: 'OFFICIAL', label: 'Official' },
  { id: 'OTHER', label: 'Other' },
];

const ELEVATED = new Set(['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL', 'OWNER']);

export function canApprove(role: string | null | undefined): boolean {
  return ELEVATED.has((role || '').toUpperCase());
}

export function passTypeOf(row: GatePass): PassType {
  const raw = String(row.payload?.passType || row.subjectType || '').toUpperCase();
  return raw === 'VISITOR' ? 'VISITOR' : 'STUDENT';
}

export function statusLabel(status: string, type?: PassType): string {
  const code = (status || '').toUpperCase();
  if (type === 'VISITOR') {
    switch (code) {
      case 'INSIDE':
        return 'Checked in';
      case 'COMPLETED':
        return 'Checked out';
      case 'CANCELLED':
        return 'Cancelled';
      case 'OPEN':
        return 'Open';
      default:
        break;
    }
  }
  switch (code) {
    case 'PENDING':
      return 'Pending approval';
    case 'APPROVED':
      return 'Approved';
    case 'REJECTED':
      return 'Rejected';
    case 'OUT':
      return 'Out of school';
    case 'RETURNED':
      return 'Returned';
    case 'CANCELLED':
      return 'Cancelled';
    case 'INSIDE':
      return 'Checked in';
    case 'COMPLETED':
      return 'Checked out';
    case 'OPEN':
      return 'Open';
    default:
      return status || '—';
  }
}

export function maskPhone(value: string | undefined): string {
  const digits = (value || '').replace(/\D/g, '');
  if (digits.length < 4) {
    return value || '—';
  }
  return `••••${digits.slice(-4)}`;
}

export function matches(row: GatePass, type: PassType, filters: GateFilters): boolean {
  if (passTypeOf(row) !== type) {
    return false;
  }
  const payload = row.payload || {};
  if (filters.status && (row.status || '').toUpperCase() !== filters.status.toUpperCase()) {
    return false;
  }
  if (filters.className && !String(payload.className || '').toLowerCase().includes(filters.className.toLowerCase())) {
    return false;
  }
  if (filters.section && String(payload.section || '').toLowerCase() !== filters.section.toLowerCase()) {
    return false;
  }
  if (filters.purpose && !String(payload.purpose || '').toLowerCase().includes(filters.purpose.toLowerCase())) {
    return false;
  }
  if (filters.category && String(payload.category || '').toUpperCase() !== filters.category.toUpperCase()) {
    return false;
  }
  if (filters.person) {
    const who = `${payload.personToMeet || ''} ${payload.department || ''}`.toLowerCase();
    if (!who.includes(filters.person.toLowerCase())) {
      return false;
    }
  }
  const passDate = String(payload.passDate || '');
  if (filters.from && (!passDate || passDate < filters.from)) {
    return false;
  }
  if (filters.to && (!passDate || passDate > filters.to)) {
    return false;
  }
  const q = filters.q.trim().toLowerCase();
  if (!q) {
    return true;
  }
  const hay = [
    row.title,
    row.subjectName,
    row.subjectRef,
    payload.admissionNo,
    payload.visitorName,
    payload.mobile,
    payload.passNumber,
    payload.personToMeet,
  ]
    .join(' ')
    .toLowerCase();
  return hay.includes(q);
}

export interface GateStats {
  today: number;
  studentsOutside: number;
  visitorsInside: number;
  completed: number;
}

export function statsFor(rows: GatePass[], today: string): GateStats {
  let todayCount = 0;
  let studentsOutside = 0;
  let visitorsInside = 0;
  let completed = 0;
  for (const row of rows) {
    const type = passTypeOf(row);
    const status = (row.status || '').toUpperCase();
    const passDate = String(row.payload?.passDate || '');
    if (passDate === today) {
      todayCount += 1;
    }
    if (type === 'STUDENT' && status === 'OUT') {
      studentsOutside += 1;
    }
    if (type === 'VISITOR' && status === 'INSIDE') {
      visitorsInside += 1;
    }
    if (
      (status === 'RETURNED' || status === 'COMPLETED') &&
      (passDate === today ||
        onKolkataDay(row.payload?.actualReturnAt, today) ||
        onKolkataDay(row.payload?.checkOutAt, today))
    ) {
      completed += 1;
    }
  }
  return { today: todayCount, studentsOutside, visitorsInside, completed };
}

export function kolkataToday(now = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Kolkata',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}

function onKolkataDay(iso: string | undefined, today: string): boolean {
  if (!iso) {
    return false;
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return false;
  }
  return kolkataToday(date) === today;
}
