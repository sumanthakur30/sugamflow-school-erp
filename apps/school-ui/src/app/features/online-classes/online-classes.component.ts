import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  CLASS_STATUSES,
  ClassStatus,
  MeetingPlatform,
  ONLINE_CLASS_SEED,
  OnlineClass,
  PLATFORMS,
  SECTION_OPTIONS,
  SESSION_WINDOWS,
  SUBJECT_OPTIONS,
  SessionWindow,
  TEACHER_OPTIONS,
} from './online-class.model';

interface ClassDraft {
  title: string;
  subject: string;
  platform: MeetingPlatform;
  sections: string[];
  teacher: string;
  startsAt: string;
  durationMinutes: number;
  passcode: string;
  description: string;
  notify: boolean;
  meetingUrl: string;
}

@Component({
  selector: 'sf-online-classes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './online-classes.component.html',
  styleUrls: ['./online-classes.component.scss'],
})
export class OnlineClassesComponent {
  readonly platforms = PLATFORMS;
  readonly statuses = CLASS_STATUSES;
  readonly windows = SESSION_WINDOWS;
  readonly sectionOptions = SECTION_OPTIONS;
  readonly subjectOptions = SUBJECT_OPTIONS;
  readonly teacherOptions = TEACHER_OPTIONS;

  classes: OnlineClass[] = ONLINE_CLASS_SEED.map((row) => ({ ...row, sections: [...row.sections] }));
  view: 'table' | 'grid' = 'table';
  audience: 'teacher' | 'student' = 'teacher';

  query = '';
  platform = '';
  window: '' | SessionWindow = '';
  status = '';
  sortAsc = true;
  notice = '';

  editorOpen = false;
  editingId: string | null = null;
  draft: ClassDraft = this.emptyDraft();

  recordingFor: OnlineClass | null = null;
  recordingUrl = '';

  get visible(): OnlineClass[] {
    const q = this.query.trim().toLowerCase();
    const rows = this.classes.filter((row) => {
      if (q && !`${row.title} ${row.subject} ${row.teacher}`.toLowerCase().includes(q)) return false;
      if (this.platform && row.platform !== this.platform) return false;
      if (this.status && row.status !== this.status) return false;
      if (this.window && this.sessionWindow(row) !== this.window) return false;
      return true;
    });
    return rows.sort((a, b) => {
      const delta = new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime();
      return this.sortAsc ? delta : -delta;
    });
  }

  toggleSort(): void {
    this.sortAsc = !this.sortAsc;
  }

  openCreate(): void {
    this.editingId = null;
    this.draft = this.emptyDraft();
    this.editorOpen = true;
  }

  openEdit(row: OnlineClass): void {
    this.editingId = row.id;
    this.draft = {
      title: row.title,
      subject: row.subject,
      platform: row.platform,
      sections: [...row.sections],
      teacher: row.teacher,
      startsAt: this.toLocalInput(row.startsAt),
      durationMinutes: row.durationMinutes,
      passcode: row.passcode,
      description: row.description,
      notify: row.notify,
      meetingUrl: row.meetingUrl,
    };
    this.editorOpen = true;
  }

  closeEditor(): void {
    this.editorOpen = false;
    this.editingId = null;
  }

  toggleSection(name: string, checked: boolean): void {
    const next = new Set(this.draft.sections);
    if (checked) next.add(name);
    else next.delete(name);
    this.draft.sections = this.sectionOptions.filter((section) => next.has(section));
  }

  save(): void {
    const starts = this.draft.startsAt
      ? new Date(this.draft.startsAt).toISOString()
      : new Date(Date.now() + 60 * 60_000).toISOString();
    const existing = this.classes.find((row) => row.id === this.editingId);
    const next: OnlineClass = {
      id: this.editingId || `oc-${Date.now()}`,
      title: this.draft.title.trim() || 'Untitled class',
      subject: this.draft.subject,
      platform: this.draft.platform,
      startsAt: starts,
      durationMinutes: Number(this.draft.durationMinutes) || 40,
      sections: this.draft.sections.length ? [...this.draft.sections] : ['Class 10-A'],
      teacher: this.draft.teacher,
      meetingUrl: this.draft.meetingUrl.trim() || this.placeholderLink(this.draft.platform),
      passcode: this.draft.passcode.trim(),
      description: this.draft.description.trim(),
      notify: this.draft.notify,
      status: existing?.status ?? 'Scheduled',
      recordingUrl: existing?.recordingUrl ?? '',
    };
    if (this.editingId) {
      this.classes = this.classes.map((row) => (row.id === this.editingId ? next : row));
    } else {
      this.classes = [next, ...this.classes];
    }
    this.notice = next.notify
      ? `Saved. A message would go to ${next.sections.join(', ')}.`
      : 'Saved. Notifications are off for this class.';
    this.closeEditor();
  }

