import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';
import {
  ACADEMIC_YEARS,
  CALENDARS,
  CalEvent,
  EVENT_TYPES,
  EventType,
  Occurrence,
  TYPE_COLOR,
  addDays,
  isoDate,
  occurrencesInRange,
  parseIso,
  seedEvents,
} from './school-calendar.model';

type ViewMode = 'month' | 'week' | 'day' | 'agenda';

interface Draft {
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
  recurrence: CalEvent['recurrence'];
  reminder: string;
  notifyTeachers: boolean;
  notifyStudents: boolean;
  notifyParents: boolean;
  status: CalEvent['status'];
}

@Component({
  selector: 'sf-school-calendar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './school-calendar.component.html',
  styleUrls: ['./school-calendar.component.scss'],
})
export class SchoolCalendarComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly storageKey = 'sf-school-calendar-events';

  readonly calendars = CALENDARS;
  readonly types = EVENT_TYPES;
  readonly years = ACADEMIC_YEARS;
  readonly colors = TYPE_COLOR;
  readonly hours = [8, 9, 10, 11, 12, 13, 14, 15, 16, 17];

  cursor = new Date(2026, 9, 9);
  view: ViewMode = 'month';
  year = '2026-27';
  search = '';
  classFilter = '';
  enabled: Record<string, boolean> = Object.fromEntries(CALENDARS.map((row) => [row.id, true]));
  events: CalEvent[] = [];
  workingDays = ['MON', 'TUE', 'WED', 'THU', 'FRI'];
  drawer = false;
  more = false;
  editingId: string | null = null;
  draft: Draft = this.emptyDraft(isoDate(this.cursor));
  error = '';
  notice = '';
  selected: Occurrence | null = null;
  pendingMove: { occurrence: Occurrence; to: string } | null = null;
  conflict = '';
  audit: string[] = [];
  drag: { id: string; date: string } | null = null;
  dayOverflow: string | null = null;

  ngOnInit(): void {
    if (window.innerWidth < 720) this.view = 'agenda';
    this.events = this.readStored();
    this.api.get<any>('/api/academic/calendar').subscribe({
      next: (calendar) => this.mergeCampus(calendar),
      error: () => {
        this.notice = 'Campus holiday list is unavailable. This calendar is using the events saved in this browser.';
      },
    });
  }

  get canManage(): boolean {
    return ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'].includes(
      (this.auth.getRole() || '').toUpperCase(),
    );
  }

  get canEdit(): boolean {
    return this.canManage || (this.auth.getRole() || '').toUpperCase() === 'TEACHER';
  }

  get title(): string {
    return this.cursor.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
  }

  get range(): { from: string; to: string } {
    if (this.view === 'day') {
      const iso = isoDate(this.cursor);
      return { from: iso, to: iso };
    }
    if (this.view === 'week') {
      const start = this.weekStart();
      return { from: isoDate(start), to: isoDate(new Date(start.getFullYear(), start.getMonth(), start.getDate() + 6)) };
    }
    if (this.view === 'agenda') {
      const from = isoDate(this.cursor);
      return { from, to: addDays(from, 21) };
    }
    const start = new Date(this.cursor.getFullYear(), this.cursor.getMonth(), 1);
    start.setDate(1 - start.getDay());
    const end = new Date(start);
    end.setDate(start.getDate() + 41);
    return { from: isoDate(start), to: isoDate(end) };
  }

  get visible(): Occurrence[] {
    const query = this.search.trim().toLowerCase();
    const allowed = new Set(
      CALENDARS.filter((row) => this.enabled[row.id]).flatMap((row) => row.types),
    );
    return occurrencesInRange(this.events, this.range.from, this.range.to).filter((row) => {
      if (row.event.academicYear !== this.year) return false;
      if (!allowed.has(row.event.type)) return false;
      if (this.classFilter && row.event.className !== this.classFilter) return false;
      if (!query) return true;
      const blob = [
        row.event.title,
        row.event.type,
        row.event.organizer,
        row.event.className,
        row.event.section,
        row.event.subject,
        row.event.location,
        row.date,
      ]
        .join(' ')
        .toLowerCase();
      return blob.includes(query);
    });
  }

  get upcoming(): Occurrence[] {
    const today = isoDate(new Date());
    return occurrencesInRange(this.events, today, addDays(today, 14))
      .filter((row) => row.event.academicYear === this.year)
      .slice(0, 5);
  }

  monthCells(): Array<{ iso: string; inMonth: boolean; weekend: boolean }> {
    const start = new Date(this.cursor.getFullYear(), this.cursor.getMonth(), 1);
    start.setDate(1 - start.getDay());
    return Array.from({ length: 42 }, (_, index) => {
      const date = new Date(start);
      date.setDate(start.getDate() + index);
      return {
        iso: isoDate(date),
        inMonth: date.getMonth() === this.cursor.getMonth(),
        weekend: date.getDay() === 0 || date.getDay() === 6,
      };
    });
  }

  weekDays(): string[] {
    const start = this.weekStart();
    return Array.from({ length: 7 }, (_, index) =>
      isoDate(new Date(start.getFullYear(), start.getMonth(), start.getDate() + index)),
    );
  }

  onDay(iso: string): Occurrence[] {
    return this.visible.filter((row) => row.date === iso);
  }

  shift(step: number): void {
    const next = new Date(this.cursor);
    if (this.view === 'month') next.setMonth(next.getMonth() + step);
    else if (this.view === 'week') next.setDate(next.getDate() + step * 7);
    else next.setDate(next.getDate() + step);
    this.cursor = next;
  }

  goToday(): void {
    this.cursor = new Date();
  }

  jump(iso: string): void {
    if (!iso) return;
    this.cursor = parseIso(iso);
  }

  openCreate(iso = isoDate(this.cursor)): void {
    if (!this.canEdit) return;
    this.editingId = null;
    this.more = false;
    this.error = '';
    this.draft = this.emptyDraft(iso);
    this.drawer = true;
    this.selected = null;
  }

  openEdit(row: Occurrence, series: boolean): void {
    if (!this.canEdit) return;
    if (!series && row.event.recurrence !== 'none') {
      this.splitOccurrence(row);
      return;
    }
    this.editingId = row.event.id;
    this.more = true;
    this.draft = this.toDraft(row.event);
    this.drawer = true;
    this.selected = null;
  }

  save(force = false): void {
    this.error = '';
    this.conflict = '';
    if (!this.draft.title.trim()) {
      this.error = 'Event title is required.';
      return;
    }
    if (!this.canManage && (this.draft.type === 'Holiday' || this.draft.type === 'Vacation')) {
      this.error = 'Only an administrator can add a holiday or vacation.';
      return;
    }
    if (this.draft.end < this.draft.start) {
      this.error = 'End date cannot be before the start date.';
      return;
    }
    if (!this.draft.allDay && this.draft.end === this.draft.start && this.draft.endTime <= this.draft.startTime) {
      this.error = 'End time must be after the start time.';
      return;
    }
    const next = this.fromDraft();
    const clash = this.findConflict(next);
    if (clash && !force) {
      this.conflict = clash;
      return;
    }
    if (this.editingId) {
      this.events = this.events.map((row) => (row.id === this.editingId ? next : row));
      this.log(`Updated “${next.title}”.`);
    } else {
      this.events = [next, ...this.events];
      this.log(`Created “${next.title}”.`);
    }
    if (next.type === 'Holiday' || next.type === 'Vacation') {
      this.notice = 'Attendance stays closed on these dates. Publish holidays to store them on the campus calendar.';
    }
    this.persist();
    this.drawer = false;
  }

  remove(row: Occurrence): void {
    if (!this.canEdit) return;
    if (!window.confirm(`Delete “${row.event.title}”?`)) return;
    this.events = this.events.filter((event) => event.id !== row.event.id);
    this.selected = null;
    this.log(`Deleted “${row.event.title}”.`);
    this.persist();
  }

  cancelEvent(row: Occurrence): void {
    if (!this.canManage) return;
    this.events = this.events.map((event) =>
      event.id === row.event.id ? { ...event, status: 'Cancelled', updatedAt: new Date().toISOString() } : event,
    );
    this.selected = null;
    this.log(`Cancelled “${row.event.title}”.`);
    this.persist();
  }

  duplicate(row: Occurrence): void {
    const copy: CalEvent = {
      ...row.event,
      id: `ev-${Date.now()}`,
      title: `${row.event.title} copy`,
      start: addDays(row.date, 1),
      end: row.event.recurrence === 'none' ? addDays(row.event.end, 1) : row.event.end,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      campusHoliday: false,
    };
    this.events = [copy, ...this.events];
    this.log(`Duplicated “${row.event.title}”.`);
    this.persist();
  }

  askMove(to: string): void {
    if (!this.drag || !this.canEdit || to === this.drag.date) return;
    const occurrence = this.visible.find((row) => row.event.id === this.drag?.id && row.date === this.drag?.date);
    this.drag = null;
    if (!occurrence) return;
    this.pendingMove = { occurrence, to };
  }

  confirmMove(): void {
    if (!this.pendingMove) return;
    const { occurrence, to } = this.pendingMove;
    const delta = Math.round(
      (parseIso(to).getTime() - parseIso(occurrence.date).getTime()) / 86_400_000,
    );
    this.events = this.events.map((event) =>
      event.id === occurrence.event.id
        ? {
            ...event,
            start: addDays(event.start, delta),
            end: addDays(event.end, delta),
            updatedAt: new Date().toISOString(),
          }
        : event,
    );
    this.log(`Moved “${occurrence.event.title}” to ${to}.`);
    this.pendingMove = null;
    this.persist();
  }

  publishHolidays(): void {
    if (!this.canManage) return;
    const holidays = this.events
      .filter((event) => event.type === 'Holiday' && event.academicYear === this.year && event.recurrence === 'none')
      .flatMap((event) =>
        event.start === event.end
          ? [{ date: event.start, name: event.title }]
          : [],
      );
    this.api
      .put('/api/academic/calendar', { workingDays: this.workingDays, holidays })
      .subscribe({
        next: () => {
          this.notice = 'Holiday dates were saved on the campus calendar. Attendance and timetable keep using that list.';
          this.log('Published holiday dates to the campus calendar.');
        },
        error: () => {
          this.notice = 'The campus calendar could not be updated. Working days were left unchanged.';
        },
      });
  }

  exportCsv(): void {
    const lines = ['date,title,type,start,end,class,location,status'];
    for (const row of this.visible) {
      lines.push(
        [row.date, row.event.title, row.event.type, row.event.startTime, row.event.endTime, row.event.className, row.event.location, row.event.status]
          .map((value) => `"${String(value).replaceAll('"', '""')}"`)
          .join(','),
      );
    }
    const blob = new Blob([lines.join('\n')], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'school-calendar.csv';
    link.click();
    URL.revokeObjectURL(url);
  }

  blockStyle(row: Occurrence): Record<string, string> {
    const start = this.minutes(row.event.startTime || '08:00');
    const end = this.minutes(row.event.endTime || '09:00');
    const top = Math.max(0, ((start - 8 * 60) / 60) * 48);
    const height = Math.max(28, ((end - start) / 60) * 48);
    return { top: `${top}px`, height: `${height}px`, borderColor: TYPE_COLOR[row.event.type] };
  }

  isToday(iso: string): boolean {
    return iso === isoDate(new Date());
  }

  private mergeCampus(calendar: any): void {
    const days = Array.isArray(calendar?.workingDays) ? calendar.workingDays : this.workingDays;
    this.workingDays = days.map((day: string) => String(day).toUpperCase());
    const holidays = Array.isArray(calendar?.holidays) ? calendar.holidays : [];
    for (const holiday of holidays) {
      const date = String(holiday.date || '');
      const name = String(holiday.name || 'Holiday');
      if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) continue;
      if (this.events.some((event) => event.campusHoliday && event.start === date && event.title === name)) continue;
      this.events.push({
        ...this.fromHoliday(date, name),
      });
    }
    this.persist();
  }

  private fromHoliday(date: string, name: string): CalEvent {
    const now = new Date().toISOString();
    return {
      id: `campus-${date}`,
      title: name,
      type: 'Holiday',
      description: 'Saved on the campus academic calendar. Attendance is closed.',
      start: date,
      end: date,
      startTime: '',
      endTime: '',
      allDay: true,
      location: 'Campus',
      meetingUrl: '',
      audience: 'Entire school',
      className: '',
      section: '',
      subject: '',
      academicYear: this.year,
      attendanceApplies: false,
      recurrence: 'none',
      reminder: '1 day',
      notify: ['teachers', 'parents'],
      status: 'Approved',
      organizer: 'School office',
      createdAt: now,
      updatedAt: now,
      campusHoliday: true,
      exceptions: [],
    };
  }

  private findConflict(next: CalEvent): string {
    if (next.allDay || next.recurrence !== 'none') return '';
    const hit = this.events.find((event) => {
      if (event.id === next.id || event.allDay || event.status === 'Cancelled') return false;
      if (event.start !== next.start) return false;
      const sameTeacher = event.organizer && event.organizer === next.organizer;
      const sameClass = event.className && event.className === next.className;
      const sameRoom = event.location && event.location === next.location;
      if (!sameTeacher && !sameClass && !sameRoom) return false;
      return this.overlaps(event.startTime, event.endTime, next.startTime, next.endTime);
    });
    if (!hit) return '';
    return `Schedule conflict with “${hit.title}” (${hit.startTime}–${hit.endTime}, ${hit.location || hit.className || hit.organizer}).`;
  }

  private overlaps(aStart: string, aEnd: string, bStart: string, bEnd: string): boolean {
    return this.minutes(aStart) < this.minutes(bEnd) && this.minutes(bStart) < this.minutes(aEnd);
  }

  private minutes(value: string): number {
    const [h, m] = value.split(':').map(Number);
    return (h || 0) * 60 + (m || 0);
  }

  private splitOccurrence(row: Occurrence): void {
    this.events = this.events.map((event) =>
      event.id === row.event.id
        ? { ...event, exceptions: [...(event.exceptions || []), row.date] }
        : event,
    );
    const copy: CalEvent = {
      ...row.event,
      id: `ev-${Date.now()}`,
      start: row.date,
      end: row.date,
      recurrence: 'none',
      exceptions: [],
      campusHoliday: false,
      updatedAt: new Date().toISOString(),
    };
    this.events = [copy, ...this.events];
    this.persist();
    this.selected = { event: copy, date: row.date };
    this.openEdit(this.selected, true);
    this.log(`Opened ${row.date} of “${row.event.title}” without changing earlier dates.`);
  }

  private emptyDraft(iso: string): Draft {
    return {
      title: '',
      type: 'Academic',
      description: '',
      start: iso,
      end: iso,
      startTime: '09:00',
      endTime: '10:00',
      allDay: false,
      location: '',
      meetingUrl: '',
      audience: 'Entire school',
      className: '',
      section: '',
      subject: '',
      academicYear: this.year,
      attendanceApplies: true,
      recurrence: 'none',
      reminder: 'None',
      notifyTeachers: false,
      notifyStudents: false,
      notifyParents: false,
      status: this.canManage ? 'Approved' : 'Pending Approval',
    };
  }

  private toDraft(event: CalEvent): Draft {
    return {
      title: event.title,
      type: event.type,
      description: event.description,
      start: event.start,
      end: event.end,
      startTime: event.startTime || '09:00',
      endTime: event.endTime || '10:00',
      allDay: event.allDay,
      location: event.location,
      meetingUrl: event.meetingUrl,
      audience: event.audience,
      className: event.className,
      section: event.section,
      subject: event.subject,
      academicYear: event.academicYear,
      attendanceApplies: event.attendanceApplies,
      recurrence: event.recurrence,
      reminder: event.reminder,
      notifyTeachers: event.notify.includes('teachers'),
      notifyStudents: event.notify.includes('students'),
      notifyParents: event.notify.includes('parents'),
      status: event.status,
    };
  }

  private fromDraft(): CalEvent {
    const now = new Date().toISOString();
    const existing = this.events.find((event) => event.id === this.editingId);
    const attendanceApplies =
      this.draft.type === 'Holiday' || this.draft.type === 'Vacation' ? false : this.draft.attendanceApplies;
    return {
      id: this.editingId || `ev-${Date.now()}`,
      title: this.draft.title.trim(),
      type: this.draft.type,
      description: this.draft.description.trim(),
      start: this.draft.start,
      end: this.draft.end,
      startTime: this.draft.allDay ? '' : this.draft.startTime,
      endTime: this.draft.allDay ? '' : this.draft.endTime,
      allDay: this.draft.allDay || this.draft.type === 'Holiday' || this.draft.type === 'Vacation',
      location: this.draft.location.trim(),
      meetingUrl: this.draft.meetingUrl.trim(),
      audience: this.draft.audience,
      className: this.draft.className.trim(),
      section: this.draft.section.trim(),
      subject: this.draft.subject.trim(),
      academicYear: this.draft.academicYear,
      attendanceApplies,
      recurrence: this.draft.recurrence,
      reminder: this.draft.reminder,
      notify: [
        this.draft.notifyTeachers ? 'teachers' : '',
        this.draft.notifyStudents ? 'students' : '',
        this.draft.notifyParents ? 'parents' : '',
      ].filter(Boolean),
      status: this.draft.status,
      organizer: existing?.organizer || 'School office',
      createdAt: existing?.createdAt || now,
      updatedAt: now,
      campusHoliday: existing?.campusHoliday,
      exceptions: existing?.exceptions || [],
    };
  }

  private weekStart(): Date {
    const date = new Date(this.cursor);
    date.setDate(date.getDate() - date.getDay());
    return date;
  }

  private readStored(): CalEvent[] {
    try {
      const raw = localStorage.getItem(this.storageKey);
      if (!raw) return seedEvents();
      const parsed = JSON.parse(raw) as CalEvent[];
      return Array.isArray(parsed) && parsed.length ? parsed : seedEvents();
    } catch {
      return seedEvents();
    }
  }

  private persist(): void {
    localStorage.setItem(this.storageKey, JSON.stringify(this.events));
  }

  private log(message: string): void {
    this.audit = [`${new Date().toLocaleString()} — ${message}`, ...this.audit].slice(0, 12);
  }
}
