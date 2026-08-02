import { Component, OnInit, inject } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WebsiteApiService } from '../core/website-api.service';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [NgFor, NgIf, RouterLink],
  template: `
    <section class="hero" *ngIf="site as s">
      <p class="eyebrow">{{ s.displayName }}</p>
      <h1>{{ heroTitle }}</h1>
      <p class="lead">{{ heroSubtitle }}</p>
      <div class="actions">
        <a routerLink="/admission/apply" class="primary">Admissions</a>
        <a routerLink="/contact" class="ghost">Contact</a>
      </div>
    </section>

    <section class="grid" *ngIf="news.length">
      <h2>Latest news</h2>
      <article *ngFor="let n of news">
        <h3><a [routerLink]="['/news', n['slug']]">{{ n['title'] }}</a></h3>
        <p>{{ n['summary'] }}</p>
      </article>
    </section>

    <section class="grid" *ngIf="events.length">
      <h2>Upcoming events</h2>
      <article *ngFor="let e of events">
        <h3>{{ e['title'] }}</h3>
        <p>{{ e['summary'] }}</p>
      </article>
    </section>
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
      .actions a {
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
    `,
  ],
})
export class HomePageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  site = this.api.site();
  heroTitle = 'Welcome';
  heroSubtitle = 'A modern school experience.';
  news: Array<Record<string, unknown>> = [];
  events: Array<Record<string, unknown>> = [];

  ngOnInit(): void {
    this.api.resolve().subscribe((site) => {
      this.site = site;
      const hero = (site.homepage || []).find((s) => s['type'] === 'HERO') as
        | { content?: { title?: string; subtitle?: string } }
        | undefined;
      this.heroTitle = hero?.content?.title || `Welcome to ${site.displayName}`;
      this.heroSubtitle =
        hero?.content?.subtitle || 'Excellence in education for every learner.';
      document.title = site.displayName;
    });
    this.api.listNews().subscribe((rows) => (this.news = (rows || []).slice(0, 3)));
    this.api.listEvents().subscribe((rows) => (this.events = (rows || []).slice(0, 3)));
  }
}
