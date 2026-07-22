import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthSessionService } from './core/auth-session.service';
import { ThemeService } from './core/theme.service';

@Component({
  selector: 'sf-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `<router-outlet />`,
})
export class AppComponent implements OnInit {
  private readonly auth = inject(AuthSessionService);
  private readonly theme = inject(ThemeService);

  ngOnInit(): void {
    if (this.auth.isLoggedIn()) {
      this.theme.loadAuthenticated().subscribe();
    } else {
      const org = (localStorage.getItem('sf.tenantId') ?? '').trim();
      if (org.length >= 3) {
        this.theme.loadPublished(org).subscribe();
      } else {
        this.theme.clearToFallback();
      }
    }
  }
}
