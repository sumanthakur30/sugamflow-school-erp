import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
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

  private dragPaletteType: string | null = null;
  private moveState: {
    id: string;
    startX: number;
    startY: number;
    origX: number;
    origY: number;
  } | null = null;

  ngOnInit(): void {
    this.campusReadySub = this.tenantContext.whenCampusReady().subscribe(() => this.bootstrap());
  }

  ngOnDestroy(): void {
    this.campusReadySub?.unsubscribe();
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
    this.busy = true;
    this.error = '';
    this.api
      .post<any>('/api/reports/preview', {
        ...this.draft,
        data: this.samplePreviewData,
        format: 'PDF',
      })
      .subscribe({
        next: (res) => {
          this.busy = false;
          this.openPdf(res);
          this.status = 'Preview PDF opened';
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
    this.busy = true;
    this.api
      .post<any>(`/api/reports/templates/${this.selectedKey}/render`, {
        format: 'PDF',
        data: this.samplePreviewData,
      })
      .subscribe({
        next: (res) => {
          this.busy = false;
          this.openPdf(res);
          this.status = 'Rendered saved template';
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
    return el.text || (el.bind ? `{{${el.bind}}}` : el.type);
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
    let cur: unknown = this.samplePreviewData;
    for (const part of path.split('.')) {
      if (!cur || typeof cur !== 'object') {
        return '';
      }
      cur = (cur as Record<string, unknown>)[part];
      if (cur == null) {
        return '';
      }
    }
    return String(cur);
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
}
