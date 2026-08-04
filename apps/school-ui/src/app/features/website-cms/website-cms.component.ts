import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

interface PageRow {
  id: string;
  slug: string;
  title: string;
  summary?: string;
  bodyHtml?: string;
  status: string;
  seoTitle?: string;
  seoDescription?: string;
  updatedAt?: string;
}

interface HomepageSection {
  type: string;
  enabled: boolean;
  order: number;
  content?: {
    title?: string;
    subtitle?: string;
    imageUrl?: string;
    ctaLabel?: string;
    [key: string]: unknown;
  };
}

interface CampusRow {
  id: string;
  branchId: string;
  displayName: string;
  defaultSite?: boolean;
  status?: string;
  templateCode?: string;
}

interface AlumniRow {
  id: string;
  slug: string;
  fullName: string;
  batchYear?: number;
  headline?: string;
  bioHtml?: string;
  status: string;
}

interface TemplateRow {
  code: string;
  name: string;
  description?: string;
}

interface MediaRow {
  id: string;
  fileName: string;
  contentType?: string;
  url: string;
  byteSize?: number;
  createdAt?: string;
}

interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

type ModuleId =
  | 'dashboard'
  | 'builder'
  | 'pages'
  | 'navigation'
  | 'theme'
  | 'media'
  | 'forms'
  | 'blogs'
  | 'events'
  | 'news'
  | 'gallery'
  | 'admissions'
  | 'staff'
  | 'campuses'
  | 'achievements'
  | 'downloads'
  | 'seo'
  | 'analytics'
  | 'marketplace'
  | 'ai'
  | 'settings'
  | 'publish'
  | 'alumni'
  | 'homepage';

/** @deprecated use ModuleId — kept for older call sites */
type Tab = ModuleId;

