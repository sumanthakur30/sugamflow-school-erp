import { Component, OnInit, inject } from '@angular/core';
import { NgIf } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { WebsiteApiService } from '../core/website-api.service';
import { SeoService } from '../core/seo.service';

@Component({
  selector: 'app-blog-detail-page',
  standalone: true,
  imports: [NgIf, RouterLink],
  template: `
    <p><a routerLink="/blog">← Blog</a></p>
    <article *ngIf="post as p">
      <h1>{{ p['title'] }}</h1>
      <p class="summary">{{ p['summary'] }}</p>
      <div class="body" [innerHTML]="p['bodyHtml']"></div>
    </article>
  `,
  styles: [
    `
      article {
        background: #fff;
        border: 1px solid #e6e9f0;
        border-radius: 12px;
        padding: 1.25rem;
      }
      .summary {
        color: #64748b;
      }
    `,
  ],
})
export class BlogDetailPageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly seo = inject(SeoService);
  post: Record<string, unknown> | null = null;

  ngOnInit(): void {
    const slug = this.route.snapshot.paramMap.get('slug') || '';
    this.api.resolve().subscribe(() => {
      this.api.getBlog(slug).subscribe((row) => {
        this.post = row;
        this.seo.apply({
          title: String(row['title'] || 'Blog'),
          description: String(row['summary'] || ''),
        });
      });
    });
  }
}
