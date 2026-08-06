import { Component, OnInit, inject } from '@angular/core';
import { NgFor } from '@angular/common';
import { WebsiteApiService } from '../core/website-api.service';

@Component({
  selector: 'app-gallery-page',
  standalone: true,
  imports: [NgFor],
  template: `
    <h1>Gallery</h1>
    <div class="grid">
      <figure *ngFor="let g of items">
        <img [src]="mediaUrl(g['imageUrl'] ?? g['url'])" [alt]="asText(g['title'], 'Gallery')" loading="lazy" />
        <figcaption>
          <strong>{{ g['title'] }}</strong>
          <span>{{ g['caption'] }}</span>
        </figcaption>
      </figure>
    </div>
  `,
  styles: [
    `
      .grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
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
    `,
  ],
})
export class GalleryPageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  items: Array<Record<string, unknown>> = [];

  ngOnInit(): void {
    this.api.resolve().subscribe(() => {
      this.api.listGallery().subscribe((rows) => (this.items = rows || []));
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
