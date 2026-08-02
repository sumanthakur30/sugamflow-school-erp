import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthSessionService } from '../../core/auth-session.service';
import { ProvisionService } from '../../core/provision.service';
import { ThemeService } from '../../core/theme.service';
import { switchMap } from 'rxjs';

export type LoginDestination =
  | 'admin'
  | 'principal'
  | 'teacher'
  | 'accountant'
  | 'reception'
  | 'librarian'
  | 'parent';

interface DestinationOption {
  value: LoginDestination;
  label: string;
  /** Route after successful login. */
  path: string;
  /** Optional role overlay for dashboards / guards (does not change JWT). */
  activeRole?: string;
}

@Component({
  selector: 'sf-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent implements OnInit {
  private readonly auth = inject(AuthSessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly theme = inject(ThemeService);
  private readonly provision = inject(ProvisionService);

  organizationId = localStorage.getItem('sf.tenantId') ?? '';
  username = 'admin';
  password = 'password';
  destination: LoginDestination = 'admin';
  readonly destinations: DestinationOption[] = [
    { value: 'admin', label: 'School Admin', path: '/admin/dashboard' },
    { value: 'principal', label: 'Principal', path: '/admin/dashboard', activeRole: 'PRINCIPAL' },
    { value: 'teacher', label: 'Teacher App', path: '/teacher', activeRole: 'TEACHER' },
    {
      value: 'accountant',
      label: 'Accountant / Finance',
      path: '/admin/dashboard',
      activeRole: 'ACCOUNTANT',
    },
    { value: 'reception', label: 'Reception', path: '/admin/dashboard', activeRole: 'RECEPTION' },
    { value: 'librarian', label: 'Librarian', path: '/admin/library', activeRole: 'LIBRARIAN' },
    { value: 'parent', label: 'Parent App', path: '/parent', activeRole: 'PARENT' },
  ];
  error = '';
  success = '';
  submitting = false;

  schoolName = 'SugamFlow School';
  tagline = 'Configuration over customization';
  loginLogo = '';
  footer = 'Powered by SugamFlow';
  showAnnouncement = true;
  showAdmissionBanner = true;

  private themeLoadTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    const qp = this.route.snapshot.queryParamMap;
    if (qp.get('activated') === '1') {
      this.success = 'Password set. Sign in with your organization id and username.';
      this.password = '';
    }
    const org = (qp.get('org') || '').trim();
    if (org) {
      this.organizationId = org;
    }
    const user = (qp.get('user') || '').trim();
    if (user) {
      // Show base username when already scoped as user_org
      const suffix = `_${this.organizationId}`;
      this.username =
        this.organizationId && user.endsWith(suffix) ? user.slice(0, -suffix.length) : user;
    }
    this.destination = this.guessDestination(this.username);
    this.refreshTheme();
  }

  onOrgChange(): void {
    if (this.themeLoadTimer) {
      clearTimeout(this.themeLoadTimer);
    }
    this.themeLoadTimer = setTimeout(() => this.refreshTheme(), 400);
  }

  onSubmit(): void {
    this.error = '';
    this.submitting = true;
    const shopId = this.organizationId.trim();
    const scopedUsername = this.auth.toScopedUsername(this.username, shopId);
    const dest =
      this.destinations.find((d) => d.value === this.destination) ?? this.destinations[0];

    this.auth
      .login({ shopId, username: scopedUsername, password: this.password })
      .subscribe({
        next: (response) => {
          this.submitting = false;
          if (response.mfaRequired) {
            this.error =
              'MFA is enabled for this account; complete login in SugamFlow shop UI for now.';
            return;
          }
          if (dest.activeRole) {
            this.auth.setActiveRole(dest.activeRole);
          }
          const navigate = () => {
            void this.router.navigateByUrl(dest.path);
          };
          // Bootstrap campus + branding from shop name, then refresh theme.
          this.provision
            .ensureProvisioned()
            .pipe(switchMap(() => this.theme.loadAuthenticated()))
            .subscribe({ next: () => navigate(), error: () => navigate() });
        },
        error: (err) => {
          this.submitting = false;
          const msg = err?.error?.message ?? err?.message ?? 'Sign-in failed';
          this.error = String(msg);
        },
      });
  }

  private guessDestination(username: string): LoginDestination {
    const u = (username || '').trim().toLowerCase();
    if (!u) return 'admin';
    if (/(parent|guardian|mother|father)/.test(u)) return 'parent';
    if (/(teacher|class.?teacher|faculty)/.test(u)) return 'teacher';
    if (/(account|finance|cashier|accounts)/.test(u)) return 'accountant';
    if (/(reception|frontoffice|front.?office)/.test(u)) return 'reception';
    if (/(librar)/.test(u)) return 'librarian';
    if (/(principal|headmaster|headmistress)/.test(u)) return 'principal';
    if (/(owner|admin)/.test(u)) return 'admin';
    return 'admin';
  }

  private refreshTheme(): void {
    const org = this.organizationId.trim();
    // Avoid calling published theme for partial IDs while typing (e.g. N, NA, NAT-0).
    if (org.length < 3) {
      this.theme.clearToFallback();
      this.schoolName = 'SugamFlow School';
      this.tagline = 'Configuration over customization';
      this.loginLogo = '';
      this.footer = 'Powered by SugamFlow';
      return;
    }
    this.theme.loadPublished(org).subscribe((t) => {
      const b = t.branding ?? {};
      const login = t.loginScreen ?? {};
      this.schoolName = b['schoolName'] || 'SugamFlow School';
      this.tagline = b['productTagline'] || 'Configuration over customization';
      this.loginLogo = this.theme.resolveAssetUrl(b['loginLogo'] || b['schoolLogo'] || '');
      this.footer = b['footer'] || 'Powered by SugamFlow';
      this.showAnnouncement = login['announcementArea'] !== false;
      this.showAdmissionBanner = login['admissionBanner'] !== false;
    });
  }
}
