import { Component, OnInit, inject } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WebsiteApiService, WebsiteResolve } from '../core/website-api.service';
import { SeoService } from '../core/seo.service';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [NgFor, NgIf, RouterLink],
  template: `
    <ng-container *ngFor="let section of sections">
      <section class="hero" *ngIf="section['type'] === 'HERO' && site as s">
        <p class="eyebrow">{{ s.displayName }}</p>
        <h1>{{ heroTitle }}</h1>
        <p class="lead">{{ heroSubtitle }}</p>
        <div class="actions">
          <a routerLink="/admission/apply" class="primary">Admissions</a>
          <a routerLink="/contact" class="ghost">Contact</a>
        </div>
      </section>

      <section class="grid" *ngIf="section['type'] === 'LATEST_NEWS' && news.length">
        <h2>Latest news</h2>
        <article *ngFor="let n of news">
          <h3><a [routerLink]="['/news', n['slug']]">{{ n['title'] }}</a></h3>
          <p>{{ n['summary'] }}</p>
        </article>
      </section>

      <section class="grid" *ngIf="section['type'] === 'UPCOMING_EVENTS' && events.length">
        <h2>Upcoming events</h2>
        <article *ngFor="let e of events">
          <h3>{{ e['title'] }}</h3>
          <p>{{ e['summary'] }}</p>
        </article>
      </section>

      <section class="cta-band" *ngIf="section['type'] === 'ADMISSION_CTA'">
        <h2>{{ admissionTitle }}</h2>
        <a routerLink="/admission/apply" class="primary">{{ admissionCta }}</a>
      </section>
    </ng-container>
  `,
  styles: [
    `
      .hero {
        background: linear-gradient(135deg, var(--sf-primary, #0b3d91), #123f7a);
        color: #fff;
        border-radius: 18px;
        padding: 2.5rem 2rem;
        margin-bottom: 2rem;
      }
      .eyebrow {
        opacity: 0.85;
        margin: 0 0 0.5rem;
        letter-spacing: 0.04em;
        text-transform: uppercase;
        font-size: 0.8rem;
      }
      h1 {
        margin: 0 0 0.75rem;
        font-size: clamp(1.8rem, 4vw, 2.8rem);
        line-height: 1.15;
      }
      .lead {
        max-width: 40rem;
        opacity: 0.95;
      }
      .actions {
        display: flex;
        gap: 0.75rem;
        margin-top: 1.25rem;
      }
      .actions a,
      .cta-band a {
        text-decoration: none;
        padding: 0.65rem 1rem;
        border-radius: 8px;
        font-weight: 600;
      }
      .primary {
        background: var(--sf-secondary, #f5b700);
        color: #111;
      }
      .ghost {
        border: 1px solid rgba(255, 255, 255, 0.5);
        color: #fff;
      }
      .grid {
        margin-bottom: 2rem;
      }
      .grid h2 {
        margin-bottom: 0.75rem;
      }
      article {
        background: #fff;
        border: 1px solid #e6e9f0;
        border-radius: 12px;
        padding: 1rem 1.1rem;
        margin-bottom: 0.75rem;
      }
      article a {
        color: var(--sf-primary, #0b3d91);
        text-decoration: none;
      }
      .cta-band {
        background: #fff;
        border: 1px solid #e6e9f0;
        border-radius: 14px;
        padding: 1.25rem;
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 1rem;
        margin-bottom: 2rem;
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
  admissionTitle = 'Admissions Open';
  admissionCta = 'Apply Now';
  news: Array<Record<string, unknown>> = [];
  events: Array<Record<string, unknown>> = [];

  ngOnInit(): void {
    this.api.resolve().subscribe((site) => {
      this.site = site;
      this.sections = this.enabledSections(site.homepage || []);
      const hero = this.sections.find((s) => s['type'] === 'HERO') as
        | { content?: { title?: string; subtitle?: string } }
        | undefined;
      this.heroTitle = hero?.content?.title || `Welcome to ${site.displayName}`;
      this.heroSubtitle =
        hero?.content?.subtitle || 'Excellence in education for every learner.';
      const cta = this.sections.find((s) => s['type'] === 'ADMISSION_CTA') as
        | { content?: { title?: string; ctaLabel?: string } }
        | undefined;
      this.admissionTitle = cta?.content?.title || 'Admissions Open';
      this.admissionCta = cta?.content?.ctaLabel || 'Apply Now';
      this.seo.apply({
        title: site.seo?.['defaultTitle'] || site.displayName,
        description: site.seo?.['defaultDescription'] || undefined,
        imageUrl: site.seo?.['ogImageUrl'] || undefined,
      });

      if (this.sections.some((s) => s['type'] === 'LATEST_NEWS' || s['type'] === 'ANNOUNCEMENTS')) {
        this.api.listNews().subscribe((rows) => (this.news = (rows || []).slice(0, 3)));
      }
      if (this.sections.some((s) => s['type'] === 'UPCOMING_EVENTS')) {
        this.api.listEvents().subscribe((rows) => (this.events = (rows || []).slice(0, 3)));
      }
    });
  }

  private enabledSections(
    homepage: Array<Record<string, unknown>>
  ): Array<Record<string, unknown>> {
    return [...homepage]
      .filter((s) => s['enabled'] !== false)
      .sort((a, b) => Number(a['order'] || 0) - Number(b['order'] || 0))
      .map((s) => {
        // Map seed ANNOUNCEMENTS to news block for Phase 3 rendering.
        if (s['type'] === 'ANNOUNCEMENTS') {
          return { ...s, type: 'LATEST_NEWS' };
        }
        return s;
      });
  }
}
