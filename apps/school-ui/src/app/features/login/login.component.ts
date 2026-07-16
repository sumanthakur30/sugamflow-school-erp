import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthSessionService } from '../../core/auth-session.service';
import { ThemeService } from '../../core/theme.service';

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

  organizationId = localStorage.getItem('sf.tenantId') ?? 'demo-school';
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

  ngOnInit(): void {
    this.refreshTheme();
  }

  onOrgChange(): void {
    this.refreshTheme();
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
          this.theme.loadAuthenticated().subscribe();
          if (this.destination === 'parent') {
            this.auth.setActiveRole('PARENT');
            this.router.navigateByUrl('/parent');
          } else if (this.destination === 'teacher') {
            this.auth.setActiveRole('TEACHER');
            this.router.navigateByUrl('/teacher');
          } else {
            this.router.navigateByUrl('/admin/admission');
          }
        },
        error: (err) => {
          this.submitting = false;
          const msg = err?.error?.message ?? err?.message ?? 'Sign-in failed';
          this.error = String(msg);
        },
      });
  }

  private refreshTheme(): void {
    this.theme.loadPublished(this.organizationId).subscribe((t) => {
      const b = t.branding ?? {};
      const login = t.loginScreen ?? {};
      this.schoolName = b['schoolName'] || this.schoolName;
      this.tagline = b['productTagline'] || this.tagline;
      this.loginLogo = b['loginLogo'] || b['schoolLogo'] || '';
      this.footer = b['footer'] || this.footer;
      this.showAnnouncement = login['announcementArea'] !== false;
      this.showAdmissionBanner = login['admissionBanner'] !== false;
    });
  }
}
