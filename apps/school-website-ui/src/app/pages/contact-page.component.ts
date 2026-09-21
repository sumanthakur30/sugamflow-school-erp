import { Component, OnInit, inject } from '@angular/core';
import { NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { WebsiteApiService, SiteBrandContact } from '../core/website-api.service';
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
        <article *ngIf="brand.contactPhone">
          <h2>Call us</h2>
          <a [href]="api.telHref(brand.contactPhone)">{{ brand.contactPhone }}</a>
          <p *ngIf="brand.workingHours">{{ brand.workingHours }}</p>
        </article>
        <article *ngIf="brand.contactEmail">
          <h2>Email</h2>
          <a [href]="api.mailtoHref(brand.contactEmail)">{{ brand.contactEmail }}</a>
          <p>We reply within one working day</p>
        </article>
        <article *ngIf="brand.workingHours">
          <h2>Office hours</h2>
          <p class="strong">{{ brand.workingHours }}</p>
        </article>
        <article *ngIf="brand.displayName || brand.address">
          <h2>Visit campus</h2>
          <p class="strong" *ngIf="brand.displayName">{{ brand.displayName }}</p>
          <p *ngIf="brand.address">{{ brand.address }}</p>
          <p *ngIf="brand.addressLine2">{{ brand.addressLine2 }}</p>
          <a
            *ngIf="mapOpen"
            class="map-link"
            [href]="mapOpen"
            target="_blank"
            rel="noopener"
            >Open in Google Maps</a
          >
        </article>
        <article *ngIf="!hasContactCards">
          <h2>Contact</h2>
          <p>School contact details are managed in Website → Theme (CMS).</p>
        </article>
      </div>

      <aside class="panel">
        <h2>How can we help?</h2>
        <p>Use online admission for enrolments, or contact the office for general queries.</p>
        <div class="actions">
          <a routerLink="/admission/apply" class="primary">Apply for admission</a>
          <a routerLink="/about" class="ghost">About the school</a>
          <a
            *ngIf="brand.socialFacebook"
            class="ghost"
            [href]="brand.socialFacebook"
            target="_blank"
            rel="noopener"
            >Facebook</a
          >
          <a
            *ngIf="brand.socialWhatsapp"
            class="ghost"
            [href]="brand.socialWhatsapp"
            target="_blank"
            rel="noopener"
            >WhatsApp</a
          >
        </div>
        <div class="cms-body" *ngIf="bodyHtml" [innerHTML]="bodyHtml"></div>
      </aside>
    </div>

    <section class="map-section" *ngIf="mapEmbedSafe || mapOpen || mapPhoto">
      <div class="map-head">
        <h2>Find us on the map</h2>
        <a *ngIf="mapOpen" class="map-link" [href]="mapOpen" target="_blank" rel="noopener"
          >Open in Google Maps</a
        >
      </div>
      <div class="map-grid" [class.has-photo]="!!mapPhoto">
        <figure class="map-photo" *ngIf="mapPhoto">
          <img [src]="mapPhoto" [alt]="(brand.displayName || 'School') + ' campus'" />
          <figcaption>Campus</figcaption>
        </figure>
        <div class="map-frame" *ngIf="mapEmbedSafe">
          <iframe
            [src]="mapEmbedSafe"
            title="School location map"
            loading="lazy"
            referrerpolicy="no-referrer-when-downgrade"
            allowfullscreen
          ></iframe>
        </div>
        <p class="map-fallback" *ngIf="!mapEmbedSafe && mapOpen">
          Map embed is not set — use
          <a [href]="mapOpen" target="_blank" rel="noopener">Google Maps</a>
          for directions. Schools can paste an embed URL in Website → Theme.
        </p>
      </div>
    </section>
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
      }
      .map-link {
        display: inline-block;
        margin-top: 0.55rem;
        color: var(--sf-primary, #0b3d91);
        font-weight: 650;
        text-decoration: none;
      }
      .strong {
        margin: 0 0 0.25rem;
        font-weight: 650;
        color: #0f172a;
      }
      .panel p {
        margin: 0 0 0.85rem;
        color: #64748b;
        line-height: 1.5;
      }
      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 0.55rem;
        margin-bottom: 0.75rem;
      }
      .actions a {
        text-decoration: none;
        padding: 0.55rem 0.9rem;
        border-radius: 999px;
        font-weight: 650;
        font-size: 0.92rem;
      }
      .primary {
        background: var(--sf-secondary, #f5b700);
        color: #111;
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
      .map-section {
        margin: 2rem 0 1rem;
      }
      .map-head {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        gap: 1rem;
        flex-wrap: wrap;
        margin-bottom: 0.85rem;
      }
      .map-head h2 {
        margin: 0;
        font-family: 'Fraunces', Georgia, serif;
        font-size: 1.45rem;
        color: #0b1f3a;
      }
      .map-grid {
        display: grid;
        grid-template-columns: 1fr;
        gap: 0.85rem;
      }
      .map-grid.has-photo {
        grid-template-columns: minmax(220px, 0.9fr) 1.4fr;
        align-items: stretch;
      }
      .map-photo {
        margin: 0;
        border-radius: 18px;
        overflow: hidden;
        border: 1px solid #e2e8f0;
        background: #0b1f3a;
        box-shadow: 0 10px 30px rgba(15, 23, 42, 0.05);
        position: relative;
        min-height: 280px;
      }
      .map-photo img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        display: block;
        min-height: 280px;
      }
      .map-photo figcaption {
        position: absolute;
        left: 0.75rem;
        bottom: 0.75rem;
        margin: 0;
        padding: 0.25rem 0.55rem;
        border-radius: 999px;
        background: rgba(11, 31, 58, 0.72);
        color: #fff;
        font-size: 0.78rem;
        font-weight: 650;
        letter-spacing: 0.04em;
        text-transform: uppercase;
      }
      .map-frame {
        border-radius: 18px;
        overflow: hidden;
        border: 1px solid #e2e8f0;
        background: #e2e8f0;
        min-height: 320px;
        box-shadow: 0 10px 30px rgba(15, 23, 42, 0.05);
      }
      .map-frame iframe {
        width: 100%;
        height: min(52vh, 420px);
        border: 0;
        display: block;
      }
      .map-fallback {
        margin: 0;
        padding: 1rem 1.1rem;
        background: #fff;
        border: 1px dashed #cbd5e1;
        border-radius: 14px;
        color: #64748b;
      }
      @media (max-width: 860px) {
        .contact-layout,
        .cards,
        .map-grid.has-photo {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class ContactPageComponent implements OnInit {
  readonly api = inject(WebsiteApiService);
  private readonly seo = inject(SeoService);
  private readonly sanitizer = inject(DomSanitizer);
  title = 'Contact Us';
  summary = 'Reach the school office — we are happy to help families and visitors.';
  bodyHtml = '';
  brand: SiteBrandContact = this.emptyBrand();
  mapOpen = '';
  mapPhoto = '';
  mapEmbedSafe: SafeResourceUrl | null = null;

  get hasContactCards(): boolean {
    return !!(
      this.brand.contactPhone ||
      this.brand.contactEmail ||
      this.brand.workingHours ||
      this.brand.address ||
      this.brand.displayName
    );
  }

  ngOnInit(): void {
    this.api.resolve().subscribe((site) => {
      this.brand = this.api.brandContact(site);
      this.mapOpen = this.api.mapOpenUrl(this.brand);
      this.mapPhoto = this.api.mediaUrl(this.brand.mapPhotoUrl || undefined);
      const embed = this.api.mapEmbedSrc(this.brand);
      this.mapEmbedSafe = embed
        ? this.sanitizer.bypassSecurityTrustResourceUrl(embed)
        : null;
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

  private emptyBrand(): SiteBrandContact {
    return {
      displayName: '',
      tagline: '',
      contactEmail: '',
      contactPhone: '',
      workingHours: '',
      address: '',
      addressLine2: '',
      footerBlurb: '',
      socialFacebook: '',
      socialInstagram: '',
      socialYoutube: '',
      socialWhatsapp: '',
      mapUrl: '',
      mapEmbedUrl: '',
      mapPhotoUrl: '',
    };
  }
}
