export type EventType =
  | 'Holiday'
  | 'Vacation'
  | 'Academic'
  | 'Exam'
  | 'Meeting'
  | 'Activity'
  | 'Attendance'
  | 'Announcement';

export type Recurrence = 'none' | 'daily' | 'weekly' | 'monthly';
export type EventStatus = 'Draft' | 'Pending Approval' | 'Approved' | 'Rejected' | 'Cancelled';

export interface CalEvent {
  id: string;
  title: string;
  type: EventType;
  description: string;
  start: string;
  end: string;
  startTime: string;
  endTime: string;
  allDay: boolean;
  location: string;
  meetingUrl: string;
  audience: string;
  className: string;
  section: string;
  subject: string;
  academicYear: string;
  attendanceApplies: boolean;
  recurrence: Recurrence;
  reminder: string;
  notify: string[];
  status: EventStatus;
  organizer: string;
  createdAt: string;
  updatedAt: string;
  campusHoliday?: boolean;
  exceptions?: string[];
}

export interface Occurrence {
  event: CalEvent;
  date: string;
}

export const EVENT_TYPES: EventType[] = [
  'Holiday',
  'Vacation',
  'Academic',
  'Exam',
  'Meeting',
  'Activity',
  'Attendance',
  'Announcement',
];

export const CALENDARS: Array<{ id: string; label: string; types: EventType[] }> = [
  { id: 'events', label: 'School events', types: ['Academic', 'Announcement'] },
  { id: 'holidays', label: 'Holidays', types: ['Holiday'] },
  { id: 'exams', label: 'Exams', types: ['Exam'] },
  { id: 'meetings', label: 'Meetings', types: ['Meeting'] },
  { id: 'activities', label: 'Activities', types: ['Activity'] },
  { id: 'attendance', label: 'Attendance', types: ['Attendance'] },
  { id: 'academic', label: 'Academic calendar', types: ['Vacation'] },
];

export const TYPE_COLOR: Record<EventType, string> = {
  Holiday: '#b91c1c',
  Vacation: '#c2410c',
  Academic: '#1d4ed8',
  Exam: '#6d28d9',
  Meeting: '#0b6e4f',
  Activity: '#0f766e',
  Attendance: '#1e3a8a',
  Announcement: '#a16207',
};

export const ACADEMIC_YEARS = ['2025-26', '2026-27', '2027-28'];

export function seedEvents(): CalEvent[] {
  const now = new Date().toISOString();
  const base = {
    meetingUrl: '',
    audience: 'Entire school',
    className: '',
    section: '',
    subject: '',
    academicYear: '2026-27',
    reminder: '1 day',
    notify: ['teachers'],
    status: 'Approved' as EventStatus,
    organizer: 'School office',
    createdAt: now,
    updatedAt: now,
    exceptions: [] as string[],
  };
  return [
    {
      ...base,
      id: 'h-gandhi',
      title: 'Gandhi Jayanti',
      type: 'Holiday',
      description: 'National holiday. Attendance is closed for this date.',
      start: '2026-10-02',
      end: '2026-10-02',
      startTime: '',
      endTime: '',
      allDay: true,
      location: 'Campus',
      attendanceApplies: false,
      recurrence: 'none',
    },
    {
      ...base,
      id: 'v-diwali',
      title: 'Diwali break',
      type: 'Vacation',
      description: 'Festival break. The range is one vacation, not a separate event each day.',
      start: '2026-10-20',
      end: '2026-10-24',
      startTime: '',
      endTime: '',
      allDay: true,
      location: 'Campus',
      attendanceApplies: false,
      recurrence: 'none',
    },
    {
      ...base,
      id: 'e-math',
      title: 'Mathematics exam',
      type: 'Exam',
      description: 'Unit test for Class 8-A.',
      start: '2026-10-12',
      end: '2026-10-12',
      startTime: '09:00',
      endTime: '11:00',
      allDay: false,
      location: 'Room 204',
      audience: 'Students',
      className: 'Class 8-A',
      section: 'A',
      subject: 'Mathematics',
      attendanceApplies: true,
      recurrence: 'none',
      organizer: 'Neha Kumari',
    },
    {
      ...base,
      id: 'm-staff',
      title: 'Staff meeting',
      type: 'Meeting',
      description: 'Weekly staff meeting.',
      start: '2026-10-05',
      end: '2027-03-31',
      startTime: '15:00',
      endTime: '16:00',
      allDay: false,
      location: 'Conference room',
      audience: 'Staff',
      attendanceApplies: false,
      recurrence: 'weekly',
      organizer: 'Principal',
    },
    {
      ...base,
      id: 'm-ptm',
      title: 'Parent-teacher meeting',
      type: 'Meeting',
      description: 'Class 8-A families.',
      start: '2026-10-13',
      end: '2026-10-13',
      startTime: '11:00',
      endTime: '13:00',
      allDay: false,
      location: 'Room 204',
      audience: 'Parents',
      className: 'Class 8-A',
      section: 'A',
      subject: 'Mathematics',
      attendanceApplies: false,
      recurrence: 'none',
      organizer: 'Neha Kumari',
    },
    {
      ...base,
      id: 'a-maths',
      title: 'Mathematics class',
      type: 'Academic',
      description: 'Regular period.',
      start: '2026-10-09',
      end: '2026-10-09',
      startTime: '10:00',
      endTime: '11:00',
      allDay: false,
      location: 'Room 204',
      audience: 'Students',
      className: 'Class 8-A',
      section: 'A',
      subject: 'Mathematics',
      attendanceApplies: true,
      recurrence: 'none',
      organizer: 'Neha Kumari',
    },
  ];
}

export function isoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function parseIso(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, (m || 1) - 1, d || 1);
}

export function addDays(iso: string, days: number): string {
  const date = parseIso(iso);
  date.setDate(date.getDate() + days);
  return isoDate(date);
}

export function datesBetween(start: string, end: string, limit = 370): string[] {
  const out: string[] = [];
  let cursor = start;
  const last = end < start ? start : end;
  while (cursor <= last && out.length < limit) {
    out.push(cursor);
    cursor = addDays(cursor, 1);
  }
  return out;
}

export function occurrencesInRange(events: CalEvent[], from: string, to: string): Occurrence[] {
  const out: Occurrence[] = [];
  for (const event of events) {
    if (event.status === 'Cancelled' || event.status === 'Rejected') continue;
    const skipped = new Set(event.exceptions || []);
    if (event.recurrence === 'none') {
      for (const date of datesBetween(event.start, event.end)) {
        if (date >= from && date <= to && !skipped.has(date)) out.push({ event, date });
      }
      continue;
    }
    let cursor = event.start;
    const seriesEnd = event.end < event.start ? event.start : event.end;
    let guard = 0;
    while (cursor <= seriesEnd && cursor <= to && guard < 400) {
      if (cursor >= from && !skipped.has(cursor)) out.push({ event, date: cursor });
      cursor =
        event.recurrence === 'daily'
          ? addDays(cursor, 1)
          : event.recurrence === 'weekly'
            ? addDays(cursor, 7)
            : addDays(cursor, 30);
      guard += 1;
    }
  }
  return out.sort((a, b) => a.date.localeCompare(b.date) || a.event.startTime.localeCompare(b.event.startTime));
}
