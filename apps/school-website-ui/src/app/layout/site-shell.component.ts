import { Component, OnInit, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AsyncPipe, NgFor, NgIf } from '@angular/common';
import { WebsiteApiService } from '../core/website-api.service';

@Component({
  selector: 'app-site-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgIf, NgFor, AsyncPipe],
  template: `
    <div class="shell" *ngIf="api.resolve() | async as site; else loading">
      <header class="top">
        <a routerLink="/" class="brand">{{ site.displayName }}</a>
        <nav>
          <a
            *ngFor="let item of site.navigation"
            [routerLink]="item.external ? null : item.path"
            [href]="item.external ? site.erpLoginUrl : null"
            [attr.target]="item.external ? '_blank' : null"
            routerLinkActive="active"
            [routerLinkActiveOptions]="{ exact: item.path === '/' }"
            >{{ item.label }}</a
          >
          <a class="cta" [href]="site.erpLoginUrl" target="_blank" rel="noopener">ERP Login</a>
        </nav>
      </header>
      <main>
        <router-outlet />
      </main>
      <footer>
        <p>&copy; {{ year }} {{ site.displayName }}. Powered by SugamFlow.</p>
      </footer>
    </div>
    <ng-template #loading>
      <div class="loading">Loading school website…</div>
    </ng-template>
  `,
  styles: [
    `
      .shell {
        min-height: 100vh;
        display: flex;
        flex-direction: column;
        background: #f7f8fb;
        color: #1a1f2c;
      }
      .top {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 1rem;
        padding: 1rem 1.5rem;
        background: #fff;
        border-bottom: 1px solid #e6e9f0;
        position: sticky;
        top: 0;
        z-index: 10;
      }
      .brand {
        font-family: 'Segoe UI', system-ui, sans-serif;
        font-weight: 700;
        font-size: 1.25rem;
        color: var(--sf-primary, #0b3d91);
        text-decoration: none;
      }
      nav {
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
        align-items: center;
      }
      nav a {
        text-decoration: none;
        color: #334155;
        font-size: 0.95rem;
      }
      nav a.active {
        color: var(--sf-primary, #0b3d91);
        font-weight: 600;
      }
      nav a.cta {
        background: var(--sf-primary, #0b3d91);
        color: #fff;
        padding: 0.4rem 0.8rem;
        border-radius: 6px;
      }
      main {
        flex: 1;
        width: min(1100px, 100%);
        margin: 0 auto;
        padding: 1.5rem;
      }
      footer {
        padding: 1.25rem 1.5rem;
        background: #0f172a;
        color: #cbd5e1;
        text-align: center;
      }
      .loading {
        padding: 4rem;
        text-align: center;
      }
      @media (max-width: 800px) {
        .top {
          flex-direction: column;
          align-items: flex-start;
        }
      }
    `,
  ],
})
export class SiteShellComponent implements OnInit {
  readonly api = inject(WebsiteApiService);
  readonly year = new Date().getFullYear();

  ngOnInit(): void {
    this.api.resolve().subscribe();
  }
}
