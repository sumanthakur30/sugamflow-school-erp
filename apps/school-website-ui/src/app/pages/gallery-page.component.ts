import { Component, OnInit, inject } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { WebsiteApiService } from '../core/website-api.service';
import { SeoService } from '../core/seo.service';

@Component({
  selector: 'app-gallery-page',
  standalone: true,
  imports: [NgFor, NgIf],
  template: `
    <section class="page-hero">
      <p class="eyebrow">Campus</p>
      <h1>Gallery</h1>
      <p class="lead">Moments from classrooms, sports, and celebrations.</p>
    </section>

    <div class="albums" *ngIf="albums.length; else empty">
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
              <span *ngIf="g['caption']">{{ g['caption'] }}</span>
            </figcaption>
          </figure>
        </div>
      </section>
    </div>
    <ng-template #empty>
      <p class="empty">No published gallery images yet. Schools upload from Website → Gallery.</p>
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
    `,
  ],
})
export class GalleryPageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  private readonly seo = inject(SeoService);
  albums: Array<{ name: string; items: Array<Record<string, unknown>> }> = [];

  ngOnInit(): void {
    this.api.resolve().subscribe((site) => {
      this.seo.apply({
        title: `Gallery | ${site.displayName}`,
        description: `Campus gallery from ${site.displayName}`,
      });
      this.api.listGallery().subscribe((rows) => {
        const map = new Map<string, Array<Record<string, unknown>>>();
        for (const row of rows || []) {
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

  asText(value: unknown, fallback = ''): string {
    if (value == null) return fallback;
    const s = String(value).trim();
    return s || fallback;
  }
}