@Component({
  selector: 'sf-website-cms',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './website-cms.component.html',
  styleUrl: './website-cms.component.scss',
})
export class WebsiteCmsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  tab: Tab = 'dashboard';
  pages: PageRow[] = [];
  sections: HomepageSection[] = [];
  selectedSectionIndex = 0;
  seo = { defaultTitle: '', defaultDescription: '', ogImageUrl: '' };
  theme = {
    primaryColor: '#0B3D91',
    secondaryColor: '#F5B700',
    logoUrl: '',
    faviconUrl: '',
  };
  siteMeta = {
    organizationId: '',
    status: '',
    displayName: '',
    templateCode: '',
    erpLoginUrl: '',
  };
  domains: Array<{
    host: string;
    primary?: boolean;
    status?: string;
    sslStatus?: string;
  }> = [];
  previewDevice: 'desktop' | 'tablet' | 'mobile' = 'desktop';
  previewSrc = (environment as { websitePreviewUrl?: string }).websitePreviewUrl || 'http://localhost:4300';
  readonly sectionCatalog: Array<{ type: string; label: string; supported: boolean }> = [
    { type: 'HERO', label: 'Hero', supported: true },
    { type: 'LATEST_NEWS', label: 'Latest news', supported: true },
    { type: 'UPCOMING_EVENTS', label: 'Events', supported: true },
    { type: 'ADMISSION_CTA', label: 'Admission CTA', supported: true },
    { type: 'PRINCIPAL_MESSAGE', label: 'Principal message', supported: false },
    { type: 'ABOUT_SCHOOL', label: 'About school', supported: false },
    { type: 'STATISTICS', label: 'Statistics', supported: false },
    { type: 'FACILITIES', label: 'Facilities', supported: false },
    { type: 'TESTIMONIALS', label: 'Testimonials', supported: false },
    { type: 'GALLERY', label: 'Gallery block', supported: false },
    { type: 'FAQ', label: 'FAQ', supported: false },
    { type: 'CUSTOM_HTML', label: 'Custom HTML', supported: false },
  ];
  readonly navModules: Array<{ id: ModuleId; label: string; group: string; ready: boolean }> = [
    { id: 'dashboard', label: 'Dashboard', group: 'Overview', ready: true },
    { id: 'builder', label: 'Website Builder', group: 'Overview', ready: true },
    { id: 'pages', label: 'Pages', group: 'Content', ready: true },
    { id: 'navigation', label: 'Navigation', group: 'Content', ready: false },
    { id: 'theme', label: 'Theme', group: 'Design', ready: true },
    { id: 'media', label: 'Media', group: 'Design', ready: true },
    { id: 'forms', label: 'Forms', group: 'Content', ready: false },
    { id: 'blogs', label: 'Blogs', group: 'Content', ready: false },
    { id: 'events', label: 'Events', group: 'Content', ready: false },
    { id: 'news', label: 'News', group: 'Content', ready: false },
    { id: 'gallery', label: 'Gallery', group: 'Content', ready: false },
    { id: 'alumni', label: 'Alumni', group: 'Content', ready: true },
    { id: 'campuses', label: 'Campuses', group: 'ERP', ready: true },
    { id: 'admissions', label: 'Admissions', group: 'ERP', ready: false },
    { id: 'staff', label: 'Staff', group: 'ERP', ready: false },
    { id: 'achievements', label: 'Achievements', group: 'Content', ready: false },
    { id: 'downloads', label: 'Downloads', group: 'Content', ready: false },
    { id: 'seo', label: 'SEO', group: 'Growth', ready: true },
    { id: 'analytics', label: 'Analytics', group: 'Growth', ready: true },
    { id: 'marketplace', label: 'Marketplace', group: 'Growth', ready: true },
    { id: 'ai', label: 'AI Assistant', group: 'Growth', ready: true },
    { id: 'settings', label: 'Settings', group: 'System', ready: true },
    { id: 'publish', label: 'Publish', group: 'System', ready: true },
  ];
  campuses: CampusRow[] = [];
  alumni: AlumniRow[] = [];
  templates: TemplateRow[] = [];
  mediaAssets: MediaRow[] = [];
  mediaUploading = false;
  mediaNotice = '';
  analyticsSummary: {
    totalEvents: number;
    days: number;
    byType: Record<string, number>;
    recent: Array<{ eventType: string; path: string; createdAt?: string }>;
  } | null = null;
  mediaUsage: {
    usedBytes: number;
    usedGb: number;
    storageLimitGb: number | null;
    pageCount: number;
    pageLimit: number | null;
  } | null = null;
  loading = false;
  saving = false;
  error = '';
  editing: PageRow | null = null;
  draft: {
    slug: string;
    title: string;
    summary: string;
    bodyHtml: string;
    seoTitle: string;
    seoDescription: string;
  } = this.emptyDraft();

  campusDraft = { branchId: '', displayName: '' };
  alumniDraft = {
    slug: '',
    fullName: '',
    batchYear: new Date().getFullYear() - 5,
    headline: '',
    bioHtml: '',
  };
  editingAlumniId: string | null = null;
  aiDraft = { kind: 'page', topic: '', tone: 'warm and professional' };
  aiResult: Record<string, unknown> | null = null;
  selectedTemplate = '';
  applySiteId = '';

  ngOnInit(): void {
    this.reload();
    this.loadWebsiteBootstrap();
    this.loadMediaUsage();
    this.loadAnalytics();
  }

  get publishedPages(): number {
    return this.pages.filter((p) => p.status === 'PUBLISHED').length;
  }

  get draftPages(): number {
    return this.pages.filter((p) => p.status !== 'PUBLISHED').length;
  }

  get selectedSection(): HomepageSection | null {
    return this.sections[this.selectedSectionIndex] || null;
  }

  get previewWidth(): string {
    if (this.previewDevice === 'mobile') return '390px';
    if (this.previewDevice === 'tablet') return '768px';
    return '100%';
  }

  moduleGroups(): Array<{ group: string; items: WebsiteCmsComponent['navModules'] }> {
    const order = ['Overview', 'Content', 'Design', 'ERP', 'Growth', 'System'];
    return order
      .map((group) => ({ group, items: this.navModules.filter((m) => m.group === group) }))
      .filter((g) => g.items.length);
  }

  setTab(tab: Tab): void {
    if (tab === 'homepage') tab = 'builder';
    this.tab = tab;
    this.error = '';
    if (tab === 'analytics' || tab === 'dashboard' || tab === 'publish') {
      this.loadAnalytics();
      this.loadMediaUsage();
    }
    if (tab === 'media' || tab === 'dashboard') {
      this.loadMedia();
      this.loadMediaUsage();
    }
    if (tab === 'builder' || tab === 'theme' || tab === 'settings') {
      this.loadWebsiteBootstrap();
    }
    if (tab === 'campuses') this.loadCampuses();
    if (tab === 'alumni') this.loadAlumni();
    if (tab === 'marketplace') this.loadTemplates();
    if (tab === 'pages') this.reload();
  }

  loadMedia(): void {
    this.error = '';
    this.http
      .get<ApiResponse<MediaRow[]>>(`${environment.apiBaseUrl}/api/cms/admin/media`)
      .subscribe({
        next: (res) => {
          this.mediaAssets = (res.data || []).map((m) => ({
            id: String(m.id),
            fileName: String(m.fileName || 'file'),
            contentType: m.contentType,
            url: String(m.url || ''),
            byteSize: m.byteSize == null ? undefined : Number(m.byteSize),
            createdAt: m.createdAt,
          }));
        },
        error: (err) => (this.error = err?.error?.message || 'Failed to load media'),
      });
  }

  onMediaSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploadMedia(file);
    input.value = '';
  }

  uploadMedia(file: File): void {
    this.mediaUploading = true;
    this.error = '';
    this.mediaNotice = '';
    const form = new FormData();
    form.append('file', file, file.name);
    this.http
      .post<ApiResponse<MediaRow>>(`${environment.apiBaseUrl}/api/cms/admin/media/upload`, form)
      .subscribe({
        next: (res) => {
          this.mediaUploading = false;
          const row = res.data;
          if (row?.url) {
            this.mediaNotice = `Uploaded ${row.fileName || file.name}. URL ready to insert.`;
          }
          this.loadMedia();
          this.loadMediaUsage();
        },
        error: (err) => {
          this.mediaUploading = false;
          this.error = err?.error?.message || 'Upload failed';
        },
      });
  }

  insertMediaIntoBody(asset: MediaRow): void {
    if (!asset.url) return;
    const alt = (asset.fileName || 'School image').replace(/"/g, '');
    const tag = `\n<p><img src="${asset.url}" alt="${alt}" style="max-width:100%;height:auto;" /></p>\n`;
    this.draft.bodyHtml = `${this.draft.bodyHtml || ''}${tag}`;
    this.error = '';
    this.tab = 'pages';
    if (!this.draft.slug?.trim() || !this.draft.title?.trim()) {
      this.mediaNotice =
        `Inserted ${asset.fileName} into Body HTML. Fill Slug and Title (or click Edit on an existing page), then Save.`;
    } else {
      this.mediaNotice = `Inserted ${asset.fileName} into page Body HTML. Save the page when ready.`;
    }
  }

  useMediaAsOg(asset: MediaRow): void {
    if (!asset.url) return;
    this.seo.ogImageUrl = asset.url;
    this.tab = 'seo';
    this.mediaNotice = 'Set as SEO OG image (also used as homepage hero). Click Save SEO to persist.';
  }

  useMediaAsHero(asset: MediaRow): void {
    if (!asset.url) return;
    const hero = this.sections.find((s) => s.type === 'HERO');
    if (!hero) {
      this.error = 'No HERO section on homepage. Enable/create one under Homepage.';
      return;
    }
    hero.content = { ...(hero.content || {}), imageUrl: asset.url };
    this.seo.ogImageUrl = asset.url;
    this.tab = 'builder';
    this.mediaNotice =
      'Hero image set. Click Save homepage in Builder (and Save SEO if you want OG to match).';
  }

  async copyMediaUrl(asset: MediaRow): Promise<void> {
    if (!asset.url) return;
    try {
      await navigator.clipboard.writeText(asset.url);
      this.mediaNotice = `Copied URL for ${asset.fileName}`;
    } catch {
      this.mediaNotice = asset.url;
    }
  }

  deleteMedia(asset: MediaRow): void {
    if (!confirm(`Delete ${asset.fileName}?`)) return;
    this.http
      .delete(`${environment.apiBaseUrl}/api/cms/admin/media/${asset.id}`)
      .subscribe({
        next: () => {
          this.loadMedia();
          this.loadMediaUsage();
        },
        error: (err) => (this.error = err?.error?.message || 'Delete failed'),
      });
  }

  formatBytes(bytes?: number): string {
    if (bytes == null || Number.isNaN(bytes)) return '—';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  }

  isImage(asset: MediaRow): boolean {
    return !!asset.contentType?.startsWith('image');
  }

  openAnalytics(): void {
    this.setTab('analytics');
  }

  loadAnalytics(): void {
    this.http
      .get<ApiResponse<Record<string, unknown>>>(
        `${environment.apiBaseUrl}/api/website/admin/analytics`,
        { params: { days: '30' } }
      )
      .subscribe({
        next: (res) => {
          const data = res.data || {};
          this.analyticsSummary = {
            totalEvents: Number(data['totalEvents'] || 0),
            days: Number(data['days'] || 30),
            byType: (data['byType'] as Record<string, number>) || {},
            recent:
              (data['recent'] as Array<{ eventType: string; path: string; createdAt?: string }>) ||
              [],
          };
        },
        error: (err) => (this.error = err?.error?.message || 'Failed to load analytics'),
      });
  }

  loadMediaUsage(): void {
    this.http
      .get<ApiResponse<Record<string, unknown>>>(`${environment.apiBaseUrl}/api/cms/admin/media/usage`)
      .subscribe({
        next: (res) => {
          const data = res.data || {};
          this.mediaUsage = {
            usedBytes: Number(data['usedBytes'] || 0),
            usedGb: Number(data['usedGb'] || 0),
            storageLimitGb:
              data['storageLimitGb'] == null ? null : Number(data['storageLimitGb']),
            pageCount: Number(data['pageCount'] || 0),
            pageLimit: data['pageLimit'] == null ? null : Number(data['pageLimit']),
          };
        },
        error: () => {
          this.mediaUsage = null;
        },
      });
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.http
      .get<ApiResponse<PageRow[]>>(`${environment.apiBaseUrl}/api/cms/admin/pages`)
      .subscribe({
        next: (res) => {
          this.pages = res.data || [];
          this.loading = false;
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.error?.message || 'Failed to load CMS pages';
        },
      });
  }

  loadWebsiteBootstrap(): void {
    this.http
      .get<ApiResponse<Record<string, unknown>>>(
        `${environment.apiBaseUrl}/api/website/admin/bootstrap`
      )
      .subscribe({
        next: (res) => {
          const data = res.data || {};
          this.sections = ((data['homepage'] as HomepageSection[]) || []).map((s) => ({
            type: String(s.type),
            enabled: s.enabled !== false,
            order: Number(s.order || 0),
            content: {
              title: String((s.content as Record<string, unknown>)?.['title'] ?? ''),
              subtitle: String((s.content as Record<string, unknown>)?.['subtitle'] ?? ''),
              imageUrl: String((s.content as Record<string, unknown>)?.['imageUrl'] ?? ''),
              ctaLabel: String((s.content as Record<string, unknown>)?.['ctaLabel'] ?? ''),
              ...(s.content || {}),
            },
          }));
          const seo = (data['seo'] as Record<string, string>) || {};
          this.seo = {
            defaultTitle: seo['defaultTitle'] || '',
            defaultDescription: seo['defaultDescription'] || '',
            ogImageUrl: seo['ogImageUrl'] || '',
          };
          const theme = (data['theme'] as Record<string, string>) || {};
          this.theme = {
            primaryColor: theme['primaryColor'] || '#0B3D91',
            secondaryColor: theme['secondaryColor'] || '#F5B700',
            logoUrl: theme['logoUrl'] || '',
            faviconUrl: theme['faviconUrl'] || '',
          };
          this.siteMeta = {
            organizationId: String(data['organizationId'] || ''),
            status: String(data['status'] || ''),
            displayName: String(data['displayName'] || ''),
            templateCode: String(data['templateCode'] || ''),
            erpLoginUrl: String(data['erpLoginUrl'] || ''),
          };
          this.domains = ((data['domains'] as typeof this.domains) || []).map((d) => ({
            host: String(d.host || ''),
            primary: !!d.primary,
            status: d.status,
            sslStatus: d.sslStatus,
          }));
          this.campuses = ((data['campuses'] as CampusRow[]) || []).map((c) => ({
            id: String(c.id),
            branchId: String(c.branchId || 'main'),
            displayName: String(c.displayName || ''),
            defaultSite: !!c.defaultSite,
            status: c.status,
            templateCode: c.templateCode,
          }));
          if (!this.applySiteId && this.campuses.length) {
            this.applySiteId = this.campuses[0].id;
          }
        },
        error: () => undefined,
      });
  }

  loadCampuses(): void {
    this.http
      .get<ApiResponse<CampusRow[]>>(`${environment.apiBaseUrl}/api/website/admin/campuses`)
      .subscribe({
        next: (res) => {
          this.campuses = res.data || [];
          if (!this.applySiteId && this.campuses.length) {
            this.applySiteId = this.campuses[0].id;
          }
        },
        error: (err) => (this.error = err?.error?.message || 'Failed to load campuses'),
      });
  }

  createCampus(): void {
    this.saving = true;
    this.error = '';
    this.http
      .post<ApiResponse<CampusRow>>(`${environment.apiBaseUrl}/api/website/admin/campuses`, {
        branchId: this.campusDraft.branchId.trim(),
        displayName: this.campusDraft.displayName.trim(),
      })
      .subscribe({
        next: () => {
          this.saving = false;
          this.campusDraft = { branchId: '', displayName: '' };
          this.loadCampuses();
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message || 'Failed to create campus';
        },
      });
  }

  loadAlumni(): void {
    this.http
      .get<ApiResponse<AlumniRow[]>>(`${environment.apiBaseUrl}/api/cms/admin/alumni`)
      .subscribe({
        next: (res) => (this.alumni = res.data || []),
        error: (err) => (this.error = err?.error?.message || 'Failed to load alumni'),
      });
  }

  saveAlumni(): void {
    this.saving = true;
    this.error = '';
    const body = { ...this.alumniDraft };
    const req$ = this.editingAlumniId
      ? this.http.put<ApiResponse<AlumniRow>>(
          `${environment.apiBaseUrl}/api/cms/admin/alumni/${this.editingAlumniId}`,
          body
        )
      : this.http.post<ApiResponse<AlumniRow>>(
          `${environment.apiBaseUrl}/api/cms/admin/alumni`,
          body
        );
    req$.subscribe({
      next: () => {
        this.saving = false;
        this.editingAlumniId = null;
        this.alumniDraft = {
          slug: '',
          fullName: '',
          batchYear: new Date().getFullYear() - 5,
          headline: '',
          bioHtml: '',
        };
        this.mediaNotice = 'Alumni saved. Publish if needed, then refresh /alumni on the public site.';
        this.loadAlumni();
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message || 'Failed to save alumni';
      },
    });
  }

  startEditAlumni(row: AlumniRow): void {
    this.editingAlumniId = row.id;
    this.alumniDraft = {
      slug: row.slug,
      fullName: row.fullName,
      batchYear: row.batchYear ?? new Date().getFullYear() - 5,
      headline: row.headline || '',
      bioHtml: row.bioHtml || '',
    };
    this.tab = 'alumni';
    this.mediaNotice = `Editing ${row.fullName}. Update the fields and click Save alumni.`;
  }

  cancelEditAlumni(): void {
    this.editingAlumniId = null;
    this.alumniDraft = {
      slug: '',
      fullName: '',
      batchYear: new Date().getFullYear() - 5,
      headline: '',
      bioHtml: '',
    };
    this.mediaNotice = '';
  }

  publishAlumni(row: AlumniRow): void {
    this.http
      .post(`${environment.apiBaseUrl}/api/cms/admin/alumni/${row.id}/publish`, {})
      .subscribe({
        next: () => this.loadAlumni(),
        error: (err) => (this.error = err?.error?.message || 'Publish failed'),
      });
  }

  runAiDraft(): void {
    this.saving = true;
    this.error = '';
    this.aiResult = null;
    this.http
      .post<ApiResponse<Record<string, unknown>>>(
        `${environment.apiBaseUrl}/api/cms/admin/ai/draft`,
        this.aiDraft
      )
      .subscribe({
        next: (res) => {
          this.saving = false;
          this.aiResult = res.data || {};
          if (this.aiDraft.kind === 'page' || this.aiDraft.kind === 'blog') {
            this.draft = {
              slug: String(this.aiResult['title'] || this.aiDraft.topic)
                .toLowerCase()
                .replace(/[^a-z0-9]+/g, '-')
                .replace(/^-|-$/g, ''),
              title: String(this.aiResult['title'] || this.aiDraft.topic),
              summary: String(this.aiResult['summary'] || ''),
              bodyHtml: String(this.aiResult['bodyHtml'] || ''),
              seoTitle: String(this.aiResult['seoTitle'] || ''),
              seoDescription: String(this.aiResult['seoDescription'] || ''),
            };
          }
          if (this.aiDraft.kind === 'seo') {
            this.seo.defaultTitle = String(this.aiResult['seoTitle'] || this.seo.defaultTitle);
            this.seo.defaultDescription = String(
              this.aiResult['seoDescription'] || this.seo.defaultDescription
            );
          }
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message || 'AI draft failed';
        },
      });
  }

  loadTemplates(): void {
    this.http
      .get<ApiResponse<TemplateRow[]>>(
        `${environment.apiBaseUrl}/api/website/admin/marketplace/templates`
      )
      .subscribe({
        next: (res) => {
          this.templates = res.data || [];
          if (!this.selectedTemplate && this.templates.length) {
            this.selectedTemplate = this.templates[0].code;
          }
        },
        error: (err) => (this.error = err?.error?.message || 'Failed to load templates'),
      });
  }

  applyTemplate(): void {
    this.saving = true;
    this.error = '';
    this.http
      .post(`${environment.apiBaseUrl}/api/website/admin/marketplace/apply`, {
        templateCode: this.selectedTemplate,
        siteId: this.applySiteId || undefined,
      })
      .subscribe({
        next: () => {
          this.saving = false;
          this.loadWebsiteBootstrap();
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message || 'Failed to apply template';
        },
      });
  }

  startCreate(): void {
    this.editing = null;
    this.draft = this.emptyDraft();
    this.tab = 'pages';
  }

  startEdit(page: PageRow): void {
    this.editing = page;
    this.draft = {
      slug: page.slug,
      title: page.title,
      summary: page.summary || '',
      bodyHtml: page.bodyHtml || '',
      seoTitle: page.seoTitle || '',
      seoDescription: page.seoDescription || '',
    };
    this.tab = 'pages';
  }

  save(): void {
    this.error = '';
    this.mediaNotice = '';
    if (!this.draft.slug?.trim() || !this.draft.title?.trim()) {
      this.error = 'Slug and Title are required before saving.';
      return;
    }
    this.saving = true;
    const body = { ...this.draft };
    const req$ = this.editing
      ? this.http.put<ApiResponse<PageRow>>(
          `${environment.apiBaseUrl}/api/cms/admin/pages/${this.editing.id}`,
          body
        )
      : this.http.post<ApiResponse<PageRow>>(
          `${environment.apiBaseUrl}/api/cms/admin/pages`,
          body
        );
    req$.subscribe({
      next: () => {
        this.saving = false;
        this.editing = null;
        this.draft = this.emptyDraft();
        this.mediaNotice = 'Page saved.';
        this.reload();
      },
      error: (err) => {
        this.saving = false;
        this.error = this.apiError(err, 'Save failed');
      },
    });
  }

  private apiError(err: unknown, fallback: string): string {
    const e = err as { error?: { message?: string; data?: unknown; errors?: unknown } };
    if (e?.error?.message) return e.error.message;
    const data = e?.error?.data;
    if (typeof data === 'string' && data.trim()) return data;
    if (data && typeof data === 'object') {
      const vals = Object.values(data as Record<string, unknown>).filter(Boolean);
      if (vals.length) return vals.map(String).join('; ');
    }
    return fallback;
  }

  publish(page: PageRow): void {
    this.http
      .post<ApiResponse<PageRow>>(
        `${environment.apiBaseUrl}/api/cms/admin/pages/${page.id}/publish`,
        {}
      )
      .subscribe({
        next: () => this.reload(),
        error: (err) => (this.error = err?.error?.message || 'Publish failed'),
      });
  }

  unpublish(page: PageRow): void {
    this.http
      .post<ApiResponse<PageRow>>(
        `${environment.apiBaseUrl}/api/cms/admin/pages/${page.id}/unpublish`,
        {}
      )
      .subscribe({
        next: () => this.reload(),
        error: (err) => (this.error = err?.error?.message || 'Unpublish failed'),
      });
  }

  moveSection(index: number, delta: number): void {
    const target = index + delta;
    if (target < 0 || target >= this.sections.length) return;
    const copy = [...this.sections];
    const [row] = copy.splice(index, 1);
    copy.splice(target, 0, row);
    this.sections = copy.map((s, i) => ({ ...s, order: (i + 1) * 10 }));
  }

  ensureContent(section: HomepageSection): void {
    if (!section.content) {
      section.content = { title: '', subtitle: '', imageUrl: '', ctaLabel: '' };
    }
  }

  saveHomepage(): void {
    this.saving = true;
    this.error = '';
    const payload = this.sections.map((s, i) => ({
      type: s.type,
      enabled: s.enabled !== false,
      order: Number(s.order || (i + 1) * 10),
      content: s.content || {},
    }));
    this.http
      .put<ApiResponse<HomepageSection[]>>(
        `${environment.apiBaseUrl}/api/website/admin/homepage`,
        payload
      )
      .subscribe({
        next: (res) => {
          this.saving = false;
          this.sections = (res.data || []).map((s) => ({
            type: String(s.type),
            enabled: s.enabled !== false,
            order: Number(s.order || 0),
            content: {
              title: String((s.content as Record<string, unknown>)?.['title'] ?? ''),
              subtitle: String((s.content as Record<string, unknown>)?.['subtitle'] ?? ''),
              imageUrl: String((s.content as Record<string, unknown>)?.['imageUrl'] ?? ''),
              ctaLabel: String((s.content as Record<string, unknown>)?.['ctaLabel'] ?? ''),
              ...(s.content || {}),
            },
          }));
          this.mediaNotice = 'Homepage saved. Refresh the public site preview to see it.';
        },
        error: (err) => {
          this.saving = false;
          this.error = this.apiError(err, 'Failed to save homepage sections');
        },
      });
  }

  saveSeo(): void {
    this.saving = true;
    this.error = '';
    this.http
      .put<ApiResponse<Record<string, string>>>(
        `${environment.apiBaseUrl}/api/website/admin/seo`,
        this.seo
      )
      .subscribe({
        next: () => {
          this.saving = false;
          this.mediaNotice = 'SEO saved.';
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message || 'Failed to save SEO defaults';
        },
      });
  }

  openPreview(): void {
    window.open(this.previewSrc.split('?')[0], '_blank', 'noopener');
  }

  refreshPreview(): void {
    const base =
      (environment as { websitePreviewUrl?: string }).websitePreviewUrl || 'http://localhost:4300';
    this.previewSrc = `${base}?t=${Date.now()}`;
  }

  addSection(type: string): void {
    const meta = this.sectionCatalog.find((s) => s.type === type);
    this.sections = [
      ...this.sections,
      {
        type,
        enabled: true,
        order: (this.sections.length + 1) * 10,
        content: {
          title: meta?.label || type,
          subtitle: '',
          imageUrl: '',
          ctaLabel: 'Learn more',
        },
      },
    ];
    this.selectedSectionIndex = this.sections.length - 1;
    this.tab = 'builder';
  }

  selectSection(index: number): void {
    this.selectedSectionIndex = index;
  }

  removeSection(index: number): void {
    if (!confirm('Remove this section from the homepage?')) return;
    this.sections = this.sections
      .filter((_, i) => i !== index)
      .map((s, i) => ({ ...s, order: (i + 1) * 10 }));
    this.selectedSectionIndex = Math.max(0, Math.min(index, this.sections.length - 1));
  }

  saveTheme(): void {
    this.saving = true;
    this.error = '';
    this.http
      .put(`${environment.apiBaseUrl}/api/website/admin/theme`, this.theme)
      .subscribe({
        next: () => {
          this.saving = false;
          this.mediaNotice = 'Theme saved. Refresh the public preview.';
        },
        error: (err) => {
          this.saving = false;
          this.error = this.apiError(err, 'Failed to save theme');
        },
      });
  }

  publishAllDrafts(): void {
    const drafts = this.pages.filter((p) => p.status !== 'PUBLISHED');
    if (!drafts.length) {
      this.mediaNotice = 'All pages are already published.';
      return;
    }
    this.saving = true;
    let pending = drafts.length;
    let failed = false;
    drafts.forEach((p) => {
      this.http
        .post(`${environment.apiBaseUrl}/api/cms/admin/pages/${p.id}/publish`, {})
        .subscribe({
          next: () => {
            pending -= 1;
            if (pending <= 0 && !failed) {
              this.saving = false;
              this.mediaNotice = `Published ${drafts.length} page(s).`;
              this.reload();
            }
          },
          error: (err) => {
            failed = true;
            this.saving = false;
            this.error = this.apiError(err, 'Publish failed');
          },
        });
    });
  }

  isSoonModule(id: ModuleId): boolean {
    const m = this.navModules.find((x) => x.id === id);
    return !!m && !m.ready;
  }

  private emptyDraft() {
    return {
      slug: '',
      title: '',
      summary: '',
      bodyHtml: '',
      seoTitle: '',
      seoDescription: '',
    };
  }
}
