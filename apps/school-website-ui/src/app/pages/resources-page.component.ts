import { Component, OnInit, inject } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WebsiteApiService } from '../core/website-api.service';
import { SeoService } from '../core/seo.service';

@Component({
  selector: 'app-resources-page',
  standalone: true,
  imports: [NgFor, NgIf, FormsModule],
  template: `
    <section class="page">
      <p class="eyebrow">Resources</p>
      <h1>Downloads and school resources</h1>
      <p class="lead">Calendars, forms, and notices the school has published for families.</p>
      <div class="tools" *ngIf="documents.length">
        <input [(ngModel)]="query" name="docQuery" placeholder="Search documents" />
        <select [(ngModel)]="category" name="docCategory">
          <option value="">All categories</option>
          <option *ngFor="let name of categories" [value]="name">{{ name }}</option>
        </select>
      </div>
      <p *ngIf="loading">Loading resources…</p>
      <p *ngIf="!loading && !documents.length">
        No public documents are published yet. The school can add them in Website CMS → Downloads.
      </p>
      <p *ngIf="!loading && documents.length && !visible.length">No documents match this search.</p>
      <ul *ngIf="visible.length">
        <li *ngFor="let doc of visible">
          <a [href]="fileUrl(doc)" target="_blank" rel="noopener">{{ doc['title'] }}</a>
          <span *ngIf="doc['category']">{{ doc['category'] }}</span>
          <span *ngIf="doc['academicYear']">{{ doc['academicYear'] }}</span>
          <p *ngIf="doc['summary']">{{ doc['summary'] }}</p>
        </li>
      </ul>
    </section>
  `,
  styles: [
    `
      .page {
        padding: 2rem 0 3rem;
      }
      .eyebrow {
        letter-spacing: 0.12em;
        text-transform: uppercase;
        color: var(--sf-primary, #0b3d91);
        font-size: 0.75rem;
        font-weight: 650;
      }
      h1 {
        font-family: 'Fraunces', Georgia, serif;
        margin: 0.3rem 0 0.5rem;
      }
      .lead,
      li p {
        color: #64748b;
      }
      ul {
        list-style: none;
        padding: 0;
        display: grid;
        gap: 0.75rem;
      }
      li {
        background: #fff;
        border: 1px solid #e6ebf2;
        border-radius: 14px;
        padding: 1rem 1.1rem;
      }
      a {
        color: var(--sf-primary, #0b3d91);
        font-weight: 650;
        text-decoration: none;
      }
      span {
        display: inline-block;
        margin-left: 0.5rem;
        font-size: 0.75rem;
        letter-spacing: 0.04em;
        text-transform: uppercase;
        color: #64748b;
      }
    `,
  ],
})
export class ResourcesPageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  private readonly seo = inject(SeoService);
  documents: Array<Record<string, unknown>> = [];
  loading = true;
  query = '';
  category = '';

  get categories(): string[] {
    return [...new Set(this.documents.map((doc) => String(doc['category'] || '')).filter(Boolean))].sort();
  }

  get visible(): Array<Record<string, unknown>> {
    const q = this.query.trim().toLowerCase();
    return this.documents.filter((doc) => {
      if (this.category && String(doc['category'] || '') !== this.category) return false;
      if (!q) return true;
      return `${doc['title'] || ''} ${doc['summary'] || ''}`.toLowerCase().includes(q);
    });
  }

  ngOnInit(): void {
    this.api.resolve().subscribe((site) => {
      this.seo.apply({
        title: `Resources | ${site.displayName}`,
        description: site.seo?.['defaultDescription'] || 'School downloads and resources',
        canonical: window.location.origin + '/resources',
      });
      this.api.listDocuments().subscribe({
        next: (rows) => {
          this.documents = rows || [];
          this.loading = false;
        },
        error: () => {
          this.documents = [];
          this.loading = false;
        },
      });
    });
  }

  fileUrl(doc: Record<string, unknown>): string {
    return this.api.mediaUrl(doc['fileUrl'] == null ? '' : String(doc['fileUrl']));
  }
}
