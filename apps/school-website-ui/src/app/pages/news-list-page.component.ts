import { Component, OnInit, inject } from '@angular/core';
import { NgFor } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WebsiteApiService } from '../core/website-api.service';

@Component({
  selector: 'app-news-list-page',
  standalone: true,
  imports: [NgFor, RouterLink],
  template: `
    <h1>News</h1>
    <article *ngFor="let n of news">
      <h2><a [routerLink]="['/news', n['slug']]">{{ n['title'] }}</a></h2>
      <p>{{ n['summary'] }}</p>
    </article>
  `,
  styles: [
    `
      article {
        background: #fff;
        border: 1px solid #e6e9f0;
        border-radius: 12px;
        padding: 1rem;
        margin-bottom: 0.75rem;
      }
      a {
        color: var(--sf-primary, #0b3d91);
        text-decoration: none;
      }
    `,
  ],
})
export class NewsListPageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  news: Array<Record<string, unknown>> = [];

  ngOnInit(): void {
    this.api.resolve().subscribe(() => {
      this.api.listNews().subscribe((rows) => (this.news = rows || []));
    });
  }
}
