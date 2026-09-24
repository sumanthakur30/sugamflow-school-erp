import { Injectable, inject } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);

  apply(opts: {
    title?: string | null;
    description?: string | null;
    imageUrl?: string | null;
  }): void {
    const pageTitle = (opts.title || 'School Website').trim();
    this.title.setTitle(pageTitle);
    this.upsert('name', 'description', opts.description || pageTitle);
    this.upsert('property', 'og:title', pageTitle);
    this.upsert('property', 'og:description', opts.description || pageTitle);
    if (opts.imageUrl) {
      this.upsert('property', 'og:image', opts.imageUrl);
    }
  }

  private upsert(attr: 'name' | 'property', key: string, content: string): void {
    const selector = attr === 'name' ? `name='${key}'` : `property='${key}'`;
    if (this.meta.getTag(selector)) {
      this.meta.updateTag({ [attr]: key, content });
    } else {
      this.meta.addTag({ [attr]: key, content });
    }
  }
}
