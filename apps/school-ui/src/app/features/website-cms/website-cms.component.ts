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
  pages: PageRow[] = [];
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

  startCreate(): void {
    this.editing = null;
    this.draft = this.emptyDraft();
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
