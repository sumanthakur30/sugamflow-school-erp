import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
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
}

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
      this.formats = boot.exportFormats ?? [];
      this.templates = boot.templates ?? [];
      this.loading = false;
      if (this.featureEnabled && this.templates.length) {
        this.selectTemplate(this.templates[0].templateKey);
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
                ? '[Image]'
                : '',
      bind: type === 'field' ? 'student.name' : undefined,
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
    if (el.type === 'line') {
      return '';
    }
    if (el.type === 'box') {
      return '';
    }
    if (el.type === 'image') {
      return el.text || '[Image]';
    }
    return el.text || (el.bind ? `{{${el.bind}}}` : el.type);
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
      elements: (t.elements ?? []).map((e) => ({ ...e })),
    };
  }
}
