import { Component, OnInit, inject } from '@angular/core';
import { NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WebsiteApiService } from '../core/website-api.service';
import { SeoService } from '../core/seo.service';

@Component({
  selector: 'app-contact-page',
  standalone: true,
  imports: [NgIf, RouterLink],
  template: `
    <section class="page-hero">
      <p class="eyebrow">Get in touch</p>
      <h1>{{ title }}</h1>
      <p class="lead">{{ summary }}</p>
    </section>

    <div class="contact-layout">
      <div class="cards">
        <article>
          <h2>Call us</h2>
          <a href="tel:+918789896189">+91-8789896189</a>
          <p>Reception desk · Mon–Sat</p>
        </article>
        <article>
          <h2>Email</h2>
          <a href="mailto:info&#64;hcpschool.com">info&#64;hcpschool.com</a>
          <p>We reply within one working day</p>
        </article>
        <article>
          <h2>Office hours</h2>
          <p class="strong">Mon–Sat, 9:00 AM – 4:00 PM</p>
          <p>Closed on public holidays</p>
        </article>
        <article>
          <h2>Visit campus</h2>
          <p class="strong">Holly Cross Public School</p>
          <p>Main Campus · Schedule a visit via Admissions</p>
        </article>
      </div>

      <aside class="panel">
        <h2>How can we help?</h2>
        <p>Use online admission for enrolments, or contact the office for general queries.</p>
        <div class="actions">
          <a routerLink="/admission/apply" class="primary">Apply for admission</a>
          <a routerLink="/about" class="ghost">About the school</a>
        </div>
        <div class="cms-body" *ngIf="bodyHtml" [innerHTML]="bodyHtml"></div>
      </aside>
    </div>
  `,
  styles: [
    `
      .page-hero {
        padding: 2.25rem 0 1.5rem;
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
        margin: 0 0 0.5rem;
        font-family: 'Fraunces', Georgia, serif;
        font-size: clamp(1.9rem, 4vw, 2.6rem);
        color: #0b1f3a;
      }
      .lead {
        margin: 0;
        max-width: 36rem;
        color: #64748b;
        font-size: 1.05rem;
      }
      .contact-layout {
        display: grid;
        grid-template-columns: 1.35fr 1fr;
        gap: 1.25rem;
        align-items: start;
      }
      .cards {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0.85rem;
      }
      .cards article,
      .panel {
        background: #fff;
        border: 1px solid #e6ebf2;
        border-radius: 16px;
        padding: 1.15rem 1.2rem;
        box-shadow: 0 10px 30px rgba(15, 23, 42, 0.04);
      }
      .cards h2,
      .panel h2 {
        margin: 0 0 0.45rem;
        font-size: 1rem;
        font-family: 'Fraunces', Georgia, serif;
        color: #0b1f3a;
      }
      .cards a {
        color: var(--sf-primary, #0b3d91);
        font-weight: 650;
        text-decoration: none;
        font-size: 1.05rem;
      }
      .cards p,
      .panel p {
        margin: 0.35rem 0 0;
        color: #64748b;
        line-height: 1.45;
      }
      .strong {
        color: #0f172a !important;
        font-weight: 600;
      }
      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 0.55rem;
        margin: 1rem 0 1.25rem;
      }
      .actions a {
        text-decoration: none;
        border-radius: 999px;
        padding: 0.55rem 1rem;
        font-weight: 650;
        font-size: 0.9rem;
      }
      .primary {
        background: var(--sf-primary, #0b3d91);
        color: #fff;
      }
      .ghost {
        border: 1px solid #cbd5e1;
        color: #0f172a;
      }
      .cms-body {
        border-top: 1px solid #eef2f7;
        padding-top: 1rem;
        color: #334155;
        line-height: 1.55;
        font-size: 0.95rem;
      }
      @media (max-width: 860px) {
        .contact-layout,
        .cards {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class ContactPageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  private readonly seo = inject(SeoService);
  title = 'Contact Us';
  summary = 'Reach the school office — we are happy to help families and visitors.';
  bodyHtml = '';

  ngOnInit(): void {
    this.api.resolve().subscribe(() => {
      this.api.getPage('contact').subscribe({
        next: (p) => {
          this.title = String(p['title'] || this.title);
          this.summary = String(p['summary'] || this.summary);
          this.bodyHtml = String(p['bodyHtml'] || '');
          this.seo.apply({
            title: String(p['seoTitle'] || p['title'] || 'Contact'),
            description: String(p['seoDescription'] || p['summary'] || ''),
          });
        },
        error: () => undefined,
      });
    });
  }
}
