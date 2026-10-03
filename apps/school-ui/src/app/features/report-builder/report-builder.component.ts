import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription, catchError, firstValueFrom, map, of, timeout } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { TenantContextService } from '../../core/tenant-context.service';
import { ModuleBootstrapService } from '../../core/module-bootstrap.service';

export interface ReportElement {
  id: string;
  type: string;
  x: number;
  y: number;
  width: number;
  height: number;
  text?: string;
  bind?: string;
  fontSize?: number;
  align?: string;
  bold?: boolean;
  objectFit?: 'cover' | 'contain' | string;
  fallbackSrc?: string;
  borderWidth?: number;
  borderColor?: string;
  borderRadius?: number;
  fillColor?: string;
  color?: string;
  quietZone?: number;
  z?: number;
}

const SAMPLE_PHOTO_DATA_URL =
  'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADAAAABACAYAAABcIPRGAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAFQSURBVGhD7dQxDsIwDEZhDssRuAVHZGXmCqAMldBrVduJG6eNh29Bavy/hdvr/fme2Y0/nE0GRMuAaBkQLQPo/niK+E0LtwCO1OAbNVwCOMyCb1k1B3BQDb5p0RTAIS34tlZ1AAd44A2NOQN42BNvSTLAG29J5gvgQW+8JzEHFDzqibckGeCNtyRzBhQ87IE3NOYNKDigBd/WagooOKQG37RoDig4yIJvWbkEFBymwTdquAUsOHILv2nhHtBbBkTLgGgZEM0lgP/zFnzLqjqAQzzwhoY5gEePwJt71AE80gM3bFEF8OGeuIXEAD4YgZvUAXwoEreJAXxgBNx43QB+OBJuzYDeuHUVwA9Gw72rgNEjuDUDeuPWzYBRI7jx2gGjRXCbKmCUCG4yBURHcAupAgo+3AM3bFEHLHjkCLy5xxyw4FEPvKFRHfCPQyz4ltUP8fzWUNoQNcsAAAAASUVORK5CYII=';

export interface ReportTemplate {
  templateKey: string;
  name?: string;
  organizationId?: string;
  layout?: { width: number; height: number; units?: string; paper?: string };
  elements: ReportElement[];
  charts?: unknown[];
  filters?: unknown[];
  calculatedFields?: unknown[];
  schedule?: unknown;
}

export interface IdCardStudent {
  name: string;
  grade: string;
  section: string;
  admissionNo: string;
  dob: string;
  bloodGroup: string;
  mobileNo: string;
  emergencyNo: string;
  apaarId: string;
  pen: string;
}

interface PersonHit {
  id: string;
  fullName?: string;
  admissionNo?: string;
  classSection?: string;
  fatherName?: string;
  parentName?: string;
  penNumber?: string;
  photoUrl?: string;
  employeeNo?: string;
  designation?: string;
  department?: string;
}

interface ClassOption {
  id: string;
  name: string;
}

interface SectionOption {
  id: string;
  classId: string;
  name: string;
  studentLabel: string;
}

interface SessionOption {
  id: string;
  label: string;
}

interface BulkStudent {
  id: string;
  name: string;
  admissionNo: string;
  classSection: string;
  penNumber: string;
  photoUrl: string;
  selected: boolean;
  missingPhoto: boolean;
  missingAdmission: boolean;
  missingPen: boolean;
}

