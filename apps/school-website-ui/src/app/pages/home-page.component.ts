import { Component, OnInit, inject } from '@angular/core';
import { NgFor, NgIf, NgStyle } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WebsiteApiService, WebsiteResolve } from '../core/website-api.service';
import { SeoService } from '../core/seo.service';

type StatItem = { label: string; value: string };
type QuickLink = { label: string; path: string };

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
          <p class="eyebrow" *ngIf="heroEyebrow">{{ heroEyebrow }}</p>
          <h1>{{ heroTitle }}</h1>
          <p class="lead" *ngIf="heroSubtitle">{{ heroSubtitle }}</p>
          <div class="actions">
            <a routerLink="/admission/apply" class="primary">{{ heroCta }}</a>
            <a routerLink="/contact" class="ghost">Contact</a>
          </div>
        </div>
      </section>

      <section class="about" *ngIf="section['type'] === 'ABOUT_SCHOOL'">
        <div class="about-copy">
          <p class="eyebrow">About</p>
          <h2>{{ aboutTitle(section) }}</h2>
          <p>{{ aboutBody(section) }}</p>
          <a routerLink="/about" class="text-link">{{ aboutCta(section) }}</a>
        </div>
        <div class="about-panel" aria-hidden="true">
          <div class="about-accent"></div>
          <p>{{ aboutHighlight(section) }}</p>
        </div>
      </section>

      <section class="stats" *ngIf="section['type'] === 'STATISTICS'">
        <h2 class="sr-only">{{ statsTitle(section) }}</h2>
        <div class="stats-grid">
          <div *ngFor="let item of statsItems(section)">
            <strong>{{ item.value }}</strong>
            <span>{{ item.label }}</span>
          </div>
        </div>
      </section>

      <section class="principal" *ngIf="section['type'] === 'PRINCIPAL_MESSAGE'">
        <div class="principal-card">
          <div class="principal-photo" *ngIf="principalPhoto(section) as photo; else principalMark">
            <img [src]="photo" [alt]="principalName(section)" />
          </div>
          <ng-template #principalMark>
            <div class="principal-mark">{{ principalInitials(section) }}</div>
          </ng-template>
          <blockquote>
            <p class="eyebrow">{{ principalTitle(section) }}</p>
            <p class="quote">“{{ principalMessage(section) }}”</p>
            <footer>
              <strong>{{ principalName(section) }}</strong>
              <span>{{ principalRole(section) }}</span>
            </footer>
          </blockquote>
        </div>
      </section>

      <section class="quick" *ngIf="section['type'] === 'QUICK_LINKS'">
        <div class="section-head">
          <h2>{{ quickTitle(section) }}</h2>
        </div>
        <div class="quick-grid">
          <a *ngFor="let link of quickLinks(section)" [routerLink]="link.path">{{ link.label }}</a>
        </div>
      </section>

      <section class="grid" *ngIf="section['type'] === 'LATEST_NEWS' && news.length">
        <div class="section-head">
          <h2>Latest news</h2>
          <a routerLink="/news" class="more">View all</a>
        </div>
        <div class="card-grid">
          <article *ngFor="let n of news">
            <h3>
              <a [routerLink]="['/news', n['slug']]">{{ n['title'] }}</a>
            </h3>
            <p>{{ n['summary'] }}</p>
          </article>
        </div>
      </section>

      <section
        class="spotlight"
        *ngIf="(section['type'] === 'GALLERY' || section['type'] === 'HERO') && gallery.length && showGalleryBlock(section)"
      >
        <div class="section-head">
          <h2>{{ galleryTitle(section) }}</h2>
          <a routerLink="/gallery" class="more">Full gallery</a>
        </div>
        <div class="gallery-row">
          <figure *ngFor="let g of gallery">
            <img [src]="mediaUrl(g['imageUrl'] ?? g['url'])" [alt]="asText(g['title'], 'Campus')" />
            <figcaption>{{ asText(g['title']) }}</figcaption>
          </figure>
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
      .sr-only {
        position: absolute;
        width: 1px;
        height: 1px;
        padding: 0;
        margin: -1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
        border: 0;
      }
      .hero {
        position: relative;
        color: #fff;
        margin: 0 -1rem 2.75rem;
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
        animation: fade-up 0.7s ease both;
      }
      .hero.has-image {
        background-color: #0f172a;
      }
      .hero-inner {
        position: relative;
        z-index: 1;
        padding: clamp(2rem, 6vw, 4rem) clamp(1.25rem, 4vw, 3rem);
        max-width: 40rem;
      }
      .eyebrow {
        opacity: 0.92;
        margin: 0 0 0.65rem;
        letter-spacing: 0.1em;
        text-transform: uppercase;
        font-size: 0.75rem;
        font-weight: 600;
        color: inherit;
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
      .about,
      .principal,
      .quick,
      .spotlight,
      .grid,
      .stats {
        margin-bottom: 2.75rem;
        animation: fade-up 0.65s ease both;
      }
      .about {
        display: grid;
        grid-template-columns: 1.4fr 1fr;
        gap: 1.5rem;
        align-items: stretch;
      }
      .about-copy h2,
      .section-head h2,
      .spotlight h2 {
        margin: 0 0 0.85rem;
        font-family: 'Fraunces', Georgia, 'Times New Roman', serif;
        font-size: 1.75rem;
        color: #0f172a;
      }
      .about-copy p {
        margin: 0 0 1rem;
        color: #475569;
        line-height: 1.65;
        font-size: 1.05rem;
      }
      .text-link {
        color: var(--sf-primary, #0b3d91);
        font-weight: 650;
        text-decoration: none;
      }
      .about-panel {
        border-radius: 18px;
        background: linear-gradient(160deg, #0f172a, var(--sf-primary, #0b3d91));
        color: #fff;
        padding: 1.75rem;
        display: flex;
        flex-direction: column;
        justify-content: flex-end;
        min-height: 220px;
        position: relative;
        overflow: hidden;
      }
      .about-accent {
        position: absolute;
        inset: auto -20% -30% auto;
        width: 220px;
        height: 220px;
        border-radius: 50%;
        background: var(--sf-secondary, #f5b700);
        opacity: 0.25;
      }
      .about-panel p {
        position: relative;
        margin: 0;
        font-family: 'Fraunces', Georgia, serif;
        font-size: 1.35rem;
        line-height: 1.35;
      }
      .stats-grid {
        display: grid;
        grid-template-columns: repeat(4, minmax(0, 1fr));
        gap: 0.85rem;
        background: #fff;
        border: 1px solid #e2e8f0;
        border-radius: 18px;
        padding: 1.25rem;
      }
      .stats-grid div {
        text-align: center;
        padding: 0.5rem;
      }
      .stats-grid strong {
        display: block;
        font-family: 'Fraunces', Georgia, serif;
        font-size: clamp(1.6rem, 3vw, 2.1rem);
        color: var(--sf-primary, #0b3d91);
        line-height: 1.1;
      }
      .stats-grid span {
        color: #64748b;
        font-size: 0.92rem;
      }
      .principal-card {
        display: grid;
        grid-template-columns: auto 1fr;
        gap: 1.25rem;
        align-items: center;
        background: #fff;
        border: 1px solid #e2e8f0;
        border-radius: 18px;
        padding: 1.35rem 1.5rem;
      }
      .principal-photo,
      .principal-mark {
        width: 96px;
        height: 96px;
        border-radius: 50%;
        overflow: hidden;
        background: #e2e8f0;
      }
      .principal-photo img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        display: block;
      }
      .principal-mark {
        display: grid;
        place-items: center;
        font-family: 'Fraunces', Georgia, serif;
        font-size: 1.5rem;
        color: var(--sf-primary, #0b3d91);
        font-weight: 700;
      }
      blockquote {
        margin: 0;
      }
      .quote {
        margin: 0.35rem 0 0.85rem;
        font-size: 1.08rem;
        line-height: 1.55;
        color: #334155;
      }
      blockquote footer {
        display: grid;
        gap: 0.15rem;
      }
      blockquote footer span {
        color: #64748b;
        font-size: 0.92rem;
      }
      .quick-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
        gap: 0.75rem;
      }
      .quick-grid a {
        text-decoration: none;
        background: #fff;
        border: 1px solid #e2e8f0;
        border-radius: 14px;
        padding: 1rem 1.1rem;
        color: #0f172a;
        font-weight: 650;
        transition: transform 0.2s ease, border-color 0.2s ease;
      }
      .quick-grid a:hover {
        transform: translateY(-2px);
        border-color: var(--sf-primary, #0b3d91);
      }
      .section-head {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        gap: 1rem;
        margin-bottom: 1rem;
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
        transition: transform 0.35s ease;
      }
      .gallery-row figure:hover img {
        transform: scale(1.04);
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
      @keyframes fade-up {
        from {
          opacity: 0;
          transform: translateY(12px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }
      @media (max-width: 800px) {
        .about,
        .principal-card {
          grid-template-columns: 1fr;
        }
        .stats-grid {
          grid-template-columns: repeat(2, minmax(0, 1fr));
        }
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
  heroSubtitle = '';
  heroEyebrow = '';
  heroImageUrl = '';
  heroCta = 'Admissions';
  admissionTitle = 'Admissions Open';
  admissionCta = 'Apply Now';
  news: Array<Record<string, unknown>> = [];
  events: Array<Record<string, unknown>> = [];
  gallery: Array<Record<string, unknown>> = [];
  private hasDedicatedGallery = false;

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
      const brand = this.api.brandContact(site);
      this.heroEyebrow = brand.tagline;
      this.sections = this.enabledSections(site.homepage || []);
      this.hasDedicatedGallery = this.sections.some((s) => s['type'] === 'GALLERY');

      const hero = this.sections.find((s) => s['type'] === 'HERO') as
        | {
            content?: {
              title?: string;
              subtitle?: string;
              imageUrl?: string;
              eyebrow?: string;
              ctaLabel?: string;
            };
          }
        | undefined;
      if (hero?.content?.eyebrow) {
        this.heroEyebrow = String(hero.content.eyebrow).trim() || this.heroEyebrow;
      }
      this.heroTitle = this.normalizeHeroTitle(hero?.content?.title, site.displayName);
      this.heroSubtitle =
        (hero?.content?.subtitle || '').trim() ||
        brand.footerBlurb ||
        (site.seo?.['defaultDescription'] || '').trim();
      this.heroCta = (hero?.content?.ctaLabel || '').trim() || 'Admissions';
      const rawImage = hero?.content?.imageUrl || site.seo?.['ogImageUrl'] || '';
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
      if (
        this.sections.some((s) => s['type'] === 'GALLERY' || s['type'] === 'HERO')
      ) {
        this.api.listGallery().subscribe((rows) => {
          this.gallery = this.campusGalleryItems(rows || []).slice(0, 4);
        });
      }
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

  showGalleryBlock(section: Record<string, unknown>): boolean {
    if (section['type'] === 'GALLERY') return true;
    // Legacy: show campus strip under hero only when no dedicated GALLERY section exists.
    return section['type'] === 'HERO' && !this.hasDedicatedGallery;
  }

  aboutTitle(section: Record<string, unknown>): string {
    return this.contentText(section, 'title', 'A place where learning feels personal');
  }

  aboutBody(section: Record<string, unknown>): string {
    return (
      this.contentText(section, 'body') ||
      this.contentText(section, 'subtitle') ||
      this.site?.seo?.['defaultDescription'] ||
      'Strong academics, character, and a caring campus community for every learner.'
    );
  }

  aboutCta(section: Record<string, unknown>): string {
    return this.contentText(section, 'ctaLabel', 'Learn more about us');
  }

  aboutHighlight(section: Record<string, unknown>): string {
    return this.contentText(
      section,
      'highlight',
      'Academics · Values · Campus life'
    );
  }

  statsTitle(section: Record<string, unknown>): string {
    return this.contentText(section, 'title', 'At a glance');
  }

  statsItems(section: Record<string, unknown>): StatItem[] {
    const content = (section['content'] || {}) as Record<string, unknown>;
    const raw = content['items'];
    if (Array.isArray(raw) && raw.length) {
      return raw
        .map((row) => {
          const item = (row || {}) as Record<string, unknown>;
          return {
            label: String(item['label'] || '').trim(),
            value: String(item['value'] || '').trim(),
          };
        })
        .filter((i) => i.label && i.value)
        .slice(0, 4);
    }
    const pairs: StatItem[] = [];
    for (let i = 1; i <= 4; i++) {
      const label = String(content[`label${i}`] || '').trim();
      const value = String(content[`value${i}`] || '').trim();
      if (label && value) pairs.push({ label, value });
    }
    if (pairs.length) return pairs;
    return [
      { label: 'Students', value: '500+' },
      { label: 'Teachers', value: '40+' },
      { label: 'Years', value: '25+' },
      { label: 'Clubs', value: '20+' },
    ];
  }

  principalTitle(section: Record<string, unknown>): string {
    return this.contentText(section, 'title', 'From the Principal');
  }

  principalName(section: Record<string, unknown>): string {
    return this.contentText(section, 'name', 'Principal');
  }

  principalRole(section: Record<string, unknown>): string {
    return this.contentText(section, 'role', 'Principal');
  }

  principalMessage(section: Record<string, unknown>): string {
    return (
      this.contentText(section, 'message') ||
      this.contentText(section, 'body') ||
      this.contentText(section, 'subtitle') ||
      'Every child deserves a safe campus, caring teachers, and the confidence to grow.'
    );
  }

  principalPhoto(section: Record<string, unknown>): string {
    const url = this.contentText(section, 'photoUrl') || this.contentText(section, 'imageUrl');
    return url ? this.mediaUrl(url) : '';
  }

  principalInitials(section: Record<string, unknown>): string {
    const name = this.principalName(section);
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0]?.toUpperCase() || '')
      .join('');
  }

  quickTitle(section: Record<string, unknown>): string {
    return this.contentText(section, 'title', 'Quick links');
  }

  quickLinks(section: Record<string, unknown>): QuickLink[] {
    const content = (section['content'] || {}) as Record<string, unknown>;
    const raw = content['links'];
    if (Array.isArray(raw) && raw.length) {
      return raw
        .map((row) => {
          const item = (row || {}) as Record<string, unknown>;
          return {
            label: String(item['label'] || '').trim(),
            path: String(item['path'] || '').trim() || '/',
          };
        })
        .filter((i) => i.label)
        .slice(0, 6);
    }
    return [
      { label: 'About', path: '/about' },
      { label: 'Faculty', path: '/faculty' },
      { label: 'Admissions', path: '/admission' },
      { label: 'Gallery', path: '/gallery' },
      { label: 'News', path: '/news' },
      { label: 'Contact', path: '/contact' },
    ];
  }

  galleryTitle(section: Record<string, unknown>): string {
    if (section['type'] === 'HERO') return 'Campus life';
    return this.contentText(section, 'title', 'Campus life');
  }

  private contentText(
    section: Record<string, unknown>,
    key: string,
    fallback = ''
  ): string {
    const content = (section['content'] || {}) as Record<string, unknown>;
    const value = content[key];
    if (value == null) return fallback;
    const s = String(value).trim();
    return s || fallback;
  }

  private normalizeHeroTitle(raw: string | undefined, displayName: string): string {
    const title = (raw || '').trim();
    if (!title || /^hero$/i.test(title)) {
      return displayName || 'Welcome';
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

    let heroSeen = false;
    return sorted.filter((s) => {
      if (s['type'] !== 'HERO') return true;
      if (heroSeen) return false;
      heroSeen = true;
      return true;
    });
  }
}
