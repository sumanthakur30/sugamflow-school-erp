import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService, PageResult } from '../../core/api.service';
import { ListToolbarComponent } from '../../shared/list-toolbar/list-toolbar.component';
import { ListSortOption, pageMeta, sortRows } from '../../shared/list-toolbar/list-controls';

@Component({
  selector: 'sf-staff-directory',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ListToolbarComponent],
  templateUrl: './staff-directory.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/list-toolbar.component.scss',
    '../../shared/list-toolbar/sortable-table.scss',
    './student-directory.component.scss',
    './staff-directory.component.scss',
  ],
})
export class StaffDirectoryComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  loading = true;
  searching = false;
  saving = false;
  error = '';
  statusMsg = '';
  featureEnabled = false;
  columns: Array<{ key: string; label: string; visible?: boolean }> = [];
  summary: Record<string, unknown> = {};
  page: PageResult<any> = {
    items: [],
    page: 0,
    size: 50,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
  };

  q = '';
  status = '';
  department = '';
  designation = '';
  employmentType = '';
  gender = '';
  staffGroup = '';
  joinedWithinDays: number | null = null;
  activeSummary: 'all' | 'active' | 'teachers' | 'nonTeaching' | 'new' = 'all';
  showAdvancedFilters = false;
  sortBy = 'updatedAt';
  sortDir: 'ASC' | 'DESC' = 'DESC';
  readonly sortOptions: ListSortOption[] = [
    { key: 'updatedAt', label: 'Updated' },
    { key: 'fullName', label: 'Full Name' },
    { key: 'employeeNo', label: 'Employee No' },
    { key: 'department', label: 'Department' },
    { key: 'designation', label: 'Designation' },
    { key: 'employmentType', label: 'Employment Type' },
    { key: 'status', label: 'Status' },
    { key: 'mobile', label: 'Mobile' },
    { key: 'branchId', label: 'Branch' },
  ];
  readonly departmentOptions = [
    'Academics',
    'Administration',
    'Finance & Accounts',
    'Human Resources',
    'Information Technology',
    'Library',
    'Transport',
    'Hostel',
    'Operations',
    'Security',
    'Maintenance',
  ];
  readonly designationOptions = [
    'Teacher',
    'Senior Teacher',
    'Head of Department',
    'Academic Coordinator',
    'Principal',
    'Vice Principal',
    'Administrator',
    'Accountant',
    'HR Manager',
    'Receptionist',
    'Librarian',
    'Transport Manager',
    'Driver',
    'Hostel Warden',
    'Security Guard',
    'Office Staff',
    'IT Support',
    'Maintenance Staff',
  ];

  showCreate = false;
  /** When set, the form edits an existing staff member instead of creating. */
  editingId: string | null = null;
  deletingId: string | null = null;
  draft: Record<string, unknown> = {
    fullName: '',
    mobile: '',
    email: '',
    department: '',
    designation: '',
    employmentType: 'Permanent',
    gender: '',
    joiningDate: '',
    status: 'ACTIVE',
  };
  /** UI display value for joining date in DD/MM/YYYY. */
  joiningDateDisplay = '';

  get showForm(): boolean {
    return this.showCreate || !!this.editingId;
  }

  get pageTitle(): string {
    if (this.editingId) return 'Edit staff member';
    if (this.showCreate) return 'Add staff member';
    return 'Staff';
  }

  get pageSubtitle(): string {
    if (this.editingId) return 'Update employee details and save changes.';
    if (this.showCreate) return 'Create a teaching or non-teaching employee profile.';
    return 'Manage teaching and non-teaching employees from one directory.';
  }

  ngOnInit(): void {
    this.showCreate = this.router.url.split('?')[0].endsWith('/staff-directory/add');
    this.resetDraft();
    const createdEmployee = history.state?.createdEmployee;
    if (createdEmployee && !this.showCreate) {
      this.statusMsg = `Staff member ${createdEmployee} created successfully`;
    }
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/staff/directory/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.columns = (boot.columns || []).filter((c: any) => c.visible !== false);
        this.loading = false;
        if (this.featureEnabled) {
          this.loadSummary();
          this.search(0);
        }
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? err?.message ?? 'Staff directory bootstrap failed';
      },
    });
  }

  loadSummary(): void {
    this.api.get<any>('/api/staff/directory/summary').subscribe({
      next: (s) => (this.summary = s || {}),
      error: () => (this.summary = {}),
    });
  }

  get showingFrom(): number {
    return pageMeta(this.page.page, this.page.size || 50, this.page.totalElements || 0).from;
  }

  get showingTo(): number {
    return pageMeta(this.page.page, this.page.size || 50, this.page.totalElements || 0).to;
  }

  get clearEnabled(): boolean {
    return (
      !!this.q.trim() ||
      !!this.status ||
      !!this.department.trim() ||
      !!this.designation.trim() ||
      !!this.employmentType ||
      !!this.gender ||
      !!this.staffGroup ||
      this.joinedWithinDays != null ||
      this.activeSummary !== 'all' ||
      this.sortBy !== 'updatedAt' ||
      this.sortDir !== 'DESC' ||
      this.page.size !== 50
    );
  }

  search(page = 0): void {
    this.searching = true;
    this.error = '';
    this.api
      .getPage<any>('/api/staff/directory/staff', page, this.page.size || 50, this.filterParams())
      .subscribe({
        next: (p) => {
          this.page = {
            ...p,
            items: sortRows(p.items || [], this.sortBy, this.sortDir, (row, key) => {
              if (key === 'fullName') return row.fullName || row.name;
              return row?.[key];
            }),
          };
          this.searching = false;
        },
        error: (err) => {
          this.searching = false;
          this.error = err?.error?.message ?? 'Search failed';
        },
      });
  }

  onPageSize(size: number): void {
    this.page = { ...this.page, size };
    this.search(0);
  }

  clearFilters(): void {
    this.q = '';
    this.status = '';
    this.department = '';
    this.designation = '';
    this.employmentType = '';
    this.gender = '';
    this.staffGroup = '';
    this.joinedWithinDays = null;
    this.activeSummary = 'all';
    this.sortBy = 'updatedAt';
    this.sortDir = 'DESC';
    this.page = { ...this.page, size: 50 };
    this.search(0);
  }

  applySummaryFilter(kind: 'all' | 'active' | 'teachers' | 'nonTeaching' | 'new'): void {
    this.q = '';
    this.department = '';
    this.designation = '';
    this.employmentType = '';
    this.gender = '';
    this.activeSummary = kind;
    this.staffGroup = '';
    this.joinedWithinDays = null;
    if (kind === 'active') {
      this.status = 'ACTIVE';
    } else if (kind === 'teachers') {
      this.status = '';
      this.staffGroup = 'TEACHER';
    } else if (kind === 'nonTeaching') {
      this.status = '';
      this.staffGroup = 'NON_TEACHING';
    } else if (kind === 'new') {
      this.status = '';
      this.joinedWithinDays = 30;
    } else {
      this.status = '';
    }
    this.search(0);
  }

  isSortableColumn(key: string): boolean {
    return this.sortOptions.some((option) => option.key === key);
  }

  sortByColumn(key: string): void {
    if (!this.isSortableColumn(key)) {
      return;
    }
    if (this.sortBy === key) {
      this.sortDir = this.sortDir === 'ASC' ? 'DESC' : 'ASC';
    } else {
      this.sortBy = key;
      this.sortDir = 'ASC';
    }
    this.search(0);
  }

  sortIndicator(key: string): string {
    if (this.sortBy !== key) {
      return '↕';
    }
    return this.sortDir === 'ASC' ? '↑' : '↓';
  }

  createStaff(): void {
    this.saveStaff();
  }

  saveStaff(): void {
    const fullName = String(this.draft['fullName'] || '').trim();
    const department = String(this.draft['department'] || '').trim();
    const designation = String(this.draft['designation'] || '').trim();
    if (!fullName || !department || !designation) {
      this.error = 'Full name, department, and designation are required';
      return;
    }
    const mobile = String(this.draft['mobile'] || '').replace(/\D/g, '');
    if (mobile && mobile.length !== 10) {
      this.error = 'Mobile number must be 10 digits';
      return;
    }
    const email = String(this.draft['email'] || '').trim();
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      this.error = 'Enter a valid email address';
      return;
    }
    const joiningIso = this.parseJoiningDateToIso(this.joiningDateDisplay);
    if (this.joiningDateDisplay.trim() && !joiningIso) {
      this.error = 'Joining date must be in DD/MM/YYYY format';
      return;
    }
    this.draft['mobile'] = mobile;
    this.draft['email'] = email;
    this.draft['joiningDate'] = joiningIso || '';

    this.saving = true;
    this.error = '';
    this.statusMsg = '';

    const body = {
      answers: this.draft,
      status: String(this.draft['status'] || 'ACTIVE'),
    };

    if (this.editingId) {
      this.api.put<any>(`/api/staff/staff/${this.editingId}`, body).subscribe({
        next: (updated) => {
          this.saving = false;
          this.editingId = null;
          this.resetDraft();
          this.statusMsg = `Updated ${updated.employeeNo || updated.id || 'staff member'}`;
          this.loadSummary();
          this.search(this.page.page);
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message ?? 'Update failed';
        },
      });
      return;
    }

    this.api.post<any>('/api/staff/staff', body).subscribe({
      next: (created) => {
        this.saving = false;
        this.resetDraft();
        void this.router.navigate(['/admin/staff-directory'], {
          state: { createdEmployee: created.employeeNo || created.id },
        });
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message ?? 'Create failed';
      },
    });
  }

  startEdit(row: any): void {
    const id = String(row?.id || '').trim();
    if (!id) return;
    this.error = '';
    this.statusMsg = '';
    this.saving = true;
    this.api.get<any>(`/api/staff/staff/${id}`).subscribe({
      next: (staff) => {
        this.saving = false;
        this.showCreate = false;
        this.editingId = id;
        const answers = (staff?.answers || {}) as Record<string, unknown>;
        const joiningIso =
          this.parseJoiningDateToIso(String(answers['joiningDate'] || '')) ||
          String(answers['joiningDate'] || staff?.joiningDate || '').trim();
        this.draft = {
          fullName: answers['fullName'] ?? staff?.fullName ?? '',
          mobile: answers['mobile'] ?? staff?.mobile ?? '',
          email: answers['email'] ?? staff?.email ?? '',
          department: answers['department'] ?? staff?.department ?? '',
          designation: answers['designation'] ?? staff?.designation ?? '',
          employmentType: answers['employmentType'] ?? staff?.employmentType ?? 'Permanent',
          gender: answers['gender'] ?? staff?.gender ?? '',
          joiningDate: joiningIso,
          status: staff?.status || answers['status'] || 'ACTIVE',
          employeeNo: staff?.employeeNo || answers['employeeNo'] || '',
        };
        this.joiningDateDisplay = joiningIso.includes('-')
          ? this.formatIsoToDisplay(joiningIso)
          : this.formatIsoToDisplay(this.parseJoiningDateToIso(joiningIso) || '');
        if (!this.joiningDateDisplay && joiningIso) {
          this.joiningDateDisplay = String(joiningIso);
        }
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message ?? 'Could not load staff member';
      },
    });
  }

  deleteStaff(row: any): void {
    const id = String(row?.id || '').trim();
    if (!id) return;
    const name = String(row?.fullName || row?.employeeNo || 'this staff member');
    if (!window.confirm(`Delete ${name}? They will be removed from the staff list.`)) {
      return;
    }
    this.deletingId = id;
    this.error = '';
    this.statusMsg = '';
    this.api.delete<any>(`/api/staff/staff/${id}`).subscribe({
      next: () => {
        this.deletingId = null;
        if (this.editingId === id) {
          this.editingId = null;
          this.resetDraft();
        }
        this.statusMsg = `Deleted ${name}`;
        this.loadSummary();
        this.search(this.page.page);
      },
      error: (err) => {
        this.deletingId = null;
        this.error = err?.error?.message ?? 'Delete failed';
      },
    });
  }

  normalizeJoiningDateDisplay(): void {
    const iso = this.parseJoiningDateToIso(this.joiningDateDisplay);
    if (iso) {
      this.joiningDateDisplay = this.formatIsoToDisplay(iso);
      this.draft['joiningDate'] = iso;
    }
  }

  /** Opens the native calendar; the visible field stays in DD/MM/YYYY. */
  openJoiningCalendar(picker: HTMLInputElement): void {
    picker.value = this.parseJoiningDateToIso(this.joiningDateDisplay) || this.localDateIso(new Date());
    if (typeof picker.showPicker === 'function') {
      picker.showPicker();
    } else {
      picker.focus();
      picker.click();
    }
  }

  onJoiningCalendarPicked(iso: string): void {
    if (!iso) return;
    this.draft['joiningDate'] = iso;
    this.joiningDateDisplay = this.formatIsoToDisplay(iso);
  }

  cancelCreate(): void {
    this.editingId = null;
    this.resetDraft();
    if (this.showCreate) {
      void this.router.navigate(['/admin/staff-directory']);
      return;
    }
    this.error = '';
  }

  exportCsv(): void {
    const qs = new URLSearchParams();
    for (const [k, v] of Object.entries(this.filterParams())) {
      if (v !== null && v !== undefined && v !== '') {
        qs.set(k, String(v));
      }
    }
    this.api.getBlob(`/api/staff/directory/export.csv?${qs.toString()}`).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'staff-directory.csv';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => (this.error = err?.error?.message ?? 'Export failed'),
    });
  }

  cell(row: any, key: string): string {
    const v = row?.[key];
    return v == null || v === '' ? '—' : String(v);
  }

  private filterParams(): Record<string, string> {
    const params: Record<string, string> = {
      q: this.q,
      status: this.status,
      department: this.department,
      designation: this.designation,
      employmentType: this.employmentType,
      gender: this.gender,
    };
    if (this.staffGroup) {
      params['staffGroup'] = this.staffGroup;
    }
    if (this.joinedWithinDays != null) {
      params['joinedWithinDays'] = String(this.joinedWithinDays);
    }
    return params;
  }

  private resetDraft(): void {
    const todayIso = this.localDateIso(new Date());
    this.draft = {
      fullName: '',
      mobile: '',
      email: '',
      department: '',
      designation: '',
      employmentType: 'Permanent',
      gender: '',
      joiningDate: todayIso,
      status: 'ACTIVE',
    };
    this.joiningDateDisplay = this.formatIsoToDisplay(todayIso);
  }

  private localDateIso(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private formatIsoToDisplay(iso: string): string {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso.trim());
    if (!m) return '';
    return `${m[3]}/${m[2]}/${m[1]}`;
  }

  /** Accepts DD/MM/YYYY or DD-MM-YYYY and returns yyyy-mm-dd, or '' if invalid. */
  private parseJoiningDateToIso(raw: string): string {
    const s = String(raw || '').trim();
    if (!s) return '';
    const m = /^(\d{1,2})[\/\-](\d{1,2})[\/\-](\d{4})$/.exec(s);
    if (!m) return '';
    const dd = Number(m[1]);
    const mm = Number(m[2]);
    const yyyy = Number(m[3]);
    if (mm < 1 || mm > 12 || dd < 1 || dd > 31 || yyyy < 1900 || yyyy > 2100) {
      return '';
    }
    const dt = new Date(yyyy, mm - 1, dd);
    if (dt.getFullYear() !== yyyy || dt.getMonth() !== mm - 1 || dt.getDate() !== dd) {
      return '';
    }
    return `${yyyy}-${String(mm).padStart(2, '0')}-${String(dd).padStart(2, '0')}`;
  }
}
