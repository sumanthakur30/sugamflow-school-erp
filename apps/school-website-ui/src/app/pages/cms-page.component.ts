import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NgIf } from '@angular/common';
import { WebsiteApiService } from '../core/website-api.service';
import { SeoService } from '../core/seo.service';

@Component({
  selector: 'app-cms-page',
  standalone: true,
  imports: [NgIf],
  template: `
    <section class="page-hero" *ngIf="page as p">
      <p class="eyebrow">School information</p>
      <h1>{{ p['title'] }}</h1>
      <p class="summary" *ngIf="p['summary']">{{ p['summary'] }}</p>
    </section>
    <article *ngIf="page as p; else loading">
      <div class="body" [innerHTML]="p['bodyHtml']"></div>
    </article>
    <ng-template #loading><p class="loading">Loading…</p></ng-template>
  `,
  styles: [
    `
      .page-hero {
        padding: 2rem 0 1rem;
      }
      .eyebrow {
        margin: 0 0 0.35rem;
        letter-spacing: 0.12em;
        text-transform: uppercase;
        font-size: 0.75rem;
        font-weight: 650;
        color: var(--sf-primary, #0b3d91);
      }
      h1 {
        margin: 0 0 0.45rem;
        font-family: 'Fraunces', Georgia, serif;
        font-size: clamp(1.8rem, 4vw, 2.5rem);
        color: #0b1f3a;
      }
      .summary {
        margin: 0;
        color: #64748b;
        max-width: 40rem;
        font-size: 1.05rem;
      }
      article {
        background: #fff;
        border-radius: 18px;
        padding: 1.5rem 1.4rem;
        border: 1px solid #e6ebf2;
        box-shadow: 0 10px 30px rgba(15, 23, 42, 0.04);
      }
      .body :where(p, ol, ul) {
        line-height: 1.65;
        color: #334155;
      }
      .body :where(h2, h3) {
        font-family: 'Fraunces', Georgia, serif;
        color: #0b1f3a;
      }
      .body img {
        border-radius: 14px;
      }
      .loading {
        padding: 2rem 0;
        color: #64748b;
      }
    `,
  ],
})
export class CmsPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(WebsiteApiService);
  private readonly seo = inject(SeoService);
  page: Record<string, unknown> | null = null;

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const slug = params.get('slug') || '';
      this.api.resolve().subscribe(() => {
        this.api.getPage(slug).subscribe({
          next: (p) => {
            this.page = p;
            this.seo.apply({
              title: String(p['seoTitle'] || p['title'] || 'Page'),
              description: String(p['seoDescription'] || p['summary'] || ''),
            });
          },
          error: () => (this.page = { title: 'Not found', bodyHtml: '<p>Page not found.</p>' }),
        });
      });
    });
  }
}
