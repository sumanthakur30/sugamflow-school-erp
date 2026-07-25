import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, catchError, forkJoin, of, timeout } from 'rxjs';
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

@Component({
  selector: 'sf-role-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './role-dashboard.component.html',
  styleUrl: './role-dashboard.component.scss',
})
export class RoleDashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);

  loading = true;
  error = '';
  role = 'STAFF';
  title = 'Staff dashboard';
  subtitle = 'Your campus overview and priority actions.';
  metrics: Metric[] = [];
  actions: Action[] = [];
  activity: ActivityItem[] = [];
  unavailable = 0;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.role = (this.auth.getRole() || 'STAFF').toUpperCase();
    const safeRole = encodeURIComponent(this.role);
    const branch = encodeURIComponent(this.auth.getBranchId() || 'main');
    const wantsFinance = this.isFinanceRole() || this.isLeadershipRole();
    const wantsTeacher = this.role === 'TEACHER';

    forkJoin({
      config: this.safeGet(`/api/config/ui/roles/${safeRole}/dashboard`),
      students: this.safeGet('/api/student/directory/summary'),
      staff: this.safeGet('/api/staff/directory/summary'),
      admissions: this.safePage('/api/admission/applications', {
        status: 'IN_PROGRESS',
        sortBy: 'updatedAt',
        sortDir: 'DESC',
      }),
      attendance: this.safePage('/api/attendance/records'),
      fees: this.safePage('/api/fee/collections'),
      issues: wantsTeacher
        ? of(null)
        : this.safeGet<any[]>('/api/library/circulation/issues'),
      finance: wantsFinance
        ? this.safeGet(
            `/api/fee/finance/income-expense?preset=THIS_MONTH&branchIds=${branch}`,
          )
        : of(null),
      salary: wantsFinance
        ? this.safeGet(`/api/payroll/reports/salary-summary?preset=THIS_MONTH&branchIds=${branch}`)
        : of(null),
      teacherScope: wantsTeacher ? this.safeGet('/api/academic/teacher-scope') : of(null),
      accessScope: wantsTeacher ? this.safeGet('/api/student/access-scope') : of(null),
      mySlots: wantsTeacher ? this.safeGet<any[]>('/api/academic/timetable/my-slots') : of(null),
    }).subscribe({
      next: (data: DashboardBundle) => {
        this.configureRole(data.config);
        this.buildMetrics(data);
        this.buildActivity(data);
        this.unavailable = this.countUnavailable(data, wantsFinance, wantsTeacher);
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Dashboard could not be loaded.';
      },
    });
  }

  /** Only count sources that were actually requested (skip intentional of(null) skips). */
  private countUnavailable(
    data: DashboardBundle,
    wantsFinance: boolean,
    wantsTeacher: boolean,
  ): number {
    const skipped = new Set<keyof DashboardBundle>();
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
    return (Object.keys(data) as Array<keyof DashboardBundle>).filter(
      (key) => !skipped.has(key) && data[key] == null,
    ).length;
  }

  private safeGet<T = any>(path: string): Observable<T | null> {
    return this.api.get<T>(path).pipe(timeout(15000), catchError(() => of(null)));
  }

  private safePage(
    path: string,
    extra?: Record<string, string | number | boolean | null | undefined>,
  ): Observable<PageResult<any> | null> {
    return this.api
      .getPage<any>(path, 0, 5, extra)
      .pipe(timeout(15000), catchError(() => of(null)));
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

    const all: Record<string, Metric> = {
      students: {
        id: 'students',
        label: 'Active students',
        value: data.students?.active ?? data.students?.total ?? '—',
        hint: `${data.students?.newAdmissions ?? 0} new admissions`,
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
