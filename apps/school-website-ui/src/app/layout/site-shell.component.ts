import { Component, OnInit, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AsyncPipe, NgFor, NgIf } from '@angular/common';
import { WebsiteApiService, WebsiteResolve, SiteBrandContact } from '../core/website-api.service';
import { AnalyticsService } from '../core/analytics.service';

@Component({
  selector: 'app-site-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgIf, NgFor, AsyncPipe],
  template: `
    <div class="shell" *ngIf="api.resolve() | async as site; else loading">
      <div class="utility" *ngIf="hasUtility(site)">
        <div class="utility-inner">
          <div class="utility-left">
            <ng-container *ngIf="brand(site) as b">
              <a *ngIf="b.contactEmail" [href]="api.mailtoHref(b.contactEmail)">{{ b.contactEmail }}</a>
              <span class="sep" *ngIf="b.contactEmail && b.contactPhone">|</span>
              <a *ngIf="b.contactPhone" [href]="api.telHref(b.contactPhone)">{{ b.contactPhone }}</a>
              <span class="sep hide-sm" *ngIf="(b.contactEmail || b.contactPhone) && b.workingHours">|</span>
              <span class="hide-sm" *ngIf="b.workingHours">{{ b.workingHours }}</span>
            </ng-container>
          </div>
          <div class="utility-right">
            <a [href]="parentUrl" target="_blank" rel="noopener">Parent</a>
            <a [href]="teacherUrl" target="_blank" rel="noopener">Teacher</a>
            <a [href]="signInUrl" target="_blank" rel="noopener">ERP Login</a>
          </div>
        </div>
      </div>

      <header class="top">
        <div class="top-inner">
          <a routerLink="/" class="brand">
            <img
              *ngIf="logoUrl(site) as logo; else initialsMark"
              class="brand-logo"
              [src]="logo"
              [alt]="site.displayName"
            />
            <ng-template #initialsMark>
              <span class="brand-mark">{{ brandInitials(site.displayName) }}</span>
            </ng-template>
            <span class="brand-text">
              <strong>{{ site.displayName }}</strong>
              <small *ngIf="brand(site).tagline">{{ brand(site).tagline }}</small>
            </span>
          </a>

          <button
            type="button"
            class="menu-toggle"
            (click)="menuOpen = !menuOpen"
            [attr.aria-expanded]="menuOpen"
            [attr.aria-label]="menuOpen ? 'Close menu' : 'Open menu'"
          >
            {{ menuOpen ? 'Close' : 'Menu' }}
          </button>

          <nav [class.open]="menuOpen" (click)="menuOpen = false" aria-label="Primary">
            <a
              *ngFor="let item of primaryNav(site.navigation)"
              [routerLink]="item.path"
              routerLinkActive="active"
              [routerLinkActiveOptions]="{ exact: item.path === '/' }"
              >{{ item.label }}</a
            >
          </nav>

          <div class="header-actions">
            <a class="ghost" routerLink="/admission/apply">Apply</a>
            <a class="cta" [href]="signInUrl" target="_blank" rel="noopener">ERP Login</a>
          </div>
        </div>
      </header>

      <main>
        <router-outlet />
      </main>

      <footer>
        <div class="footer-grid">
          <div>
            <h3>{{ site.displayName }}</h3>
            <p *ngIf="brand(site).footerBlurb">{{ brand(site).footerBlurb }}</p>
            <p *ngIf="brand(site).address">{{ brand(site).address }}</p>
            <p *ngIf="brand(site).addressLine2">{{ brand(site).addressLine2 }}</p>
          </div>
          <div>
            <h4>Explore</h4>
            <a
              *ngFor="let item of footerNav(site.navigation)"
              [routerLink]="item.path"
              >{{ item.label }}</a
            >
          </div>
          <div>
            <h4>Visit / Call</h4>
            <ng-container *ngIf="brand(site) as b">
              <p *ngIf="b.contactEmail">
                <a [href]="api.mailtoHref(b.contactEmail)">{{ b.contactEmail }}</a>
              </p>
              <p *ngIf="b.contactPhone">
                <a [href]="api.telHref(b.contactPhone)">{{ b.contactPhone }}</a>
              </p>
              <p *ngIf="b.workingHours">{{ b.workingHours }}</p>
              <p *ngIf="!b.contactEmail && !b.contactPhone && !b.workingHours">
                Contact details can be set in Website → Theme.
              </p>
            </ng-container>
          </div>
          <div>
            <h4>Account</h4>
            <a [href]="parentUrl" target="_blank" rel="noopener">Parent portal</a>
            <a [href]="teacherUrl" target="_blank" rel="noopener">Teacher portal</a>
            <a [href]="signInUrl" target="_blank" rel="noopener">Staff / ERP login</a>
            <div class="socials" *ngIf="hasSocials(site)">
              <a *ngIf="brand(site).socialFacebook" [href]="brand(site).socialFacebook" target="_blank" rel="noopener">Facebook</a>
              <a *ngIf="brand(site).socialInstagram" [href]="brand(site).socialInstagram" target="_blank" rel="noopener">Instagram</a>
              <a *ngIf="brand(site).socialYoutube" [href]="brand(site).socialYoutube" target="_blank" rel="noopener">YouTube</a>
              <a *ngIf="brand(site).socialWhatsapp" [href]="brand(site).socialWhatsapp" target="_blank" rel="noopener">WhatsApp</a>
            </div>
          </div>
        </div>
        <div class="footer-bottom">
          <span>&copy; {{ year }} {{ site.displayName }}. All rights reserved.</span>
        </div>
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
        background: #f4f6f9;
        color: #122033;
      }
      .utility {
        background: #0b1f3a;
        color: #dbe7f5;
        font-size: 0.82rem;
      }
      .utility-inner,
      .top-inner,
      .footer-grid,
      .footer-bottom {
        width: min(1180px, calc(100% - 2rem));
        margin: 0 auto;
      }
      .utility-inner {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        padding: 0.45rem 0;
        flex-wrap: wrap;
      }
      .utility a {
        color: #e8f0fa;
        text-decoration: none;
      }
      .utility .sep {
        opacity: 0.45;
        margin: 0 0.45rem;
      }
      .utility-right {
        display: flex;
        gap: 1rem;
      }
      .top {
        position: sticky;
        top: 0;
        z-index: 20;
        background: rgba(255, 255, 255, 0.96);
        backdrop-filter: blur(10px);
        border-bottom: 1px solid #e2e8f0;
      }
      .top-inner {
        position: relative;
        display: grid;
        grid-template-columns: minmax(180px, 1.1fr) minmax(0, 2.4fr) auto;
        align-items: center;
        gap: 1rem;
        padding: 0.85rem 0;
      }
      .brand {
        display: flex;
        align-items: center;
        gap: 0.7rem;
        text-decoration: none;
        color: var(--sf-primary, #0b3d91);
        min-width: 0;
      }
      .brand-mark {
        width: 2.5rem;
        height: 2.5rem;
        border-radius: 12px;
        display: grid;
        place-items: center;
        background: linear-gradient(145deg, var(--sf-primary, #0b3d91), #123f7a);
        color: #fff;
        font-weight: 700;
        font-size: 0.85rem;
        flex-shrink: 0;
      }
      .brand-logo {
        width: 2.75rem;
        height: 2.75rem;
        object-fit: contain;
        flex-shrink: 0;
        border-radius: 8px;
        background: #fff;
      }
      .brand-text {
        display: grid;
        min-width: 0;
      }
      .brand-text strong {
        font-family: 'Fraunces', Georgia, serif;
        font-size: 1.05rem;
        line-height: 1.15;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .brand-text small {
        color: #64748b;
        font-size: 0.72rem;
        letter-spacing: 0.04em;
        text-transform: uppercase;
      }
      nav {
        display: flex;
        flex-wrap: wrap;
        justify-content: center;
        gap: 0.15rem 0.35rem;
      }
      nav a {
        text-decoration: none;
        color: #334155;
        font-size: 0.92rem;
        font-weight: 500;
        padding: 0.4rem 0.65rem;
        border-radius: 999px;
      }
      nav a:hover {
        background: #eef3fb;
        color: var(--sf-primary, #0b3d91);
      }
      nav a.active {
        background: #e8eefc;
        color: var(--sf-primary, #0b3d91);
        font-weight: 650;
      }
      .header-actions {
        display: flex;
        gap: 0.5rem;
        justify-content: flex-end;
      }
      .header-actions a,
      .menu-toggle {
        text-decoration: none;
        border-radius: 999px;
        padding: 0.45rem 0.9rem;
        font-weight: 650;
        font-size: 0.88rem;
        border: 1px solid transparent;
        background: transparent;
        cursor: pointer;
      }
      .ghost {
        border-color: #cbd5e1 !important;
        color: #0f172a;
      }
      .cta {
        background: var(--sf-primary, #0b3d91);
        color: #fff !important;
      }
      .menu-toggle {
        display: none;
        border-color: #cbd5e1;
      }
      main {
        flex: 1;
        width: min(1180px, 100%);
        margin: 0 auto;
        padding: 0 1rem 2.5rem;
      }
      footer {
        background: #0b1f3a;
        color: #c9d6e8;
        margin-top: auto;
        padding: 2.5rem 0 1rem;
      }
      .footer-grid {
        display: grid;
        grid-template-columns: 1.4fr 1fr 1fr 1fr;
        gap: 1.5rem;
      }
      footer h3,
      footer h4 {
        color: #fff;
        margin: 0 0 0.75rem;
        font-family: 'Fraunces', Georgia, serif;
      }
      footer h4 {
        font-size: 0.95rem;
      }
      footer p {
        margin: 0 0 0.4rem;
        line-height: 1.5;
        font-size: 0.92rem;
      }
      footer a {
        display: block;
        color: #d7e3f4;
        text-decoration: none;
        margin-bottom: 0.35rem;
        font-size: 0.92rem;
      }
      footer a:hover {
        color: #fff;
      }
      .footer-note {
        margin-top: 0.55rem !important;
        font-size: 0.8rem !important;
        color: #9fb0c7 !important;
        line-height: 1.4 !important;
      }
      .socials {
        margin-top: 0.75rem;
        display: flex;
        flex-wrap: wrap;
        gap: 0.65rem;
      }
      .socials a {
        display: inline;
        margin: 0;
        font-size: 0.85rem;
        color: #9fb0c7;
      }
      footer .utility-right a,
      .footer-grid a[href^='mailto'],
      .footer-grid a[href^='tel'] {
        display: inline;
        color: inherit;
      }
      .footer-bottom {
        margin-top: 1.75rem;
        padding-top: 1rem;
        border-top: 1px solid rgba(255, 255, 255, 0.12);
        font-size: 0.82rem;
        color: #9fb0c7;
      }
      .loading {
        padding: 4rem;
        text-align: center;
      }
      @media (max-width: 960px) {
        .top-inner {
          grid-template-columns: 1fr auto auto;
        }
        nav {
          display: none;
          position: absolute;
          left: 0;
          right: 0;
          top: 100%;
          background: #fff;
          border-bottom: 1px solid #e2e8f0;
          padding: 0.75rem 1rem 1rem;
          flex-direction: column;
          align-items: stretch;
        }
        nav.open {
          display: flex;
        }
        .menu-toggle {
          display: inline-flex;
        }
        .footer-grid {
          grid-template-columns: 1fr 1fr;
        }
        .hide-sm {
          display: none;
        }
      }
      @media (max-width: 560px) {
        .footer-grid {
          grid-template-columns: 1fr;
        }
        .header-actions .ghost {
          display: none;
        }
      }
    `,
  ],
})
export class SiteShellComponent implements OnInit {
  readonly api = inject(WebsiteApiService);
  private readonly analytics = inject(AnalyticsService);
  readonly year = new Date().getFullYear();
  /** Staff / general ERP sign-in (role chosen on login when no destination). */
  signInUrl = '#';
  parentUrl = '#';
  teacherUrl = '#';
  menuOpen = false;

