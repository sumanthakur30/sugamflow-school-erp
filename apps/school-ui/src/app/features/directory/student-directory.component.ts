import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, finalize, map, timeout } from 'rxjs';
import { ApiService, PageResult } from '../../core/api.service';
import { ListToolbarComponent } from '../../shared/list-toolbar/list-toolbar.component';
import {
  ListSortOption,
  headerSortIndicator,
  nextHeaderSort,
  pageMeta,
  sortRows,
} from '../../shared/list-toolbar/list-controls';

@Component({
  selector: 'sf-student-directory',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ListToolbarComponent],
  templateUrl: './student-directory.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/list-toolbar.component.scss',
    '../../shared/list-toolbar/sortable-table.scss',
    './student-directory.component.scss',
  ],
})
export class StudentDirectoryComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  searching = false;
  error = '';
  featureEnabled = false;
  columns: Array<{ key: string; label: string; visible?: boolean }> = [];
  quickActions: Array<{ key: string; label: string; route?: string }> = [];
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
  classSection = '';
  classSectionOptions: string[] = [];
  private officialClassSections: string[] = [];
  gender = '';
  transport = false;
  hostel = false;
  /** Trash mode — elevated roles; lists soft-deleted students only. */
  trashMode = false;
  selectedIds = new Set<string>();
  showAdvancedFilters = false;
  bulkBusy = false;
  bulkReason = '';
  showBulkDeleteConfirm = false;
  statusMsg = '';
  sortBy = 'updatedAt';
  sortDir: 'ASC' | 'DESC' = 'DESC';
  readonly sortOptions: ListSortOption[] = [
    { key: 'updatedAt', label: 'Updated' },
    { key: 'fullName', label: 'Student Name' },
    { key: 'admissionNo', label: 'Admission No' },
    { key: 'classSection', label: 'Class / Section' },
    { key: 'status', label: 'Status' },
    { key: 'mobile', label: 'Mobile' },
  ];

  /** API catalog uses fullName; keep studentName as alias for older configs. */
  private readonly preferredKeys = [
    'fullName',
    'studentName',
    'classSection',
    'status',
    'mobile',
  ];

  ngOnInit(): void {
    this.reload();
  }

  get displayColumns(): Array<{ key: string; label: string; visible?: boolean }> {
    const preferred = this.columns.filter((c) => this.preferredKeys.includes(c.key));
    let cols = preferred.length ? preferred : this.columns;
    const hasName = cols.some((c) => this.isNameKey(c.key));
    cols = cols.filter((c) => {
      if (c.key === 'gender' && !this.hasGenderData) {
        return false;
      }
      // Admission is shown under the student name.
      if (c.key === 'admissionNo' && hasName) {
        return false;
      }
      // Prefer fullName over duplicate studentName.
      if (c.key === 'studentName' && cols.some((x) => x.key === 'fullName')) {
        return false;
      }
      return true;
    });
    return cols;
  }

  get hasGenderData(): boolean {
    const boys = Number(this.summary['boys'] ?? 0);
    const girls = Number(this.summary['girls'] ?? 0);
    if (boys > 0 || girls > 0) {
      return true;
    }
    return this.page.items.some((row) => {
      const g = row?.gender;
      return g != null && String(g).trim() !== '' && String(g) !== '—';
    });
  }

  get genderUnspecified(): number {
    const total = Number(this.summary['total'] ?? 0);
    const boys = Number(this.summary['boys'] ?? 0);
    const girls = Number(this.summary['girls'] ?? 0);
    return Math.max(0, total - boys - girls);
  }

  get filterChips(): Array<{ key: string; label: string }> {
    const chips: Array<{ key: string; label: string }> = [];
    if (this.trashMode) chips.push({ key: 'trash', label: 'Trash (deleted)' });
    if (this.q.trim()) chips.push({ key: 'q', label: `Search: ${this.q.trim()}` });
    if (this.status) chips.push({ key: 'status', label: `Status: ${this.status}` });
    if (this.classSection.trim()) {
      chips.push({ key: 'classSection', label: `Class: ${this.classSection.trim()}` });
    }
    if (this.gender) chips.push({ key: 'gender', label: `Gender: ${this.gender}` });
    if (this.transport) chips.push({ key: 'transport', label: 'Transport' });
    if (this.hostel) chips.push({ key: 'hostel', label: 'Hostel' });
    return chips;
  }

  get hasFilters(): boolean {
    return this.filterChips.length > 0;
  }

  get showingFrom(): number {
    return pageMeta(this.page.page, this.page.size || 50, this.page.totalElements || 0).from;
  }

  get showingTo(): number {
    return pageMeta(this.page.page, this.page.size || 50, this.page.totalElements || 0).to;
  }

  get clearEnabled(): boolean {
    return (
      this.hasFilters ||
      this.trashMode ||
      this.sortBy !== 'updatedAt' ||
      this.sortDir !== 'DESC' ||
      this.page.size !== 50
    );
  }

  get selectedCount(): number {
    return this.selectedIds.size;
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.loadClassSectionOptions();
    this.api.get<any>('/api/student/directory/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.columns = (boot.columns || []).filter((c: any) => c.visible !== false);
        this.quickActions = boot.quickActions || [];
        const opts = boot.pageSizeOptions as number[] | undefined;
        if (opts?.length && !opts.includes(this.page.size)) {
          this.page = { ...this.page, size: boot.defaultPageSize || 50 };
        }
        this.loading = false;
        if (this.featureEnabled) {
          this.loadSummary();
          this.search(0);
        }
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? err?.message ?? 'Student directory bootstrap failed';
      },
    });
  }

  loadSummary(): void {
    this.api.get<any>('/api/student/directory/summary').subscribe({
      next: (s) => (this.summary = s || {}),
      error: () => (this.summary = {}),
    });
  }

  search(page = 0): void {
    this.searching = true;
    this.error = '';
    this.api
      .getPage<any>('/api/student/directory/students', page, this.page.size || 50, {
        ...this.filterParams(),
        sort: this.sortBy,
        dir: this.sortDir,
      })
      .pipe(
        timeout(8000),
        catchError(() => this.searchViaStudentMaster(page)),
        finalize(() => {
          this.searching = false;
        }),
      )
      .subscribe({
        next: (p) => {
          this.page = {
            ...p,
            items: sortRows(p.items || [], this.sortBy, this.sortDir, (row, key) => {
              if (key === 'fullName') return row.fullName || row.studentName;
              return row?.[key];
            }),
          };
          this.rebuildClassSectionOptions();
          this.selectedIds.clear();
        },
        error: (err) => {
          this.error = err?.error?.message ?? err?.message ?? 'Search failed';
        },
      });
  }

  /**
   * Fallback when /directory/students hangs or fails — the student master list
   * endpoint is healthy and returns the same roster; we adapt + filter locally.
   */
  private searchViaStudentMaster(page: number) {
    return this.api.getPage<any>('/api/student/students', 0, 200, {
      q: this.q || undefined,
    }).pipe(
      map((p) => {
        let items = (p.items || []).map((row) => this.toDirectoryRow(row));
        items = items.filter((row) => this.matchesDirectoryFilters(row));
        items = sortRows(items, this.sortBy, this.sortDir, (row, key) => {
          if (key === 'fullName') return row.fullName || row.studentName;
          return row?.[key];
        });
        const size = this.page.size || 50;
        const start = page * size;
        const slice = items.slice(start, start + size);
        return {
          items: slice,
          page,
          size,
          totalElements: items.length,
          totalPages: Math.max(1, Math.ceil(items.length / size)),
          hasNext: start + size < items.length,
        };
      }),
    );
  }

  private toDirectoryRow(row: any): any {
    const answers = row?.answers ?? {};
    return {
      id: row?.id,
      admissionNo: row?.admissionNo ?? answers.admissionNo ?? '',
      fullName: answers.fullName ?? answers.studentName ?? '',
      studentName: answers.studentName ?? answers.fullName ?? '',
      classSection: answers.classSection || answers.classApplied || '',
      gender: answers.gender ?? '',
      status: row?.status ?? '',
      mobile: answers.mobile ?? '',
      email: answers.email ?? '',
      category: answers.category ?? '',
      house: answers.house ?? '',
      parentName: answers.parentName || answers.fatherName || '',
      transport: !!answers.transport,
      hostel: !!answers.hostel,
      scholarship: !!answers.scholarship,
      updatedAt: row?.updatedAt,
      deleted: !!row?.deleted,
    };
  }

  private matchesDirectoryFilters(row: any): boolean {
    if (this.trashMode) {
      if (!row.deleted) return false;
    } else if (row.deleted) {
      return false;
    }
    if (this.status && String(row.status || '').toUpperCase() !== this.status.toUpperCase()) {
      return false;
    }
    if (this.classSection.trim()) {
      const want = this.classSection.trim().toLowerCase();
      const have = String(row.classSection || '').trim().toLowerCase();
      if (have !== want) return false;
    }
    if (this.gender) {
      if (String(row.gender || '').toLowerCase() !== this.gender.toLowerCase()) return false;
    }
    if (this.transport && !row.transport) return false;
    if (this.hostel && !row.hostel) return false;
    if (this.q.trim()) {
      const q = this.q.trim().toLowerCase();
      const blob = [
        row.fullName,
        row.admissionNo,
        row.mobile,
        row.parentName,
        row.classSection,
        row.email,
      ]
        .map((v) => String(v || '').toLowerCase())
        .join(' ');
      if (!blob.includes(q)) return false;
    }
    return true;
  }

  private loadClassSectionOptions(): void {
    this.api.get<any[]>('/api/academic/sections').subscribe({
      next: (sections) => {
        this.officialClassSections = (sections ?? [])
          .map((section) =>
            String(section?.studentLabel || section?.name || section?.label || '').trim(),
          )
          .filter(Boolean);
        this.rebuildClassSectionOptions();
      },
      error: () => {
        this.officialClassSections = [];
        this.rebuildClassSectionOptions();
      },
    });
  }

  private rebuildClassSectionOptions(): void {
    const studentSections = (this.page.items ?? [])
      .map((student) => this.formatClass(student?.classSection))
      .filter((section) => section !== '—');
    const current = this.classSection.trim();
    const options = [
      ...new Set([
        ...this.officialClassSections,
        ...studentSections,
        ...(current ? [current] : []),
      ]),
    ];
    options.sort((a, b) =>
      a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' }),
    );
    this.classSectionOptions = options;
  }

  onPageSize(size: number): void {
    this.page = { ...this.page, size };
    this.search(0);
  }

  /** studentName is a legacy alias of fullName in some directory configs. */
  private sortKeyFor(columnKey: string): string {
    return columnKey === 'studentName' ? 'fullName' : columnKey;
  }

  isSortableColumn(key: string): boolean {
    const k = this.sortKeyFor(key);
    return this.sortOptions.some((option) => option.key === k);
  }

  sortByColumn(key: string): void {
    if (!this.isSortableColumn(key)) {
      return;
    }
    const next = nextHeaderSort(this.sortBy, this.sortDir, this.sortKeyFor(key));
    this.sortBy = next.sortBy;
    this.sortDir = next.sortDir;
    this.search(0);
  }

  sortIndicator(key: string): string {
    return headerSortIndicator(this.sortBy, this.sortDir, this.sortKeyFor(key));
  }

  isActiveSort(key: string): boolean {
    return this.sortBy === this.sortKeyFor(key);
  }

  clearFilters(): void {
    this.q = '';
    this.status = '';
    this.classSection = '';
    this.gender = '';
    this.transport = false;
    this.hostel = false;
    this.trashMode = false;
    this.sortBy = 'updatedAt';
    this.sortDir = 'DESC';
    this.page = { ...this.page, size: 50 };
    this.search(0);
  }

  removeChip(key: string): void {
    switch (key) {
      case 'trash':
        this.trashMode = false;
        break;
      case 'q':
        this.q = '';
        break;
      case 'status':
        this.status = '';
        break;
      case 'classSection':
        this.classSection = '';
        break;
      case 'gender':
        this.gender = '';
        break;
      case 'transport':
        this.transport = false;
        break;
      case 'hostel':
        this.hostel = false;
        break;
    }
    this.search(0);
  }

  applySummaryFilter(kind: 'all' | 'active' | 'tc' | 'new' | 'trash'): void {
    this.q = '';
    this.classSection = '';
    this.gender = '';
    this.transport = false;
    this.hostel = false;
    if (kind === 'trash') {
      this.trashMode = true;
      this.status = '';
    } else {
      this.trashMode = false;
      if (kind === 'active') {
        this.status = 'ACTIVE';
      } else if (kind === 'tc') {
        this.status = 'TC_ISSUED';
      } else {
        this.status = '';
      }
    }
    this.search(0);
  }

  toggleTrash(): void {
    this.trashMode = !this.trashMode;
    if (this.trashMode) {
      this.status = '';
    }
    this.search(0);
  }

  toggleAll(checked: boolean): void {
    this.selectedIds.clear();
    if (checked) {
      for (const row of this.page.items) {
        this.selectedIds.add(row.id);
      }
    }
  }

  toggleRow(id: string, checked: boolean): void {
    if (checked) {
      this.selectedIds.add(id);
    } else {
      this.selectedIds.delete(id);
    }
  }

  isSelected(id: string): boolean {
    return this.selectedIds.has(id);
  }

  openBulkDelete(): void {
    if (!this.selectedCount || this.trashMode) return;
    this.bulkReason = '';
    this.showBulkDeleteConfirm = true;
    this.error = '';
  }

  cancelBulkDelete(): void {
    this.showBulkDeleteConfirm = false;
    this.bulkReason = '';
  }

  confirmBulkSoftDelete(): void {
    if (!this.selectedCount || this.bulkBusy) return;
    const reason = this.bulkReason.trim();
    if (!reason) {
      this.error = 'Deletion reason is required for bulk soft-delete.';
      return;
    }
    this.bulkBusy = true;
    this.error = '';
    this.statusMsg = '';
    this.api
      .post<any>('/api/student/students/bulk-soft-delete', {
        studentIds: [...this.selectedIds],
        reason,
      })
      .subscribe({
        next: (res) => {
          this.bulkBusy = false;
          this.showBulkDeleteConfirm = false;
          this.bulkReason = '';
          this.statusMsg = `Soft-deleted ${res?.deleted ?? 0} of ${res?.requested ?? 0} students` +
            (res?.failed ? ` (${res.failed} failed)` : '') +
            '.';
          this.loadSummary();
          this.search(this.page.page);
        },
        error: (err) => {
          this.bulkBusy = false;
          this.error = err?.error?.message ?? 'Bulk soft-delete failed';
        },
      });
  }

  confirmBulkRestore(): void {
    if (!this.selectedCount || !this.trashMode || this.bulkBusy) return;
    this.bulkBusy = true;
    this.error = '';
    this.statusMsg = '';
    this.api
      .post<any>('/api/student/students/bulk-restore', {
        studentIds: [...this.selectedIds],
        reason: 'Bulk restore from directory trash',
        status: 'ACTIVE',
      })
      .subscribe({
        next: (res) => {
          this.bulkBusy = false;
          this.statusMsg = `Restored ${res?.restored ?? 0} of ${res?.requested ?? 0} students` +
            (res?.failed ? ` (${res.failed} failed)` : '') +
            '.';
          this.loadSummary();
          this.search(this.page.page);
        },
        error: (err) => {
          this.bulkBusy = false;
          this.error = err?.error?.message ?? 'Bulk restore failed';
        },
      });
  }

  exportCsv(): void {
    const qs = new URLSearchParams();
    const filters = this.filterParams();
    for (const [k, v] of Object.entries(filters)) {
      if (v !== null && v !== undefined && v !== '') {
        qs.set(k, String(v));
      }
    }
    this.api.getBlob(`/api/student/directory/export.csv?${qs.toString()}`).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'student-directory.csv';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => (this.error = err?.error?.message ?? 'Export failed'),
    });
  }

  displayName(row: any): string {
    return this.firstNonBlank(row?.fullName, row?.studentName, row?.admissionNo, '—');
  }

  cell(row: any, key: string): string {
    if (this.isNameKey(key)) {
      return this.displayName(row);
    }
    if (key === 'mobile') {
      return this.formatMobile(row?.mobile);
    }
    if (key === 'classSection') {
      return this.formatClass(row?.classSection);
    }
    const v = row?.[key];
    if (v === true) return 'Yes';
    if (v === false) return 'No';
    return v == null || v === '' ? '—' : String(v);
  }

  statusClass(status: unknown): string {
    const s = String(status || '')
      .trim()
      .toUpperCase();
    if (s === 'ACTIVE') return 'badge badge-active';
    if (s === 'TRANSFERRED' || s === 'TC_ISSUED') return 'badge badge-tc';
    if (s === 'INACTIVE' || s === 'DROPOUT' || s === 'DELETED') return 'badge badge-muted';
    if (s === 'ALUMNI') return 'badge badge-alumni';
    return 'badge';
  }

  isNameKey(key: string): boolean {
    return key === 'fullName' || key === 'studentName';
  }

  private formatMobile(raw: unknown): string {
    if (raw == null || String(raw).trim() === '') return '—';
    const digits = String(raw).replace(/\D/g, '');
    if (digits.length === 10) return digits;
    if (digits.length > 10) {
      // Smoke imports sometimes concatenate stamps; show last 10 when plausible.
      return digits.slice(-10);
    }
    return String(raw);
  }

  private formatClass(raw: unknown): string {
    const s = raw == null ? '' : String(raw).trim();
    if (!s) return '—';
    // Hide obvious smoke-test labels from class column readability.
    if (/^(ReportCard|Gradebook|Alert|Roster)-\d{10,}$/i.test(s)) {
      return '—';
    }
    return s;
  }

  private firstNonBlank(...vals: unknown[]): string {
    for (const v of vals) {
      if (v == null) continue;
      const s = String(v).trim();
      if (s && s !== '—') return s;
    }
    return '—';
  }

  private filterParams(): Record<string, string | boolean> {
    return {
      q: this.q,
      status: this.status,
      classSection: this.classSection,
      gender: this.gender,
      transport: this.transport ? 'true' : '',
      hostel: this.hostel ? 'true' : '',
      includeDeleted: this.trashMode ? 'true' : 'false',
    };
  }
}