  start(row: OnlineClass): void {
    if (row.status === 'Cancelled' || row.status === 'Completed') return;
    this.classes = this.classes.map((item) =>
      item.id === row.id ? { ...item, status: 'In Progress' } : item,
    );
    window.open(row.meetingUrl, '_blank', 'noopener');
  }

  joinEnabled(row: OnlineClass): boolean {
    if (row.status === 'Cancelled' || row.status === 'Completed') return false;
    const start = new Date(row.startsAt).getTime();
    const end = start + row.durationMinutes * 60_000;
    const now = Date.now();
    return now >= start - 10 * 60_000 && now <= end;
  }

  join(row: OnlineClass): void {
    if (!this.joinEnabled(row)) return;
    window.open(row.meetingUrl, '_blank', 'noopener');
  }

  async copyLink(row: OnlineClass): Promise<void> {
    try {
      await navigator.clipboard.writeText(row.meetingUrl);
      this.notice = 'Meeting link copied.';
    } catch {
      this.notice = row.meetingUrl;
    }
  }

  remove(row: OnlineClass): void {
    if (!window.confirm(`Delete “${row.title}”?`)) return;
    this.classes = this.classes.filter((item) => item.id !== row.id);
  }

  openRecording(row: OnlineClass): void {
    if (row.recordingUrl) {
      window.open(row.recordingUrl, '_blank', 'noopener');
      return;
    }
    this.recordingFor = row;
    this.recordingUrl = '';
  }

  saveRecording(): void {
    if (!this.recordingFor) return;
    const url = this.recordingUrl.trim();
    const id = this.recordingFor.id;
    this.classes = this.classes.map((row) =>
      row.id === id ? { ...row, recordingUrl: url, status: 'Completed' } : row,
    );
    this.recordingFor = null;
    this.notice = url ? 'Recording link saved.' : 'Recording link cleared.';
  }

  sessionWindow(row: OnlineClass): SessionWindow {
    if (row.status === 'Completed' || row.status === 'Cancelled') return 'Past Sessions';
    const start = new Date(row.startsAt).getTime();
    const end = start + row.durationMinutes * 60_000;
    const now = Date.now();
    if (now >= start && now <= end) return 'Live Now';
    if (start > now) return 'Upcoming';
    return 'Past Sessions';
  }

  statusTone(row: OnlineClass): 'live' | 'scheduled' | 'done' | 'cancelled' {
    if (row.status === 'Cancelled') return 'cancelled';
    if (row.status === 'Completed') return 'done';
    if (row.status === 'In Progress' || this.sessionWindow(row) === 'Live Now') return 'live';
    return 'scheduled';
  }

  statusLabel(row: OnlineClass): string {
    const tone = this.statusTone(row);
    if (tone === 'live') return 'Live';
    if (tone === 'done') return 'Completed';
    if (tone === 'cancelled') return 'Cancelled';
    return 'Scheduled';
  }

  whenLabel(iso: string): string {
    const date = new Date(iso);
    const pad = (n: number) => String(n).padStart(2, '0');
    const hours = date.getHours();
    const h12 = hours % 12 || 12;
    const ampm = hours >= 12 ? 'PM' : 'AM';
    return `${pad(date.getDate())}-${pad(date.getMonth() + 1)}-${date.getFullYear()} | ${pad(h12)}:${pad(date.getMinutes())} ${ampm}`;
  }

  initials(name: string): string {
    return name
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('');
  }

  private emptyDraft(): ClassDraft {
    const start = new Date(Date.now() + 60 * 60_000);
    start.setMinutes(0, 0, 0);
    return {
      title: '',
      subject: this.subjectOptions[0],
      platform: 'Google Meet',
      sections: ['Class 10-A'],
      teacher: this.teacherOptions[0],
      startsAt: this.toLocalInput(start.toISOString()),
      durationMinutes: 40,
      passcode: '',
      description: '',
      notify: true,
      meetingUrl: '',
    };
  }

  private toLocalInput(iso: string): string {
    const date = new Date(iso);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  private placeholderLink(platform: MeetingPlatform): string {
    const token = Math.random().toString(36).slice(2, 8);
    if (platform === 'Zoom') return `https://zoom.us/j/${token}`;
    if (platform === 'MS Teams') return `https://teams.microsoft.com/l/meetup-join/${token}`;
    if (platform === 'YouTube Live') return `https://youtube.com/live/${token}`;
    return `https://meet.google.com/sugam-${token}`;
  }
}
