import { Component, OnInit, inject } from '@angular/core';
import { NgIf } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { WebsiteApiService } from '../core/website-api.service';

/** CMS admission page + CTA to online apply and ERP portals. */
@Component({
  selector: 'app-admission-hub-page',
  standalone: true,
  imports: [NgIf, RouterLink],
  template: `
    <article *ngIf="page as p" class="card">
      <h1>{{ p['title'] }}</h1>
      <p class="summary" *ngIf="p['summary']">{{ p['summary'] }}</p>
      <div class="body" [innerHTML]="trusted(p['bodyHtml'])"></div>
      <div class="actions">
        <a routerLink="/admission/apply" class="primary">Apply online</a>
        <a [href]="parentLogin" class="ghost" target="_blank" rel="noopener">Parent login</a>
        <a [href]="teacherLogin" class="ghost" target="_blank" rel="noopener">Teacher login</a>
      </div>
    </article>
  `,
  styles: [
    `
      .card {
        background: #fff;
        border-radius: 14px;
        padding: 1.5rem;
        border: 1px solid #e6e9f0;
      }
      .summary {
        color: #64748b;
      }
      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
        margin-top: 1.25rem;
      }
      .actions a {
        text-decoration: none;
        padding: 0.65rem 1rem;
        border-radius: 8px;
        font-weight: 600;
      }
      .primary {
        background: var(--sf-primary, #0b3d91);
        color: #fff;
      }
      .ghost {
        border: 1px solid #cbd5e1;
        color: #0f172a;
      }
    `,
  ],
})
export class AdmissionHubPageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly sanitizer = inject(DomSanitizer);
  page: Record<string, unknown> | null = null;
  parentLogin = '#';
  teacherLogin = '#';
  private lastScrolledFragment: string | null = null;
  private scrollTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.api.resolve().subscribe(() => {
      this.parentLogin = this.api.erpLoginUrl('parent', '/parent');
      this.teacherLogin = this.api.erpLoginUrl('teacher', '/teacher');
      this.api.getPage('admission').subscribe({
        next: (p) => {
          this.page = p;
          this.scrollTo(this.route.snapshot.fragment);
        },
        error: () =>
          (this.page = {
            title: 'Admission',
            bodyHtml: '<p>Admissions are open. Use Apply online to submit an enquiry.</p>',
          }),
      });
    });
    this.route.fragment.subscribe((id) => this.scrollTo(id));
  }

  trusted(html: unknown): SafeHtml {
    const raw = String(html || '').replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '');
    return this.sanitizer.bypassSecurityTrustHtml(raw);
  }

  private scrollTo(id: string | null): void {
    if (!id || id === this.lastScrolledFragment) return;
    if (this.scrollTimer) clearTimeout(this.scrollTimer);
    this.scrollTimer = setTimeout(() => {
      const el = document.getElementById(id);
      if (!el) return;
      this.lastScrolledFragment = id;
      el.scrollIntoView({ behavior: 'auto', block: 'start' });
    }, 60);
  }
}
