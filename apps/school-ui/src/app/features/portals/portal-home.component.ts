import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Observable, Subscription, catchError, forkJoin, of, timeout } from 'rxjs';
import { ApiService, PageResult } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';
import { PortalBootstrap, PortalContextService } from './portal-context.service';
import { environment } from '../../environments/environment';

interface InAppAlert {
  id: number;
  subject: string;
  body: string;
  status: string;
  createdAt: string;
  readAt: string | null;
}

@Component({
  selector: 'sf-portal-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './portal-home.component.html',
  styleUrls: ['../../shared/admin-page.scss', './portal-home.component.scss'],
})
export class PortalHomeComponent implements OnInit, OnDestroy {
  private readonly portalCtx = inject(PortalContextService);
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthSessionService);
  private sub?: Subscription;
  private liveSub?: Subscription;
  private hydratedPortal = '';
  boot: PortalBootstrap | null = null;
  liveLoading = false;
  liveWarning = '';
  inAppAlerts: InAppAlert[] = [];

  ngOnInit(): void {
    this.sub = this.portalCtx.bootstrap$.subscribe((boot) => {
      if (!boot) {
        this.boot = null;
        return;
      }
      // Never render configured sample profile/summary while live hydration runs.
      this.boot = {
        ...boot,
        profile: {},
        summary: {},
        notices: boot.notices ?? [],
      };
      if (boot.featureEnabled && boot.portalKey !== this.hydratedPortal) {
        this.hydratedPortal = boot.portalKey;
        this.loadLive(boot);
      }
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.liveSub?.unsubscribe();
  }

  summaryValue(key?: string): string {
    if (!key || !this.boot?.summary) {
      return '—';
    }
    const v = this.boot.summary[key];
    return v == null || v === '' ? '—' : String(v);
  }

  profileEntries(): Array<{ key: string; value: string }> {
    const p = this.boot?.profile ?? {};
    return Object.keys(p)
      .filter((key) => p[key] != null && String(p[key]).trim() !== '')
      .map((key) => ({ key: this.label(key), value: String(p[key] ?? '') }));
  }

  unreadAlertCount(): number {
    return this.inAppAlerts.filter((alert) => !alert.readAt).length;
  }

  markAlertRead(alert: InAppAlert): void {
    if (alert.readAt) {
      return;
    }
    this.http
      .post<InAppAlert>(
        `${environment.apiBaseUrl}/api/v1/notifications/in-app/${alert.id}/read`,
        {},
      )
      .pipe(timeout(6000), catchError(() => of(null)))
      .subscribe((updated) => {
        if (!updated) {
          return;
        }
        this.inAppAlerts = this.inAppAlerts.map((item) =>
          item.id === updated.id ? updated : item,
        );
        if (this.boot?.summary) {
          this.boot = {
            ...this.boot,
            summary: { ...this.boot.summary, noticesCount: this.unreadAlertCount() || null },
          };
        }
      });
  }

  private loadLive(boot: PortalBootstrap): void {
    this.liveSub?.unsubscribe();
    this.liveLoading = true;
    this.liveWarning = '';
    this.inAppAlerts = [];
    this.liveSub =
      boot.portalKey === 'teacher' ? this.loadTeacher(boot) : this.loadParent(boot);
  }

  private loadParent(boot: PortalBootstrap): Subscription {
    return forkJoin({
      students: this.safePage('/api/student/students'),
      attendance: this.safeGet<any[]>('/api/attendance/marks/mine'),
      fees: this.safePage('/api/fee/collections'),
      cards: this.safeGet<any[]>('/api/exam/report-cards/mine'),
      alerts: this.safeRawGet<InAppAlert[]>('/api/v1/notifications/in-app'),
    }).subscribe((data) => {
      this.inAppAlerts = data.alerts ?? [];
      const items = data.students?.items ?? [];
      const primary = items[0] ?? {};
      const answers = primary?.answers ?? {};
      const admissionNo = String(primary?.admissionNo ?? answers?.admissionNo ?? '').trim();
      const childCount = items.length;

      const marks = data.attendance ?? [];
      const attendancePercent =
        data.attendance == null
          ? null
          : marks.length
            ? `${Math.round(
                (marks.filter((mark) =>
                  ['PRESENT', 'LATE'].includes(
                    String(mark?.markStatus ?? mark?.status ?? '').toUpperCase(),
                  ),
                ).length *
                  100) /
                  marks.length,
              )}%`
            : 'No records';

      const pending =
        data.fees == null
          ? null
          : (data.fees.items ?? [])
              .filter(
                (row) =>
                  !['APPROVED', 'PAID', 'CAPTURED'].includes(
                    String(row?.status ?? '').toUpperCase(),
                  ),
              )
              .reduce(
                (sum, row) => sum + Number(row?.answers?.amount ?? row?.amount ?? 0),
                0,
              );

      const latest = data.cards?.[0];
      const latestLabel =
        data.cards == null
          ? null
          : latest
            ? [
                latest?.termKey,
                latest?.student?.overallGrade ?? latest?.student?.grade ?? latest?.overallGrade,
              ]
                .filter(Boolean)
                .join(' · ')
            : 'No published result';

      if (admissionNo) {
        this.safeGet<any>(
          `/api/fee/students/${encodeURIComponent(admissionNo)}/pending-fees`,
        ).subscribe((pendingFees) => {
          const amount =
            pendingFees == null
              ? pending == null
                ? null
                : this.money(pending)
              : this.money(Number(pendingFees.pendingAmount ?? 0));
          this.applyLive(boot, {
            profile: {
              studentName:
                answers?.fullName ?? answers?.studentName ?? primary?.fullName ?? 'Linked student',
              admissionNo,
              className:
                answers?.classSection ?? answers?.classApplied ?? primary?.classSection,
              linkedChildren: childCount > 1 ? childCount : undefined,
              guardianName: this.auth.getSession()?.username,
            },
            summary: {
              attendancePercent,
              pendingFeeAmount: amount,
              latestExamLabel: latestLabel,
              noticesCount: this.unreadAlertCount() || null,
            },
            unavailable: Object.values({
              students: data.students,
              attendance: data.attendance,
              fees: pendingFees ?? data.fees,
              cards: data.cards,
              alerts: data.alerts,
            }).filter((value) => value == null).length,
          });
        });
        return;
      }

      this.applyLive(boot, {
        profile: {
          studentName: null,
          admissionNo: null,
          className: null,
          guardianName: this.auth.getSession()?.username,
        },
        summary: {
          attendancePercent,
          pendingFeeAmount: pending == null ? null : this.money(pending),
          latestExamLabel: latestLabel,
          noticesCount: this.unreadAlertCount() || null,
        },
        unavailable: Object.values(data).filter((value) => value == null).length,
      });
    });
  }

  private loadTeacher(boot: PortalBootstrap): Subscription {
    return forkJoin({
      scope: this.safeGet<any>('/api/academic/teacher-scope'),
      students: this.safeGet<any>('/api/student/access-scope'),
      slots: this.safeGet<any[]>('/api/academic/timetable/my-slots'),
      exams: this.safeGet<any[]>('/api/exam/definitions'),
    }).subscribe((data) => {
      const sectionIds: string[] = Array.isArray(data.scope?.sectionIds)
        ? data.scope.sectionIds
        : [];
      const labels = this.asStringList(data.scope?.studentLabels);
      const names = this.asStringList(data.scope?.sectionNames);
      const sectionLabels = labels.length ? labels : names;
      const todayIso = ((new Date().getDay() + 6) % 7) + 1; // Monday=1 ... Sunday=7
      const todaySlots =
        data.slots == null
          ? null
          : data.slots.filter((slot) => Number(slot?.dayOfWeek) === todayIso).length;
      const openExams =
        data.exams == null
          ? null
          : (data.exams ?? []).filter((exam) =>
              ['OPEN', 'DRAFT', 'IN_PROGRESS'].includes(String(exam?.status || '').toUpperCase()),
            ).length;

      this.applyLive(boot, {
        profile: {
          teacherName: this.auth.getSession()?.username,
          className: sectionLabels.join(', ') || (sectionIds.length ? `${sectionIds.length} section(s)` : 'No section assigned'),
          assignedSections: sectionIds.length || null,
        },
        summary: {
          studentsCount: data.students == null ? null : data.students.studentCount ?? 0,
          attendanceToday:
            todaySlots == null
              ? null
              : todaySlots
                ? `${todaySlots} scheduled period${todaySlots === 1 ? '' : 's'}`
                : 'No periods today',
          pendingMarks:
            openExams == null
              ? null
              : openExams
                ? `${openExams} open exam${openExams === 1 ? '' : 's'}`
                : 'No open exams',
          noticesCount: (boot.notices ?? []).length || null,
        },
        unavailable: Object.values(data).filter((value) => value == null).length,
      });
    });
  }

  private applyLive(
    base: PortalBootstrap,
    live: {
      profile: Record<string, unknown>;
      summary: Record<string, unknown>;
      unavailable: number;
    },
  ): void {
    const profile: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(live.profile)) {
      if (value != null && String(value).trim() !== '') {
        profile[key] = value;
      }
    }
    this.boot = {
      ...base,
      profile,
      // Replace summary entirely — never merge fictional bootstrap defaults.
      summary: live.summary,
      notices: base.notices ?? [],
    };
    this.liveLoading = false;
    this.liveWarning = live.unavailable
      ? `${live.unavailable} live data source${live.unavailable === 1 ? '' : 's'} unavailable.`
      : '';
  }

  private asStringList(raw: unknown): string[] {
    if (Array.isArray(raw)) {
      return raw.map((value) => String(value)).filter((value) => value.trim() !== '');
    }
    if (raw && typeof raw === 'object') {
      return Object.values(raw as Record<string, unknown>)
        .map((value) => String(value))
        .filter((value) => value.trim() !== '');
    }
    return [];
  }

  private safeGet<T>(path: string): Observable<T | null> {
    return this.api.get<T>(path).pipe(timeout(6000), catchError(() => of(null)));
  }

  private safePage(path: string): Observable<PageResult<any> | null> {
    return this.api
      .getPage<any>(path, 0, 100)
      .pipe(timeout(6000), catchError(() => of(null)));
  }

  private safeRawGet<T>(path: string): Observable<T | null> {
    return this.http
      .get<T>(`${environment.apiBaseUrl}${path}`)
      .pipe(timeout(6000), catchError(() => of(null)));
  }

  private money(value: number): string {
    return `₹${Number(value || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}`;
  }

  private label(key: string): string {
    return key
      .replace(/([A-Z])/g, ' $1')
      .replace(/^./, (value) => value.toUpperCase())
      .trim();
  }
}
