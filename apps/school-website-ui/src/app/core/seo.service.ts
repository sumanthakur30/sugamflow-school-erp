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
    canonical?: string | null;
    jsonLd?: Record<string, unknown> | null;
  }): void {
    const pageTitle = (opts.title || 'School Website').trim();
    this.title.setTitle(pageTitle);
    this.upsert('name', 'description', opts.description || pageTitle);
    this.upsert('property', 'og:title', pageTitle);
    this.upsert('property', 'og:description', opts.description || pageTitle);
    if (opts.imageUrl) {
      this.upsert('property', 'og:image', opts.imageUrl);
    }
    this.setCanonical(opts.canonical || '');
    this.setJsonLd(opts.jsonLd || null);
  }

  schoolJsonLd(input: {
    name: string;
    url: string;
    description?: string;
    email?: string;
    telephone?: string;
    address?: string;
  }): Record<string, unknown> {
    const data: Record<string, unknown> = {
      '@context': 'https://schema.org',
      '@type': 'School',
      name: input.name,
      url: input.url,
    };
    if (input.description) data['description'] = input.description;
    if (input.email) data['email'] = input.email;
    if (input.telephone) data['telephone'] = input.telephone;
    if (input.address) {
      data['address'] = { '@type': 'PostalAddress', streetAddress: input.address };
    }
    return data;
  }

  private setCanonical(href: string): void {
    let link = document.querySelector("link[rel='canonical']") as HTMLLinkElement | null;
    if (!href) {
      link?.remove();
      return;
    }
    if (!link) {
      link = document.createElement('link');
      link.rel = 'canonical';
      document.head.appendChild(link);
    }
    link.href = href;
  }

  private setJsonLd(data: Record<string, unknown> | null): void {
    const id = 'sf-jsonld';
    let script = document.getElementById(id) as HTMLScriptElement | null;
    if (!data) {
      script?.remove();
      return;
    }
    if (!script) {
      script = document.createElement('script');
      script.id = id;
      script.type = 'application/ld+json';
      document.head.appendChild(script);
    }
    script.text = JSON.stringify(data);
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
