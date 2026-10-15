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
    <div
      class="shell"
      *ngIf="api.resolve() | async as site; else loading"
      [class.premium]="isPremium(site)"
      [class.menu-open]="menuOpen"
    >
      <header class="pps-head" *ngIf="isPremium(site)">
        <div class="pps-identity">
          <a routerLink="/" class="brand">
            <img
              *ngIf="logoUrl(site) as logo; else premiumInitials"
              class="brand-logo"
              [src]="logo"
              [alt]="site.displayName"
            />
            <ng-template #premiumInitials>
              <span class="brand-mark">{{ brandInitials(site.displayName) }}</span>
            </ng-template>
            <span class="brand-text">
              <strong>{{ site.displayName }}</strong>
              <small *ngIf="brand(site).tagline">{{ brand(site).tagline }}</small>
            </span>
          </a>
          <div class="pps-tools">
            <a *ngIf="brand(site).contactEmail" [href]="api.mailtoHref(brand(site).contactEmail)">{{
              brand(site).contactEmail
            }}</a>
            <a [href]="parentUrl" target="_blank" rel="noopener">Parent</a>
            <a [href]="teacherUrl" target="_blank" rel="noopener">Teacher</a>
            <a [href]="signInUrl" target="_blank" rel="noopener">ERP Login</a>
          </div>
          <button
            type="button"
            class="menu-toggle"
            (click)="menuOpen = !menuOpen"
            [attr.aria-expanded]="menuOpen"
            [attr.aria-label]="menuOpen ? 'Close menu' : 'Open menu'"
          >
            {{ menuOpen ? 'Close' : 'Menu' }}
          </button>
        </div>
        <div class="pps-bar">
          <nav [class.open]="menuOpen" aria-label="Primary">
            <ng-container *ngFor="let item of premiumNav; trackBy: trackNav">
              <div class="nav-item" *ngIf="item.children?.length; else premiumPlain">
                <a
                  [routerLink]="item.path"
                  routerLinkActive="active"
                  [routerLinkActiveOptions]="{ exact: item.path === '/' }"
                  (click)="menuOpen = false"
                  >{{ item.label }}</a
                >
                <div class="dropdown">
                  <a
                    *ngFor="let child of item.children; trackBy: trackNav"
                    [routerLink]="child.path"
                    [fragment]="child.fragment || undefined"
                    (click)="menuOpen = false"
                    >{{ child.label }}</a
                  >
                </div>
              </div>
              <ng-template #premiumPlain>
                <a
                  [routerLink]="item.path"
                  routerLinkActive="active"
                  [routerLinkActiveOptions]="{ exact: item.path === '/' }"
                  (click)="menuOpen = false"
                  >{{ item.label }}</a
                >
              </ng-template>
            </ng-container>
          </nav>
          <a class="apply" routerLink="/admission/apply">Apply Now</a>
        </div>
      </header>

      <div class="utility" *ngIf="!isPremium(site) && hasUtility(site)">
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
            <a
              *ngIf="brand(site).socialFacebook"
              class="hide-sm"
              [href]="brand(site).socialFacebook"
              target="_blank"
              rel="noopener"
              >Facebook</a
            >
            <a [href]="parentUrl" target="_blank" rel="noopener">Parent Portal</a>
            <a [href]="teacherUrl" target="_blank" rel="noopener">Teacher Portal</a>
            <a [href]="signInUrl" target="_blank" rel="noopener">ERP Login</a>
          </div>
        </div>
      </div>

      <header class="top" *ngIf="!isPremium(site)">
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

          <nav [class.open]="menuOpen" aria-label="Primary">
            <ng-container *ngFor="let item of classicNav; trackBy: trackNav">
              <div class="nav-item" *ngIf="item.children?.length; else plainLink">
                <a
                  [routerLink]="item.path"
                  routerLinkActive="active"
                  [routerLinkActiveOptions]="{ exact: item.path === '/' }"
                  (click)="menuOpen = false"
                  >{{ item.label }}</a
                >
                <div class="dropdown">
                  <a
                    *ngFor="let child of item.children; trackBy: trackNav"
                    [routerLink]="child.path"
                    [fragment]="child.fragment || undefined"
                    (click)="menuOpen = false"
                    >{{ child.label }}</a
                  >
                </div>
              </div>
              <ng-template #plainLink>
                <a
                  [routerLink]="item.path"
                  routerLinkActive="active"
                  [routerLinkActiveOptions]="{ exact: item.path === '/' }"
                  (click)="menuOpen = false"
                  >{{ item.label }}</a
                >
              </ng-template>
            </ng-container>
          </nav>

          <div class="header-actions">
            <a class="ghost apply" routerLink="/admission/apply">Apply Now</a>
            <details class="login-menu">
              <summary class="cta">ERP Login</summary>
              <div class="login-panel">
                <a [href]="loginFor('parent')" target="_blank" rel="noopener">Parent</a>
                <a [href]="loginFor('student')" target="_blank" rel="noopener">Student</a>
                <a [href]="loginFor('teacher')" target="_blank" rel="noopener">Teacher</a>
                <a [href]="loginFor('staff')" target="_blank" rel="noopener">Staff</a>
                <a [href]="loginFor('admin')" target="_blank" rel="noopener">Admin</a>
              </div>
            </details>
          </div>
        </div>
      </header>

      <main [class.bleed]="isPremium(site)">
        <router-outlet />
      </main>

      <footer [class.premium-foot]="isPremium(site)">
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
              *ngFor="let item of footerLinks; trackBy: trackNav"
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
              <p *ngIf="api.mapOpenUrl(b)">
                <a [href]="api.mapOpenUrl(b)" target="_blank" rel="noopener">Google Maps</a>
              </p>
              <p *ngIf="!b.contactEmail && !b.contactPhone && !b.workingHours && !api.mapOpenUrl(b)">
                Contact details can be set in Website → Theme.
              </p>
            </ng-container>
          </div>
          <div>
            <h4>Account</h4>
            <a *ngIf="isPremium(site)" routerLink="/admission/apply">Apply now</a>
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
          <span class="legal" *ngIf="isPremium(site)">Privacy policy and terms can be added in the website CMS.</span>
          <a class="powered" [href]="poweredUrl(site)" target="_blank" rel="noopener">{{
            poweredLabel(site)
          }}</a>
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
        overflow-x: hidden;
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
      nav a:hover,
      nav a:focus-visible {
        background: #eef3fb;
        color: var(--sf-primary, #0b3d91);
        outline: 2px solid var(--sf-primary, #0b3d91);
        outline-offset: 2px;
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
      .login-menu {
        position: relative;
      }
      .login-menu summary {
        list-style: none;
      }
      .login-menu summary::-webkit-details-marker {
        display: none;
      }
      .login-panel {
        position: absolute;
        right: 0;
        top: calc(100% + 0.35rem);
        min-width: 10rem;
        background: #fff;
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        padding: 0.35rem;
        box-shadow: 0 12px 30px rgba(15, 23, 42, 0.12);
        z-index: 30;
      }
      .login-panel a {
        display: block;
        padding: 0.55rem 0.7rem;
        border-radius: 8px;
        color: #0f172a;
        text-decoration: none;
      }
      .login-panel a:hover,
      .login-panel a:focus-visible {
        background: #eef3fb;
        outline: none;
      }
      .footer-bottom {
        margin-top: 1.75rem;
        padding-top: 1rem;
        border-top: 1px solid rgba(255, 255, 255, 0.12);
        font-size: 0.82rem;
        color: #9fb0c7;
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        flex-wrap: wrap;
      }
      .powered {
        color: #d7e3f4;
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
        .shell:not(.premium) .header-actions .ghost {
          display: none;
        }
      }
      .shell.premium {
        background: #f7f4ee;
        color: #1c2430;
        font-family: var(--sf-sans, Manrope, sans-serif);
      }
      .pps-head {
        position: sticky;
        top: 0;
        z-index: 20;
        background: #fff;
      }
      .pps-identity,
      .pps-bar {
        width: min(1240px, calc(100% - 2rem));
        margin: 0 auto;
      }
      .pps-identity {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1rem;
        min-height: 84px;
        background: #fff;
      }
      .pps-tools {
        display: flex;
        gap: 1rem;
        align-items: center;
        margin-left: auto;
        font-size: 0.82rem;
      }
      .pps-tools a {
        color: #1b4d3e;
        text-decoration: none;
        font-weight: 650;
      }
      .pps-bar {
        width: 100%;
        max-width: none;
        background: #1b4d3e;
        display: flex;
        align-items: center;
        justify-content: center;
        position: relative;
        min-height: 52px;
      }
      .shell.premium .brand-text strong {
        font-family: var(--sf-serif, 'Playfair Display', Georgia, serif);
        font-size: 1.25rem;
        color: #1b4d3e;
        white-space: normal;
      }
      .shell.premium .brand-text small {
        color: #8a6a2f;
        letter-spacing: 0.12em;
      }
      .pps-bar nav {
        display: flex;
        justify-content: center;
        gap: 0.15rem;
        flex-wrap: nowrap;
      }
      .pps-bar nav > a,
      .pps-bar .nav-item > a {
        color: #fff;
        text-decoration: none;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        font-size: 0.78rem;
        font-weight: 700;
        padding: 0.95rem 0.9rem;
        border-radius: 0;
        background: transparent;
      }
      .pps-bar nav > a:hover,
      .pps-bar .nav-item > a:hover,
      .pps-bar nav a.active {
        background: transparent;
        color: #fff;
        box-shadow: inset 0 -3px 0 #c4a35a;
      }
      .pps-bar .nav-item {
        position: relative;
      }
      .pps-bar .dropdown {
        display: none;
        position: absolute;
        top: 100%;
        left: 0;
        min-width: 230px;
        background: #e7f0ea;
        padding: 0.4rem;
        border-radius: 0 0 12px 12px;
        box-shadow: 0 16px 30px rgba(27, 77, 62, 0.16);
        z-index: 30;
      }
      .pps-bar .nav-item:hover .dropdown,
      .pps-bar .nav-item:focus-within .dropdown {
        display: grid;
      }
      .pps-bar .dropdown a {
        color: #1b4d3e;
        text-transform: none;
        letter-spacing: 0;
        font-size: 0.92rem;
        font-weight: 650;
        padding: 0.6rem 0.75rem;
        border-radius: 8px;
        text-decoration: none;
        box-shadow: none;
      }
      .pps-bar .dropdown a:hover,
      .pps-bar .dropdown a:focus-visible {
        background: #fff;
        color: #1b4d3e;
      }
      .pps-bar .apply {
        position: absolute;
        right: max(1rem, calc((100% - 1240px) / 2));
        background: #c4a35a;
        color: #1a1408;
        text-decoration: none;
        font-weight: 750;
        border-radius: 999px;
        padding: 0.45rem 0.95rem;
        font-size: 0.84rem;
      }
      .shell.premium .footer-grid,
      .shell.premium .footer-bottom {
        width: min(1240px, calc(100% - 2.5rem));
      }
      .shell.premium main.bleed {
        width: 100%;
        padding: 0 0 3rem;
      }
      .shell.premium footer {
        background: #1b4d3e;
        padding-top: 3rem;
      }
      .shell.premium .legal {
        opacity: 0.75;
      }
      @media (max-width: 960px) {
        .pps-tools {
          display: none;
        }
        .pps-bar nav {
          display: none;
        }
        .pps-bar nav.open {
          display: flex;
        }
        .pps-bar .apply {
          position: static;
          margin-right: 0.75rem;
        }
        .shell.premium nav.open {
          position: fixed;
          inset: 0;
          z-index: 40;
          background: #1b4d3e;
          color: #fff;
          display: flex;
          flex-direction: column;
          justify-content: center;
          padding: 4.5rem 1.5rem;
          gap: 0.35rem;
        }
        .shell.premium nav.open a,
        .shell.premium nav.open .nav-item > a {
          color: #fff;
          font-family: var(--sf-serif, Georgia, serif);
          font-size: 1.7rem;
          background: transparent;
          text-transform: none;
          letter-spacing: 0;
        }
        .shell.premium nav.open .dropdown {
          position: static;
          display: grid;
          background: transparent;
          box-shadow: none;
          padding: 0 0 0.6rem 0.2rem;
        }
        .shell.premium nav.open .dropdown a {
          color: rgba(255, 255, 255, 0.78);
          font-family: var(--sf-sans, Manrope, sans-serif);
          font-size: 1rem;
        }
        .shell.premium .menu-toggle {
          z-index: 50;
          background: #fff;
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
  premiumNav: WebsiteResolve['navigation'] = [];
  classicNav: WebsiteResolve['navigation'] = [];
  footerLinks: WebsiteResolve['navigation'] = [];

  ngOnInit(): void {
    this.analytics.start();
    this.api.resolve().subscribe((site) => {
      this.signInUrl = this.api.erpLoginUrl();
      this.parentUrl = this.api.erpLoginUrl('parent');
      this.teacherUrl = this.api.erpLoginUrl('teacher');
      this.classicNav = this.primaryNav(site.navigation);
      this.footerLinks = this.classicNav.slice(0, 8);
      this.premiumNav = this.buildPremiumNav(site);
    });
  }

  trackNav(_: number, item: { path?: string; label?: string; fragment?: string }): string {
    return `${item.path || ''}|${item.label || ''}|${item.fragment || ''}`;
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

  loginFor(destination: string): string {
    return this.api.erpLoginUrl(destination);
  }

  poweredLabel(site: WebsiteResolve): string {
    const raw = site.theme?.['poweredByLabel'];
    const label = raw == null ? '' : String(raw).trim();
    return label || 'Powered by SugamFlow';
  }

  poweredUrl(site: WebsiteResolve): string {
    const raw = site.theme?.['poweredByUrl'];
    const url = raw == null ? '' : String(raw).trim();
    return url || 'https://sugamflow.com';
  }

  isPremium(site: WebsiteResolve): boolean {
    return site?.theme?.['skin'] === 'premium';
  }

  admissionNotice(site: WebsiteResolve): string {
    const notice = site.theme?.['admissionNotice'];
    return notice && String(notice).trim() ? String(notice).trim() : 'Admissions';
  }

  menuItems(site: WebsiteResolve) {
    return this.isPremium(site) ? this.premiumNav : this.classicNav;
  }

  /** Six-item bar. Gallery and news stay in the footer. */
  buildPremiumNav(site: WebsiteResolve): WebsiteResolve['navigation'] {
    const keep = ['/about', '/academics', '/admission', '/campus', '/student-life', '/contact'];
    return this.primaryNav(site.navigation)
      .filter((item) => keep.includes(item.path))
      .map((item) =>
        item.path === '/campus'
          ? {
              ...item,
              label: 'Campus',
              children: (item.children || []).map((child) => ({ ...child })),
            }
          : {
              ...item,
              children: (item.children || []).map((child) => ({ ...child })),
            }
      );
  }

  childLinks(item: { children?: Array<{ label: string; path: string; fragment?: string }> }) {
    return item.children || [];
  }

  primaryNav(
    navigation: WebsiteResolve['navigation']
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