@Component({
  selector: 'sf-report-builder',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './report-builder.component.html',
  styleUrls: ['../../shared/admin-page.scss', './report-builder.component.scss'],
})
export class ReportBuilderComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly tenantContext = inject(TenantContextService);
  private readonly modules = inject(ModuleBootstrapService);
  private readonly route = inject(ActivatedRoute);
  private campusReadySub?: Subscription;

  loading = true;
  featureEnabled = false;
  error = '';
  status = '';
  busy = false;

  templates: ReportTemplate[] = [];
  elementTypes: Array<{
    type: string;
    label: string;
    defaultWidth: number;
    defaultHeight: number;
    hasText: boolean;
  }> = [];
  samplePreviewData: Record<string, unknown> = {};
  formats: string[] = [];

  selectedKey = '';
  draft: ReportTemplate | null = null;
  selectedId: string | null = null;
  canvasScale = 0.72;

  /** Runtime preview only. Never written back onto template elements. */
  previewContext: Record<string, unknown> | null = null;
  previewLabel = '';
  photoWarning = '';
  personQuery = '';
  searchHits: PersonHit[] = [];
  searchOpen = false;
  searchBusy = false;
  filterClassId = '';
  filterSectionId = '';
  classes: ClassOption[] = [];
  sections: SectionOption[] = [];
  sessions: SessionOption[] = [];

  bulkOpen = false;
  bulkSessionId = '';
  bulkClassId = '';
  bulkSectionId = '';
  bulkStudents: BulkStudent[] = [];
  bulkMissingOnly = false;
  bulkAllowIncomplete = false;
  bulkNote = '';

  private dragPaletteType: string | null = null;
  private moveState: {
    id: string;
    startX: number;
    startY: number;
    origX: number;
    origY: number;
  } | null = null;
  private searchTimer?: ReturnType<typeof setTimeout>;
  private searchSub?: Subscription;

  ngOnInit(): void {
    this.campusReadySub = this.tenantContext.whenCampusReady().subscribe(() => this.bootstrap());
  }

  ngOnDestroy(): void {
    this.campusReadySub?.unsubscribe();
    this.searchSub?.unsubscribe();
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
  }

  bootstrap(): void {
    this.loading = true;
    this.error = '';
    const path = '/api/reports/bootstrap';
    const apply = (boot: any) => {
      this.featureEnabled = !!boot.featureEnabled;
      this.elementTypes = boot.elementTypes ?? [];
      this.samplePreviewData = boot.samplePreviewData ?? {};
      this.ensureSamplePhoto();
      this.formats = boot.exportFormats ?? [];
      this.templates = boot.templates ?? [];
      this.loading = false;
      if (this.featureEnabled) {
        this.loadCatalogs();
      }
      if (this.featureEnabled && this.templates.length) {
        const requested = this.route.snapshot.queryParamMap.get('template');
        const match = this.templates.find((t) => t.templateKey === requested);
        this.selectTemplate(match?.templateKey || this.templates[0].templateKey);
      }
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
        this.error = ModuleBootstrapService.errorMessage(err, 'Report bootstrap failed');
        if (ModuleBootstrapService.isFeatureDisabled(err)) {
          this.featureEnabled = false;
        } else {
          // Do NOT imply plan feature is off on workflow/form/network errors
          this.featureEnabled = true;
        }
      },
    });
  }

  selectTemplate(key: string): void {
    this.selectedKey = key;
    this.selectedId = null;
    this.status = '';
    this.clearPreviewPerson();
    this.api.get<ReportTemplate>(`/api/reports/templates/${key}`).subscribe({
      next: (t) => {
        this.draft = this.cloneTemplate(t);
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load template'),
    });
  }

  get selected(): ReportElement | null {
    if (!this.draft || !this.selectedId) {
      return null;
    }
    return this.draft.elements.find((e) => e.id === this.selectedId) ?? null;
  }

  get canvasWidth(): number {
    return this.draft?.layout?.width ?? 794;
  }

  get canvasHeight(): number {
    return this.draft?.layout?.height ?? 1123;
  }

  onPaletteDragStart(type: string, ev: DragEvent): void {
    this.dragPaletteType = type;
    ev.dataTransfer?.setData('text/plain', type);
    if (ev.dataTransfer) {
      ev.dataTransfer.effectAllowed = 'copy';
    }
  }

  onCanvasDragOver(ev: DragEvent): void {
    ev.preventDefault();
    if (ev.dataTransfer) {
      ev.dataTransfer.dropEffect = 'copy';
    }
  }

  onCanvasDrop(ev: DragEvent): void {
    ev.preventDefault();
    if (!this.draft) {
      return;
    }
    const type = this.dragPaletteType || ev.dataTransfer?.getData('text/plain');
    this.dragPaletteType = null;
    if (!type) {
      return;
    }
    const rect = (ev.currentTarget as HTMLElement).getBoundingClientRect();
    const x = Math.max(0, Math.round((ev.clientX - rect.left) / this.canvasScale));
    const y = Math.max(0, Math.round((ev.clientY - rect.top) / this.canvasScale));
    const def = this.elementTypes.find((t) => t.type === type);
    const el: ReportElement = {
      id: crypto.randomUUID(),
      type,
      x,
      y,
      width: def?.defaultWidth ?? 280,
      height: def?.defaultHeight ?? 24,
      fontSize: type === 'heading' ? 18 : 11,
      align: 'left',
      bold: type === 'heading',
      text:
        type === 'heading'
          ? 'Heading'
          : type === 'text'
            ? 'Sample text {{student.name}}'
            : type === 'field'
              ? '{{student.name}}'
              : type === 'image'
                ? '{{student.photoDirectUrl}}'
                : '',
      bind:
        type === 'field'
          ? 'student.name'
          : type === 'image'
            ? 'student.photoDirectUrl'
            : undefined,
      objectFit: type === 'image' ? 'cover' : undefined,
    };
    this.draft.elements = [...this.draft.elements, el];
    this.selectedId = el.id;
  }

  selectElement(el: ReportElement, ev: MouseEvent): void {
    ev.stopPropagation();
    this.selectedId = el.id;
  }

  clearSelection(): void {
    this.selectedId = null;
  }

  onElementPointerDown(el: ReportElement, ev: PointerEvent): void {
    if (!this.draft) {
      return;
    }
    ev.stopPropagation();
    ev.preventDefault();
    this.selectedId = el.id;
    (ev.target as HTMLElement).setPointerCapture?.(ev.pointerId);
    this.moveState = {
      id: el.id,
      startX: ev.clientX,
      startY: ev.clientY,
      origX: el.x,
      origY: el.y,
    };
  }

  onCanvasPointerMove(ev: PointerEvent): void {
    if (!this.moveState || !this.draft) {
      return;
    }
    const el = this.draft.elements.find((e) => e.id === this.moveState!.id);
    if (!el) {
      return;
    }
    const dx = (ev.clientX - this.moveState.startX) / this.canvasScale;
    const dy = (ev.clientY - this.moveState.startY) / this.canvasScale;
    el.x = Math.max(0, Math.round(this.moveState.origX + dx));
    el.y = Math.max(0, Math.round(this.moveState.origY + dy));
  }

  onCanvasPointerUp(): void {
    this.moveState = null;
  }

  deleteSelected(): void {
    if (!this.draft || !this.selectedId) {
      return;
    }
    this.draft.elements = this.draft.elements.filter((e) => e.id !== this.selectedId);
    this.selectedId = null;
  }

  bringForward(): void {
    if (!this.draft || !this.selectedId) {
      return;
    }
    const idx = this.draft.elements.findIndex((e) => e.id === this.selectedId);
    if (idx < 0 || idx >= this.draft.elements.length - 1) {
      return;
    }
    const copy = [...this.draft.elements];
    const [item] = copy.splice(idx, 1);
    copy.splice(idx + 1, 0, item);
    this.draft.elements = copy;
  }

  sendBackward(): void {
    if (!this.draft || !this.selectedId) {
      return;
    }
    const idx = this.draft.elements.findIndex((e) => e.id === this.selectedId);
    if (idx <= 0) {
      return;
    }
    const copy = [...this.draft.elements];
    const [item] = copy.splice(idx, 1);
    copy.splice(idx - 1, 0, item);
    this.draft.elements = copy;
  }

  save(): void {
    if (!this.draft || !this.selectedKey) {
      return;
    }
    this.busy = true;
    this.error = '';
    this.api.put<ReportTemplate>(`/api/reports/templates/${this.selectedKey}`, this.draft).subscribe({
      next: (saved) => {
        this.busy = false;
        this.draft = this.cloneTemplate(saved);
        this.status = `Saved ${this.selectedKey}`;
        this.templates = this.templates.map((t) =>
          t.templateKey === this.selectedKey ? { ...t, name: saved.name } : t,
        );
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Save failed';
      },
    });
  }

  previewPdf(): void {
    if (!this.draft) {
      return;
    }
    const data = this.runtimeData();
    if (!data) {
      return;
    }
    this.busy = true;
    this.error = '';
    this.api
      .post<any>('/api/reports/preview', {
        ...this.draft,
        data,
        format: 'PDF',
      })
      .subscribe({
        next: (res) => {
          this.busy = false;
          this.openPdf(res);
          this.status = this.previewLabel
            ? `Preview PDF opened for ${this.previewLabel}`
            : 'Preview PDF opened';
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Preview failed';
        },
      });
  }

  renderSaved(): void {
    if (!this.selectedKey) {
      return;
    }
    const data = this.runtimeData();
    if (!data) {
      return;
    }
    this.busy = true;
    this.api
      .post<any>(`/api/reports/templates/${this.selectedKey}/render`, {
        format: 'PDF',
        data,
      })
      .subscribe({
        next: (res) => {
          this.busy = false;
          this.openPdf(res);
          this.status = this.previewLabel
            ? `Rendered saved template for ${this.previewLabel}`
            : 'Rendered saved template';
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Render failed';
        },
      });
  }

  displayLabel(el: ReportElement): string {
    if (el.type === 'line' || el.type === 'box' || el.type === 'image') {
      return '';
    }
    if (el.type === 'qr') {
      return 'QR';
    }
    const raw = el.text || (el.bind ? `{{${el.bind}}}` : el.type);
    if (!this.previewContext) {
      return raw;
    }
    return this.resolvePlaceholders(raw);
  }

  imageSrc(el: ReportElement): string {
    const raw = el.text?.trim() || (el.bind ? `{{${el.bind}}}` : '');
    const resolved = this.resolvePlaceholders(raw);
    if (this.isDrawableImage(resolved)) {
      return resolved;
    }
    if (el.fallbackSrc && this.isDrawableImage(el.fallbackSrc)) {
      return el.fallbackSrc;
    }
    if (this.previewContext || (el.bind ?? '').includes('logo')) {
      return '';
    }
    return SAMPLE_PHOTO_DATA_URL;
  }

  onFallbackSelected(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    const selected = this.selected;
    if (!file || !selected) {
      return;
    }
    if (!file.type.startsWith('image/')) {
      this.error = 'Fallback must be an image file';
      input.value = '';
      return;
    }
    if (file.size > 200_000) {
      this.error = 'Fallback image must be under 200 KB';
      input.value = '';
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      selected.fallbackSrc = String(reader.result || '');
      this.error = '';
    };
    reader.readAsDataURL(file);
  }

  clearFallback(): void {
    if (this.selected) {
      this.selected.fallbackSrc = '';
    }
  }

  private openPdf(res: { contentBase64?: string; fileName?: string }): void {
    if (!res?.contentBase64) {
      this.error = 'PDF payload missing';
      return;
    }
    const binary = atob(res.contentBase64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    const blob = new Blob([bytes], { type: 'application/pdf' });
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }

  private cloneTemplate(t: ReportTemplate): ReportTemplate {
    return {
      ...t,
      layout: { ...(t.layout ?? { width: 794, height: 1123 }) },
      elements: (t.elements ?? []).map((e) => this.normalizeImageElement({ ...e })),
    };
  }

  private normalizeImageElement(el: ReportElement): ReportElement {
    if (el.type !== 'image') {
      return el;
    }
    const text = el.text ?? '';
    const bind = el.bind ?? '';
    if (text.includes('student.photoBase64') || bind === 'student.photoBase64') {
      el.text = '{{student.photoDirectUrl}}';
      el.bind = 'student.photoDirectUrl';
    } else if (text.includes('student.photoDirectUrl') && !el.bind) {
      el.bind = 'student.photoDirectUrl';
    }
    el.objectFit = el.objectFit || 'cover';
    if (el.borderWidth == null) {
      el.borderWidth = 1;
    }
    if (!el.borderColor) {
      el.borderColor = '#94a3b8';
    }
    if (el.borderRadius == null) {
      el.borderRadius = 0;
    }
    return el;
  }

  private ensureSamplePhoto(): void {
    const student = {
      ...((this.samplePreviewData['student'] as Record<string, unknown>) ?? {}),
    };
    const current = String(student['photoDirectUrl'] ?? '');
    if (!current || current.includes('{{')) {
      student['photoDirectUrl'] = SAMPLE_PHOTO_DATA_URL;
    }
    this.samplePreviewData = { ...this.samplePreviewData, student };
  }

  private resolvePlaceholders(template: string): string {
    return template.replace(/\{\{([^}]+)\}\}/g, (_match, path: string) => {
      const value = this.bindValue(path.trim());
      return value ?? '';
    });
  }

  private bindValue(path: string): string {
    let cur: unknown = this.previewContext ?? this.samplePreviewData;
    for (const part of path.split('.')) {
      if (!cur || typeof cur !== 'object') {
        return '';
      }
      cur = (cur as Record<string, unknown>)[part];
      if (cur == null) {
        return '';
      }
    }
    return this.formatBound(path, String(cur));
  }

  /** Match the PDF: issued/expiry as "16 Jul 2026", DOB as DD/MM/YYYY. */
  private formatBound(path: string, value: string): string {
    const key = path.toLowerCase();
    const dob = key.endsWith('dateofbirth') || key.endsWith('.dob');
    const cardDate = key.endsWith('issuedat') || key.endsWith('expiresat') || key.endsWith('validuntil');
    if (!dob && !cardDate) {
      return value;
    }
    if (!value.trim() || value.trim() === '—') {
      return '—';
    }
    const dmy = value.match(/^(\d{2})[-/](\d{2})[-/](\d{4})$/);
    if (dob && dmy) {
      return `${dmy[1]}/${dmy[2]}/${dmy[3]}`;
    }
    const iso = value.match(/^(\d{4})-(\d{2})-(\d{2})(?:T|$)/);
    if (!iso) {
      return value;
    }
    const day = iso[3];
    const month = iso[2];
    const year = iso[1];
    if (dob) {
      return `${day}/${month}/${year}`;
    }
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    return `${day} ${months[Number(month) - 1]} ${year}`;
  }

  private dash(value: string): string {
    return value.trim() ? value.trim() : '—';
  }

  private idCardStudent(answers: Record<string, unknown>, admissionNo: string, name: string): IdCardStudent {
    const klass = this.classDisplay(answers);
    const mobileNo = this.dash(
      this.firstText(answers, ['mobileNo', 'mobile', 'contactNo', 'contactNumber', 'phone', 'studentMobile']),
    );
    return {
      name,
      grade: klass.grade,
      section: klass.section,
      admissionNo,
      dob: this.formatBound('student.dob', this.firstText(answers, ['dateOfBirth', 'dob', 'birthDate'])),
      bloodGroup: this.dash(this.firstText(answers, ['bloodGroup', 'blood_group', 'bloodType'])),
      mobileNo,
      emergencyNo: this.emergencyNo(answers, mobileNo),
      apaarId: this.dash(this.firstText(answers, ['apaarId', 'apaarNumber'])),
      pen: this.dash(this.firstText(answers, ['penNumber', 'pen'])),
    };
  }

  private classDisplay(answers: Record<string, unknown>): { grade: string; section: string; classSection: string } {
    let grade = this.firstText(answers, ['classGrade', 'grade', 'className']);
    let section = this.firstText(answers, ['sectionLetter', 'section']);
    const combined = this.firstText(answers, ['classSection', 'classApplied']);
    if (!grade && combined) {
      const parts = this.splitGradeSection(combined);
      grade = parts[0];
      if (!section) {
        section = parts[1];
      }
    }
    if (/^\d{1,2}$/.test(grade) || /^[IVX]+$/i.test(grade)) {
      grade = `Grade ${grade}`;
    }
    if (section.length <= 3) {
      section = section.toUpperCase();
    }
    return {
      grade: grade || '—',
      section: section || '—',
      classSection: grade ? (section ? `${grade} - ${section}` : grade) : '—',
    };
  }

  private splitGradeSection(raw: string): [string, string] {
    const paren = raw.match(/^(.*?)\s*\(([A-Za-z0-9]+)\)\s*$/);
    if (paren) {
      return [paren[1].trim(), paren[2].trim()];
    }
    const spaced = raw.lastIndexOf(' - ');
    if (spaced > 0) {
      return [raw.slice(0, spaced).trim(), raw.slice(spaced + 3).trim()];
    }
    const dash = Math.max(raw.lastIndexOf('-'), raw.lastIndexOf('–'));
    if (dash > 0 && raw.length - dash <= 3) {
      return [raw.slice(0, dash).trim(), raw.slice(dash + 1).trim()];
    }
    return [raw.trim(), ''];
  }

  private firstText(answers: Record<string, unknown>, keys: string[]): string {
    for (const key of keys) {
      const value = this.text(answers[key]);
      if (value) {
        return value;
      }
    }
    return '';
  }

  private emergencyNo(answers: Record<string, unknown>, mobileNo: string): string {
    const direct = this.firstText(answers, [
      'emergencyNo',
      'emergencyContact',
      'emergencyMobile',
      'emergencyPhone',
      'alternateMobile',
    ]);
    if (direct && !this.samePhone(direct, mobileNo)) {
      return direct;
    }
    const guardian = this.guardianPhone(answers);
    if (guardian && !this.samePhone(guardian, mobileNo)) {
      return guardian;
    }
    return this.dash(direct);
  }

  private guardianPhone(answers: Record<string, unknown>): string {
    const guardians = answers['guardians'];
    if (!Array.isArray(guardians)) {
      return '';
    }
    let fallback = '';
    for (const row of guardians) {
      if (!row || typeof row !== 'object') {
        continue;
      }
      const guardian = row as Record<string, unknown>;
      const mobile = this.text(guardian['mobile'] || guardian['phone']);
      if (!mobile) {
        continue;
      }
      if (guardian['isPrimary'] === true || String(guardian['isPrimary']).toLowerCase() === 'true') {
        return mobile;
      }
      if (!fallback) {
        fallback = mobile;
      }
    }
    return fallback;
  }

  private samePhone(left: string, right: string): boolean {
    const a = left.replace(/\D/g, '');
    const b = right.replace(/\D/g, '');
    return !!a && a === b;
  }

  private transportLabel(answers: Record<string, unknown>): string {
    const mode = this.text(answers['transportMode'] || answers['modeOfTransport'] || answers['conveyance']);
    if (mode) {
      return mode;
    }
    if (!('transport' in answers)) {
      return '—';
    }
    const flag = String(answers['transport']).toLowerCase();
    return flag === 'true' || flag === 'yes' || flag === '1' ? 'Bus' : 'Walker';
  }

  private isDrawableImage(value: string): boolean {
    const trimmed = value.trim();
    return (
      trimmed.startsWith('data:image/') ||
      trimmed.startsWith('blob:') ||
      trimmed.startsWith('http://') ||
      trimmed.startsWith('https://')
    );
  }

  get documentSubject(): 'student' | 'employee' | 'none' {
    if (this.selectedKey === 'salary_slip' || this.selectedKey === 'offer_letter') {
      return 'employee';
    }
    const blob = (this.draft?.elements ?? []).map((el) => `${el.text || ''} ${el.bind || ''}`).join('\n');
    if (/\bstudent\./.test(blob)) {
      return 'student';
    }
    if (/\b(?:employee|staff)\./.test(blob)) {
      return 'employee';
    }
    return 'none';
  }

  get sectionChoices(): SectionOption[] {
    if (!this.filterClassId) {
      return this.sections;
    }
    return this.sections.filter((section) => section.classId === this.filterClassId);
  }

  get bulkSectionChoices(): SectionOption[] {
    if (!this.bulkClassId) {
      return this.sections;
    }
    return this.sections.filter((section) => section.classId === this.bulkClassId);
  }

  get visibleBulkStudents(): BulkStudent[] {
    if (!this.bulkMissingOnly) {
      return this.bulkStudents;
    }
    return this.bulkStudents.filter(
      (student) => student.missingPhoto || student.missingAdmission || student.missingPen,
    );
  }

  get bulkSelectedCount(): number {
    return this.bulkStudents.filter((student) => student.selected).length;
  }

  get bulkWarnings(): string[] {
    const selected = this.bulkStudents.filter((student) => student.selected);
    const notes: string[] = [];
    const photos = selected.filter((student) => student.missingPhoto).length;
    const admissions = selected.filter((student) => student.missingAdmission).length;
    const pens = selected.filter((student) => student.missingPen).length;
    if (photos) {
      notes.push(`${photos} student${photos === 1 ? '' : 's'} have no photo`);
    }
    if (admissions) {
      notes.push(`${admissions} student${admissions === 1 ? '' : 's'} have missing Admission Number`);
    }
    if (pens) {
      notes.push(`${pens} student${pens === 1 ? '' : 's'} have missing PEN`);
    }
    return notes;
  }

  onPersonQuery(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    const term = this.personQuery.trim();
    if (term.length < 2) {
      this.searchHits = [];
      this.searchOpen = false;
      return;
    }
    this.searchTimer = setTimeout(() => this.runPersonSearch(term), 300);
  }

  onClassFilterChange(): void {
    if (this.filterSectionId && !this.sectionChoices.some((section) => section.id === this.filterSectionId)) {
      this.filterSectionId = '';
    }
    this.onPersonQuery();
  }

  async selectPerson(hit: PersonHit): Promise<void> {
    this.searchOpen = false;
    this.searchHits = [];
    this.busy = true;
    this.error = '';
    this.photoWarning = '';
    try {
      if (this.documentSubject === 'employee') {
        const person = {
          id: hit.id,
          name: hit.fullName || '',
          fullName: hit.fullName || '',
          employeeNo: hit.employeeNo || hit.admissionNo || '',
          designation: hit.designation || '',
          department: hit.department || '',
        };
        this.previewContext = {
          ...this.samplePreviewData,
          employee: person,
          staff: person,
          student: {
            ...person,
            admissionNo: person.employeeNo,
            classSection: person.designation,
          },
        };
        this.previewLabel = [person.fullName, person.employeeNo, person.designation].filter(Boolean).join(' · ');
        this.personQuery = person.fullName;
        this.photoWarning = '';
      } else {
        const loaded = await this.loadStudentContext(hit.id);
        this.previewContext = loaded.data;
        this.previewLabel = loaded.label;
        this.personQuery = this.text((loaded.data['student'] as Record<string, unknown>)?.['name']);
        this.photoWarning = loaded.photoMissing ? 'Student photo is not available.' : '';
      }
    } catch (err: any) {
      this.previewContext = null;
      this.previewLabel = '';
      this.error = err?.error?.message ?? 'Could not load this record';
    } finally {
      this.busy = false;
    }
  }

  clearPreviewPerson(): void {
    this.previewContext = null;
    this.previewLabel = '';
    this.photoWarning = '';
    this.personQuery = '';
    this.searchHits = [];
    this.searchOpen = false;
  }

  openBulk(): void {
    this.bulkOpen = true;
    this.bulkNote = '';
    this.bulkAllowIncomplete = false;
    this.bulkMissingOnly = false;
    this.bulkClassId = this.filterClassId;
    this.bulkSectionId = this.filterSectionId;
    if (!this.bulkStudents.length && (this.bulkClassId || this.bulkSectionId)) {
      void this.loadBulkStudents();
    }
  }

  closeBulk(): void {
    this.bulkOpen = false;
  }

  onBulkClassChange(): void {
    if (this.bulkSectionId && !this.bulkSectionChoices.some((section) => section.id === this.bulkSectionId)) {
      this.bulkSectionId = '';
    }
    this.bulkAllowIncomplete = false;
    void this.loadBulkStudents();
  }

  selectAllBulk(selected: boolean): void {
    for (const student of this.visibleBulkStudents) {
      student.selected = selected;
    }
    this.bulkAllowIncomplete = false;
  }

  toggleMissingView(): void {
    this.bulkMissingOnly = !this.bulkMissingOnly;
  }

  async generateBulk(): Promise<void> {
    const chosen = this.bulkStudents.filter((student) => student.selected);
    if (!this.selectedKey || !chosen.length) {
      this.bulkNote = 'Select at least one student.';
      return;
    }
    if (this.bulkWarnings.length && !this.bulkAllowIncomplete) {
      this.bulkNote = 'Review the missing data, then confirm to continue.';
      return;
    }
    if (chosen.length > 80) {
      this.bulkNote = 'Generate at most 80 students at a time. Narrow the class or section.';
      return;
    }
    this.busy = true;
    this.bulkNote = `Preparing ${chosen.length} pages…`;
    this.error = '';
    try {
      const records: Record<string, unknown>[] = [];
      for (const student of chosen) {
        const loaded = await this.loadStudentContext(student.id);
        records.push(loaded.data);
      }
      const res = await firstValueFrom(
        this.api.post<any>(`/api/reports/templates/${this.selectedKey}/render`, {
          format: 'PDF',
          records,
        }),
      );
      this.openPdf(res);
      this.status = `Generated ${records.length} pages from the saved template`;
      this.bulkNote = '';
      this.bulkOpen = false;
    } catch (err: any) {
      this.bulkNote = err?.error?.message ?? 'Bulk generation failed';
    } finally {
      this.busy = false;
    }
  }

  private runtimeData(): Record<string, unknown> | null {
    if (this.documentSubject === 'none') {
      return this.samplePreviewData;
    }
    if (!this.previewContext) {
      this.error =
        this.documentSubject === 'employee'
          ? 'Please select an employee to preview this document.'
          : 'Please select a student to preview this document.';
      return null;
    }
    return this.previewContext;
  }

  private runPersonSearch(term: string): void {
    this.searchSub?.unsubscribe();
    this.searchBusy = true;
    this.searchOpen = true;
    const classSection = this.selectedSectionLabel(this.filterSectionId);
    if (this.documentSubject === 'employee') {
      this.searchSub = this.api
        .getPage<PersonHit>('/api/staff/directory/staff', 0, 8, { q: term })
        .pipe(
          timeout(4000),
          map((page) => page.items ?? []),
          catchError(() => of([] as PersonHit[])),
        )
        .subscribe((items) => {
          this.searchHits = items;
          this.searchBusy = false;
        });
      return;
    }
    this.searchSub = this.api
      .getPage<PersonHit>('/api/student/directory/students', 0, 8, {
        q: term,
        status: 'ACTIVE',
        classSection: classSection || undefined,
        includeDeleted: 'false',
      })
      .pipe(
        timeout(4000),
        map((page) => this.filterHits(page.items ?? [])),
        catchError(() =>
          this.api
            .getPage<PersonHit>('/api/student/students/search', 0, 8, {
              q: term,
              classSection: classSection || undefined,
            })
            .pipe(
              timeout(4000),
              map((page) => this.filterHits(page.items ?? [])),
              catchError(() => of([] as PersonHit[])),
            ),
        ),
      )
      .subscribe((items) => {
        this.searchHits = items;
        this.searchBusy = false;
      });
  }

  private filterHits(items: PersonHit[]): PersonHit[] {
    const labels = this.classFilterLabels();
    if (!labels.length) {
      return items;
    }
    return items.filter((item) => labels.includes(String(item.classSection || '').trim()));
  }

  private classFilterLabels(): string[] {
    if (this.filterSectionId) {
      const label = this.selectedSectionLabel(this.filterSectionId);
      return label ? [label] : [];
    }
    if (!this.filterClassId) {
      return [];
    }
    return this.sections
      .filter((section) => section.classId === this.filterClassId)
      .map((section) => section.studentLabel || section.name)
      .filter(Boolean);
  }

  async loadBulkStudents(): Promise<void> {
    const labels = this.bulkClassLabels();
    if (!labels.length) {
      this.bulkStudents = [];
      this.bulkNote = 'Select a class or section.';
      return;
    }
    this.bulkNote = 'Loading students…';
    const merged = new Map<string, PersonHit>();
    for (const label of labels) {
      const page = await firstValueFrom(
        this.api
          .getPage<PersonHit>('/api/student/directory/students', 0, 80, {
            classSection: label,
            status: 'ACTIVE',
            academicSessionId: this.bulkSessionId || undefined,
            includeDeleted: 'false',
          })
          .pipe(
            timeout(8000),
            catchError(() =>
              of({
                items: [] as PersonHit[],
                page: 0,
                size: 0,
                totalElements: 0,
                totalPages: 0,
                hasNext: false,
              }),
            ),
          ),
      );
      for (const item of page.items ?? []) {
        if (item.id) {
          merged.set(item.id, item);
        }
      }
      if (merged.size >= 80) {
        break;
      }
    }
    this.bulkStudents = [...merged.values()].slice(0, 80).map((item) => this.toBulkStudent(item));
    this.bulkNote = this.bulkStudents.length
      ? `${this.bulkStudents.length} students loaded. The saved template is not changed.`
      : 'No students in this class or section.';
  }

  private bulkClassLabels(): string[] {
    if (this.bulkSectionId) {
      const label = this.selectedSectionLabel(this.bulkSectionId);
      return label ? [label] : [];
    }
    if (!this.bulkClassId) {
      return [];
    }
    const labels = this.sections
      .filter((section) => section.classId === this.bulkClassId)
      .map((section) => section.studentLabel || section.name)
      .filter(Boolean);
    if (labels.length) {
      return labels;
    }
    const klass = this.classes.find((item) => item.id === this.bulkClassId);
    return klass?.name ? [klass.name] : [];
  }

  private toBulkStudent(item: PersonHit): BulkStudent {
    const photo = String(item.photoUrl || '');
    const admission = String(item.admissionNo || '').trim();
    const pen = String(item.penNumber || '').trim();
    return {
      id: item.id,
      name: item.fullName || 'Student',
      admissionNo: admission,
      classSection: item.classSection || '',
      penNumber: pen,
      photoUrl: photo,
      selected: true,
      missingPhoto: !photo.startsWith('/api/') && !photo.startsWith('data:image/') && !photo.startsWith('http'),
      missingAdmission: !admission,
      missingPen: !pen,
    };
  }

  private selectedSectionLabel(sectionId: string): string {
    const section = this.sections.find((item) => item.id === sectionId);
    return section ? section.studentLabel || section.name : '';
  }

  private async loadStudentContext(id: string): Promise<{
    data: Record<string, unknown>;
    label: string;
    photoMissing: boolean;
  }> {
    const student = await firstValueFrom(this.api.get<any>(`/api/student/students/${id}`));
    const answers = (student?.answers ?? {}) as Record<string, unknown>;
    const name = this.text(answers['fullName'] || answers['studentName']);
    const admissionNo = this.text(student?.admissionNo || answers['admissionNo']);
    const card = this.idCardStudent(answers, admissionNo, name);
    const classSection = this.classDisplay(answers).classSection;
    const fatherName = this.text(answers['fatherName'] || answers['parentName']);
    let photoDirectUrl = '';
    try {
      photoDirectUrl = await this.photoDataUrl(this.text(answers['photoUrl']));
      if (!photoDirectUrl) {
        photoDirectUrl = await this.photoDataUrl(this.text(answers['photo']));
      }
    } catch {
      photoDirectUrl = '';
    }
    const studentMap: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(answers)) {
      if (typeof value === 'string' && (value.startsWith('data:') || value === 'on-file')) {
        continue;
      }
      studentMap[key] = value;
    }
    studentMap['id'] = student?.id || id;
    studentMap['name'] = name;
    studentMap['fullName'] = name;
    studentMap['studentName'] = name;
    studentMap['admissionNo'] = card.admissionNo;
    studentMap['grade'] = card.grade;
    studentMap['section'] = card.section;
    studentMap['classSection'] = classSection;
    studentMap['classApplied'] = classSection;
    studentMap['fatherName'] = fatherName;
    studentMap['pen'] = card.pen;
    studentMap['penNumber'] = card.pen;
    studentMap['apaarId'] = card.apaarId;
    studentMap['schoolStudentId'] = this.text(answers['schoolStudentId']);
    studentMap['dob'] = card.dob;
    studentMap['dateOfBirth'] = card.dob;
    studentMap['bloodGroup'] = card.bloodGroup;
    studentMap['mobileNo'] = card.mobileNo;
    studentMap['mobile'] = card.mobileNo;
    studentMap['emergencyNo'] = card.emergencyNo;
    studentMap['emergencyContact'] = card.emergencyNo;
    studentMap['transportMode'] = this.transportLabel(answers);
    const rollNo = this.text(answers['rollNo']);
    studentMap['rollLine'] = rollNo
      ? `Roll  ${rollNo}  ·  ${admissionNo}`
      : `Adm  ${admissionNo}`;
    studentMap['photoDirectUrl'] = photoDirectUrl;
    studentMap['photoBase64'] = photoDirectUrl;
    studentMap['photoUrl'] = photoDirectUrl;
    return {
      data: { ...this.samplePreviewData, student: studentMap },
      label: [name, admissionNo, classSection].filter(Boolean).join(' · '),
      photoMissing: !photoDirectUrl,
    };
  }

  private async photoDataUrl(raw: string): Promise<string> {
    const value = raw.trim();
    if (!value || value === 'on-file') {
      return '';
    }
    if (value.startsWith('data:image/')) {
      return value;
    }
    let path = value;
    if (value.startsWith('http://') || value.startsWith('https://')) {
      try {
        const url = new URL(value);
        path = `${url.pathname}${url.search}`;
      } catch {
        return '';
      }
    }
    if (!path.startsWith('/api/')) {
      return '';
    }
    const blob = await firstValueFrom(this.api.getBlob(path));
    if (!blob.type.startsWith('image/')) {
      return '';
    }
    return await this.blobToDataUrl(blob);
  }

  private blobToDataUrl(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result || ''));
      reader.onerror = () => reject(reader.error);
      reader.readAsDataURL(blob);
    });
  }

  private loadCatalogs(): void {
    this.api.get<any[]>('/api/academic/classes').subscribe({
      next: (rows) => {
        this.classes = (rows ?? []).map((row) => ({
          id: String(row.id),
          name: String(row.name || row.label || 'Class'),
        }));
      },
      error: () => {
        this.classes = [];
      },
    });
    this.api.get<any[]>('/api/academic/sections').subscribe({
      next: (rows) => {
        this.sections = (rows ?? []).map((row) => ({
          id: String(row.id),
          classId: String(row.classId || ''),
          name: String(row.name || ''),
          studentLabel: String(row.studentLabel || row.name || ''),
        }));
      },
      error: () => {
        this.sections = [];
      },
    });
    this.api.get<any[]>('/api/student/lifecycle/sessions').subscribe({
      next: (rows) => {
        this.sessions = (rows ?? []).map((row) => ({
          id: String(row.academicSessionId || row.definitionKey || row.id || ''),
          label: String(row.name || row.label || row.definitionKey || 'Session'),
        }));
      },
      error: () => {
        this.sessions = [];
      },
    });
  }

  private text(value: unknown): string {
    return value == null ? '' : String(value).trim();
  }
}
