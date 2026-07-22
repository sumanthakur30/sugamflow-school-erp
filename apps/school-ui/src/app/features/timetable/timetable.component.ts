import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';

interface SlotDraft {
  dayOfWeek: number;
  periodId: string;
  subjectId: string;
  teacherUsername: string;
  room: string;
}

interface TeacherOption {
  username: string;
  label: string;
}

@Component({
  selector: 'sf-timetable',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './timetable.component.html',
  styleUrls: ['../../shared/admin-page.scss', './timetable.component.scss'],
})
export class TimetableComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  canManage = false;
  error = '';
  status = '';
  busy = false;

  periods: any[] = [];
  sections: any[] = [];
  subjects: any[] = [];
  assignments: any[] = [];
  teachers: TeacherOption[] = [];
  private staffMembers: any[] = [];
  slots: any[] = [];
  rooms: any[] = [];

  selectedSectionId = '';
  activeView: 'schedule' | 'periods' = 'schedule';
  aiGenerating = false;
  aiResult: any = null;
  conflicts: any[] = [];
  savingAssignmentId = '';
  roomDraft = { name: '', capacity: 30 };
  days = [
    { value: 1, label: 'Mon', fullLabel: 'Monday' },
    { value: 2, label: 'Tue', fullLabel: 'Tuesday' },
    { value: 3, label: 'Wed', fullLabel: 'Wednesday' },
    { value: 4, label: 'Thu', fullLabel: 'Thursday' },
    { value: 5, label: 'Fri', fullLabel: 'Friday' },
    { value: 6, label: 'Sat', fullLabel: 'Saturday' },
  ];

  periodDraft = {
    periodNo: 1,
    label: '',
    startTime: '',
    endTime: '',
    breakPeriod: false,
  };

  /** Editable grid keyed by `${day}:${periodId}` */
  grid: Record<string, SlotDraft> = {};

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/academic/bootstrap').subscribe({
      next: (boot) => {
        this.canManage = !!boot.canManage;
        this.loadCore();
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to load timetable';
      },
    });
  }

  private loadCore(): void {
    let pending = 6;
    const done = () => {
      pending -= 1;
      if (pending <= 0) {
        this.loading = false;
        if (this.selectedSectionId) {
          this.loadSlots();
        }
      }
    };
    this.api.get<any[]>('/api/academic/timetable/periods').subscribe({
      next: (items) => {
        this.periods = (items ?? []).slice().sort((a, b) => a.periodNo - b.periodNo);
        this.suggestNextPeriodNo();
        done();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Failed to load periods';
        done();
      },
    });
    this.api.get<any[]>('/api/academic/sections').subscribe({
      next: (items) => {
        this.sections = items ?? [];
        done();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Failed to load sections';
        done();
      },
    });
    this.api.get<any[]>('/api/academic/subjects').subscribe({
      next: (items) => {
        this.subjects = items ?? [];
        done();
      },
      error: () => done(),
    });
    this.api.get<any[]>('/api/academic/assignments').subscribe({
      next: (items) => {
        this.assignments = items ?? [];
        this.rebuildTeacherOptions();
        done();
      },
      error: () => done(),
    });
    this.api
      .getPage<any>('/api/staff/directory/staff', 0, 200, { status: 'ACTIVE' })
      .subscribe({
        next: (page) => {
          this.rebuildTeacherOptions(page.items ?? []);
          done();
        },
        error: () => {
          // Timetable remains usable from academic assignments even if staff-service is down.
          this.rebuildTeacherOptions();
          done();
        },
      });
    this.api.get<any[]>('/api/academic/timetable/rooms').subscribe({
      next: (items) => {
        this.rooms = items ?? [];
        done();
      },
      error: () => done(),
    });
  }

  createPeriod(): void {
    if (!this.periodDraft.label.trim()) {
      this.error = 'Period label is required';
      return;
    }
    const no = Number(this.periodDraft.periodNo) || 0;
    if (this.periods.some((p) => Number(p.periodNo) === no)) {
      this.error = `Period number ${no} already exists — use the next free number or edit/delete the existing one`;
      return;
    }
    this.busy = true;
    this.api
      .post('/api/academic/timetable/periods', {
        periodNo: no,
        label: this.periodDraft.label.trim(),
        startTime: this.periodDraft.startTime || undefined,
        endTime: this.periodDraft.endTime || undefined,
        breakPeriod: !!this.periodDraft.breakPeriod,
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.status = `Period ${this.periodDraft.label} created`;
          this.periodDraft = {
            periodNo: no + 1,
            label: '',
            startTime: '',
            endTime: '',
            breakPeriod: false,
          };
          this.reload();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Create period failed';
        },
      });
  }

  private suggestNextPeriodNo(): void {
    if (!this.periods.length) {
      this.periodDraft.periodNo = 1;
      return;
    }
    const max = Math.max(...this.periods.map((p) => Number(p.periodNo) || 0));
    this.periodDraft.periodNo = max + 1;
  }

  deletePeriod(id: string): void {
    if (!confirm('Delete this period?')) {
      return;
    }
    this.busy = true;
    this.api.delete(`/api/academic/timetable/periods/${id}`).subscribe({
      next: () => {
        this.busy = false;
        this.status = 'Period deleted';
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Delete period failed';
      },
    });
  }

  onSectionChange(): void {
    this.error = '';
    this.status = '';
    this.aiResult = null;
    this.conflicts = [];
    this.loadSlots();
  }

  generateSchedule(): void {
    if (!this.selectedSectionId) {
      this.error = 'Select a class / section first';
      return;
    }
    this.aiGenerating = true;
    this.error = '';
    this.status = '';
    this.conflicts = [];
    this.api
      .post<any>(
        `/api/academic/timetable/sections/${encodeURIComponent(this.selectedSectionId)}/generate`,
        {},
      )
      .subscribe({
        next: (result) => {
          this.aiGenerating = false;
          this.aiResult = result;
          this.conflicts = result?.conflicts ?? [];
          if (result?.slots?.length) {
            this.applyGeneratedPreview();
            this.status = `${result.placedPeriods} of ${result.requestedPeriods} requested periods generated. Review before saving.`;
          }
        },
        error: (err) => {
          this.aiGenerating = false;
          this.error = err?.error?.message ?? 'Timetable generation failed';
        },
      });
  }

  applyGeneratedPreview(): void {
    if (!this.aiResult?.slots) return;
    const next: Record<string, SlotDraft> = {};
    for (const day of this.days) {
      for (const period of this.teachingPeriods()) {
        next[this.cellKey(day.value, period.id)] = {
          dayOfWeek: day.value,
          periodId: period.id,
          subjectId: '',
          teacherUsername: '',
          room: '',
        };
      }
    }
    for (const slot of this.aiResult.slots) {
      next[this.cellKey(slot.dayOfWeek, slot.periodId)] = {
        dayOfWeek: slot.dayOfWeek,
        periodId: slot.periodId,
        subjectId: slot.subjectId || '',
        teacherUsername: slot.teacherUsername || '',
        room: slot.room || '',
      };
    }
    this.grid = next;
  }

  saveAssignmentConstraints(assignment: any): void {
    this.savingAssignmentId = assignment.id;
    this.error = '';
    this.api
      .put<any>(`/api/academic/assignments/${assignment.id}`, {
        weeklyPeriods: Number(assignment.weeklyPeriods) || 1,
        maxDailyPeriods: Number(assignment.maxDailyPeriods) || 1,
        preferredRoom: assignment.preferredRoom || undefined,
        unavailableSlots: assignment.unavailableSlots ?? [],
      })
      .subscribe({
        next: (saved) => {
          this.savingAssignmentId = '';
          Object.assign(assignment, saved);
          this.status = `Scheduling rules saved for ${this.subjectName(assignment.subjectId)}`;
          this.aiResult = null;
        },
        error: (err) => {
          this.savingAssignmentId = '';
          this.error = err?.error?.message ?? 'Save scheduling rules failed';
        },
      });
  }

  isTeacherUnavailable(assignment: any, day: number, periodId: string): boolean {
    return (assignment?.unavailableSlots ?? []).some(
      (slot: any) =>
        Number(slot.dayOfWeek) === Number(day) && String(slot.periodId) === String(periodId),
    );
  }

  toggleTeacherAvailability(assignment: any, day: number, periodId: string): void {
    const list = [...(assignment.unavailableSlots ?? [])];
    const index = list.findIndex(
      (slot: any) =>
        Number(slot.dayOfWeek) === Number(day) && String(slot.periodId) === String(periodId),
    );
    if (index >= 0) {
      list.splice(index, 1);
    } else {
      list.push({ dayOfWeek: day, periodId });
    }
    assignment.unavailableSlots = list;
  }

  createRoom(): void {
    if (!this.roomDraft.name.trim()) {
      this.error = 'Room name is required';
      return;
    }
    this.busy = true;
    this.api.post<any>('/api/academic/timetable/rooms', this.roomDraft).subscribe({
      next: (room) => {
        this.busy = false;
        this.rooms = [...this.rooms, room].sort((a, b) => a.name.localeCompare(b.name));
        this.roomDraft = { name: '', capacity: 30 };
        this.status = 'Room added';
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Add room failed';
      },
    });
  }

  deleteRoom(id: string): void {
    if (!confirm('Delete this room?')) return;
    this.busy = true;
    this.api.delete(`/api/academic/timetable/rooms/${id}`).subscribe({
      next: () => {
        this.busy = false;
        this.rooms = this.rooms.filter((room) => room.id !== id);
        this.status = 'Room deleted';
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Delete room failed';
      },
    });
  }

  assignmentSubjectName(assignment: any): string {
    return this.subjectName(assignment?.subjectId) || 'Unassigned subject';
  }

  hasErrorConflicts(): boolean {
    return this.conflicts.some((item) => item.severity === 'ERROR');
  }

  selectedSectionName(): string {
    const section = this.sections.find((item) => item.id === this.selectedSectionId);
    return section?.studentLabel || section?.name || 'Selected section';
  }

  teachingPeriods(): any[] {
    return this.periods.filter((period) => !period.breakPeriod);
  }

  totalTeachingSlots(): number {
    return this.teachingPeriods().length * this.days.length;
  }

  assignedSlotCount(): number {
    return Object.values(this.grid).filter(
      (slot) => !!(slot.subjectId || slot.teacherUsername || slot.room),
    ).length;
  }

  completionPercent(): number {
    const total = this.totalTeachingSlots();
    return total ? Math.round((this.assignedSlotCount() / total) * 100) : 0;
  }

  isCellFilled(day: number, periodId: string): boolean {
    const slot = this.cell(day, periodId);
    return !!(slot.subjectId || slot.teacherUsername || slot.room);
  }

  loadSlots(): void {
    if (!this.selectedSectionId) {
      this.slots = [];
      this.grid = {};
      return;
    }
    this.api
      .get<any[]>(
        `/api/academic/timetable/slots?sectionId=${encodeURIComponent(this.selectedSectionId)}`,
      )
      .subscribe({
        next: (items) => {
          this.slots = items ?? [];
          this.rebuildGrid();
        },
        error: (err) => {
          this.error = err?.error?.message ?? 'Failed to load slots';
        },
      });
  }

  private rebuildGrid(): void {
    const next: Record<string, SlotDraft> = {};
    for (const day of this.days) {
      for (const p of this.periods) {
        if (p.breakPeriod) {
          continue;
        }
        const key = this.cellKey(day.value, p.id);
        next[key] = {
          dayOfWeek: day.value,
          periodId: p.id,
          subjectId: '',
          teacherUsername: '',
          room: '',
        };
      }
    }
    for (const s of this.slots) {
      const key = this.cellKey(s.dayOfWeek, s.periodId);
      next[key] = {
        dayOfWeek: s.dayOfWeek,
        periodId: s.periodId,
        subjectId: s.subjectId || '',
        teacherUsername: s.teacherUsername || '',
        room: s.room || '',
      };
    }
    this.grid = next;
  }

  cellKey(day: number, periodId: string): string {
    return `${day}:${periodId}`;
  }

  cell(day: number, periodId: string): SlotDraft {
    const key = this.cellKey(day, periodId);
    if (!this.grid[key]) {
      this.grid[key] = {
        dayOfWeek: day,
        periodId,
        subjectId: '',
        teacherUsername: '',
        room: '',
      };
    }
    return this.grid[key];
  }

  sectionSubjects(): any[] {
    return this.selectedSectionId
      ? this.subjects.filter((subject) => subject.status !== 'INACTIVE')
      : [];
  }

  teacherOptions(day: number, periodId: string): TeacherOption[] {
    const current = this.cell(day, periodId);
    const mapped = this.sectionAssignments().filter(
      (assignment) => !current.subjectId || assignment.subjectId === current.subjectId,
    );
    const mappedNames = new Set(
      mapped.map((assignment) => assignment.teacherUsername).filter((name) => !!name),
    );
    let options = mappedNames.size
      ? this.teachers.filter((teacher) => mappedNames.has(teacher.username))
      : this.teachers;

    // Preserve a previously saved username even when the staff record was archived.
    if (
      current.teacherUsername &&
      !options.some((teacher) => teacher.username === current.teacherUsername)
    ) {
      options = [
        ...options,
        { username: current.teacherUsername, label: current.teacherUsername },
      ];
    }
    return options;
  }

  onSubjectChange(day: number, periodId: string): void {
    const current = this.cell(day, periodId);
    const matching = this.sectionAssignments().filter(
      (assignment) =>
        assignment.subjectId === current.subjectId && !!assignment.teacherUsername,
    );
    const usernames = [...new Set(matching.map((assignment) => assignment.teacherUsername))];
    if (usernames.length === 1) {
      current.teacherUsername = usernames[0];
    } else if (
      current.teacherUsername &&
      usernames.length &&
      !usernames.includes(current.teacherUsername)
    ) {
      current.teacherUsername = '';
    }
  }

  sectionAssignments(): any[] {
    return this.assignments.filter(
      (assignment) => assignment.sectionId === this.selectedSectionId,
    );
  }

  private rebuildTeacherOptions(staff: any[] = []): void {
    if (staff.length) {
      this.staffMembers = staff;
    }
    const labels = new Map<string, string>();
    for (const row of this.staffMembers) {
      const username = String(row?.authUsername || '').trim();
      if (!username) {
        continue;
      }
      const name = String(row?.fullName || '').trim();
      labels.set(username, name ? `${name} (${username})` : username);
    }
    for (const assignment of this.assignments) {
      const username = String(assignment?.teacherUsername || '').trim();
      if (username && !labels.has(username)) {
        labels.set(username, username);
      }
    }
    for (const section of this.sections) {
      const username = String(section?.classTeacherUsername || '').trim();
      if (username && !labels.has(username)) {
        labels.set(username, username);
      }
    }
    this.teachers = [...labels.entries()]
      .map(([username, label]) => ({ username, label }))
      .sort((a, b) => a.label.localeCompare(b.label));
  }

  saveGrid(): void {
    if (!this.selectedSectionId) {
      this.error = 'Select a section first';
      return;
    }
    const slots = Object.values(this.grid)
      .filter((c) => c.subjectId || c.teacherUsername || c.room)
      .map((c) => ({
        dayOfWeek: c.dayOfWeek,
        periodId: c.periodId,
        subjectId: c.subjectId || undefined,
        teacherUsername: c.teacherUsername || undefined,
        room: c.room || undefined,
      }));
    this.busy = true;
    this.error = '';
    this.api
      .post<any>(
        `/api/academic/timetable/sections/${encodeURIComponent(this.selectedSectionId)}/validate`,
        { slots },
      )
      .subscribe({
        next: (validation) => {
          this.conflicts = validation?.conflicts ?? [];
          if (!validation?.valid) {
            this.busy = false;
            this.error = 'Resolve the timetable conflicts shown below before saving.';
            return;
          }
          this.persistGrid(slots);
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Conflict check failed';
        },
      });
  }

  private persistGrid(slots: any[]): void {
    this.api
      .put(`/api/academic/timetable/sections/${this.selectedSectionId}`, { slots })
      .subscribe({
        next: () => {
          this.busy = false;
          this.conflicts = [];
          this.aiResult = null;
          this.status = `Timetable saved (${slots.length} slots)`;
          this.loadSlots();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Save timetable failed';
        },
      });
  }

  subjectName(id: string | null): string {
    if (!id) {
      return '';
    }
    return this.subjects.find((s) => s.id === id)?.name ?? '';
  }
}
