import { Component, OnInit, inject } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WebsiteApiService, WebsiteResolve } from '../core/website-api.service';

type Card = { title: string; body: string };
type Photo = { title: string; imageUrl: string; album: string; category: string };

@Component({
  selector: 'app-premium-home',
  standalone: true,
  imports: [NgFor, NgIf, RouterLink],
  template: `
    <section class="hero" *ngIf="site" [style.backgroundImage]="heroBackground">
      <div class="hero-shade"></div>
      <div class="hero-copy">
        <p class="eyebrow">{{ eyebrow }}</p>
        <h1>{{ title }}</h1>
        <p class="lead">{{ subtitle }}</p>
        <div class="actions">
          <a routerLink="/about" class="btn light">Explore Our School</a>
          <a routerLink="/admission/apply" class="btn gold">Apply for Admission</a>
        </div>
      </div>
      <aside class="hero-card">
        <p class="card-kicker">{{ notice }}</p>
        <p class="card-title" *ngIf="session">{{ session }}</p>
        <a routerLink="/admission/apply">Apply online →</a>
      </aside>
    </section>

    <section class="band" *ngIf="aims.length">
      <div class="wrap">
        <p class="eyebrow">The school</p>
        <h2>What Pratibha stands for</h2>
        <div class="aim-grid">
          <article *ngFor="let aim of aims">
            <h3>{{ aim.title }}</h3>
            <p>{{ aim.body }}</p>
          </article>
        </div>
      </div>
    </section>

    <section class="band soft" *ngIf="explore.length">
      <div class="wrap">
        <p class="eyebrow">Look around</p>
        <h2>Explore Pratibha</h2>
        <div class="explore">
          <a *ngFor="let card of explore" [routerLink]="card.path" class="explore-card">
            <img [src]="card.image" [alt]="card.title" />
            <span>
              <strong>{{ card.title }}</strong>
              <em>{{ card.cta }}</em>
            </span>
          </a>
        </div>
      </div>
    </section>

    <section class="band" *ngIf="founder">
      <div class="wrap founder">
        <img *ngIf="founder.photo" [src]="founder.photo" [alt]="founder.name" />
        <blockquote>
          <p class="eyebrow">{{ founder.title }}</p>
          <p class="quote">“{{ founder.message }}”</p>
          <footer>
            <strong>{{ founder.name }}</strong>
            <span>{{ founder.role }}</span>
          </footer>
        </blockquote>
      </div>
    </section>

    <section class="band campus" *ngIf="campusPhoto">
      <div class="wrap split">
        <img [src]="campusPhoto" alt="School campus photograph" />
        <div>
          <p class="eyebrow">Campus</p>
          <h2>A campus designed for discovery</h2>
          <p>
            Library, science lab, computer lab, and activity spaces are shown here from photographs
            on the existing Pratibha website. They are a starting gallery for Rampur and can be
            replaced when Rampur photographs are ready.
          </p>
          <ul>
            <li *ngFor="let item of facilities">{{ item.title }}</li>
          </ul>
          <a routerLink="/campus" class="text">See campus notes →</a>
        </div>
      </div>
    </section>

    <section class="band" *ngIf="spotlight.length">
      <div class="wrap">
        <div class="section-head">
          <div>
            <p class="eyebrow">Photographs</p>
            <h2>Life at school</h2>
          </div>
          <a routerLink="/gallery" class="text">View full gallery →</a>
        </div>
        <div class="masonry">
          <a
            *ngFor="let photo of spotlight; let i = index"
            routerLink="/gallery"
            class="shot"
            [class.feature]="i === 0"
          >
            <img [src]="photo.imageUrl" [alt]="photo.title" loading="lazy" />
            <span>
              <small>{{ photo.category }}</small>
              {{ photo.title }}
              <em>→</em>
            </span>
          </a>
        </div>
      </div>
    </section>

    <section class="band soft">
      <div class="wrap">
        <p class="eyebrow">News</p>
        <h2>What’s happening at Pratibha</h2>
        <div class="news-grid" *ngIf="news.length; else newsEmpty">
          <article *ngFor="let item of news">
            <p class="meta">{{ item.category }}</p>
            <h3><a [routerLink]="['/news', item.slug]">{{ item.title }}</a></h3>
            <p>{{ item.summary }}</p>
            <a [routerLink]="['/news', item.slug]" class="text">Read more →</a>
          </article>
        </div>
        <ng-template #newsEmpty>
          <div class="news-grid">
            <article class="placeholder">
              <p class="meta">Events</p>
              <h3>[Add a school event]</h3>
              <p>Notices and events appear here when the school publishes them.</p>
            </article>
            <article class="placeholder">
              <p class="meta">Announcements</p>
              <h3>[Add an announcement]</h3>
              <p>No announcement is published for Rampur yet.</p>
            </article>
            <article class="placeholder">
              <p class="meta">Achievements</p>
              <h3>[Add a school achievement]</h3>
              <p>Results and awards stay blank until the school adds them.</p>
            </article>
          </div>
        </ng-template>
      </div>
    </section>

    <section class="admit">
      <div class="wrap admit-inner">
        <div>
          <h2>{{ admitTitle }}</h2>
          <p>{{ admitBody }}</p>
          <div class="actions">
            <a routerLink="/admission/apply" class="btn gold">Start application</a>
            <a routerLink="/contact" class="btn light">Talk to admissions</a>
          </div>
        </div>
        <ol>
          <li><span>01</span> Enquire</li>
          <li><span>02</span> Visit</li>
          <li><span>03</span> Apply</li>
        </ol>
      </div>
    </section>

    <section class="band">
      <div class="wrap">
        <p class="eyebrow">Community</p>
        <h2>What our school community says</h2>
        <article class="placeholder quote-card">
          <h3>[Add a parent or student testimonial]</h3>
          <p>Names and quotes are shown only after the school publishes them.</p>
        </article>
      </div>
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
        color: #1c2430;
        font-family: var(--sf-sans, Manrope, sans-serif);
      }
      .wrap {
        width: min(1240px, calc(100% - 2.5rem));
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
      h1,
      h2,
      h3 {
        font-family: var(--sf-serif, 'Playfair Display', Georgia, serif);
        font-weight: 500;
        color: #1b4d3e;
      }
      h2 {
        margin: 0 0 1.25rem;
        font-size: clamp(2rem, 4vw, 3.1rem);
        line-height: 1.1;
      }
      .hero {
        position: relative;
        min-height: min(88vh, 760px);
        display: grid;
        align-items: end;
        background-size: cover;
        background-position: center;
        color: #fff;
        margin-bottom: 0;
      }
      .hero-shade {
        position: absolute;
        inset: 0;
        background: linear-gradient(90deg, rgba(20, 56, 44, 0.78) 0%, rgba(20, 56, 44, 0.4) 52%, rgba(20, 56, 44, 0.18) 100%);
      }
      .hero-copy,
      .hero-card {
        position: relative;
        z-index: 1;
      }
      .hero-copy {
        width: min(1240px, calc(100% - 2.5rem));
        margin: 0 auto;
        padding: 7rem 0 4.5rem;
      }
      h1 {
        margin: 0;
        max-width: 12ch;
        color: #fff;
        font-size: clamp(3rem, 6.4vw, 5.4rem);
        line-height: 0.98;
      }
      .lead {
        max-width: 36rem;
        font-size: 1.12rem;
        line-height: 1.6;
        color: rgba(255, 255, 255, 0.88);
      }
      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
        margin-top: 1.4rem;
      }
      .btn {
        display: inline-flex;
        align-items: center;
        text-decoration: none;
        border-radius: 999px;
        padding: 0.85rem 1.25rem;
        font-weight: 700;
      }
      .btn.gold {
        background: var(--sf-secondary, #c6a15b);
        color: #1a1408;
      }
      .btn.light {
        background: transparent;
        color: #fff;
        border: 1px solid rgba(255, 255, 255, 0.7);
      }
      .hero-card {
        position: absolute;
        right: max(1.25rem, calc((100% - 1240px) / 2));
        bottom: 2rem;
        width: min(280px, calc(100% - 2rem));
        background: #fff;
        color: #1b4d3e;
        border-radius: 16px;
        padding: 1.1rem 1.15rem;
        box-shadow: 0 18px 40px rgba(16, 42, 67, 0.18);
      }
      .card-kicker {
        margin: 0;
        font-size: 0.78rem;
        letter-spacing: 0.12em;
        text-transform: uppercase;
        color: #8a6a2f;
        font-weight: 700;
      }
      .card-title {
        margin: 0.35rem 0;
        font-family: var(--sf-serif, Georgia, serif);
        font-size: 1.35rem;
      }
      .hero-card a {
        color: #1b4d3e;
        font-weight: 700;
        text-decoration: none;
      }
      .band {
        padding: 4.5rem 0;
      }
      .band.soft {
        background: #f6f3ec;
      }
      .aim-grid,
      .news-grid {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 1rem;
      }
      .aim-grid article,
      .news-grid article,
      .quote-card {
        background: #fff;
        border-radius: 16px;
        padding: 1.25rem 1.2rem 1.35rem;
        box-shadow: 0 10px 30px rgba(16, 42, 67, 0.05);
      }
      .aim-grid h3,
      .news-grid h3 {
        margin: 0 0 0.45rem;
        font-size: 1.45rem;
      }
      .aim-grid p,
      .news-grid p {
        margin: 0;
        color: #4b5563;
        line-height: 1.55;
      }
      .explore {
        display: grid;
        grid-template-columns: 1.4fr 1fr;
        grid-template-rows: 280px 280px;
        gap: 0.9rem;
      }
      .explore-card {
        position: relative;
        overflow: hidden;
        border-radius: 18px;
        color: #fff;
        text-decoration: none;
      }
      .explore-card:first-child {
        grid-row: 1 / span 2;
      }
      .explore-card img,
      .shot img,
      .split img,
      .founder img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        display: block;
        transition: transform 0.45s ease;
      }
      .explore-card:hover img,
      .shot:hover img {
        transform: scale(1.05);
      }
      .explore-card span,
      .shot span {
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        padding: 1.1rem 1.15rem;
        background: linear-gradient(transparent, rgba(10, 22, 40, 0.78));
        display: flex;
        justify-content: space-between;
        align-items: flex-end;
        gap: 0.5rem;
      }
      .explore-card strong {
        display: block;
        font-family: var(--sf-serif, Georgia, serif);
        font-size: 1.8rem;
        font-weight: 500;
      }
      .explore-card em,
      .shot em {
        font-style: normal;
      }
      .founder,
      .split {
        display: grid;
        grid-template-columns: 0.9fr 1.1fr;
        gap: 2rem;
        align-items: center;
      }
      .founder img,
      .split img {
        height: 420px;
        border-radius: 18px;
      }
      .quote {
        font-family: var(--sf-serif, Georgia, serif);
        font-size: clamp(1.4rem, 2.4vw, 1.9rem);
        line-height: 1.35;
        margin: 0 0 1rem;
      }
      .founder footer {
        display: grid;
      }
      .founder span,
      .meta {
        color: #6b7280;
      }
      .split ul {
        padding: 0;
        margin: 1rem 0;
        list-style: none;
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem;
      }
      .split li {
        border: 1px solid #e6dcc4;
        border-radius: 999px;
        padding: 0.35rem 0.75rem;
        background: #fff;
      }
      .section-head {
        display: flex;
        justify-content: space-between;
        align-items: end;
        gap: 1rem;
      }
      .text {
        color: #1b4d3e;
        font-weight: 700;
        text-decoration: none;
      }
      .masonry {
        display: grid;
        grid-template-columns: 1.4fr 1fr 1fr;
        grid-auto-rows: 190px;
        gap: 0.85rem;
      }
      .shot {
        position: relative;
        overflow: hidden;
        border-radius: 16px;
        display: block;
      }
      .shot.feature {
        grid-row: span 2;
      }
      .shot span {
        opacity: 0;
        color: #fff;
        font-weight: 650;
      }
      .shot small {
        display: block;
        font-size: 0.72rem;
        letter-spacing: 0.08em;
        text-transform: uppercase;
      }
      .shot:hover span,
      .shot:focus-visible span {
        opacity: 1;
      }
      .placeholder {
        border: 1px dashed #d6d0c4;
        background: transparent;
        box-shadow: none;
      }
      .admit {
        background: #1b4d3e;
        color: #fff;
        padding: 4rem 0;
      }
      .admit h2 {
        color: #fff;
      }
      .admit-inner {
        display: flex;
        justify-content: space-between;
        gap: 2rem;
        align-items: center;
      }
      .admit p {
        max-width: 36rem;
        color: rgba(255, 255, 255, 0.82);
        line-height: 1.6;
      }
      .admit ol {
        display: flex;
        gap: 1rem;
        list-style: none;
        padding: 0;
        margin: 0;
      }
      .admit li {
        min-width: 7rem;
      }
      .admit span {
        display: block;
        color: var(--sf-secondary, #c6a15b);
        font-family: var(--sf-serif, Georgia, serif);
        font-size: 1.4rem;
      }
      .quote-card {
        max-width: 40rem;
      }
      @media (max-width: 960px) {
        .aim-grid,
        .news-grid,
        .explore,
        .founder,
        .split,
        .masonry,
        .admit-inner {
          grid-template-columns: 1fr;
          display: grid;
        }
        .explore {
          grid-template-rows: none;
        }
        .explore-card:first-child,
        .shot.feature {
          grid-row: auto;
          min-height: 240px;
        }
        .hero-card {
          position: relative;
          right: auto;
          bottom: auto;
          margin: 0 1.25rem 1.25rem;
        }
        .hero-copy {
          padding-bottom: 1.5rem;
        }
        .founder img,
        .split img,
        .shot,
        .explore-card {
          min-height: 220px;
        }
      }
    `,
  ],
})
export class PremiumHomeComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  site: WebsiteResolve | null = null;
  eyebrow = 'Rampur Branch';
  title = 'Where curiosity becomes confidence.';
  subtitle = '';
  notice = 'Admissions';
  session = '';
  aims: Card[] = [];
  facilities: Card[] = [];
  explore: Array<{ title: string; cta: string; path: string; image: string }> = [];
  founder: { title: string; name: string; role: string; message: string; photo: string } | null = null;
  campusPhoto = '';
  spotlight: Photo[] = [];
  news: Array<{ title: string; summary: string; slug: string; category: string }> = [];
  admitTitle = 'Give your child a place to grow';
  admitBody =
    'Fee amounts, eligibility, and important dates are published here when the Rampur office confirms them.';

  ngOnInit(): void {
    this.api.resolve().subscribe((site) => {
      this.site = site;
      const hero = this.section(site, 'HERO');
      this.eyebrow = this.text(hero, 'eyebrow', site.theme?.['tagline'] || 'Rampur Branch');
      this.title = this.text(hero, 'title', this.title);
      this.subtitle = this.text(hero, 'subtitle');
      this.notice = String(site.theme?.['admissionNotice'] || 'Admissions');
      this.session = String(site.theme?.['sessionLabel'] || '').trim();
      this.aims = this.cards(this.section(site, 'WHY_CHOOSE'));
      this.facilities = this.cards(this.section(site, 'FACILITIES'));
      const founder = this.section(site, 'PRINCIPAL_MESSAGE');
      if (founder) {
        const content = (founder['content'] || {}) as Record<string, unknown>;
        this.founder = {
          title: this.text(founder, 'title', 'From the Founder Director'),
          name: this.text(founder, 'name'),
          role: this.text(founder, 'role'),
          message: this.text(founder, 'message'),
          photo: this.api.mediaUrl(String(content['photoUrl'] || content['imageUrl'] || '')),
        };
      }
      const admit = this.section(site, 'ADMISSION_CTA');
      this.admitTitle = this.text(admit, 'title', this.admitTitle);
      this.admitBody = this.text(admit, 'body', this.admitBody);
      const heroImage = this.text(hero, 'imageUrl', '/pps/images/facilities/school-campus.jpg');
      this.campusPhoto = this.api.mediaUrl(heroImage);
      this.api.listNews(3).subscribe((rows) => {
        this.news = (rows || []).slice(0, 3).map((row) => ({
          title: String(row['title'] || ''),
          summary: String(row['summary'] || ''),
          slug: String(row['slug'] || ''),
          category: String(row['category'] || 'News'),
        }));
      });
      this.api.listGallery(80).subscribe((rows) => {
        const photos = (rows || [])
          .map((row) => this.toPhoto(row))
          .filter((photo): photo is Photo => !!photo);
        this.spotlight = this.pickSpotlight(photos);
        this.explore = [
          {
            title: 'Academics',
            cta: 'Learn →',
            path: '/academics',
            image: this.byCategory(photos, 'Academics') || this.campusPhoto,
          },
          {
            title: 'Campus',
            cta: 'Discover →',
            path: '/campus',
            image: this.byCategory(photos, 'Campus') || this.campusPhoto,
          },
          {
            title: 'Student life',
            cta: 'Experience →',
            path: '/student-life',
            image: this.byCategory(photos, 'Celebrations') || this.campusPhoto,
          },
          {
            title: 'Sports and activities',
            cta: 'Explore →',
            path: '/gallery',
            image: this.byCategory(photos, 'Sports') || this.campusPhoto,
          },
        ];
      });
    });
  }

  get heroBackground(): string {
    const image = this.campusPhoto || '/pps/images/facilities/school-campus.jpg';
    return `url('${image}')`;
  }

  private section(site: WebsiteResolve, type: string): Record<string, unknown> | undefined {
    return (site.homepage || []).find((row) => row['type'] === type && row['enabled'] !== false);
  }

  private text(section: Record<string, unknown> | undefined, key: string, fallback = ''): string {
    const content = (section?.['content'] || {}) as Record<string, unknown>;
    const value = String(content[key] || '').trim();
    return value || fallback;
  }

  private cards(section: Record<string, unknown> | undefined): Card[] {
    const content = (section?.['content'] || {}) as Record<string, unknown>;
    const raw = content['items'];
    if (!Array.isArray(raw)) return [];
    return raw
      .map((row) => {
        const item = (row || {}) as Record<string, unknown>;
        return {
          title: String(item['title'] || '').trim(),
          body: String(item['body'] || '').trim(),
        };
      })
      .filter((item) => item.title);
  }

  private toPhoto(row: Record<string, unknown>): Photo | null {
    const album = String(row['album'] || '').toLowerCase();
    if (album === 'banners') return null;
    const title = String(row['title'] || 'School photograph').trim();
    const imageUrl = this.api.mediaUrl(String(row['imageUrl'] || row['url'] || ''));
    if (!imageUrl) return null;
    return { title, imageUrl, album, category: this.category(album, title) };
  }

  private category(album: string, title: string): string {
    const blob = `${album} ${title}`.toLowerCase();
    if (/annual|celebration|function/.test(blob)) return 'Celebrations';
    if (/game|sport|chess|tennis|play/.test(blob)) return 'Sports';
    if (/lab|library|class|computer|science/.test(blob)) return 'Academics';
    if (/campus|corridor|school|building|hall/.test(blob)) return 'Campus';
    if (album === 'activities') return 'Events';
    if (album === 'facilities' || album === 'school') return 'Campus';
    return 'Events';
  }

  private byCategory(photos: Photo[], category: string): string {
    return photos.find((photo) => photo.category === category)?.imageUrl || '';
  }

  private pickSpotlight(photos: Photo[]): Photo[] {
    const preferred = ['Celebrations', 'Sports', 'Academics', 'Campus', 'Events'];
    const picked: Photo[] = [];
    for (const category of preferred) {
      const match = photos.find((photo) => photo.category === category && !picked.includes(photo));
      if (match) picked.push(match);
    }
    for (const photo of photos) {
      if (picked.length >= 5) break;
      if (!picked.includes(photo) && photo.album !== 'management') picked.push(photo);
    }
    return picked.slice(0, 5);
  }
}