  ngOnInit(): void {
    this.analytics.start();
    this.api.resolve().subscribe(() => {
      this.signInUrl = this.api.erpLoginUrl();
      this.parentUrl = this.api.erpLoginUrl('parent');
      this.teacherUrl = this.api.erpLoginUrl('teacher');
    });
  }

  brand(site: WebsiteResolve): SiteBrandContact {
    return this.api.brandContact(site);
  }

  hasUtility(site: WebsiteResolve): boolean {
    const b = this.brand(site);
    return !!(b.contactEmail || b.contactPhone || b.workingHours);
  }

  hasSocials(site: WebsiteResolve): boolean {
    const b = this.brand(site);
    return !!(b.socialFacebook || b.socialInstagram || b.socialYoutube || b.socialWhatsapp);
  }

  brandInitials(name: string): string {
    const parts = (name || 'S').trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return 'S';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  /** Theme logo when set; otherwise null so initials mark shows. */
  logoUrl(site: { theme?: Record<string, string | null> | null }): string | null {
    const raw = site?.theme?.['logoUrl'];
    if (raw == null) return null;
    const s = String(raw).trim();
    if (!s || s === 'null' || s === 'undefined') return null;
    return this.api.mediaUrl(s);
  }

  primaryNav(
    navigation: Array<{ label: string; path: string; order?: number; external?: boolean }>
  ) {
    return (navigation || []).filter((n) => {
      const label = (n.label || '').toLowerCase();
      const path = (n.path || '').toLowerCase();
      if (n.external) return false;
      if (path === '/login' || path.includes('/login')) return false;
      if (label.includes('login') || label.includes('portal') || label.includes('sign in')) {
        return false;
      }
      return true;
    });
  }

  /** Footer explore links — primary nav, capped for layout. */
  footerNav(
    navigation: Array<{ label: string; path: string; order?: number; external?: boolean }>
  ) {
    return this.primaryNav(navigation).slice(0, 8);
  }
}
