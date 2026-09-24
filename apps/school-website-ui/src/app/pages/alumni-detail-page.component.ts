import { Component, OnInit, inject } from '@angular/core';
import { NgIf } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { WebsiteApiService } from '../core/website-api.service';
import { SeoService } from '../core/seo.service';

@Component({
  selector: 'app-alumni-detail-page',
  standalone: true,
  imports: [NgIf, RouterLink],
  template: `
    <p><a routerLink="/alumni">← Alumni</a></p>
    <article *ngIf="profile as p">
      <h1>{{ p['fullName'] }}</h1>
      <p class="meta" *ngIf="p['batchYear']">Batch {{ p['batchYear'] }}</p>
      <p class="headline">{{ p['headline'] }}</p>
      <div class="body" [innerHTML]="p['bioHtml']"></div>
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
      .meta,
      .headline {
        color: #64748b;
      }
    `,
  ],
})
export class AlumniDetailPageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly seo = inject(SeoService);
  profile: Record<string, unknown> | null = null;

  ngOnInit(): void {
    const slug = this.route.snapshot.paramMap.get('slug') || '';
    this.api.resolve().subscribe(() => {
      this.api.getAlumni(slug).subscribe((row) => {
        this.profile = row;
        this.seo.apply({
          title: String(row['fullName'] || 'Alumni'),
          description: String(row['headline'] || ''),
        });
      });
    });
  }
}
