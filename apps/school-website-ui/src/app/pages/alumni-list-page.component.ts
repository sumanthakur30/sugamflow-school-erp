import { Component, OnInit, inject } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WebsiteApiService } from '../core/website-api.service';

@Component({
  selector: 'app-alumni-list-page',
  standalone: true,
  imports: [NgFor, NgIf, RouterLink],
  template: `
    <h1>Alumni</h1>
    <article *ngFor="let a of alumni">
      <h2>
        <a [routerLink]="['/alumni', a['slug']]">{{ a['fullName'] }}</a>
      </h2>
      <p class="meta" *ngIf="a['batchYear']">Batch {{ a['batchYear'] }}</p>
      <p>{{ a['headline'] }}</p>
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
      .meta {
        color: #64748b;
        font-size: 0.9rem;
      }
      a {
        color: var(--sf-primary, #0b3d91);
        text-decoration: none;
      }
    `,
  ],
})
export class AlumniListPageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  alumni: Array<Record<string, unknown>> = [];

  ngOnInit(): void {
    this.api.resolve().subscribe(() => {
      this.api.listAlumni().subscribe((rows) => (this.alumni = rows || []));
    });
  }
}
