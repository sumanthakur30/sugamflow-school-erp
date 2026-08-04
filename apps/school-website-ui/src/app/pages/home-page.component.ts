import { Component, OnInit, inject } from '@angular/core';
import { NgFor, NgIf, NgStyle } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WebsiteApiService, WebsiteResolve } from '../core/website-api.service';
import { SeoService } from '../core/seo.service';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [NgFor, NgIf, NgStyle, RouterLink],
  template: `
    <ng-container *ngFor="let section of sections">
      <section
        class="hero"
        *ngIf="section['type'] === 'HERO' && site"
        [class.has-image]="!!heroImageUrl"
        [ngStyle]="heroStyles"
      >
        <div class="hero-inner">
          <p class="eyebrow">CBSE · Nursery to Class VIII · Katihar</p>
          <h1>{{ heroTitle }}</h1>
          <p class="lead">{{ heroSubtitle }}</p>
          <div class="actions">
            <a routerLink="/admission/apply" class="primary">Admissions</a>
            <a routerLink="/contact" class="ghost">Contact</a>
          </div>
        </div>
      </section>

      <section class="spotlight" *ngIf="section['type'] === 'HERO' && gallery.length">
        <h2>Campus life</h2>
        <div class="gallery-row">
          <figure *ngFor="let g of gallery">
            <img [src]="mediaUrl(g['imageUrl'] ?? g['url'])" [alt]="asText(g['title'], 'Campus')" />
            <figcaption>{{ asText(g['title']) }}</figcaption>
          </figure>
        </div>
      </section>

      <section class="grid" *ngIf="section['type'] === 'LATEST_NEWS' && news.length">
        <div class="section-head">
          <h2>Latest news</h2>
          <a routerLink="/news" class="more">View all</a>
        </div>
        <div class="card-grid">
          <article *ngFor="let n of news">
            <h3><a [routerLink]="['/news', n['slug']]">{{ n['title'] }}</a></h3>
            <p>{{ n['summary'] }}</p>
          </article>
        </div>
      </section>

      <section class="grid" *ngIf="section['type'] === 'UPCOMING_EVENTS' && events.length">
        <div class="section-head">
          <h2>Upcoming events</h2>
        </div>
        <div class="card-grid">
          <article *ngFor="let e of events" class="event">
            <h3>{{ e['title'] }}</h3>
            <p>{{ e['summary'] }}</p>
          </article>
        </div>
      </section>

      <section class="cta-band" *ngIf="section['type'] === 'ADMISSION_CTA'">
        <div>
          <h2>{{ admissionTitle }}</h2>
          <p>Start your application online — our team will follow up from the school office.</p>
        </div>
        <a routerLink="/admission/apply" class="primary">{{ admissionCta }}</a>
      </section>
    </ng-container>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .hero {
        position: relative;
        color: #fff;
        border-radius: 0;
        margin: 0 -1rem 2.5rem;
        width: calc(100% + 2rem);
        min-height: min(72vh, 560px);
        display: flex;
        align-items: flex-end;
        background:
          linear-gradient(105deg, rgba(11, 31, 58, 0.72) 0%, rgba(11, 31, 58, 0.35) 48%, rgba(11, 31, 58, 0.15) 100%),
          linear-gradient(135deg, var(--sf-primary, #0b3d91), #0f172a);
        background-size: cover;
        background-position: center;
        overflow: hidden;
      }
      .hero.has-image {
        background-color: #0f172a;
      }
      .hero-inner {
        position: relative;
        z-index: 1;
        padding: clamp(2rem, 6vw, 4rem) clamp(1.25rem, 4vw, 3rem);
        max-width: 38rem;
      }
      .eyebrow {
        opacity: 0.92;
        margin: 0 0 0.65rem;
        letter-spacing: 0.1em;
        text-transform: uppercase;
        font-size: 0.75rem;
        font-weight: 600;
      }
      h1 {
        margin: 0 0 0.85rem;
        font-family: 'Fraunces', Georgia, 'Times New Roman', serif;
        font-size: clamp(2.1rem, 5vw, 3.4rem);
        line-height: 1.1;
        font-weight: 700;
        text-wrap: balance;
      }
      .lead {
        margin: 0;
        max-width: 34rem;
        font-size: 1.05rem;
        line-height: 1.55;
        opacity: 0.95;
      }
      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
        margin-top: 1.5rem;
      }
      .actions a,
      .cta-band a {
        text-decoration: none;
        padding: 0.75rem 1.2rem;
        border-radius: 999px;
        font-weight: 650;
      }
      .primary {
        background: var(--sf-secondary, #f5b700);
        color: #111;
      }
      .ghost {
        border: 1px solid rgba(255, 255, 255, 0.65);
        color: #fff;
        backdrop-filter: blur(4px);
      }
      .spotlight,
      .grid {
        margin-bottom: 2.5rem;
      }
      .section-head {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        gap: 1rem;
        margin-bottom: 1rem;
      }
      .section-head h2,
      .spotlight h2 {
        margin: 0 0 1rem;
        font-family: 'Fraunces', Georgia, 'Times New Roman', serif;
        font-size: 1.65rem;
        color: #0f172a;
      }
      .section-head h2 {
        margin-bottom: 0;
      }
      .more {
        color: var(--sf-primary, #0b3d91);
        text-decoration: none;
        font-weight: 600;
        font-size: 0.95rem;
      }
      .gallery-row {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 0.85rem;
      }
      .gallery-row figure {
        margin: 0;
        border-radius: 14px;
        overflow: hidden;
        background: #fff;
        border: 1px solid #e2e8f0;
      }
      .gallery-row img {
        width: 100%;
        aspect-ratio: 4 / 3;
        object-fit: cover;
        display: block;
      }
      .gallery-row figcaption {
        padding: 0.55rem 0.75rem;
        font-size: 0.85rem;
        color: #475569;
      }
      .card-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
        gap: 0.9rem;
      }
      article {
        background: #fff;
        border: 1px solid #e6e9f0;
        border-radius: 14px;
        padding: 1.15rem 1.2rem;
        box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
        transition: transform 0.2s ease, box-shadow 0.2s ease;
      }
      article:hover {
        transform: translateY(-2px);
        box-shadow: 0 12px 28px rgba(15, 23, 42, 0.08);
      }
      article h3 {
        margin: 0 0 0.45rem;
        font-size: 1.05rem;
      }
      article p {
        margin: 0;
        color: #64748b;
        line-height: 1.45;
      }
      article a {
        color: var(--sf-primary, #0b3d91);
        text-decoration: none;
      }
      .cta-band {
        background: linear-gradient(135deg, var(--sf-primary, #0b3d91), #123f7a);
        color: #fff;
        border: none;
        border-radius: 18px;
        padding: 1.5rem 1.4rem;
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 1rem;
        margin-bottom: 2rem;
        flex-wrap: wrap;
      }
      .cta-band h2 {
        margin: 0 0 0.35rem;
        font-family: 'Fraunces', Georgia, 'Times New Roman', serif;
      }
      .cta-band p {
        margin: 0;
        opacity: 0.9;
      }
      @media (max-width: 700px) {
        .hero {
          min-height: 70vh;
          align-items: center;
        }
      }
    `,
  ],
})
export class HomePageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  private readonly seo = inject(SeoService);
  site: WebsiteResolve | null = this.api.site();
  sections: Array<Record<string, unknown>> = [];
  heroTitle = 'Welcome';
  heroSubtitle = 'A modern school experience.';
  heroImageUrl = '';
  admissionTitle = 'Admissions Open';
  admissionCta = 'Apply Now';
  news: Array<Record<string, unknown>> = [];
  events: Array<Record<string, unknown>> = [];
  gallery: Array<Record<string, unknown>> = [];

  get heroStyles(): Record<string, string> {
    if (!this.heroImageUrl) return {};
    return {
      backgroundImage: `linear-gradient(105deg, rgba(11, 31, 58, 0.7) 0%, rgba(11, 31, 58, 0.32) 45%, rgba(11, 31, 58, 0.12) 100%), url('${this.heroImageUrl}')`,
      backgroundSize: 'cover',
      backgroundPosition: 'center',
    };
  }

  ngOnInit(): void {
    this.api.resolve().subscribe((site) => {
      this.site = site;
      this.sections = this.enabledSections(site.homepage || []);
      const hero = this.sections.find((s) => s['type'] === 'HERO') as
        | { content?: { title?: string; subtitle?: string; imageUrl?: string } }
        | undefined;
      this.heroTitle = this.normalizeHeroTitle(hero?.content?.title, site.displayName);
      this.heroSubtitle =
        hero?.content?.subtitle ||
        'Strong academics, character, and a caring campus community in Mirchaibari, Katihar.';
      const rawImage =
        hero?.content?.imageUrl || site.seo?.['ogImageUrl'] || '';
      this.heroImageUrl = this.mediaUrl(rawImage);

      const cta = this.sections.find((s) => s['type'] === 'ADMISSION_CTA') as
        | { content?: { title?: string; ctaLabel?: string } }
        | undefined;
      this.admissionTitle = cta?.content?.title || 'Admissions Open';
      this.admissionCta = cta?.content?.ctaLabel || 'Apply Now';
      this.seo.apply({
        title: site.seo?.['defaultTitle'] || site.displayName,
        description: site.seo?.['defaultDescription'] || undefined,
        imageUrl: this.mediaUrl(site.seo?.['ogImageUrl'] || undefined),
      });

      if (this.sections.some((s) => s['type'] === 'LATEST_NEWS' || s['type'] === 'ANNOUNCEMENTS')) {
        this.api.listNews().subscribe((rows) => (this.news = (rows || []).slice(0, 3)));
      }
      if (this.sections.some((s) => s['type'] === 'UPCOMING_EVENTS')) {
        this.api.listEvents().subscribe((rows) => (this.events = (rows || []).slice(0, 3)));
      }
      this.api.listGallery().subscribe((rows) => {
        this.gallery = this.campusGalleryItems(rows || []).slice(0, 4);
      });
    });
  }

  asText(value: unknown, fallback = ''): string {
    if (value == null) return fallback;
    const s = String(value).trim();
    return s || fallback;
  }

  mediaUrl(path?: string | null | unknown): string {
    return this.api.mediaUrl(path == null ? undefined : String(path));
  }

  /** Prefer a short brand line over repeating the full school name twice. */
  private normalizeHeroTitle(raw: string | undefined, displayName: string): string {
    const title = (raw || '').trim();
    if (!title || /^welcome to\b/i.test(title) || title === displayName) {
      return 'Where curiosity meets character';
    }
    if (/^hero$/i.test(title)) {
      return 'Where curiosity meets character';
    }
    return title;
  }

  private campusGalleryItems(
    rows: Array<Record<string, unknown>>
  ): Array<Record<string, unknown>> {
    return rows.filter((g) => {
      const title = String(g['title'] || '').trim().toLowerCase();
      if (!title) return true;
      if (title === 'logo' || title.includes('logo') || title.includes('favicon')) return false;
      return true;
    });
  }

  private enabledSections(
    homepage: Array<Record<string, unknown>>
  ): Array<Record<string, unknown>> {
    const sorted = [...homepage]
      .filter((s) => s['enabled'] !== false)
      .sort((a, b) => Number(a['order'] || 0) - Number(b['order'] || 0))
      .map((s) => {
        if (s['type'] === 'ANNOUNCEMENTS') {
          return { ...s, type: 'LATEST_NEWS' };
        }
        return s;
      });

    // One hero only — skip placeholder duplicate HERO blocks from CMS.
    let heroSeen = false;
    return sorted.filter((s) => {
      if (s['type'] !== 'HERO') return true;
      if (heroSeen) return false;
      heroSeen = true;
      return true;
    });
  }
}
