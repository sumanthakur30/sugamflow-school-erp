import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiService, PageResult } from '../../core/api.service';
import { ListToolbarComponent } from '../../shared/list-toolbar/list-toolbar.component';
import { ListPagerComponent } from '../../shared/list-toolbar/list-pager.component';
import {
  ListSortOption,
  ListStatusOption,
  headerSortIndicator,
  nextHeaderSort,
  pageMeta,
  sortRows,
} from '../../shared/list-toolbar/list-controls';
import { parseListViewParams } from '../../shared/list-toolbar/list-view-route';

@Component({
  selector: 'sf-students',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ListToolbarComponent, ListPagerComponent],
  templateUrl: './students.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/list-toolbar.component.scss',
    '../../shared/list-toolbar/sortable-table.scss',
    './students.component.scss',
  ],
})
export class StudentsComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private routeSub?: Subscription;

  loading = true;
  error = '';
  statusMsg = '';
  featureEnabled = false;
  formKey = 'student_master';
  parentFormKey = 'parent_master';
  guardiansAnswerKey = 'guardians';
  parentFields: Array<{ key: string; label: string; type: string; mandatory: boolean }> = [];
  masterFields: Array<{
    key: string;
    label: string;
    type: string;
    mandatory: boolean;
    sectionId?: string;
  }> = [];
  formSections: Array<{ id: string; title: string }> = [];
  activeEditSectionId = '';
  students: any[] = [];
  selected: any = null;
  selectedId: string | null = null;
  guardianDraft: Record<string, unknown> = {};
  savingGuardians = false;

  editOpen = false;
  editAnswers: Record<string, unknown> = {};
  editReason = '';
  savingProfile = false;
  readonly houseOptions = ['Red', 'Blue', 'Green', 'Yellow'];

  deleteOpen = false;
  deleteReason = '';
  restoreReason = '';
  statusDraft = '';
  busyAction = false;

  auditItems: any[] = [];
  auditLoading = false;
  showAudit = false;
  documents: any[] = [];
  documentsLoading = false;
  attachments: any[] = [];
  attachmentsLoading = false;
  uploadingAttachment = false;
  identity = {
    enableAadhaar: true,
    enablePen: true,
    enableApaar: true,
    enableSamagra: false,
    enableSchoolStudentId: true,
    maskAadhaar: true,
    enableStudentPhoto: true,
    enableGuardianPhoto: true,
    enableDocumentVault: true,
    maxPhotoKb: 512,
    maxDocumentKb: 2048,
  };
  readonly defaultGrades = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10'];
  readonly defaultSections = ['A', 'B', 'C'];
  editClassGrade = '';
  editClassSection = '';
  readonly documentTypeOptions = [
    { value: 'BIRTH_CERTIFICATE', label: 'Birth Certificate' },
    { value: 'TRANSFER_CERTIFICATE', label: 'Transfer Certificate' },
    { value: 'PREVIOUS_MARKSHEET', label: 'Previous Marksheet' },
    { value: 'AADHAAR_COPY', label: 'Aadhaar Copy' },
    { value: 'INCOME_CERTIFICATE', label: 'Income Certificate' },
    { value: 'CASTE_CERTIFICATE', label: 'Caste Certificate' },
    { value: 'MEDICAL_CERTIFICATE', label: 'Medical Certificate' },
    { value: 'PASSPORT_PHOTO', label: 'Passport Photo' },
    { value: 'OTHER', label: 'Other' },
  ];
  readonly guardianPhotoSlots = [
    { type: 'FATHER_PHOTO', label: 'Father photo' },
    { type: 'MOTHER_PHOTO', label: 'Mother photo' },
    { type: 'GUARDIAN_PHOTO', label: 'Guardian photo' },
  ];
  private readonly photoAttachmentTypes = new Set([
    'STUDENT_PHOTO',
    'FATHER_PHOTO',
    'MOTHER_PHOTO',
    'GUARDIAN_PHOTO',
  ]);
  documentUploadType = 'BIRTH_CERTIFICATE';

  listQ = '';
  listStatus = '';
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
  readonly sortOptions: ListSortOption[] = [
    { key: 'updatedAt', label: 'Updated' },
    { key: 'fullName', label: 'Student Name' },
    { key: 'admissionNo', label: 'Admission No' },
    { key: 'classApplied', label: 'Class' },
    { key: 'status', label: 'Status' },
  ];
  readonly statusOptions: ListStatusOption[] = [
    { value: 'ACTIVE', label: 'ACTIVE' },
    { value: 'TRANSFERRED', label: 'TRANSFERRED' },
    { value: 'INACTIVE', label: 'INACTIVE' },
  ];

  /** Preferred student answer keys for structured detail (order matters). */
  private readonly preferredAnswerKeys = [
    'fullName',
    'admissionNo',
    'rollNo',
    'classApplied',
    'house',
    'age',
    'gender',
    'dateOfBirth',
    'mobile',
    'email',
    'aadhaar',
    'penNumber',
    'apaarId',
    'samagraId',
    'schoolStudentId',
    'fatherName',
    'parentName',
    'address',
    'bloodGroup',
  ];

  /**
   * Extra profile fields shown in Edit even when the configured student form
   * only has a subset (e.g. form may omit fatherName while the profile displays it).
   */
  private readonly editableExtraKeys = [
    'gender',
    'fatherName',
    'parentName',
    'rollNo',
    'house',
    'category',
    'classSection',
    'dateOfBirth',
    'address',
    'bloodGroup',
    'aadhaar',
    'penNumber',
    'apaarId',
    'samagraId',
    'schoolStudentId',
  ];
  readonly genderOptions = ['Male', 'Female', 'Other'];
  houseInvalid = false;

  ngOnInit(): void {
    this.routeSub = this.route.queryParamMap.subscribe((params) => this.syncFromRoute(params));
    this.reload();
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.busyAction || this.savingProfile || this.savingGuardians) return;
    if (this.deleteOpen) {
      this.deleteOpen = false;
      return;
    }
    if (this.editOpen) {
      this.cancelEdit();
      return;
    }
    if (this.selected) {
      this.closeDetail();
    }
  }

  private syncFromRoute(params: import('@angular/router').ParamMap): void {
    const { mode, id } = parseListViewParams(params);
    if (mode === 'detail' && id) {
      if (this.selectedId !== id || !this.selected) {
        this.loadDetail(id);
      }
      return;
    }
    this.selected = null;
    this.selectedId = null;
    this.editOpen = false;
    this.deleteOpen = false;
    this.showAudit = false;
    this.resetGuardianDraft();
    this.statusMsg = '';
  }

  private loadDetail(id: string): void {
    this.selectedId = id;
    this.error = '';
    this.statusMsg = '';
    this.editOpen = false;
    this.deleteOpen = false;
    this.api.get<any>(`/api/student/students/${id}`).subscribe({
      next: (full) => {
        this.selected = full;
        this.selectedId = full?.id ?? id;
        this.statusDraft = String(full?.status || 'ACTIVE');
        this.resetGuardianDraft();
        this.loadDocuments();
        this.loadAttachments();
        if (this.showAudit) {
          this.loadAudit();
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load student'),
    });
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/student/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.formKey = boot.formKey;
        this.parentFormKey = boot.parentFormKey ?? 'parent_master';
        this.guardiansAnswerKey = boot.guardiansAnswerKey ?? 'guardians';
        this.parentFields = this.extractFields(boot.parentForm);
        this.formSections = this.extractSections(boot.form);
        this.masterFields = this.extractFields(boot.form).filter(
          (f) => f.key !== this.guardiansAnswerKey && f.key !== 'guardians',
        );
        const visible = this.visibleEditSections();
        this.activeEditSectionId = visible[0]?.id || '';
        if (boot.identity && typeof boot.identity === 'object') {
          this.identity = { ...this.identity, ...boot.identity };
        }
        this.resetGuardianDraft();
        this.loading = false;
        this.loadStudents();
      },
      error: (err) => {
        this.loading = false;
        // Keep featureEnabled unchanged on transport errors so we don't imply FEATURE_STUDENT_MASTER is off.
        const status = err?.status;
        const detail = err?.error?.message ?? err?.message ?? 'Student bootstrap failed';
        this.error =
          status === 503 || status === 0
            ? `Student service unavailable (${status || 'network'}). ${detail}`
            : detail;
      },
    });
  }

  loadStudents(): void {
    this.searching = true;
    this.api
      .getPage<any>('/api/student/students', this.pageIndex, this.pageSize, {
        q: this.listQ || undefined,
      })
      .subscribe({
        next: (p) => {
          let items = p.items || [];
          if (this.listQ?.trim()) {
            const q = this.listQ.trim().toLowerCase();
            items = items.filter((row) => {
              const name = String(row.answers?.fullName ?? '').toLowerCase();
              const admissionNo = String(row.admissionNo ?? '').toLowerCase();
              const status = String(row.status ?? '').toLowerCase();
              const klass = String(row.answers?.classApplied ?? '').toLowerCase();
              return (
                name.includes(q) ||
                admissionNo.includes(q) ||
                status.includes(q) ||
                klass.includes(q)
              );
            });
          }
          if (this.listStatus) {
            const st = this.listStatus.toUpperCase();
            items = items.filter((row) => String(row.status || '').toUpperCase() === st);
          }
          items = sortRows(items, this.sortBy, this.sortDir, (row, key) => {
            if (key === 'fullName') return row.answers?.fullName;
            if (key === 'classApplied') return row.answers?.classApplied;
            if (key === 'updatedAt') return row.updatedAt || row.createdAt;
            return row?.[key];
          });
          this.page = { ...p, items };
          this.students = items;
          this.searching = false;
        },
        error: (err) => {
          this.searching = false;
          this.error = err?.error?.message ?? 'Failed to load students';
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

  get listClearEnabled(): boolean {
    return (
      !!this.listQ ||
      !!this.listStatus ||
      this.sortBy !== 'updatedAt' ||
      this.sortDir !== 'DESC' ||
      this.pageSize !== 50
    );
  }

  clearListFilters(): void {
    this.listQ = '';
    this.listStatus = '';
    this.sortBy = 'updatedAt';
    this.sortDir = 'DESC';
    this.pageSize = 50;
    this.pageIndex = 0;
    this.loadStudents();
  }

  onPageChange(index: number): void {
    this.pageIndex = index;
    this.loadStudents();
  }

  isSortableColumn(key: string): boolean {
    return this.sortOptions.some((option) => option.key === key);
  }

  sortByColumn(key: string): void {
    if (!this.isSortableColumn(key)) {
      return;
    }
    const next = nextHeaderSort(this.sortBy, this.sortDir, key);
    this.sortBy = next.sortBy;
    this.sortDir = next.sortDir;
    this.pageIndex = 0;
    this.loadStudents();
  }

  sortIndicator(key: string): string {
    return headerSortIndicator(this.sortBy, this.sortDir, key);
  }

  select(row: any): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { id: row.id },
    });
  }

  closeDetail(): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  guardians(): any[] {
    const fromDto = this.selected?.guardians;
    if (Array.isArray(fromDto)) {
      return fromDto;
    }
    const fromAnswers = this.selected?.answers?.[this.guardiansAnswerKey];
    return Array.isArray(fromAnswers) ? fromAnswers : [];
  }

  /** Structured student fields for the detail page (skips guardian blob). */
  studentFields(): Array<{ key: string; label: string; value: string }> {
    const answers = this.selected?.answers ?? {};
    // Skip hero duplicates + class part keys that only clutter the grid.
    const skip = new Set([
      this.guardiansAnswerKey,
      'guardians',
      'classGrade',
      'sectionLetter',
      'fullName',
      'admissionNo',
      'classApplied',
      'classSection',
      'house',
    ]);
    const out: Array<{ key: string; label: string; value: string }> = [];
    const seen = new Set<string>();

    const push = (key: string) => {
      if (seen.has(key) || skip.has(key)) return;
      const raw = answers[key];
      if (raw == null || typeof raw === 'object') return;
      const value =
        key === 'classApplied'
          ? this.formatGrade(raw)
          : key === 'classSection'
            ? this.formatClass(raw)
            : key === 'aadhaar' || key === 'aadhaarNumber'
              ? this.displayAadhaar(raw)
              : this.formatAnswerValue(raw);
      if (value === '—') return;
      seen.add(key);
      out.push({ key, label: this.prettyLabel(key), value });
    };

    for (const key of this.preferredAnswerKeys) {
      push(key);
    }
    for (const key of Object.keys(answers)) {
      push(key);
    }

    // Surface names from linked guardians when flat profile fields are empty.
    if (!seen.has('fatherName')) {
      const father = this.guardianNameByRelation('father', 'dad', 'papa');
      if (father) {
        seen.add('fatherName');
        out.push({ key: 'fatherName', label: this.prettyLabel('fatherName'), value: father });
      }
    }
    if (!seen.has('parentName')) {
      const parent =
        this.primaryGuardianName() ||
        this.guardianNameByRelation('father', 'parent') ||
        this.guardianNameByRelation('mother');
      if (parent) {
        seen.add('parentName');
        out.push({ key: 'parentName', label: this.prettyLabel('parentName'), value: parent });
      }
    }

    return out;
  }

  /** Profile body groups — one job per section. */
  profileFieldGroups(): Array<{
    id: string;
    title: string;
    fields: Array<{ key: string; label: string; value: string }>;
  }> {
    const all = this.studentFields();
    const buckets: Record<string, Set<string>> = {
      personal: new Set(['gender', 'dateOfBirth', 'age', 'bloodGroup', 'category']),
      contact: new Set(['mobile', 'email', 'address']),
      family: new Set(['fatherName', 'parentName', 'motherName']),
      ids: new Set([
        'aadhaar',
        'aadhaarNumber',
        'penNumber',
        'apaarId',
        'samagraId',
        'schoolStudentId',
        'rollNo',
      ]),
    };
    const used = new Set<string>();
    const pick = (keys: Set<string>) => {
      const fields = all.filter((f) => keys.has(f.key));
      fields.forEach((f) => used.add(f.key));
      return fields;
    };
    const groups = [
      { id: 'personal', title: 'Personal', fields: pick(buckets['personal']) },
      { id: 'contact', title: 'Contact', fields: pick(buckets['contact']) },
      { id: 'family', title: 'Family', fields: pick(buckets['family']) },
      { id: 'ids', title: 'IDs & records', fields: pick(buckets['ids']) },
    ];
    const other = all.filter((f) => !used.has(f.key));
    if (other.length) {
      groups.push({ id: 'other', title: 'Other', fields: other });
    }
    return groups.filter((g) => g.fields.length > 0);
  }

  displayHouse(row: any): string {
    const h = String(row?.answers?.house ?? '').trim();
    return h || '';
  }

  houseTone(house: string): string {
    const h = house.trim().toLowerCase();
    if (h === 'red') return 'house-red';
    if (h === 'blue') return 'house-blue';
    if (h === 'green') return 'house-green';
    if (h === 'yellow') return 'house-yellow';
    return 'house-neutral';
  }

  studentInitials(row: any): string {
    const name = this.studentName(row).trim();
    if (!name || name === '—') return '?';
    const parts = name.split(/\s+/).filter(Boolean);
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  issuedDocuments(): any[] {
    return (this.documents || []).filter((d) => String(d?.status || '').toUpperCase() === 'ISSUED');
  }

  addGuardian(): void {
    if (!this.selected?.id || this.savingGuardians) {
      return;
    }
    const draft = this.normalizeGuardian(this.guardianDraft);
    const name = String(draft['fullName'] ?? '').trim();
    if (!name) {
      this.error = 'Guardian full name is required.';
      return;
    }
    this.error = '';
    const next = [...this.guardians(), draft];
    this.saveGuardians(next, true);
  }

  removeGuardian(index: number): void {
    if (!this.selected?.id || this.savingGuardians || this.selected?.deleted) {
      return;
    }
    const next = this.guardians().filter((_: any, i: number) => i !== index);
    this.saveGuardians(next, false);
  }

  /** Fields available in the Edit form (configured form + common profile extras). */
  editFields(): Array<{
    key: string;
    label: string;
    type: string;
    mandatory: boolean;
    sectionId?: string;
  }> {
    const seen = new Set(this.masterFields.map((f) => f.key));
    const extras: Array<{
      key: string;
      label: string;
      type: string;
      mandatory: boolean;
      sectionId?: string;
    }> = [];
    for (const key of this.editableExtraKeys) {
      if (seen.has(key)) continue;
      if (this.isClassPartField(key)) continue;
      seen.add(key);
      extras.push({
        key,
        label: this.prettyLabel(key),
        type:
          key === 'hostel' || key === 'transport' || key === 'scholarship'
            ? 'CHECKBOX'
            : key === 'gender'
              ? 'DROPDOWN'
              : key === 'house'
                ? 'DROPDOWN'
                : 'TEXTBOX',
        mandatory: key === 'house',
        sectionId: '_more',
      });
    }
    return [...this.masterFields, ...extras];
  }

  selectEditSection(id: string): void {
    this.activeEditSectionId = id;
  }

  visibleEditSections(): Array<{ id: string; title: string }> {
    const sections = this.formSections.filter((s) => this.editFieldsForSection(s.id).length > 0);
    if (this.editFieldsForSection('_more').length > 0) {
      sections.push({ id: '_more', title: 'More' });
    }
    return sections;
  }

  editFieldsForSection(sectionId: string): Array<{
    key: string;
    label: string;
    type: string;
    mandatory: boolean;
    sectionId?: string;
  }> {
    return this.editFields().filter((f) => {
      if (this.isClassPartField(f.key)) return false;
      const sid = f.sectionId || 'main';
      return sid === sectionId;
    });
  }

  isClassPartField(key: string): boolean {
    const normalized = key.replace(/[^a-z0-9]/gi, '').toLowerCase();
    return (
      normalized === 'classgrade' ||
      normalized === 'grade' ||
      normalized === 'sectionletter' ||
      normalized === 'section' ||
      normalized === 'sectionname'
    );
  }

  startEdit(): void {
    if (!this.selected || this.selected.deleted) return;
    const draft: Record<string, unknown> = { ...(this.selected.answers || {}) };
    if (this.selected.admissionNo && draft['admissionNo'] == null) {
      draft['admissionNo'] = this.selected.admissionNo;
    }
    for (const f of this.editFields()) {
      if (draft[f.key] === undefined) {
        draft[f.key] = f.type === 'CHECKBOX' ? false : f.type === 'NUMBER' ? 0 : '';
      }
    }
    this.hydrateProfileNamesFromGuardians(draft);
    this.hydrateClassPicker(draft);
    this.editAnswers = draft;
    this.editReason = '';
    this.editOpen = true;
    this.error = '';
    const visible = this.visibleEditSections();
    this.activeEditSectionId = visible[0]?.id || '';
  }

  selectEditClassGrade(grade: string): void {
    this.editClassGrade = grade;
    this.syncEditClassApplied();
  }

  selectEditClassSection(section: string): void {
    if (!this.editClassGrade) return;
    this.editClassSection = section;
    this.syncEditClassApplied();
  }

  private syncEditClassApplied(): void {
    if (!this.editClassGrade || !this.editClassSection) {
      return;
    }
    const label = `Grade ${this.editClassGrade}-${this.editClassSection}`;
    this.editAnswers['classApplied'] = label;
    this.editAnswers['classSection'] = label;
    this.editAnswers['classGrade'] = this.editClassGrade;
    this.editAnswers['sectionLetter'] = this.editClassSection;
  }

  private hydrateClassPicker(draft: Record<string, unknown>): void {
    const raw = String(draft['classApplied'] ?? draft['classSection'] ?? '');
    const m = raw.match(/(?:grade\s*)?(\d{1,2})\s*[-–]?\s*([A-Za-z])/i);
    if (m) {
      this.editClassGrade = String(Number(m[1]));
      this.editClassSection = m[2].toUpperCase();
    } else {
      this.editClassGrade = '';
      this.editClassSection = '';
    }
  }

  displayAadhaar(raw: unknown): string {
    const digits = String(raw ?? '').replace(/\D/g, '');
    if (!digits) return '—';
    if (this.identity.maskAadhaar && digits.length >= 4) {
      return `********${digits.slice(-4)}`;
    }
    return digits;
  }

  studentPhotoUrl(): string {
    const answers = this.selected?.answers ?? {};
    const url = String(answers.photoUrl || answers.photo || '').trim();
    if (url.startsWith('/api/')) return url;
    return this.attachmentUrl('STUDENT_PHOTO');
  }

  attachmentFor(type: string): any | null {
    return this.attachments.find((a) => a.attachmentType === type) || null;
  }

  attachmentUrl(type: string): string {
    return this.attachmentFor(type)?.contentUrl || '';
  }

  loadAttachments(): void {
    if (!this.selected?.id) {
      this.attachments = [];
      return;
    }
    this.attachmentsLoading = true;
    this.api.get<any[]>(`/api/student/students/${this.selected.id}/attachments`).subscribe({
      next: (rows) => {
        this.attachments = rows ?? [];
        this.attachmentsLoading = false;
      },
      error: () => {
        this.attachments = [];
        this.attachmentsLoading = false;
      },
    });
  }

  onPhotoSelected(ev: Event, type = 'STUDENT_PHOTO'): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || !this.selected?.id) return;
    const maxKb = this.photoAttachmentTypes.has(type)
      ? this.identity.maxPhotoKb
      : this.identity.maxDocumentKb;
    if (file.size > maxKb * 1024) {
      this.error = `File exceeds ${maxKb} KB limit`;
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = String(reader.result || '');
      const contentBase64 = dataUrl.includes(',') ? dataUrl.split(',')[1] : dataUrl;
      this.uploadAttachment({
        type,
        fileName: file.name,
        contentType: file.type || 'application/octet-stream',
        contentBase64,
      });
    };
    reader.readAsDataURL(file);
  }

  onDocumentSelected(ev: Event): void {
    this.onPhotoSelected(ev, this.documentUploadType || 'OTHER');
  }

  uploadAttachment(body: Record<string, unknown>): void {
    if (!this.selected?.id) return;
    this.uploadingAttachment = true;
    this.error = '';
    this.api.post<any>(`/api/student/students/${this.selected.id}/attachments`, body).subscribe({
      next: () => {
        this.uploadingAttachment = false;
        this.statusMsg = 'Attachment uploaded';
        this.loadAttachments();
        this.loadDetail(this.selected.id);
      },
      error: (err) => {
        this.uploadingAttachment = false;
        this.error = err?.error?.message ?? 'Upload failed';
      },
    });
  }

  removeAttachment(att: any): void {
    if (!att?.id || !confirm(`Remove ${att.fileName || att.attachmentType}?`)) return;
    this.api.delete(`/api/student/attachments/${att.id}`).subscribe({
      next: () => {
        this.statusMsg = 'Attachment removed';
        this.loadAttachments();
        if (this.selected?.id) this.loadDetail(this.selected.id);
      },
      error: (err) => (this.error = err?.error?.message ?? 'Remove failed'),
    });
  }

  attachmentLabel(type: unknown): string {
    const t = String(type || '');
    const found = this.documentTypeOptions.find((o) => o.value === t);
    if (found) return found.label;
    return t.replace(/_/g, ' ');
  }

  cancelEdit(): void {
    this.editOpen = false;
    this.editReason = '';
    this.houseInvalid = false;
  }

  saveEdit(): void {
    if (!this.selected?.id || this.savingProfile) return;
    this.houseInvalid = false;
    const house = String(this.editAnswers['house'] ?? '').trim();
    if (!house) {
      this.houseInvalid = true;
      this.error = 'Select a house before saving the student profile.';
      this.focusHouseField();
      return;
    }
    if (!this.availableHouseOptions().includes(house)) {
      this.houseInvalid = true;
      this.error = 'Select a valid school house.';
      this.focusHouseField();
      return;
    }
    const aadhaar = String(this.editAnswers['aadhaar'] ?? '').replace(/\D/g, '');
    if (aadhaar && aadhaar.length !== 12) {
      this.error = 'Aadhaar must be exactly 12 digits';
      return;
    }
    if (aadhaar) this.editAnswers['aadhaar'] = aadhaar;
    this.syncEditClassApplied();
    this.savingProfile = true;
    this.error = '';
    this.statusMsg = 'Saving student profile…';
    const payload: Record<string, unknown> = {
      answers: this.editAnswers,
      reason: this.editReason?.trim() || 'Student profile correction',
    };
    this.api.put<any>(`/api/student/students/${this.selected.id}`, payload).subscribe({
      next: (full) => {
        this.savingProfile = false;
        this.editOpen = false;
        this.houseInvalid = false;
        this.selected = full;
        this.statusDraft = String(full?.status || 'ACTIVE');
        this.statusMsg = 'Student profile updated.';
        window.alert('Student profile updated successfully.');
        this.loadStudents();
        if (this.showAudit) this.loadAudit();
      },
      error: (err) => {
        this.savingProfile = false;
        this.statusMsg = '';
        this.error = err?.error?.message ?? 'Failed to update student';
        window.alert(this.error);
      },
    });
  }

  /** Jump to the tab that contains House and scroll it into view. */
  private focusHouseField(): void {
    const houseField = this.editFields().find((f) => f.key === 'house');
    const sectionId = houseField?.sectionId || '_more';
    this.activeEditSectionId = sectionId;
    queueMicrotask(() => {
      const el = document.getElementById('edit-house-field');
      el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
  }

  onHouseChange(): void {
    this.houseInvalid = false;
    if (this.error?.includes('house')) {
      this.error = '';
    }
  }

  availableHouseOptions(): string[] {
    const current = String(this.editAnswers['house'] ?? this.selected?.answers?.house ?? '').trim();
    return current && !this.houseOptions.includes(current)
      ? [current, ...this.houseOptions]
      : this.houseOptions;
  }

  openDelete(): void {
    if (!this.selected || this.selected.deleted) return;
    this.deleteReason = '';
    this.deleteOpen = true;
  }

  confirmSoftDelete(): void {
    if (!this.selected?.id || this.busyAction) return;
    const reason = this.deleteReason.trim();
    if (!reason) {
      this.error = 'Deletion reason is required.';
      return;
    }
    this.busyAction = true;
    this.error = '';
    this.api
      .post<any>(`/api/student/students/${this.selected.id}/soft-delete`, { reason })
      .subscribe({
        next: (full) => {
          this.busyAction = false;
          this.deleteOpen = false;
          this.selected = full;
          this.statusMsg = 'Student soft-deleted.';
          this.loadStudents();
        },
        error: (err) => {
          this.busyAction = false;
          this.error = err?.error?.message ?? 'Soft delete failed';
        },
      });
  }

  restoreStudent(): void {
    if (!this.selected?.id || !this.selected.deleted || this.busyAction) return;
    this.busyAction = true;
    this.error = '';
    this.api
      .post<any>(`/api/student/students/${this.selected.id}/restore`, {
        reason: this.restoreReason?.trim() || 'Student restored',
        status: 'ACTIVE',
      })
      .subscribe({
        next: (full) => {
          this.busyAction = false;
          this.selected = full;
          this.statusDraft = String(full?.status || 'ACTIVE');
          this.statusMsg = 'Student restored.';
          this.loadStudents();
        },
        error: (err) => {
          this.busyAction = false;
          this.error = err?.error?.message ?? 'Restore failed';
        },
      });
  }

  applyStatus(): void {
    if (!this.selected?.id || this.selected.deleted || this.busyAction) return;
    const status = String(this.statusDraft || '').trim().toUpperCase();
    if (!status || status === String(this.selected.status || '').toUpperCase()) return;
    this.busyAction = true;
    this.error = '';
    this.api
      .post<any>(`/api/student/students/${this.selected.id}/status`, {
        status,
        reason: `Status set to ${status}`,
      })
      .subscribe({
        next: (full) => {
          this.busyAction = false;
          this.selected = full;
          this.statusDraft = String(full?.status || status);
          this.statusMsg = `Status updated to ${status}.`;
          this.loadStudents();
        },
        error: (err) => {
          this.busyAction = false;
          this.error = err?.error?.message ?? 'Status change failed';
        },
      });
  }

  toggleAudit(): void {
    this.showAudit = !this.showAudit;
    if (this.showAudit) {
      this.loadAudit();
    }
  }

  loadAudit(): void {
    if (!this.selected?.id) return;
    this.auditLoading = true;
    this.api.getPage<any>(`/api/student/students/${this.selected.id}/audit`, 0, 50).subscribe({
      next: (p) => {
        this.auditItems = p.items || [];
        this.auditLoading = false;
      },
      error: () => {
        this.auditLoading = false;
        this.auditItems = [];
      },
    });
  }

  historyChanges(h: any): Array<{ field: string; from: unknown; to: unknown }> {
    return Array.isArray(h?.changes) ? h.changes : [];
  }

  studentName(row: any): string {
    const name = this.answer(row, 'fullName');
    return name === '—' ? String(row?.admissionNo || row?.id || '—') : name;
  }

  answer(row: any, key: string): string {
    const v = row?.answers?.[key] ?? (key === 'admissionNo' ? row?.admissionNo : undefined);
    if (key === 'classApplied') {
      return this.formatClass(v);
    }
    return this.formatAnswerValue(v);
  }

  displayClass(row: any): string {
    return this.formatClass(row?.answers?.classApplied ?? row?.classSection);
  }

  statusClass(status: unknown): string {
    const s = String(status || '')
      .trim()
      .toUpperCase();
    if (s === 'ACTIVE' || s === 'ENROLLED' || s === 'APPROVED' || s === 'PROMOTED')
      return 'badge badge-ok';
    if (
      s === 'INACTIVE' ||
      s === 'WITHDRAWN' ||
      s === 'REJECTED' ||
      s === 'DELETED' ||
      s === 'EXPELLED'
    )
      return 'badge badge-bad';
    if (s === 'PENDING' || s === 'IN_PROGRESS' || s === 'SUSPENDED' || s === 'TRANSFERRED')
      return 'badge badge-progress';
    return 'badge';
  }

  documentLabel(type: unknown): string {
    const t = String(type || '').toUpperCase();
    if (t === 'ID_CARD') return 'ID card';
    if (t === 'BONAFIDE') return 'Bonafide certificate';
    if (t === 'CHARACTER_CERTIFICATE') return 'Character certificate';
    return String(type || 'Document');
  }

  loadDocuments(): void {
    if (!this.selected?.id) {
      this.documents = [];
      return;
    }
    this.documentsLoading = true;
    this.api.get<any[]>(`/api/student/students/${this.selected.id}/documents`).subscribe({
      next: (rows) => {
        this.documents = rows ?? [];
        this.documentsLoading = false;
      },
      error: () => {
        this.documents = [];
        this.documentsLoading = false;
      },
    });
  }

  issueDocument(type: 'ID_CARD' | 'BONAFIDE' | 'CHARACTER_CERTIFICATE'): void {
    if (!this.selected?.id || this.busyAction || this.selected.deleted) {
      return;
    }
    this.busyAction = true;
    this.error = '';
    this.statusMsg = `Issuing ${this.documentLabel(type)}…`;
    this.api.post<any>(`/api/student/students/${this.selected.id}/documents`, { type }).subscribe({
      next: (doc) => {
        this.busyAction = false;
        this.statusMsg = `${this.documentLabel(type)} issued (${doc.referenceNo}).`;
        this.loadDocuments();
        if (doc?.id) {
          this.downloadDocument(doc);
        }
      },
      error: (err) => {
        this.busyAction = false;
        this.statusMsg = '';
        this.error = err?.error?.message ?? 'Document issue failed';
      },
    });
  }

  downloadDocument(doc: any): void {
    if (!doc?.id) return;
    this.api.getBlob(`/api/student/documents/${doc.id}/pdf`).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = doc.fileName || `student-document-${doc.id}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => (this.error = err?.error?.message ?? 'PDF download failed'),
    });
  }

  revokeDocument(doc: any): void {
    if (!doc?.id || this.busyAction) return;
    this.busyAction = true;
    this.api
      .post<any>(`/api/student/documents/${doc.id}/revoke`, { reason: 'Revoked from student profile' })
      .subscribe({
        next: () => {
          this.busyAction = false;
          this.statusMsg = `Document ${doc.referenceNo} revoked.`;
          this.loadDocuments();
        },
        error: (err) => {
          this.busyAction = false;
          this.error = err?.error?.message ?? 'Revoke failed';
        },
      });
  }

  formatWhen(raw: unknown): string {
    if (!raw) return '—';
    const d = new Date(String(raw));
    if (Number.isNaN(d.getTime())) return String(raw);
    return d.toLocaleString(undefined, {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  private saveGuardians(guardians: any[], clearDraft: boolean): void {
    this.savingGuardians = true;
    this.error = '';
    this.statusMsg = 'Saving guardians…';
    this.api
      .put<any>(`/api/student/students/${this.selected.id}/guardians`, { guardians })
      .subscribe({
        next: (full) => {
          this.savingGuardians = false;
          this.selected = full;
          this.statusMsg = clearDraft ? 'Guardian added.' : 'Guardian removed.';
          if (clearDraft) {
            this.resetGuardianDraft();
          }
          this.loadStudents();
        },
        error: (err) => {
          this.savingGuardians = false;
          this.statusMsg = '';
          this.error = err?.error?.message ?? 'Failed to save guardians';
        },
      });
  }

  private resetGuardianDraft(): void {
    const draft: Record<string, unknown> = {};
    for (const f of this.parentFields) {
      draft[f.key] = f.type === 'CHECKBOX' ? false : '';
    }
    if (draft['isPrimary'] === false && this.guardians().length === 0) {
      draft['isPrimary'] = true;
    }
    this.guardianDraft = draft;
  }

  /** Prefer linked Father guardian when the flat fatherName answer was never filled. */
  private hydrateProfileNamesFromGuardians(draft: Record<string, unknown>): void {
    const blank = (key: string) => !String(draft[key] ?? '').trim();
    if (blank('fatherName')) {
      const father = this.guardianNameByRelation('father', 'dad', 'papa');
      if (father) draft['fatherName'] = father;
    }
    if (blank('motherName')) {
      const mother = this.guardianNameByRelation('mother', 'mom', 'mummy');
      if (mother) draft['motherName'] = mother;
    }
    if (blank('parentName')) {
      const parent =
        this.primaryGuardianName() ||
        this.guardianNameByRelation('father', 'parent') ||
        this.guardianNameByRelation('mother');
      if (parent) draft['parentName'] = parent;
    }
  }

  private primaryGuardianName(): string {
    const primary = this.guardians().find((g) => !!g?.isPrimary) || this.guardians()[0];
    return String(primary?.fullName || primary?.name || '').trim();
  }

  private guardianNameByRelation(...patterns: string[]): string {
    for (const g of this.guardians()) {
      const relation = String(g?.relation || '').toLowerCase();
      if (!relation) continue;
      if (patterns.some((p) => relation.includes(p))) {
        const name = String(g?.fullName || g?.name || '').trim();
        if (name) return name;
      }
    }
    return '';
  }

  private normalizeGuardian(raw: Record<string, unknown>): Record<string, unknown> {
    const out: Record<string, unknown> = {};
    for (const f of this.parentFields) {
      let v = raw[f.key];
      if (f.type === 'CHECKBOX') {
        v = !!v;
      }
      out[f.key] = v;
    }
    return out;
  }

  private formatAnswerValue(v: unknown): string {
    if (v === true) return 'Yes';
    if (v === false) return 'No';
    if (v == null || String(v).trim() === '') return '—';
    return String(v);
  }

  /** Hide smoke-test class labels (ReportCard-…, Gradebook-…, etc.). */
  private formatClass(raw: unknown): string {
    const s = raw == null ? '' : String(raw).trim();
    if (!s) return '—';
    if (/^(ReportCard|Gradebook|Alert|Roster)-\d{10,}$/i.test(s)) {
      return '—';
    }
    return s;
  }

  /**
   * Grade level without the section suffix, e.g. "Grade 8-A" -> "Grade 8".
   * Used for the "Class" field so it does not duplicate "Class section".
   */
  private formatGrade(raw: unknown): string {
    const s = this.formatClass(raw);
    if (s === '—') return s;
    const match = s.match(/^(.*?)[\s\-/]+([A-Za-z]|\d{1,2})$/);
    return match ? match[1].trim() : s;
  }

  private prettyLabel(key: string): string {
    const map: Record<string, string> = {
      fullName: 'Full name',
      admissionNo: 'Admission No',
      classApplied: 'Class',
      classSection: 'Class section',
      dateOfBirth: 'Date of birth',
      bloodGroup: 'Blood group',
      house: 'House',
      gender: 'Gender',
      category: 'Category',
      fatherName: 'Father name',
      parentName: 'Parent / mother name',
      rollNo: 'Roll no',
      aadhaar: 'Aadhaar',
      penNumber: 'PEN number',
      apaarId: 'APAAR ID',
      samagraId: 'Samagra ID',
      schoolStudentId: 'School student ID',
    };
    if (map[key]) return map[key];
    return key
      .replace(/([A-Z])/g, ' $1')
      .replace(/^./, (c) => c.toUpperCase())
      .trim();
  }

  private extractSections(form: any): Array<{ id: string; title: string }> {
    const sections = form?.sections ?? [];
    return sections.map((section: any, index: number) => ({
      id: String(section.id ?? `section-${index}`),
      title: String(section.title ?? `Section ${index + 1}`),
    }));
  }

  private extractFields(
    form: any,
  ): Array<{ key: string; label: string; type: string; mandatory: boolean; sectionId?: string }> {
    const fields: Array<{
      key: string;
      label: string;
      type: string;
      mandatory: boolean;
      sectionId?: string;
    }> = [];
    const sections = form?.sections ?? [];
    sections.forEach((section: any, index: number) => {
      const sectionId = String(section.id ?? `section-${index}`);
      for (const field of section.fields ?? []) {
        fields.push({
          key: field.key,
          label: field.label ?? field.key,
          type: String(field.type ?? 'TEXTBOX').toUpperCase(),
          mandatory: !!field.mandatory,
          sectionId,
        });
      }
    });
    return fields;
  }
}
