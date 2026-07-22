import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthSessionService } from '../../core/auth-session.service';
import { ProvisionService } from '../../core/provision.service';
import { ThemeService } from '../../core/theme.service';
import { switchMap } from 'rxjs';

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
  private readonly theme = inject(ThemeService);
  private readonly provision = inject(ProvisionService);

  organizationId = localStorage.getItem('sf.tenantId') ?? '';
  username = 'admin';
  password = 'password';
  /** Where to land after login: admin | parent | teacher */
  destination: 'admin' | 'parent' | 'teacher' = 'admin';
  error = '';
  submitting = false;

  schoolName = 'SugamFlow School';
  tagline = 'Configuration over customization';
  loginLogo = '';
  footer = 'Powered by SugamFlow';
  showAnnouncement = true;
  showAdmissionBanner = true;

  private themeLoadTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
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
          const navigate = () => {
            if (this.destination === 'parent') {
              this.router.navigateByUrl('/parent');
            } else if (this.destination === 'teacher') {
              this.router.navigateByUrl('/teacher');
            } else {
              this.router.navigateByUrl('/admin/dashboard');
            }
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
      this.loginLogo = b['loginLogo'] || b['schoolLogo'] || '';
      this.footer = b['footer'] || 'Powered by SugamFlow';
      this.showAnnouncement = login['announcementArea'] !== false;
      this.showAdmissionBanner = login['admissionBanner'] !== false;
    });
  }
}
