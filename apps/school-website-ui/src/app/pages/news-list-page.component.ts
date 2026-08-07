import { Component, OnInit, inject } from '@angular/core';
import { DatePipe, NgFor, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WebsiteApiService } from '../core/website-api.service';
import { SeoService } from '../core/seo.service';

@Component({
  selector: 'app-news-list-page',
  standalone: true,
  imports: [NgFor, NgIf, RouterLink, DatePipe],
  template: `
    <section class="page-hero">
      <p class="eyebrow">Updates</p>
      <h1>News</h1>
      <p class="lead">Announcements and stories from campus.</p>
    </section>

    <div class="list" *ngIf="news.length; else empty">
      <article *ngFor="let n of news">
        <div class="cover" *ngIf="mediaUrl(n['coverImageUrl']) as cover">
          <img [src]="cover" [alt]="asText(n['title'], 'News')" loading="lazy" />
        </div>
        <div class="body">
          <p class="date" *ngIf="asDate(n['publishedAt']) as published">
            {{ published | date: 'mediumDate' }}
          </p>
          <h2>
            <a [routerLink]="['/news', n['slug']]">{{ n['title'] }}</a>
          </h2>
          <p>{{ n['summary'] }}</p>
        </div>
      </article>
    </div>
    <ng-template #empty>
      <p class="empty">No published news yet. Schools add stories from Website → News.</p>
    </ng-template>
  `,
  styles: [
    `
      .page-hero {
        padding: 1.5rem 0 1.25rem;
      }
      .eyebrow {
        margin: 0 0 0.4rem;
        letter-spacing: 0.12em;
        text-transform: uppercase;
        font-size: 0.75rem;
        font-weight: 650;
        color: var(--sf-primary, #0b3d91);
      }
      h1 {
        margin: 0 0 0.45rem;
        font-family: 'Fraunces', Georgia, serif;
        font-size: clamp(1.9rem, 4vw, 2.6rem);
      }
      .lead {
        margin: 0;
        color: #64748b;
      }
      .list {
        display: grid;
        gap: 0.9rem;
      }
      article {
        display: grid;
        grid-template-columns: 180px 1fr;
        gap: 1rem;
        background: #fff;
        border: 1px solid #e6e9f0;
        border-radius: 14px;
        overflow: hidden;
      }
      .cover img {
        width: 100%;
        height: 100%;
        min-height: 140px;
        object-fit: cover;
        display: block;
      }
      .body {
        padding: 1rem 1rem 1rem 0;
      }
      .date {
        margin: 0 0 0.35rem;
        color: #94a3b8;
        font-size: 0.85rem;
      }
      h2 {
        margin: 0 0 0.4rem;
        font-size: 1.15rem;
      }
      a {
        color: var(--sf-primary, #0b3d91);
        text-decoration: none;
      }
      .body p:last-child {
        margin: 0;
        color: #64748b;
        line-height: 1.5;
      }
      .empty {
        color: #64748b;
        background: #fff;
        border: 1px dashed #cbd5e1;
        border-radius: 12px;
        padding: 1.25rem;
      }
      @media (max-width: 640px) {
        article {
          grid-template-columns: 1fr;
        }
        .body {
          padding: 0 1rem 1rem;
        }
      }
    `,
  ],
})
export class NewsListPageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  private readonly seo = inject(SeoService);
  news: Array<Record<string, unknown>> = [];

  ngOnInit(): void {
    this.api.resolve().subscribe((site) => {
      this.seo.apply({
        title: `News | ${site.displayName}`,
        description: `Latest news from ${site.displayName}`,
      });
      this.api.listNews().subscribe((rows) => (this.news = rows || []));
    });
  }

  mediaUrl(path?: string | null | unknown): string {
    return this.api.mediaUrl(path == null ? undefined : String(path));
  }

  asText(value: unknown, fallback = ''): string {
    if (value == null) return fallback;
    const s = String(value).trim();
    return s || fallback;
  }

  asDate(value: unknown): string | null {
    if (value == null) return null;
    const s = String(value).trim();
    return s || null;
  }
}
