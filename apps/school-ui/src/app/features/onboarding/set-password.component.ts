import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthSessionService } from '../../core/auth-session.service';

@Component({
  selector: 'sf-set-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './set-password.component.html',
  styleUrl: './set-password.component.scss',
})
export class SetPasswordComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthSessionService);

  token = '';
  inviteValid: boolean | null = null;
  shopId = '';
  username = '';
  password = '';
  confirmPassword = '';
  error = '';
  submitting = false;

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((params) => {
      this.token = params.get('token') ?? '';
      if (!this.token) {
        this.inviteValid = false;
        return;
      }
      this.auth.validateInvitation(this.token).subscribe({
        next: (res) => {
          this.inviteValid = !!res.valid;
          this.shopId = res.shopId ?? '';
          this.username = res.username ?? '';
        },
        error: () => {
          this.inviteValid = false;
        },
      });
    });
  }

  onSubmit(): void {
    this.error = '';
    if (!this.token || this.inviteValid !== true) {
      return;
    }
    if (!this.password || this.password.length < 8) {
      this.error = 'Password must be at least 8 characters.';
      return;
    }
    if (this.password !== this.confirmPassword) {
      this.error = 'Passwords do not match.';
      return;
    }
    if (this.submitting) {
      return;
    }
    this.submitting = true;
    this.auth.acceptInvitation(this.token, this.password).subscribe({
      next: () => {
        this.submitting = false;
        // Land on login — avoids dashboard role redirect loops for SHOP_EMPLOYEE.
        void this.router.navigate(['/login'], {
          queryParams: {
            activated: '1',
            org: this.shopId || undefined,
            user: this.username || undefined,
          },
        });
      },
      error: () => {
        this.submitting = false;
        this.error = 'Could not set password. The link may be expired or already used.';
      },
    });
  }
}
