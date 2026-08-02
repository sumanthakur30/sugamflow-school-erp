import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NgIf } from '@angular/common';
import { WebsiteApiService } from '../core/website-api.service';

@Component({
  selector: 'app-cms-page',
  standalone: true,
  imports: [NgIf],
  template: `
    <article *ngIf="page as p; else loading">
      <h1>{{ p['title'] }}</h1>
      <p class="summary" *ngIf="p['summary']">{{ p['summary'] }}</p>
      <div class="body" [innerHTML]="p['bodyHtml']"></div>
    </article>
    <ng-template #loading><p>Loading…</p></ng-template>
  `,
  styles: [
    `
      article {
        background: #fff;
        border-radius: 14px;
        padding: 1.5rem;
        border: 1px solid #e6e9f0;
      }
      .summary {
        color: #64748b;
      }
      .body :where(p, ol, ul) {
        line-height: 1.6;
      }
    `,
  ],
})
export class CmsPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(WebsiteApiService);
  page: Record<string, unknown> | null = null;

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const slug = params.get('slug') || '';
      this.api.resolve().subscribe(() => {
        this.api.getPage(slug).subscribe({
          next: (p) => {
            this.page = p;
            document.title = String(p['seoTitle'] || p['title'] || 'Page');
          },
          error: () => (this.page = { title: 'Not found', bodyHtml: '<p>Page not found.</p>' }),
        });
      });
    });
  }
}
