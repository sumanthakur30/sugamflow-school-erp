import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { CommonModule } from '@angular/common';
import { DesignTheme, ThemeService } from '../../core/theme.service';

@Component({
  selector: 'sf-design-studio',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './design-studio.component.html',
  styleUrls: ['../../shared/admin-page.scss', './design-studio.component.scss'],
})
export class DesignStudioComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly themeService = inject(ThemeService);

  theme: DesignTheme | null = null;
  status = '';
  loading = true;
  serviceUnavailable = false;
  readonly colorKeys = [
    'primary',
    'secondary',
    'accent',
    'menu',
    'button',
    'text',
    'warning',
    'success',
    'error',
    'surface',
    'panel',
  ];

  ngOnInit(): void {
    this.loadTheme();
  }

  loadTheme(): void {
    this.loading = true;
    this.serviceUnavailable = false;
    this.api.get<DesignTheme>('/api/config/design-studio/theme').subscribe({
      next: (t) => {
        this.ensureMaps(t);
        this.theme = t;
        this.loading = false;
        this.preview();
      },
      error: () => {
        const fallback = this.themeService.theme() ?? this.themeService.apply(null);
        this.ensureMaps(fallback);
        this.theme = fallback;
        this.loading = false;
        this.serviceUnavailable = true;
        this.status =
          'Color settings service is temporarily unavailable. You can preview colors, then retry before saving.';
      },
    });
  }

  preview(): void {
    if (this.theme) {
      this.themeService.apply(this.theme);
    }
  }

  save(): void {
    if (!this.theme) {
      return;
    }
    this.api.put('/api/config/design-studio/theme', this.theme).subscribe({
      next: (t) => {
        this.ensureMaps(t as DesignTheme);
        this.theme = t as DesignTheme;
        this.serviceUnavailable = false;
        this.status = 'Saved — live theme applied across the app.';
        this.preview();
      },
      error: () => {
        this.serviceUnavailable = true;
        this.status = 'Could not save colors. Retry after the settings service is available.';
      },
    });
  }

  publish(): void {
    this.api.put<DesignTheme>('/api/config/design-studio/theme/publish', {}).subscribe({
      next: (t) => {
        this.ensureMaps(t);
        this.theme = t;
        this.serviceUnavailable = false;
        this.status = `Published version ${t.version} — login screen will use this branding.`;
        this.preview();
      },
      error: () => {
        this.serviceUnavailable = true;
        this.status = 'Could not publish the theme. Retry after the settings service is available.';
      },
    });
  }

  private ensureMaps(t: DesignTheme): void {
    t.branding ??= {};
    t.colors ??= {};
    t.typography ??= {};
    t.loginScreen ??= {};
    t.dashboard ??= {};
    for (const key of this.colorKeys) {
      if (!t.colors[key]) {
        t.colors[key] = this.themeService.theme()?.colors?.[key] ?? '#0B6E4F';
      }
    }
  }
}
