import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthSessionService } from '../../core/auth-session.service';
import { EntitlementsService } from '../../core/entitlements.service';
import { roleHome } from '../../core/role-home';

@Component({
  selector: 'sf-modules-unavailable',
  standalone: true,
  templateUrl: './modules-unavailable.component.html',
  styleUrl: './modules-unavailable.component.scss',
})
export class ModulesUnavailableComponent {
  private readonly entitlements = inject(EntitlementsService);
  private readonly auth = inject(AuthSessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  retrying = false;
  stillDown = false;

  get planBlocked(): boolean {
    return this.route.snapshot.queryParamMap.get('reason') === 'plan';
  }

  retry(): void {
    if (this.retrying) {
      return;
    }
    this.retrying = true;
    this.stillDown = false;
    this.entitlements.load().subscribe((loaded) => {
      this.retrying = false;
      if (loaded.featureFlags) {
        void this.router.navigateByUrl(roleHome(this.auth.getRole()));
        return;
      }
      this.stillDown = true;
    });
  }

  signOut(): void {
    this.auth.logout();
    this.entitlements.clear();
    void this.router.navigateByUrl('/login');
  }
}
