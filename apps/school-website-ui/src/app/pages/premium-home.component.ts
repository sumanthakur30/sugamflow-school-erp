import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DomSanitizer, SafeHtml, SafeResourceUrl } from '@angular/platform-browser';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { WebsiteApiService, WebsiteResolve } from '../core/website-api.service';

type Card = { title: string; body: string; image: string };
type LinkCard = { title: string; path: string; cta: string; image: string };
type Stat = { value: string; label: string };
type Quote = { quote: string; name: string; role: string };
type Step = { label: string };
type HomeBlock = {
  type: string;
  eyebrow: string;
  title: string;
  subtitle: string;
  body: string;
  image: string;
  imageAlt: string;
  images: Array<{ url: string; alt: string }>;
  ctaLabel: string;
  secondaryCtaLabel: string;
  ctaPath: string;
  secondaryCtaPath: string;
  name: string;
  role: string;
  message: string;
  photo: string;
  cards: Card[];
  links: LinkCard[];
  stats: Stat[];
  quotes: Quote[];
  steps: Step[];
  html: string;
  placeholderTitle: string;
  placeholderBody: string;
};

@Component({
  selector: 'app-premium-home',
  standalone: true,
  imports: [NgFor, NgIf, RouterLink],
  template: `
    <ng-container *ngFor="let block of blocks">
      <section class="hero" *ngIf="block.type === 'HERO'" [class.no-photo]="!heroImage">
        <div
          class="hero-slide"
          *ngIf="heroImage as photo"
          [style.backgroundImage]="'url(' + photo.url + ')'"
          role="img"
          [attr.aria-label]="photo.alt"
        ></div>
        <div class="hero-shade"></div>
        <div class="hero-copy">
          <p class="eyebrow" *ngIf="block.eyebrow">{{ block.eyebrow }}</p>
          <h1>{{ block.title }}</h1>
          <p class="lead" *ngIf="block.subtitle">{{ block.subtitle }}</p>
          <div class="actions">
            <a *ngIf="block.secondaryCtaLabel" [routerLink]="block.secondaryCtaPath" class="btn light">{{
              block.secondaryCtaLabel
            }}</a>
            <a *ngIf="block.ctaLabel" [routerLink]="block.ctaPath" class="btn gold">{{ block.ctaLabel }}</a>
          </div>
        </div>
        <div class="hero-nav" *ngIf="block.images.length > 1">
          <button type="button" (click)="stepHero(-1)">Previous</button>
          <button
            type="button"
            *ngFor="let img of block.images; let i = index"
            [class.on]="i === heroIndex"
            (click)="goHero(i)"
            [attr.aria-label]="'Slide ' + (i + 1)"
          ></button>
          <button type="button" (click)="stepHero(1)">Next</button>
        </div>
      </section>

      <section class="band" *ngIf="block.type === 'ABOUT_SCHOOL'">
        <div class="wrap split">
          <div>
            <p class="eyebrow" *ngIf="block.eyebrow">{{ block.eyebrow }}</p>
            <h2>{{ block.title }}</h2>
            <p class="prose">{{ block.body }}</p>
            <a *ngIf="block.ctaLabel" [routerLink]="block.ctaPath" class="text">{{ block.ctaLabel }} →</a>
          </div>
          <img *ngIf="block.image" [src]="block.image" [alt]="block.imageAlt || block.title" loading="lazy" />
        </div>
      </section>

      <section class="band soft" *ngIf="block.type === 'WHY_CHOOSE' && block.cards.length">
        <div class="wrap">
          <p class="eyebrow" *ngIf="block.eyebrow">{{ block.eyebrow }}</p>
          <h2>{{ block.title }}</h2>
          <div class="aim-grid">
            <article *ngFor="let aim of block.cards">
              <h3>{{ aim.title }}</h3>
              <p>{{ aim.body }}</p>
            </article>
          </div>
        </div>
      </section>

      <section class="band" *ngIf="block.type === 'QUICK_LINKS' && block.links.length">
        <div class="wrap">
          <p class="eyebrow" *ngIf="block.eyebrow">{{ block.eyebrow }}</p>
          <h2>{{ block.title }}</h2>
          <div class="explore">
            <a *ngFor="let card of block.links" [routerLink]="card.path" class="explore-card">
              <img *ngIf="card.image" [src]="card.image" [alt]="card.title" loading="lazy" />
              <span>
                <strong>{{ card.title }}</strong>
                <em *ngIf="card.cta">{{ card.cta }}</em>
              </span>
            </a>
          </div>
        </div>
      </section>

      <section
        class="band"
        *ngIf="block.type === 'DIRECTOR_MESSAGE' || block.type === 'PRINCIPAL_MESSAGE'"
      >
        <div class="wrap founder">
          <img *ngIf="block.photo" [src]="block.photo" [alt]="block.name || block.title" loading="lazy" />
          <blockquote>
            <p class="eyebrow" *ngIf="block.title">{{ block.title }}</p>
            <p class="quote" *ngIf="block.message">“{{ block.message }}”</p>
            <p class="placeholder" *ngIf="!block.message">{{ block.placeholderBody }}</p>
            <footer *ngIf="block.name || block.role">
              <strong *ngIf="block.name">{{ block.name }}</strong>
              <span *ngIf="block.role">{{ block.role }}</span>
            </footer>
          </blockquote>
        </div>
      </section>

      <section class="band soft" *ngIf="block.type === 'STATISTICS' && block.stats.length">
        <div class="wrap">
          <h2 *ngIf="block.title">{{ block.title }}</h2>
          <div class="aim-grid">
            <article *ngFor="let stat of block.stats">
              <h3>{{ stat.value }}</h3>
              <p>{{ stat.label }}</p>
            </article>
          </div>
        </div>
      </section>

      <section class="band" *ngIf="block.type === 'FACILITIES'">
        <div class="wrap split">
          <img *ngIf="block.image" [src]="block.image" [alt]="block.imageAlt || block.title" loading="lazy" />
          <div>
            <p class="eyebrow" *ngIf="block.eyebrow">{{ block.eyebrow }}</p>
            <h2>{{ block.title }}</h2>
            <p class="prose" *ngIf="block.body">{{ block.body }}</p>
            <ul *ngIf="block.cards.length">
              <li *ngFor="let item of block.cards">{{ item.title }}</li>
            </ul>
            <a *ngIf="block.ctaLabel" [routerLink]="block.ctaPath" class="text">{{ block.ctaLabel }} →</a>
          </div>
        </div>
      </section>

      <section class="band soft" *ngIf="block.type === 'ACADEMIC_PROGRAMS' && block.cards.length">
        <div class="wrap">
          <p class="eyebrow" *ngIf="block.eyebrow">{{ block.eyebrow }}</p>
          <h2>{{ block.title }}</h2>
          <div class="aim-grid">
            <article *ngFor="let item of block.cards">
              <img *ngIf="item.image" [src]="item.image" [alt]="item.title" loading="lazy" />
              <h3>{{ item.title }}</h3>
              <p>{{ item.body }}</p>
            </article>
          </div>
        </div>
      </section>

      <section class="band" *ngIf="block.type === 'GALLERY'">
        <div class="wrap">
          <div class="section-head">
            <div>
              <p class="eyebrow" *ngIf="block.eyebrow">{{ block.eyebrow }}</p>
              <h2>{{ block.title }}</h2>
            </div>
            <a routerLink="/gallery" class="text">View full gallery →</a>
          </div>
          <div class="masonry" *ngIf="spotlight.length; else galleryEmpty">
            <a
              *ngFor="let photo of spotlight; let i = index"
              routerLink="/gallery"
              class="shot"
              [class.feature]="i === 0"
            >
              <img [src]="photo.image" [alt]="photo.title" loading="lazy" />
              <span>
                <small>{{ photo.body }}</small>
                {{ photo.title }}
              </span>
            </a>
          </div>
          <ng-template #galleryEmpty>
            <article class="placeholder">
              <h3>{{ block.placeholderTitle }}</h3>
              <p>{{ block.placeholderBody }}</p>
            </article>
          </ng-template>
        </div>
      </section>

      <section class="band soft" *ngIf="block.type === 'LATEST_NEWS'">
        <div class="wrap">
          <p class="eyebrow" *ngIf="block.eyebrow">{{ block.eyebrow }}</p>
          <h2>{{ block.title }}</h2>
          <div class="news-grid" *ngIf="news.length; else newsEmpty">
            <article *ngFor="let item of news">
              <p class="meta">{{ item.category }}</p>
              <h3><a [routerLink]="['/news', item.slug]">{{ item.title }}</a></h3>
              <p>{{ item.summary }}</p>
            </article>
          </div>
          <ng-template #newsEmpty>
            <article class="placeholder">
              <h3>{{ block.placeholderTitle }}</h3>
              <p>{{ block.placeholderBody }}</p>
            </article>
          </ng-template>
        </div>
      </section>

      <section class="band" *ngIf="block.type === 'UPCOMING_EVENTS'">
        <div class="wrap">
          <p class="eyebrow" *ngIf="block.eyebrow">{{ block.eyebrow }}</p>
          <h2>{{ block.title }}</h2>
          <div class="news-grid" *ngIf="events.length; else eventEmpty">
            <article *ngFor="let item of events">
              <p class="meta">{{ item.when }}</p>
              <h3>{{ item.title }}</h3>
              <p>{{ item.summary }}</p>
            </article>
          </div>
          <ng-template #eventEmpty>
            <article class="placeholder">
              <h3>{{ block.placeholderTitle }}</h3>
              <p>{{ block.placeholderBody }}</p>
            </article>
          </ng-template>
        </div>
      </section>

      <section class="admit" *ngIf="block.type === 'ADMISSION_CTA'">
        <div class="wrap admit-inner">
          <div>
            <h2>{{ block.title }}</h2>
            <p>{{ block.body }}</p>
            <div class="actions">
              <a [routerLink]="block.ctaPath" class="btn gold">{{ block.ctaLabel || 'Start application' }}</a>
              <a *ngIf="block.secondaryCtaLabel" [routerLink]="block.secondaryCtaPath" class="btn light">{{
                block.secondaryCtaLabel
              }}</a>
            </div>
          </div>
          <ol *ngIf="block.steps.length">
            <li *ngFor="let step of block.steps; let i = index">
              <span>0{{ i + 1 }}</span> {{ step.label }}
            </li>
          </ol>
        </div>
      </section>

      <section class="band" *ngIf="block.type === 'TESTIMONIALS'">
        <div class="wrap">
          <p class="eyebrow" *ngIf="block.eyebrow">{{ block.eyebrow }}</p>
          <h2>{{ block.title }}</h2>
          <div class="news-grid" *ngIf="block.quotes.length; else quoteEmpty">
            <article *ngFor="let quote of block.quotes">
              <p class="quote">“{{ quote.quote }}”</p>
              <h3>{{ quote.name }}</h3>
              <p>{{ quote.role }}</p>
            </article>
          </div>
          <ng-template #quoteEmpty>
            <article class="placeholder">
              <h3>{{ block.placeholderTitle }}</h3>
              <p>{{ block.placeholderBody }}</p>
            </article>
          </ng-template>
        </div>
      </section>

      <section class="band soft" *ngIf="block.type === 'DOCUMENTS'">
        <div class="wrap">
          <p class="eyebrow" *ngIf="block.eyebrow">{{ block.eyebrow }}</p>
          <h2>{{ block.title }}</h2>
          <ul class="docs" *ngIf="documents.length; else docsEmpty">
            <li *ngFor="let doc of documents">
              <a [href]="doc.url" target="_blank" rel="noopener">{{ doc.title }}</a>
              <span>{{ doc.category }}</span>
            </li>
          </ul>
          <ng-template #docsEmpty>
            <article class="placeholder">
              <h3>{{ block.placeholderTitle }}</h3>
              <p>{{ block.placeholderBody }}</p>
            </article>
          </ng-template>
          <a routerLink="/resources" class="text">All documents →</a>
        </div>
      </section>

      <section class="band" *ngIf="block.type === 'STAFF'">
        <div class="wrap">
          <p class="eyebrow" *ngIf="block.eyebrow">{{ block.eyebrow }}</p>
          <h2>{{ block.title }}</h2>
          <div class="aim-grid" *ngIf="profiles.length; else staffEmpty">
            <article *ngFor="let person of profiles">
              <img *ngIf="person.photo" [src]="person.photo" [alt]="person.name" loading="lazy" />
              <h3>{{ person.name }}</h3>
              <p>{{ person.role }}</p>
            </article>
          </div>
          <ng-template #staffEmpty>
            <article class="placeholder">
              <h3>{{ block.placeholderTitle }}</h3>
              <p>{{ block.placeholderBody }}</p>
            </article>
          </ng-template>
        </div>
      </section>

      <section class="band" *ngIf="block.type === 'FAQ' && block.cards.length">
        <div class="wrap">
          <h2>{{ block.title }}</h2>
          <article *ngFor="let item of block.cards" class="faq">
            <h3>{{ item.title }}</h3>
            <p>{{ item.body }}</p>
          </article>
        </div>
      </section>

      <section class="band" *ngIf="block.type === 'CONTACT'">
        <div class="wrap split">
          <div>
            <p class="eyebrow" *ngIf="block.eyebrow">{{ block.eyebrow }}</p>
            <h2>{{ block.title }}</h2>
            <p *ngIf="brand.address">{{ brand.address }}</p>
            <p *ngIf="brand.addressLine2">{{ brand.addressLine2 }}</p>
            <p *ngIf="brand.contactPhone">
              <a [href]="api.telHref(brand.contactPhone)">{{ brand.contactPhone }}</a>
            </p>
            <p *ngIf="brand.contactEmail">
              <a [href]="api.mailtoHref(brand.contactEmail)">{{ brand.contactEmail }}</a>
            </p>
            <p *ngIf="brand.workingHours">{{ brand.workingHours }}</p>
            <a *ngIf="mapLink" [href]="mapLink" target="_blank" rel="noopener" class="text">Open in Google Maps →</a>
          </div>
          <iframe *ngIf="mapFrame" class="map" [src]="mapFrame" title="School location" loading="lazy"></iframe>
        </div>
      </section>

      <section class="band" *ngIf="block.type === 'CUSTOM_HTML' && block.html">
        <div class="wrap prose" [innerHTML]="trusted(block.html)"></div>
      </section>
    </ng-container>
  `,
  styles: [
    `
      :host {
        display: block;
        color: #1c2430;
        font-family: var(--sf-sans, Manrope, sans-serif);
      }
      .wrap {
        width: min(1120px, calc(100% - 2.5rem));
        margin: 0 auto;
      }
      .eyebrow {
        margin: 0 0 0.45rem;
        letter-spacing: 0.16em;
        text-transform: uppercase;
        font-size: 0.72rem;
        font-weight: 700;
        color: var(--sf-secondary, #c6a15b);
      }
      h1, h2, h3 {
        font-family: var(--sf-serif, 'Playfair Display', Georgia, serif);
        font-weight: 500;
        color: var(--sf-primary, #1b4d3e);
      }
      h2 {
        margin: 0 0 1rem;
        font-size: clamp(1.8rem, 4vw, 2.8rem);
        line-height: 1.12;
      }
      .prose, .aim-grid p, .news-grid p {
        line-height: 1.65;
        color: #3d4a44;
      }
      .band {
        padding: 4.2rem 0;
      }
      .band.soft {
        background: #f3efe6;
      }
      .hero {
        position: relative;
        min-height: min(82vh, 720px);
        display: grid;
        align-items: end;
        color: #fff;
        overflow: hidden;
        background: #14382c;
      }
      .hero-slide {
        position: absolute;
        inset: 0;
        background-size: cover;
        background-position: center;
        animation: fade 0.8s ease;
      }
      .hero-shade {
        position: absolute;
        inset: 0;
        background: linear-gradient(90deg, rgba(16, 48, 38, 0.82), rgba(16, 48, 38, 0.28));
      }
      .hero-copy, .hero-nav {
        position: relative;
        z-index: 1;
      }
      .hero-copy {
        width: min(1120px, calc(100% - 2.5rem));
        margin: 0 auto;
        padding: 6.5rem 0 4.5rem;
      }
      h1 {
        margin: 0;
        max-width: 14ch;
        color: #fff;
        font-size: clamp(2.6rem, 6vw, 4.8rem);
        line-height: 1.02;
      }
      .lead {
        max-width: 36rem;
        font-size: 1.08rem;
        line-height: 1.6;
      }
      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
        margin-top: 1.25rem;
      }
      .btn {
        display: inline-flex;
        align-items: center;
        text-decoration: none;
        border-radius: 999px;
        padding: 0.75rem 1.15rem;
        font-weight: 700;
      }
      .btn.gold {
        background: var(--sf-secondary, #c4a35a);
        color: #1a1408;
      }
      .btn.light {
        background: transparent;
        color: #fff;
        border: 1px solid rgba(255, 255, 255, 0.7);
      }
      .hero-nav {
        display: flex;
        gap: 0.45rem;
        align-items: center;
        width: min(1120px, calc(100% - 2.5rem));
        margin: 0 auto 1.4rem;
      }
      .hero-nav button {
        border: 0;
        background: rgba(255, 255, 255, 0.2);
        color: #fff;
        border-radius: 999px;
        padding: 0.35rem 0.7rem;
        cursor: pointer;
      }
      .hero-nav button.on {
        background: var(--sf-secondary, #c4a35a);
      }
      .split, .founder, .aim-grid, .news-grid, .explore {
        display: grid;
        gap: 1.25rem;
      }
      .split, .founder {
        grid-template-columns: 1.1fr 0.9fr;
        align-items: center;
      }
      .aim-grid, .news-grid {
        grid-template-columns: repeat(3, 1fr);
      }
      .explore {
        grid-template-columns: 1.4fr 1fr 1fr;
      }
      img, .shot {
        width: 100%;
        object-fit: cover;
        border-radius: 18px;
      }
      .split img, .founder img, .shot {
        min-height: 240px;
        max-height: 420px;
      }
      .aim-grid article, .news-grid article, .placeholder, .faq {
        background: #fff;
        border-radius: 16px;
        padding: 1.1rem 1.15rem;
        box-shadow: 0 10px 30px rgba(27, 77, 62, 0.06);
      }
      .aim-grid img {
        height: 140px;
        margin-bottom: 0.7rem;
      }
      .explore-card {
        position: relative;
        min-height: 220px;
        overflow: hidden;
        border-radius: 18px;
        color: #fff;
        text-decoration: none;
      }
      .explore-card img {
        height: 100%;
        min-height: 220px;
      }
      .explore-card span {
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        padding: 1rem;
        background: linear-gradient(transparent, rgba(12, 32, 26, 0.82));
      }
      .masonry {
        display: grid;
        grid-template-columns: 1.4fr 1fr 1fr;
        gap: 0.8rem;
      }
      .shot {
        position: relative;
        display: block;
        color: #fff;
        text-decoration: none;
        overflow: hidden;
      }
      .shot.feature {
        grid-row: span 2;
      }
      .shot span {
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        padding: 0.8rem;
        background: linear-gradient(transparent, rgba(12, 32, 26, 0.8));
      }
      .section-head, .admit-inner {
        display: flex;
        justify-content: space-between;
        gap: 1.5rem;
        align-items: end;
      }
      .text {
        color: var(--sf-primary, #1b4d3e);
        font-weight: 700;
        text-decoration: none;
      }
      .quote {
        font-family: var(--sf-serif, Georgia, serif);
        font-size: 1.25rem;
        line-height: 1.45;
      }
      .admit {
        background: #1b4d3e;
        color: #fff;
        padding: 3.5rem 0;
      }
      .admit h2, .admit p {
        color: #fff;
      }
      .admit ol {
        display: flex;
        gap: 1rem;
        list-style: none;
        padding: 0;
        margin: 0;
      }
      .admit span {
        display: block;
        color: var(--sf-secondary, #c4a35a);
        font-family: var(--sf-serif, Georgia, serif);
      }
      .docs {
        list-style: none;
        padding: 0;
        display: grid;
        gap: 0.6rem;
      }
      .docs a {
        color: var(--sf-primary, #1b4d3e);
        font-weight: 700;
      }
      .map {
        width: 100%;
        min-height: 280px;
        border: 0;
        border-radius: 18px;
      }
      .placeholder h3 {
        margin-top: 0;
      }
      @keyframes fade {
        from { opacity: 0.55; }
        to { opacity: 1; }
      }
      @media (max-width: 900px) {
        .split, .founder, .aim-grid, .news-grid, .explore, .masonry, .admit-inner {
          grid-template-columns: 1fr;
          display: grid;
        }
        .shot.feature { grid-row: auto; }
        .admit ol { flex-direction: column; }
        .hero-copy { padding-top: 4.5rem; }
      }
    `,
  ],
})
export class PremiumHomeComponent implements OnInit, OnDestroy {
  readonly api = inject(WebsiteApiService);
  private readonly sanitizer = inject(DomSanitizer);
  blocks: HomeBlock[] = [];
  heroIndex = 0;
  news: Array<{ title: string; summary: string; slug: string; category: string }> = [];
  events: Array<{ title: string; summary: string; when: string }> = [];
  documents: Array<{ title: string; category: string; url: string }> = [];
  profiles: Array<{ name: string; role: string; photo: string }> = [];
  spotlight: Card[] = [];
  brand = this.api.brandContact();
  mapLink = '';
  mapFrame: SafeResourceUrl | null = null;
  private heroSlides: Array<{ url: string; alt: string }> = [];
  private timer: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.api.resolve().subscribe((site) => {
      this.brand = this.api.brandContact(site);
      this.mapLink = this.api.mapOpenUrl(this.brand);
      const embed = this.api.mapEmbedSrc(this.brand);
      this.mapFrame = this.safeMap(embed);
      forkJoin({
        news: this.api.listNews(6).pipe(catchError(() => of([]))),
        events: this.api.listEvents(4).pipe(catchError(() => of([]))),
        gallery: this.api.listGallery(80).pipe(catchError(() => of([]))),
        documents: this.api.listDocuments(12).pipe(catchError(() => of([]))),
        profiles: this.api.listProfiles().pipe(catchError(() => of([]))),
      }).subscribe((data) => this.apply(site, data));
    });
  }

  ngOnDestroy(): void {
    this.stopSlider();
  }

  get heroImage(): { url: string; alt: string } | null {
    if (!this.heroSlides.length) return null;
    return this.heroSlides[this.heroIndex % this.heroSlides.length];
  }

  stepHero(delta: number): void {
    if (this.heroSlides.length < 2) return;
    this.heroIndex = (this.heroIndex + delta + this.heroSlides.length) % this.heroSlides.length;
    this.startSlider();
  }

  goHero(index: number): void {
    this.heroIndex = index;
    this.startSlider();
  }

  trusted(raw: string): SafeHtml {
    const cleaned = raw
      .replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '')
      .replace(/\son\w+\s*=\s*(['"]).*?\1/gi, '')
      .replace(/javascript:/gi, '');
    return this.sanitizer.bypassSecurityTrustHtml(cleaned);
  }

  private apply(
    site: WebsiteResolve,
    data: {
      news: Array<Record<string, unknown>>;
      events: Array<Record<string, unknown>>;
      gallery: Array<Record<string, unknown>>;
      documents: Array<Record<string, unknown>>;
      profiles: Array<Record<string, unknown>>;
    }
  ): void {
    const photos = (data.gallery || [])
      .map((row) => this.toPhoto(row))
      .filter((row): row is Card => !!row);
    this.spotlight = photos.slice(0, 5);
    this.news = (data.news || []).slice(0, 3).map((row) => ({
      title: String(row['title'] || ''),
      summary: String(row['summary'] || ''),
      slug: String(row['slug'] || ''),
      category: String(row['category'] || 'News'),
    }));
    this.events = (data.events || []).slice(0, 3).map((row) => ({
      title: String(row['title'] || ''),
      summary: String(row['summary'] || row['locationText'] || ''),
      when: String(row['startsAt'] || '').replace('T', ' ').slice(0, 16),
    }));
    this.documents = (data.documents || []).map((row) => ({
      title: String(row['title'] || 'Document'),
      category: String(row['category'] || ''),
      url: this.api.mediaUrl(String(row['fileUrl'] || '')),
    }));
    this.profiles = (data.profiles || []).map((row) => ({
      name: String(row['fullName'] || ''),
      role: String(row['role'] || ''),
      photo: this.api.mediaUrl(String(row['photoUrl'] || '')),
    }));
    const ordered = [...(site.homepage || [])]
      .filter((row) => row['enabled'] !== false)
      .sort((a, b) => Number(a['order'] || 0) - Number(b['order'] || 0));
    this.blocks = ordered.map((row) => this.toBlock(row, photos));
    const hero = this.blocks.find((block) => block.type === 'HERO');
    this.heroSlides = hero?.images || [];
    this.heroIndex = 0;
    this.startSlider();
  }

  private toBlock(row: Record<string, unknown>, photos: Card[]): HomeBlock {
    const content = (row['content'] || {}) as Record<string, unknown>;
    const type = String(row['type'] || '');
    const images = this.slides(content);
    const links = this.links(content, photos);
    return {
      type,
      eyebrow: this.str(content, 'eyebrow'),
      title: this.str(content, 'title') || this.fallbackTitle(type),
      subtitle: this.str(content, 'subtitle'),
      body: this.str(content, 'body'),
      image: this.api.mediaUrl(this.str(content, 'imageUrl')),
      imageAlt: this.str(content, 'imageAlt') || this.str(content, 'title'),
      images,
      ctaLabel: this.str(content, 'ctaLabel'),
      secondaryCtaLabel: this.str(content, 'secondaryCtaLabel'),
      ctaPath: this.str(content, 'ctaPath') || '/admission/apply',
      secondaryCtaPath: this.str(content, 'secondaryCtaPath') || '/about',
      name: this.str(content, 'name'),
      role: this.str(content, 'role'),
      message: this.str(content, 'message'),
      photo: this.api.mediaUrl(this.str(content, 'photoUrl') || this.str(content, 'imageUrl')),
      cards: this.cards(content),
      links,
      stats: this.stats(content),
      quotes: this.quotes(content),
      steps: this.steps(content),
      html: this.str(content, 'html'),
      placeholderTitle: this.str(content, 'placeholderTitle') || 'Nothing published yet',
      placeholderBody:
        this.str(content, 'placeholderBody') ||
        'This section appears when the school publishes it in Website CMS.',
    };
  }

  private fallbackTitle(type: string): string {
    const titles: Record<string, string> = {
      HERO: 'Welcome',
      ABOUT_SCHOOL: 'About the school',
      WHY_CHOOSE: 'Highlights',
      QUICK_LINKS: 'Explore',
      FACILITIES: 'Facilities',
      ACADEMIC_PROGRAMS: 'Academics',
      GALLERY: 'Gallery',
      LATEST_NEWS: 'News',
      UPCOMING_EVENTS: 'Events',
      ADMISSION_CTA: 'Admissions',
      TESTIMONIALS: 'Community',
      DOCUMENTS: 'Documents',
      STAFF: 'Staff',
      CONTACT: 'Contact',
      FAQ: 'Questions',
    };
    return titles[type] || '';
  }

  private slides(content: Record<string, unknown>): Array<{ url: string; alt: string }> {
    const raw = content['images'];
    const slides: Array<{ url: string; alt: string }> = [];
    if (Array.isArray(raw)) {
      for (const item of raw) {
        if (typeof item === 'string') {
          const url = this.api.mediaUrl(item);
          if (url) slides.push({ url, alt: this.str(content, 'title') || 'School photograph' });
        } else if (item && typeof item === 'object') {
          const row = item as Record<string, unknown>;
          const url = this.api.mediaUrl(String(row['imageUrl'] || row['url'] || ''));
          if (url) slides.push({ url, alt: String(row['alt'] || row['title'] || 'School photograph') });
        }
      }
    }
    const single = this.api.mediaUrl(this.str(content, 'imageUrl'));
    if (single && !slides.some((slide) => slide.url === single)) {
      slides.unshift({ url: single, alt: this.str(content, 'imageAlt') || 'School photograph' });
    }
    return slides;
  }

  private cards(content: Record<string, unknown>): Card[] {
    const raw = content['items'];
    if (!Array.isArray(raw)) return [];
    return raw
      .map((row) => {
        const item = (row || {}) as Record<string, unknown>;
        return {
          title: String(item['title'] || '').trim(),
          body: String(item['body'] || '').trim(),
          image: this.api.mediaUrl(String(item['imageUrl'] || '')),
        };
      })
      .filter((item) => item.title);
  }

  private links(content: Record<string, unknown>, photos: Card[]): LinkCard[] {
    const raw = content['links'];
    if (!Array.isArray(raw)) return [];
    return raw
      .map((row) => {
        const item = (row || {}) as Record<string, unknown>;
        const title = String(item['label'] || item['title'] || '').trim();
        return {
          title,
          path: String(item['path'] || '/'),
          cta: String(item['cta'] || item['ctaLabel'] || ''),
          image: this.api.mediaUrl(String(item['imageUrl'] || '')) || photos[0]?.image || '',
        };
      })
      .filter((item) => item.title);
  }

  private stats(content: Record<string, unknown>): Stat[] {
    const fromItems = content['items'];
    if (Array.isArray(fromItems) && fromItems.length) {
      return fromItems
        .map((row) => {
          const item = (row || {}) as Record<string, unknown>;
          return { value: String(item['value'] || item['title'] || ''), label: String(item['label'] || item['body'] || '') };
        })
        .filter((item) => item.value);
    }
    const stats: Stat[] = [];
    for (let i = 1; i <= 4; i += 1) {
      const value = String(content['value' + i] || '').trim();
      const label = String(content['label' + i] || '').trim();
      if (value) stats.push({ value, label });
    }
    return stats;
  }

  private quotes(content: Record<string, unknown>): Quote[] {
    const raw = content['items'];
    if (!Array.isArray(raw)) return [];
    return raw
      .map((row) => {
        const item = (row || {}) as Record<string, unknown>;
        return {
          quote: String(item['quote'] || item['message'] || item['body'] || '').trim(),
          name: String(item['name'] || item['title'] || '').trim(),
          role: String(item['role'] || '').trim(),
        };
      })
      .filter((item) => item.quote && item.name);
  }

  private steps(content: Record<string, unknown>): Step[] {
    const raw = content['steps'];
    if (Array.isArray(raw) && raw.length) {
      return raw
        .map((row) => ({ label: String((row as { label?: string })?.label || row || '').trim() }))
        .filter((step) => step.label);
    }
    return [{ label: 'Enquire' }, { label: 'Visit' }, { label: 'Apply' }];
  }

  private toPhoto(row: Record<string, unknown>): Card | null {
    const album = String(row['album'] || '').toLowerCase();
    if (album === 'banners' || album === 'management') return null;
    const image = this.api.mediaUrl(String(row['imageUrl'] || row['url'] || ''));
    if (!image) return null;
    return {
      title: String(row['title'] || 'School photograph'),
      body: album || 'Gallery',
      image,
    };
  }

  private str(content: Record<string, unknown>, key: string): string {
    const value = content[key];
    return value == null ? '' : String(value).trim();
  }

  private safeMap(src: string): SafeResourceUrl | null {
    if (!src) return null;
    try {
      const url = new URL(src);
      const host = url.hostname.toLowerCase();
      if (host !== 'maps.google.com' && !host.endsWith('.google.com')) return null;
      return this.sanitizer.bypassSecurityTrustResourceUrl(src);
    } catch {
      return null;
    }
  }

  private startSlider(): void {
    this.stopSlider();
    if (this.heroSlides.length < 2) return;
    this.timer = setInterval(() => {
      this.heroIndex = (this.heroIndex + 1) % this.heroSlides.length;
    }, 6500);
  }

  private stopSlider(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
}
