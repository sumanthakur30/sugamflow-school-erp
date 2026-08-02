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
  content?: Record<string, unknown>;
}

interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

@Component({
  selector: 'sf-website-cms',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './website-cms.component.html',
  styleUrl: './website-cms.component.scss',
})
export class WebsiteCmsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  tab: 'pages' | 'homepage' | 'seo' = 'pages';
  pages: PageRow[] = [];
  sections: HomepageSection[] = [];
  seo = { defaultTitle: '', defaultDescription: '', ogImageUrl: '' };
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

  ngOnInit(): void {
    this.reload();
    this.loadWebsiteBootstrap();
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
            content: s.content || {},
          }));
          const seo = (data['seo'] as Record<string, string>) || {};
          this.seo = {
            defaultTitle: seo['defaultTitle'] || '',
            defaultDescription: seo['defaultDescription'] || '',
            ogImageUrl: seo['ogImageUrl'] || '',
          };
        },
        error: () => {
          /* website bootstrap optional if FEATURE_WEBSITE off */
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
    this.saving = true;
    this.error = '';
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
        this.reload();
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message || 'Save failed';
      },
    });
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

  saveHomepage(): void {
    this.saving = true;
    this.error = '';
    this.http
      .put<ApiResponse<HomepageSection[]>>(
        `${environment.apiBaseUrl}/api/website/admin/homepage`,
        this.sections
      )
      .subscribe({
        next: (res) => {
          this.saving = false;
          this.sections = (res.data || []).map((s) => ({
            type: String(s.type),
            enabled: s.enabled !== false,
            order: Number(s.order || 0),
            content: s.content || {},
          }));
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message || 'Failed to save homepage sections';
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
        next: () => (this.saving = false),
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message || 'Failed to save SEO defaults';
        },
      });
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
