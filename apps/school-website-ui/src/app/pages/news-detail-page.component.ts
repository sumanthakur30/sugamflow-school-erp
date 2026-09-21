import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NgIf } from '@angular/common';
import { WebsiteApiService } from '../core/website-api.service';

@Component({
  selector: 'app-news-detail-page',
  standalone: true,
  imports: [NgIf],
  template: `
    <article *ngIf="item as n">
      <h1>{{ n['title'] }}</h1>
      <p class="summary">{{ n['summary'] }}</p>
      <div [innerHTML]="n['bodyHtml']"></div>
    </article>
  `,
  styles: [
    `
      article {
        background: #fff;
        border-radius: 14px;
        padding: 1.5rem;
        border: 1px solid #e6e9f0;
      }
      .summary {
        color: #64748b;
      }
    `,
  ],
})
export class NewsDetailPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(WebsiteApiService);
  item: Record<string, unknown> | null = null;

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const slug = params.get('slug') || '';
      this.api.resolve().subscribe(() => {
        this.api.getNews(slug).subscribe((n) => (this.item = n));
      });
    });
  }
}
