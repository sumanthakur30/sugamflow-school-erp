import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, Subscription, catchError, of, switchMap, timer, timeout } from 'rxjs';
import { ApiService, PageResult } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';

type Metric = {
  id: string;
  label: string;
  value: string | number;
  hint: string;
  route: string;
  queryParams?: Record<string, string>;
  tone: 'green' | 'blue' | 'amber' | 'purple';
};

type Action = {
  label: string;
  description: string;
  route: string;
  queryParams?: Record<string, string>;
};

type ActivityItem = {
  label: string;
  value: string;
  route: string;
  queryParams?: Record<string, string>;
};

type DashboardBundle = {
  config: any;
  students: any;
  staff: any;
  admissions: PageResult<any> | null;
  attendance: PageResult<any> | null;
  fees: PageResult<any> | null;
  issues: any[] | null;
  finance: any;
  salary: any;
  teacherScope: any;
  accessScope: any;
  mySlots: any[] | null;
};

type BundleKey = keyof DashboardBundle;

@Component({
  selector: 'sf-role-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './role-dashboard.component.html',
  styleUrl: './role-dashboard.component.scss',
})
export class RoleDashboardComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly cdr = inject(ChangeDetectorRef);
  private loadSub = new Subscription();
  private loadGeneration = 0;

  /** True only before the first metric shell is painted. */
  loading = true;
  /** True while any dashboard API is still in flight. */
  refreshing = false;
  error = '';
  role = 'STAFF';
  title = 'Staff dashboard';
  subtitle = 'Your campus overview and priority actions.';
  metrics: Metric[] = [];
  actions: Action[] = [];
  activity: ActivityItem[] = [];
  unavailable = 0;

  private wantsFinance = false;
  private wantsTeacher = false;
  private pending = 0;
  private bundle: DashboardBundle = this.emptyBundle();

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.loadSub.unsubscribe();
  }

  load(): void {
    this.loadSub.unsubscribe();
    this.loadSub = new Subscription();

    const generation = ++this.loadGeneration;
    this.error = '';
    this.role = (this.auth.getRole() || 'STAFF').toUpperCase();
    this.wantsFinance = this.isFinanceRole() || this.isLeadershipRole();
    this.wantsTeacher = this.role === 'TEACHER';
    this.bundle = this.emptyBundle();
    this.pending = 0;
    this.refreshing = true;

    // Paint shell immediately (actions + placeholder metrics) — do not wait on APIs.
    this.configureRole(null);
    this.buildMetrics(this.bundle);
    this.buildActivity(this.bundle);
    this.unavailable = 0;
    this.loading = false;

    const safeRole = encodeURIComponent(this.role);
    const branch = encodeURIComponent(this.auth.getBranchId() || 'main');

    // Critical path first (core KPIs), then heavier optional reports.
    this.track(
      generation,
      'config',
      this.safeGet(`/api/config/ui/roles/${safeRole}/dashboard`, 8000),
    );
    // Retry once — first paint often races branch bootstrap / cold Eureka and shows "—".
    this.track(generation, 'students', this.safeGetRetry('/api/student/directory/summary', 10000));
    this.track(generation, 'staff', this.safeGetRetry('/api/staff/directory/summary', 10000));
    this.track(
      generation,
      'admissions',
      this.safePageRetry('/api/admission/applications', {
        status: 'IN_PROGRESS',
        sortBy: 'updatedAt',
        sortDir: 'DESC',
      }),
    );
    this.track(generation, 'attendance', this.safePage('/api/attendance/records'));
    this.track(generation, 'fees', this.safePage('/api/fee/collections'));

    if (this.wantsTeacher) {
      this.track(generation, 'teacherScope', this.safeGet('/api/academic/teacher-scope'));
      this.track(generation, 'accessScope', this.safeGet('/api/student/access-scope'));
      this.track(generation, 'mySlots', this.safeGet<any[]>('/api/academic/timetable/my-slots'));
    } else {
      this.track(generation, 'issues', this.safeGet<any[]>('/api/library/circulation/issues'));
    }

    if (this.wantsFinance) {
      this.track(
        generation,
        'finance',
        this.safeGet(
          `/api/fee/finance/income-expense?preset=THIS_MONTH&branchIds=${branch}`,
          12000,
        ),
      );
      this.track(
        generation,
        'salary',
        this.safeGet(
          `/api/payroll/reports/salary-summary?preset=THIS_MONTH&branchIds=${branch}`,
          12000,
        ),
      );
    }

    if (this.pending === 0) {
      this.refreshing = false;
    }
  }

  private track<K extends BundleKey>(
    generation: number,
    key: K,
    source: Observable<DashboardBundle[K]>,
  ): void {
    this.pending += 1;
    this.loadSub.add(
      source.subscribe({
        next: (value) => this.applyPartial(generation, key, value),
        error: () => this.applyPartial(generation, key, null as DashboardBundle[K]),
      }),
    );
  }

  private applyPartial<K extends BundleKey>(
    generation: number,
    key: K,
    value: DashboardBundle[K],
  ): void {
    if (generation !== this.loadGeneration) {
      return;
    }
    this.bundle[key] = value;
    if (key === 'config') {
      this.configureRole(value);
    }
    this.buildMetrics(this.bundle);
    this.buildActivity(this.bundle);
    this.unavailable = this.countUnavailable(this.bundle, this.wantsFinance, this.wantsTeacher);
    this.pending = Math.max(0, this.pending - 1);
    if (this.pending === 0) {
      this.refreshing = false;
    }
    this.cdr.markForCheck();
  }

  private emptyBundle(): DashboardBundle {
    return {
      config: null,
      students: null,
      staff: null,
      admissions: null,
      attendance: null,
      fees: null,
      issues: null,
      finance: null,
      salary: null,
      teacherScope: null,
      accessScope: null,
      mySlots: null,
    };
  }

  /** Only count sources that were actually requested (skip intentional of(null) skips). */
  private countUnavailable(
    data: DashboardBundle,
    wantsFinance: boolean,
    wantsTeacher: boolean,
  ): number {
    const skipped = new Set<BundleKey>();
    if (!wantsTeacher) {
      skipped.add('teacherScope');
      skipped.add('accessScope');
      skipped.add('mySlots');
    } else {
      skipped.add('issues');
    }
    if (!wantsFinance) {
      skipped.add('finance');
      skipped.add('salary');
    }
    return (Object.keys(data) as BundleKey[]).filter(
      (key) => !skipped.has(key) && data[key] == null,
    ).length;
  }

  private safeGet<T = any>(path: string, ms = 15000): Observable<T | null> {
    return this.api.get<T>(path).pipe(timeout(ms), catchError(() => of(null)));
  }

  /** One delayed retry — covers branch-header race and cold service timeouts. */
  private safeGetRetry<T = any>(path: string, ms = 15000): Observable<T | null> {
    return this.api.get<T>(path).pipe(
      timeout(ms),
      catchError(() =>
        timer(900).pipe(
          switchMap(() => this.api.get<T>(path).pipe(timeout(ms), catchError(() => of(null)))),
        ),
      ),
    );
  }

  private safePage(
    path: string,
    extra?: Record<string, string | number | boolean | null | undefined>,
  ): Observable<PageResult<any> | null> {
    return this.api
      .getPage<any>(path, 0, 5, extra)
      .pipe(timeout(10000), catchError(() => of(null)));
  }

  private safePageRetry(
    path: string,
    extra?: Record<string, string | number | boolean | null | undefined>,
  ): Observable<PageResult<any> | null> {
    const once = () =>
      this.api.getPage<any>(path, 0, 5, extra).pipe(timeout(10000), catchError(() => of(null)));
    return once().pipe(
      switchMap((value) => (value != null ? of(value) : timer(900).pipe(switchMap(() => once())))),
    );
  }

  private isLeadershipRole(): boolean {
    return ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'].includes(this.role);
  }

  private isFinanceRole(): boolean {
    return ['ACCOUNTANT', 'ACCOUNTS', 'FINANCE'].includes(this.role);
  }

  private configureRole(config: any): void {
    const labels: Record<string, string> = {
      SHOP_OWNER: 'School owner',
      SUPER_ADMIN: 'Platform administrator',
      ADMIN: 'Administrator',
      PRINCIPAL: 'Principal',
      TEACHER: 'Teacher',
      ACCOUNTANT: 'Accounts',
      ACCOUNTS: 'Accounts',
      FINANCE: 'Finance',
      RECEPTION: 'Reception',
      RECEPTIONIST: 'Reception',
      LIBRARIAN: 'Library',
      STAFF: 'Staff',
    };
    const roleLabel = labels[this.role] ?? this.role.replace(/_/g, ' ').toLowerCase();
    this.title = config?.title || `${roleLabel} dashboard`;
    this.subtitle =
      config?.subtitle ||
      `Live overview for ${this.auth.getBranchId()} campus · ${this.auth.getSessionId()}`;
    this.actions = this.actionsForRole();
  }

  private buildMetrics(data: DashboardBundle): void {
    const kpis = data.finance?.kpis ?? {};
    const sectionCount = Array.isArray(data.teacherScope?.sectionIds)
      ? data.teacherScope.sectionIds.length
      : 0;
    const scopedStudents = Number(data.accessScope?.studentCount ?? 0);
    const slotCount = Array.isArray(data.mySlots) ? data.mySlots.length : 0;

    const studentActive = data.students?.active ?? data.students?.total;
    const studentOrgWide = data.students?.activeOrgWide;
    let studentHint = `${data.students?.newAdmissions ?? 0} new admissions`;
    if (data.students == null) {
      studentHint = 'Count unavailable — check student service / network';
    } else if (
      Number(studentActive ?? 0) === 0 &&
      Number(studentOrgWide ?? 0) > 0
    ) {
      studentHint = `${studentOrgWide} active in school, but not in this campus/session`;
    }

    const all: Record<string, Metric> = {
      students: {
        id: 'students',
        label: 'Active students',
        value: studentActive ?? '—',
        hint: studentHint,
        route: '/admin/student-directory',
        tone: 'green',
      },
      staff: {
        id: 'staff',
        label: 'Active staff',
        value: data.staff?.active ?? data.staff?.total ?? '—',
        hint: `${data.staff?.total ?? 0} total records`,
        route: '/admin/staff-directory',
        tone: 'blue',
      },
      admissions: {
        id: 'admissions',
        label: 'Admissions in review',
        value: data.admissions?.totalElements ?? '—',
        hint: 'Awaiting workflow decisions',
        route: '/admin/admission',
        tone: 'amber',
      },
      attendance: {
        id: 'attendance',
        label: 'Attendance records',
        value: data.attendance?.totalElements ?? '—',
        hint: 'Current campus and session',
        route: '/admin/attendance',
        tone: 'purple',
      },
      feeIncome: {
        id: 'feeIncome',
        label: 'Fee collection (month)',
        value: this.money(kpis.feeIncome ?? kpis.totalIncome),
        hint: 'Approved receipts this month',
        route: '/admin/income-expense',
        tone: 'green',
      },
      pendingFees: {
        id: 'pendingFees',
        label: 'Pending fees',
        value: this.money(kpis.pendingFees),
        hint: `Overdue ${this.money(kpis.overdueFees)}`,
        route: '/admin/finance',
        tone: 'amber',
      },
      netProfit: {
        id: 'netProfit',
        label: Number(kpis.totalIncome || 0) - Number(kpis.totalExpense || 0) >= 0
          ? 'Net surplus'
          : 'Net deficit',
        value: this.money(
          Math.abs(Number(kpis.totalIncome || 0) - Number(kpis.totalExpense || 0)),
        ),
        hint: 'Income vs expense this month',
        route: '/admin/income-expense',
        tone: 'blue',
      },
      salary: {
        id: 'salary',
        label: 'Salary paid',
        value: this.money(data.salary?.salaryPaid ?? kpis.salaryPaid),
        hint: 'Approved payroll this month',
        route: '/admin/payroll',
        tone: 'purple',
      },
      fees: {
        id: 'fees',
        label: 'Fee collections',
        value: data.fees?.totalElements ?? '—',
        hint: 'Collection entries',
        route: '/admin/finance',
        tone: 'green',
      },
      library: {
        id: 'library',
        label: 'Open library issues',
        value: (data.issues ?? []).filter((row) =>
          ['ISSUED', 'OVERDUE'].includes(String(row?.status || '').toUpperCase()),
        ).length,
        hint: 'Issued or overdue books',
        route: '/admin/library',
        tone: 'blue',
      },
      classes: {
        id: 'classes',
        label: 'Assigned classes',
        value: sectionCount || '—',
        hint: sectionCount ? 'From teacher scope' : 'No class assignment',
        route: '/teacher/students',
        tone: 'blue',
      },
      scopedStudents: {
        id: 'scopedStudents',
        label: 'Scoped students',
        value: scopedStudents || '—',
        hint: scopedStudents ? 'Students in your sections' : 'No linked students yet',
        route: '/teacher/students',
        tone: 'green',
      },
      slots: {
        id: 'slots',
        label: 'Timetable slots',
        value: slotCount || '—',
        hint: slotCount ? 'Your teaching periods' : 'No timetable slots',
        route: '/teacher',
        tone: 'purple',
      },
    };

    const roleMetricIds: Record<string, string[]> = {
      PRINCIPAL: ['students', 'staff', 'admissions', 'feeIncome', 'pendingFees', 'attendance'],
      ACCOUNTANT: ['feeIncome', 'pendingFees', 'netProfit', 'salary', 'students'],
      ACCOUNTS: ['feeIncome', 'pendingFees', 'netProfit', 'salary', 'students'],
      FINANCE: ['feeIncome', 'pendingFees', 'netProfit', 'salary', 'students'],
      RECEPTION: ['admissions', 'students', 'attendance', 'staff'],
      RECEPTIONIST: ['admissions', 'students', 'attendance', 'staff'],
      LIBRARIAN: ['library', 'students', 'staff', 'attendance'],
      TEACHER: ['classes', 'scopedStudents', 'slots', 'attendance'],
    };
    const ids =
      roleMetricIds[this.role] ??
      ['students', 'staff', 'admissions', 'feeIncome', 'pendingFees', 'attendance'];
    this.metrics = ids.map((id) => all[id]).filter(Boolean);
  }

  private buildActivity(data: DashboardBundle): void {
    const rows: ActivityItem[] = [];
    if (this.role === 'TEACHER') {
      const sectionNames = data.teacherScope?.sectionNames ?? {};
      const sectionIds: string[] = data.teacherScope?.sectionIds ?? [];
      if (!sectionIds.length) {
        rows.push({
          label: 'Class assignment',
          value: 'No sections assigned',
          route: '/teacher',
        });
      } else {
        for (const id of sectionIds.slice(0, 4)) {
          rows.push({
            label: String(sectionNames?.[id] || id),
            value: 'Assigned section',
            route: '/teacher/attendance',
          });
        }
      }
      for (const slot of (data.mySlots ?? []).slice(0, 3)) {
        rows.push({
          label: String(slot?.subjectName || slot?.subjectId || 'Period'),
          value: String(slot?.dayOfWeek || slot?.day || 'Scheduled'),
          route: '/teacher',
        });
      }
      this.activity = rows.slice(0, 6);
      return;
    }

    for (const app of data.admissions?.items ?? []) {
      const id = String(app?.id || '').trim();
      rows.push({
        label: app?.answers?.fullName || 'Admission applicant',
        value: app?.statusLabel || 'Under review',
        route: '/admin/admission',
        queryParams: id ? { id } : undefined,
      });
    }
    for (const record of (data.attendance?.items ?? []).slice(0, 3)) {
      rows.push({
        label: record?.answers?.studentName || 'Attendance record',
        value: record?.answers?.mark || record?.answers?.status || record?.status || 'Recorded',
        route: '/admin/attendance',
      });
    }
    this.activity = rows.slice(0, 6);
  }

  private actionsForRole(): Action[] {
    const common: Action[] = [
      {
        label: 'Student directory',
        description: 'Search and review student profiles',
        route: '/admin/student-directory',
      },
      {
        label: 'Reports hub',
        description: 'Open operational and academic reports',
        route: '/admin/reports-hub',
      },
    ];
    const byRole: Record<string, Action[]> = {
      PRINCIPAL: [
        {
          label: 'Review admissions',
          description: 'Process pending decisions',
          route: '/admin/admission',
        },
        { label: 'Timetable', description: 'Review class schedules', route: '/admin/timetable' },
        { label: 'Report cards', description: 'Review exam performance', route: '/admin/exam' },
        {
          label: 'Income & expense',
          description: 'Campus finance overview',
          route: '/admin/income-expense',
        },
      ],
      ACCOUNTANT: [
        { label: 'Collect fees', description: 'Record a student payment', route: '/admin/fee' },
        {
          label: 'Finance overview',
          description: 'Collections, dues, and payments',
          route: '/admin/finance',
        },
        {
          label: 'Income & expense',
          description: 'Review the campus ledger',
          route: '/admin/income-expense',
        },
        { label: 'Payroll', description: 'Salary summary and payouts', route: '/admin/payroll' },
      ],
      ACCOUNTS: [
        { label: 'Collect fees', description: 'Record a student payment', route: '/admin/fee' },
        {
          label: 'Finance overview',
          description: 'Collections, dues, and payments',
          route: '/admin/finance',
        },
      ],
      FINANCE: [
        { label: 'Collect fees', description: 'Record a student payment', route: '/admin/fee' },
        {
          label: 'Income & expense',
          description: 'Review the campus ledger',
          route: '/admin/income-expense',
        },
      ],
      RECEPTION: [
        {
          label: 'New application',
          description: 'Create an admission application',
          route: '/admin/admission',
          queryParams: { new: '1' },
        },
        {
          label: 'Admissions inbox',
          description: 'Review incoming applications',
          route: '/admin/admission',
        },
        {
          label: 'Student master',
          description: 'Open enrolled students',
          route: '/admin/students',
        },
      ],
      RECEPTIONIST: [
        {
          label: 'New application',
          description: 'Create an admission application',
          route: '/admin/admission',
          queryParams: { new: '1' },
        },
        {
          label: 'Admissions inbox',
          description: 'Review incoming applications',
          route: '/admin/admission',
        },
      ],
      LIBRARIAN: [
        {
          label: 'Library circulation',
          description: 'Issue and return books',
          route: '/admin/library',
        },
        {
          label: 'Student lookup',
          description: 'Find a borrower',
          route: '/admin/student-directory',
        },
      ],
      TEACHER: [
        {
          label: 'Mark attendance',
          description: 'Take today’s section attendance',
          route: '/teacher/attendance',
        },
        { label: 'Gradebook', description: 'Enter and review marks', route: '/teacher/gradebook' },
        {
          label: 'Report cards',
          description: 'Publish and review insights',
          route: '/teacher/report-cards',
        },
        { label: 'My students', description: 'Open assigned roster', route: '/teacher/students' },
      ],
    };
    if (this.role === 'TEACHER') {
      return byRole['TEACHER'];
    }
    return [...(byRole[this.role] ?? []), ...common].slice(0, 5);
  }

  private money(value: unknown): string {
    if (value == null || value === '') {
      return '—';
    }
    const n = Number(value);
    if (Number.isNaN(n)) {
      return '—';
    }
    return '₹' + n.toLocaleString('en-IN', { maximumFractionDigits: 0 });
  }
}
