import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ApiService, PageResult } from '../../core/api.service';
import { TenantContextService } from '../../core/tenant-context.service';
import { ModuleBootstrapService } from '../../core/module-bootstrap.service';
import {
  headerSortIndicator,
  nextHeaderSort,
  pageMeta,
  sortRows,
} from '../../shared/list-toolbar/list-controls';
import { StudentLookupComponent } from '../../shared/student-lookup/student-lookup.component';
import { StudentLookupRow } from '../../shared/student-lookup/student-lookup.models';

type TransportView = 'routes' | 'assign' | 'assignments' | 'workflow';

@Component({
  selector: 'sf-transport',
  standalone: true,
  imports: [CommonModule, FormsModule, StudentLookupComponent],
  templateUrl: './transport.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/list-toolbar.component.scss',
    './transport.component.scss',
  ],
})
export class TransportComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly tenantContext = inject(TenantContextService);
  private readonly modules = inject(ModuleBootstrapService);
  private campusReadySub?: Subscription;

  loading = true;
  error = '';
  statusMsg = '';
  featureEnabled = false;
  view: TransportView = 'routes';
  formKey = 'transport_route';
  workflowKey = 'transport';
  fields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
  answers: Record<string, unknown> = {};
  records: any[] = [];
  selectedId: string | null = null;
  selected: any = null;
  actionComment = '';
  submitting = false;

  routes: any[] = [];
  assignments: any[] = [];
  showRouteForm = false;
  routeDraft = { routeKey: 'route_a', routeName: '', vehicleNo: '', capacity: 40 };
  assignDraft = {
    routeId: '',
    studentId: '',
    admissionNo: '',
    studentName: '',
    stopName: '',
    pickupTime: '',
  };
  selectedStudent: StudentLookupRow | null = null;
  lookupNonce = 0;

  workflowStudent: StudentLookupRow | null = null;
  workflowLookupNonce = 0;
  workflowRouteId = '';

  routeQ = '';
  routeSortBy = 'routeName';
  routeSortDir: 'ASC' | 'DESC' = 'ASC';
  routePageSize = 25;
  routePageIndex = 0;

  assignQ = '';
  assignRouteFilter = '';
  assignSortBy = 'studentName';
  assignSortDir: 'ASC' | 'DESC' = 'ASC';
  assignPageSize = 25;
  assignPageIndex = 0;

  listQ = '';
  sortBy = 'updatedAt';
  sortDir: 'ASC' | 'DESC' = 'DESC';
  pageIndex = 0;
  pageSize = 50;
  searching = false;
  page: PageResult<any> = {
    items: [],
    page: 0,
    size: 50,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
  };

  ngOnInit(): void {
    this.campusReadySub = this.tenantContext.whenCampusReady().subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.campusReadySub?.unsubscribe();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    const path = '/api/transport/bootstrap';
    const apply = (boot: any) => {
      this.featureEnabled = !!boot.featureEnabled;
      this.formKey = boot.formKey;
      this.workflowKey = boot.workflowKey;
      this.fields = this.extractFields(boot.form);
      this.resetWorkflowAnswers();
      this.loading = false;
      this.loadRecords();
      this.loadRoutes();
    };
    const peeked = this.modules.peek(path);
    if (peeked) {
      apply(peeked);
      return;
    }
    this.modules.load(path).subscribe({
      next: apply,
      error: (err) => {
        this.loading = false;
        this.error = ModuleBootstrapService.errorMessage(err, 'Transport bootstrap failed');
        if (ModuleBootstrapService.isFeatureDisabled(err)) {
          this.featureEnabled = false;
        } else {
          // Do NOT imply plan feature is off on workflow/form/network errors
          this.featureEnabled = true;
        }
      },
    });
  }

  selectView(view: TransportView): void {
    this.view = view;
    this.error = '';
    this.statusMsg = '';
  }

  loadRoutes(): void {
    this.api.get<any[]>('/api/transport/routes').subscribe({
      next: (list) => (this.routes = list ?? []),
      error: () => (this.routes = []),
    });
    this.api.get<any[]>('/api/transport/routes/assignments').subscribe({
      next: (list) => (this.assignments = list ?? []),
      error: () => (this.assignments = []),
    });
  }

  get totalCapacity(): number {
    return this.routes.reduce((sum, route) => sum + Number(route.capacity || 0), 0);
  }

  get availableSeats(): number {
    return Math.max(0, this.totalCapacity - this.assignments.length);
  }

  get routesAtCapacity(): number {
    return this.routes.filter((route) => this.routeOccupancy(route.id) >= Number(route.capacity || 0))
      .length;
  }

  openRouteSummary(): void {
    this.selectView('routes');
    this.clearRouteFilters();
  }

  openAssignments(): void {
    this.selectView('assignments');
    this.clearAssignFilters();
  }

  openAssignStudent(): void {
    this.selectView('assign');
  }

  routeOccupancy(routeId: string): number {
    return this.assignments.filter((assignment) => String(assignment.routeId) === String(routeId))
      .length;
  }

  routeLabel(routeId: string | null | undefined): string {
    const route = this.routes.find((item) => String(item.id) === String(routeId));
    return route?.routeName || 'Unknown route';
  }

  routeVehicle(routeId: string | null | undefined): string {
    const route = this.routes.find((item) => String(item.id) === String(routeId));
    return route?.vehicleNo || '—';
  }

  saveRoute(): void {
    this.submitting = true;
    this.error = '';
    this.statusMsg = '';
    this.api.post<any>('/api/transport/routes', this.routeDraft).subscribe({
      next: () => {
        this.submitting = false;
        this.statusMsg = `Route “${this.routeDraft.routeName}” saved.`;
        this.routeDraft = { routeKey: 'route_a', routeName: '', vehicleNo: '', capacity: 40 };
        this.showRouteForm = false;
        this.loadRoutes();
      },
      error: (err) => {
        this.submitting = false;
        this.error = err?.error?.message ?? 'Save route failed';
      },
    });
  }

  onStudentSelected(student: StudentLookupRow): void {
    this.selectedStudent = student;
    this.assignDraft.studentId = student.id || '';
    this.assignDraft.admissionNo = student.admissionNo || '';
    this.assignDraft.studentName = student.fullName || '';
    this.error = '';
  }

  onStudentCleared(): void {
    this.selectedStudent = null;
    this.assignDraft.studentId = '';
    this.assignDraft.admissionNo = '';
    this.assignDraft.studentName = '';
  }

  get selectedStudentAssignment(): any | null {
    const admissionNo = this.selectedStudent?.admissionNo?.trim().toLowerCase();
    if (!admissionNo) return null;
    return (
      this.assignments.find(
        (assignment) =>
          String(assignment.admissionNo || '').trim().toLowerCase() === admissionNo,
      ) ?? null
    );
  }

  assignStudent(): void {
    if (!this.selectedStudent?.admissionNo) {
      this.error = 'Search and select a student before assigning a route';
      return;
    }
    if (!this.assignDraft.routeId) {
      this.error = 'Select a route for the student';
      return;
    }
    this.submitting = true;
    this.error = '';
    this.statusMsg = '';
    this.api.post<any>('/api/transport/routes/assign', this.assignDraft).subscribe({
      next: () => {
        const studentName = this.assignDraft.studentName || this.assignDraft.admissionNo;
        this.submitting = false;
        this.statusMsg = `${studentName} assigned to ${this.routeLabel(this.assignDraft.routeId)}.`;
        this.assignDraft = {
          routeId: '',
          studentId: '',
          admissionNo: '',
          studentName: '',
          stopName: '',
          pickupTime: '',
        };
        this.selectedStudent = null;
        this.lookupNonce++;
        this.view = 'assignments';
        this.loadRoutes();
      },
      error: (err) => {
        this.submitting = false;
        this.error = err?.error?.message ?? 'Assign failed';
      },
    });
  }

  endAssignment(assignment: any): void {
    this.submitting = true;
    this.error = '';
    this.statusMsg = '';
    this.api.post<any>(`/api/transport/routes/assignments/${assignment.id}/end`, {}).subscribe({
      next: () => {
        this.submitting = false;
        this.statusMsg = `Route assignment ended for ${assignment.studentName || assignment.admissionNo}.`;
        this.loadRoutes();
      },
      error: (err) => {
        this.submitting = false;
        this.error = err?.error?.message ?? 'End assignment failed';
      },
    });
  }

  private routeRows(): any[] {
    let rows = this.routes;
    const q = this.routeQ.trim().toLowerCase();
    if (q) {
      rows = rows.filter(
        (route) =>
          String(route.routeName ?? '').toLowerCase().includes(q) ||
          String(route.routeKey ?? '').toLowerCase().includes(q) ||
          String(route.vehicleNo ?? '').toLowerCase().includes(q),
      );
    }
    return sortRows(rows, this.routeSortBy, this.routeSortDir, (route, key) =>
      key === 'occupancy' ? this.routeOccupancy(route.id) : route?.[key],
    );
  }

  get filteredRoutes(): any[] {
    const start = this.routePageIndex * this.routePageSize;
    return this.routeRows().slice(start, start + this.routePageSize);
  }

  get filteredRoutesTotal(): number {
    return this.routeRows().length;
  }

  get routePageLabel(): string {
    const total = this.filteredRoutesTotal;
    if (!total) return '0 routes';
    const meta = pageMeta(this.routePageIndex, this.routePageSize, total);
    return `${meta.from}–${meta.to} of ${total} routes`;
  }

  sortRoutesBy(key: string): void {
    const next = nextHeaderSort(this.routeSortBy, this.routeSortDir, key);
    this.routeSortBy = next.sortBy;
    this.routeSortDir = next.sortDir;
    this.routePageIndex = 0;
  }

  routeSortIcon(key: string): string {
    return headerSortIndicator(this.routeSortBy, this.routeSortDir, key);
  }

  clearRouteFilters(): void {
    this.routeQ = '';
    this.routeSortBy = 'routeName';
    this.routeSortDir = 'ASC';
    this.routePageIndex = 0;
  }

  private assignmentRows(): any[] {
    let rows = this.assignments;
    if (this.assignRouteFilter) {
      rows = rows.filter(
        (assignment) => String(assignment.routeId) === String(this.assignRouteFilter),
      );
    }
    const q = this.assignQ.trim().toLowerCase();
    if (q) {
      rows = rows.filter(
        (assignment) =>
          String(assignment.admissionNo ?? '').toLowerCase().includes(q) ||
          String(assignment.studentName ?? '').toLowerCase().includes(q) ||
          String(assignment.stopName ?? '').toLowerCase().includes(q) ||
          this.routeLabel(assignment.routeId).toLowerCase().includes(q),
      );
    }
    return sortRows(rows, this.assignSortBy, this.assignSortDir, (assignment, key) =>
      key === 'routeName' ? this.routeLabel(assignment.routeId) : assignment?.[key],
    );
  }

  get filteredAssignments(): any[] {
    const start = this.assignPageIndex * this.assignPageSize;
    return this.assignmentRows().slice(start, start + this.assignPageSize);
  }

  get filteredAssignmentsTotal(): number {
    return this.assignmentRows().length;
  }

  get assignPageLabel(): string {
    const total = this.filteredAssignmentsTotal;
    if (!total) return '0 assignments';
    const meta = pageMeta(this.assignPageIndex, this.assignPageSize, total);
    return `${meta.from}–${meta.to} of ${total} assignments`;
  }

  sortAssignmentsBy(key: string): void {
    const next = nextHeaderSort(this.assignSortBy, this.assignSortDir, key);
    this.assignSortBy = next.sortBy;
    this.assignSortDir = next.sortDir;
    this.assignPageIndex = 0;
  }

  assignmentSortIcon(key: string): string {
    return headerSortIndicator(this.assignSortBy, this.assignSortDir, key);
  }

  clearAssignFilters(): void {
    this.assignQ = '';
    this.assignRouteFilter = '';
    this.assignSortBy = 'studentName';
    this.assignSortDir = 'ASC';
    this.assignPageIndex = 0;
  }

  onWorkflowStudentSelected(student: StudentLookupRow): void {
    this.workflowStudent = student;
    this.setAnswer(['studentName', 'fullName'], student.fullName || '');
    this.setAnswer(['admissionNo', 'admissionNumber'], student.admissionNo || '');
    this.setAnswer(['classSection', 'class'], student.classSection || '');
    this.setAnswer(['email', 'studentEmail'], student.email || '');
    this.setAnswer(['mobile', 'phone', 'studentMobile'], student.mobile || '');
    this.error = '';
  }

  onWorkflowStudentCleared(): void {
    this.workflowStudent = null;
    this.setAnswer(['studentName', 'fullName'], '');
    this.setAnswer(['admissionNo', 'admissionNumber'], '');
    this.setAnswer(['classSection', 'class'], '');
    this.setAnswer(['email', 'studentEmail'], '');
    this.setAnswer(['mobile', 'phone', 'studentMobile'], '');
  }

  onWorkflowRouteSelected(routeId: string): void {
    this.workflowRouteId = routeId;
    const route = this.routes.find((item) => String(item.id) === String(routeId));
    this.setAnswer(['routeId'], route?.id || '');
    this.setAnswer(['routeName'], route?.routeName || '');
    this.setAnswer(['routeKey'], route?.routeKey || '');
    this.setAnswer(['vehicleNo', 'vehicleNumber'], route?.vehicleNo || '');
  }

  isDateField(field: { key: string; type: string }): boolean {
    const type = String(field.type || '').toUpperCase();
    return type === 'DATE' || type === 'DATE_PICKER' || /date$/i.test(field.key);
  }

  isWorkflowAutoField(key: string): boolean {
    const normalized = this.normalizeKey(key);
    return [
      'studentname',
      'fullname',
      'admissionno',
      'admissionnumber',
      'class',
      'classsection',
      'email',
      'studentemail',
      'mobile',
      'phone',
      'studentmobile',
      'routeid',
      'routekey',
      'routename',
      'vehicleno',
      'vehiclenumber',
    ].includes(normalized);
  }

  loadRecords(): void {
    this.searching = true;
    this.api
      .getPage<any>('/api/transport/records', this.pageIndex, this.pageSize)
      .subscribe({
        next: (result) => {
          let items = result.items || [];
          const q = this.listQ.trim().toLowerCase();
          if (q) {
            items = items.filter((row) =>
              [
                row.answers?.studentName,
                row.answers?.admissionNo,
                row.answers?.routeName,
                row.answers?.stopName,
                row.status,
              ].some((value) => String(value ?? '').toLowerCase().includes(q)),
            );
          }
          items = sortRows(items, this.sortBy, this.sortDir, (row, key) => {
            if (key === 'studentName') return row.answers?.studentName;
            if (key === 'routeName') return row.answers?.routeName;
            if (key === 'updatedAt') return row.updatedAt || row.createdAt;
            return row?.[key];
          });
          this.page = { ...result, items };
          this.records = items;
          this.searching = false;
        },
        error: (err) => {
          this.searching = false;
          this.error = err?.error?.message ?? 'Failed to load records';
        },
      });
  }

  get showingFrom(): number {
    return pageMeta(this.page.page, this.page.size || this.pageSize, this.page.totalElements || 0)
      .from;
  }

  get showingTo(): number {
    return pageMeta(this.page.page, this.page.size || this.pageSize, this.page.totalElements || 0)
      .to;
  }

  clearListFilters(): void {
    this.listQ = '';
    this.sortBy = 'updatedAt';
    this.sortDir = 'DESC';
    this.pageIndex = 0;
    this.loadRecords();
  }

  sortInboxBy(key: string): void {
    const next = nextHeaderSort(this.sortBy, this.sortDir, key);
    this.sortBy = next.sortBy;
    this.sortDir = next.sortDir;
    this.loadRecords();
  }

  inboxSortIcon(key: string): string {
    return headerSortIndicator(this.sortBy, this.sortDir, key);
  }

  recordStatusLabel(status: unknown): string {
    const value = String(status || '').toUpperCase();
    if (value === 'IN_PROGRESS') return 'In progress';
    if (value === 'INFO_REQUESTED') return 'Info requested';
    if (value === 'APPROVED') return 'Approved';
    if (value === 'REJECTED') return 'Rejected';
    return value ? value.charAt(0) + value.slice(1).toLowerCase() : '—';
  }

  recordStatusClass(status: unknown): string {
    const value = String(status || '').toUpperCase();
    if (value === 'APPROVED') return 'ok';
    if (value === 'REJECTED') return 'bad';
    if (value === 'INFO_REQUESTED') return 'warn';
    return 'progress';
  }

  submit(): void {
    if (!this.workflowStudent) {
      this.error = 'Search and select a student before submitting the request';
      return;
    }
    if (!this.workflowRouteId) {
      this.error = 'Select a route before submitting the request';
      return;
    }
    this.submitting = true;
    this.error = '';
    this.statusMsg = '';
    this.api
      .post<any>('/api/transport/records', {
        formKey: this.formKey,
        workflowKey: this.workflowKey,
        answers: this.normalizeAnswers(),
      })
      .subscribe({
        next: (row) => {
          this.submitting = false;
          this.selectedId = row.id;
          this.selected = row;
          this.statusMsg = `Transport request submitted for ${row.answers?.studentName || 'student'}.`;
          this.workflowStudent = null;
          this.workflowRouteId = '';
          this.workflowLookupNonce++;
          this.resetWorkflowAnswers();
          this.loadRecords();
        },
        error: (err) => {
          this.submitting = false;
          this.error = err?.error?.message ?? 'Submit failed';
        },
      });
  }

  select(row: any): void {
    this.selectedId = row.id;
    this.api.get<any>(`/api/transport/records/${row.id}`).subscribe({
      next: (full) => (this.selected = full),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load record'),
    });
  }

  act(action: 'APPROVE' | 'REJECT' | 'REQUEST_INFO'): void {
    if (!this.selectedId) return;
    this.submitting = true;
    this.error = '';
    this.api
      .post<any>(`/api/transport/records/${this.selectedId}/actions`, {
        action,
        comment: this.actionComment || undefined,
      })
      .subscribe({
        next: (row) => {
          this.submitting = false;
          this.selected = row;
          this.actionComment = '';
          this.statusMsg = `Request updated — ${this.recordStatusLabel(row.status)}.`;
          this.loadRecords();
        },
        error: (err) => {
          this.submitting = false;
          this.error = err?.error?.message ?? 'Action failed';
        },
      });
  }

  answerEntries(answers: Record<string, unknown> | null | undefined): Array<{
    label: string;
    value: string;
  }> {
    if (!answers) return [];
    return Object.entries(answers)
      .filter(([, value]) => value !== null && value !== undefined && value !== '')
      .map(([key, value]) => ({
        label:
          this.fields.find((field) => field.key === key)?.label ||
          key
            .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
            .replace(/[_-]+/g, ' ')
            .replace(/^./, (character) => character.toUpperCase()),
        value: typeof value === 'boolean' ? (value ? 'Yes' : 'No') : String(value),
      }));
  }

  fmtDate(value: string | null | undefined): string {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return date.toLocaleDateString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  }

  latestApproveDelivery(): any[] {
    const intents = this.selected?.notificationIntents ?? [];
    for (let index = intents.length - 1; index >= 0; index--) {
      if (intents[index]?.intent === 'TRANSPORT_APPROVED') {
        return intents[index].delivery ?? [];
      }
    }
    return [];
  }

  private normalizeAnswers(): Record<string, unknown> {
    const normalized: Record<string, unknown> = {};
    for (const field of this.fields) {
      let value = this.answers[field.key];
      if (field.type === 'NUMBER' && value !== '' && value != null) value = Number(value);
      if (field.type === 'CHECKBOX') value = !!value;
      normalized[field.key] = value;
    }
    return normalized;
  }

  private resetWorkflowAnswers(): void {
    for (const field of this.fields) {
      this.answers[field.key] =
        field.type === 'CHECKBOX' ? false : field.type === 'NUMBER' ? 0 : '';
    }
  }

  private setAnswer(keys: string[], value: unknown): void {
    const targets = new Set(keys.map((key) => this.normalizeKey(key)));
    for (const field of this.fields) {
      if (targets.has(this.normalizeKey(field.key))) this.answers[field.key] = value;
    }
  }

  private normalizeKey(key: string): string {
    return key.replace(/[^a-z0-9]/gi, '').toLowerCase();
  }

  private extractFields(
    form: any,
  ): Array<{ key: string; label: string; type: string; mandatory: boolean }> {
    const fields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
    for (const section of form?.sections ?? []) {
      for (const field of section.fields ?? []) {
        fields.push({
          key: field.key,
          label: field.label ?? field.key,
          type: field.type ?? 'TEXTBOX',
          mandatory: !!field.mandatory,
        });
      }
    }
    return fields;
  }
}
