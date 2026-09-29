import { Component, OnInit, inject } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { WebsiteApiService } from '../core/website-api.service';
import { SeoService } from '../core/seo.service';

@Component({
  selector: 'app-gallery-page',
  standalone: true,
  imports: [NgFor, NgIf],
  template: `
    <section class="page-hero" [class.editorial]="editorial">
      <p class="eyebrow">Campus</p>
      <h1>Gallery</h1>
      <p class="lead">Moments from classrooms, sports, and celebrations.</p>
    </section>

    <div class="filters" *ngIf="editorial && photos.length">
      <button
        type="button"
        *ngFor="let name of filters"
        [class.on]="activeFilter === name"
        (click)="activeFilter = name"
      >
        {{ name }}
      </button>
    </div>

    <div class="masonry" *ngIf="editorial && visiblePhotos.length; else classic">
      <figure *ngFor="let g of visiblePhotos; let i = index" [class.feature]="i % 7 === 0">
        <img [src]="mediaUrl(g['imageUrl'] ?? g['url'])" [alt]="asText(g['title'], 'Gallery')" loading="lazy" />
        <figcaption>
          <small>{{ categoryOf(g) }}</small>
          <strong>{{ g['title'] }}</strong>
          <span *ngIf="visibleCaption(g)">{{ visibleCaption(g) }}</span>
        </figcaption>
      </figure>
    </div>

    <ng-template #classic>
      <div class="albums" *ngIf="!editorial && albums.length; else empty">
        <section *ngFor="let album of albums">
          <h2>{{ album.name }}</h2>
          <div class="grid">
            <figure *ngFor="let g of album.items">
              <img
                [src]="mediaUrl(g['imageUrl'] ?? g['url'])"
                [alt]="asText(g['title'], 'Gallery')"
                loading="lazy"
              />
              <figcaption>
                <strong>{{ g['title'] }}</strong>
                <span *ngIf="visibleCaption(g)">{{ visibleCaption(g) }}</span>
              </figcaption>
            </figure>
          </div>
        </section>
      </div>
    </ng-template>
    <ng-template #empty>
      <p class="empty" *ngIf="!editorial || !photos.length">
        No published gallery images yet. Schools upload from Website → Gallery.
      </p>
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
      .albums section {
        margin-bottom: 2rem;
      }
      h2 {
        margin: 0 0 0.85rem;
        font-family: 'Fraunces', Georgia, serif;
        font-size: 1.35rem;
        text-transform: capitalize;
      }
      .grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
        gap: 1rem;
      }
      figure {
        margin: 0;
        background: #fff;
        border: 1px solid #e6e9f0;
        border-radius: 12px;
        overflow: hidden;
      }
      img {
        width: 100%;
        height: 180px;
        object-fit: cover;
        display: block;
        transition: transform 0.35s ease;
      }
      figure:hover img {
        transform: scale(1.04);
      }
      figcaption {
        padding: 0.75rem;
        display: grid;
        gap: 0.25rem;
      }
      figcaption span {
        color: #64748b;
        font-size: 0.9rem;
      }
      .empty {
        color: #64748b;
        background: #fff;
        border: 1px dashed #cbd5e1;
        border-radius: 12px;
        padding: 1.25rem;
      }
      .editorial h1 {
        font-family: var(--sf-serif, 'Playfair Display', Georgia, serif);
        font-size: clamp(2.6rem, 5vw, 4.2rem);
      }
      .filters {
        display: flex;
        flex-wrap: wrap;
        gap: 0.45rem;
        margin: 0.5rem 0 1.25rem;
      }
      .filters button {
        border: 1px solid #e4dcc8;
        background: #fff;
        border-radius: 999px;
        padding: 0.45rem 0.85rem;
        cursor: pointer;
        font: inherit;
      }
      .filters button.on {
        background: var(--sf-primary, #102a43);
        color: #fff;
        border-color: transparent;
      }
      .masonry {
        columns: 3;
        column-gap: 0.9rem;
      }
      .masonry figure {
        break-inside: avoid;
        margin: 0 0 0.9rem;
        position: relative;
        border: 0;
        border-radius: 16px;
      }
      .masonry img {
        height: 220px;
      }
      .masonry .feature img {
        height: 360px;
      }
      .masonry figcaption {
        position: absolute;
        inset: auto 0 0 0;
        color: #fff;
        background: linear-gradient(transparent, rgba(10, 22, 40, 0.78));
        opacity: 0;
        transition: opacity 0.25s ease;
      }
      .masonry figure:hover figcaption,
      .masonry figure:focus-within figcaption {
        opacity: 1;
      }
      .masonry figcaption small {
        display: block;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        font-size: 0.7rem;
      }
      .masonry figcaption span {
        color: rgba(255, 255, 255, 0.85);
      }
      @media (max-width: 800px) {
        .masonry {
          columns: 1;
        }
      }
    `,
  ],
})
export class GalleryPageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  private readonly seo = inject(SeoService);
  albums: Array<{ name: string; items: Array<Record<string, unknown>> }> = [];
  photos: Array<Record<string, unknown>> = [];
  editorial = false;
  readonly filters = ['All', 'Academics', 'Sports', 'Events', 'Celebrations', 'Campus'];
  activeFilter = 'All';

  get visiblePhotos(): Array<Record<string, unknown>> {
    if (this.activeFilter === 'All') return this.photos;
    return this.photos.filter((row) => this.categoryOf(row) === this.activeFilter);
  }

  ngOnInit(): void {
    this.api.resolve().subscribe((site) => {
      this.editorial = site.theme?.['skin'] === 'premium';
      this.seo.apply({
        title: `Gallery | ${site.displayName}`,
        description: `Campus gallery from ${site.displayName}`,
      });
      this.api.listGallery(500).subscribe((rows) => {
        const visible = (rows || []).filter((row) => String(row['album'] || '').toLowerCase() !== 'banners');
        this.photos = visible;
        const map = new Map<string, Array<Record<string, unknown>>>();
        for (const row of visible) {
          const album = String(row['album'] || 'general').trim() || 'general';
          if (!map.has(album)) map.set(album, []);
          map.get(album)!.push(row);
        }
        this.albums = [...map.entries()].map(([name, items]) => ({ name, items }));
      });
    });
  }

  mediaUrl(path?: string | null | unknown): string {
    return this.api.mediaUrl(path == null ? undefined : String(path));
  }

  visibleCaption(row: Record<string, unknown>): string {
    const caption = String(row['caption'] || '').trim();
    if (!caption || /^imported from/i.test(caption)) return '';
    return caption;
  }

  categoryOf(row: Record<string, unknown>): string {
    const blob = `${row['album'] || ''} ${row['title'] || ''}`.toLowerCase();
    if (/annual|celebration|function/.test(blob)) return 'Celebrations';
    if (/game|sport|chess|tennis|play/.test(blob)) return 'Sports';
    if (/lab|library|class|computer|science/.test(blob)) return 'Academics';
    if (/campus|corridor|school|building|hall/.test(blob)) return 'Campus';
    if (String(row['album'] || '').toLowerCase() === 'activities') return 'Events';
    if (/facilities|school/.test(String(row['album'] || '').toLowerCase())) return 'Campus';
    return 'Events';
  }

  asText(value: unknown, fallback = ''): string {
    if (value == null) return fallback;
    const s = String(value).trim();
    return s || fallback;
  }
}
