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
    this.api.get<DesignTheme>('/api/config/design-studio/theme').subscribe((t) => {
      this.ensureMaps(t);
      this.theme = t;
      this.preview();
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
    this.api.put('/api/config/design-studio/theme', this.theme).subscribe((t) => {
      this.ensureMaps(t as DesignTheme);
      this.theme = t as DesignTheme;
      this.status = 'Saved — live theme applied across the app.';
      this.preview();
    });
  }

  publish(): void {
    this.api.put<DesignTheme>('/api/config/design-studio/theme/publish', {}).subscribe((t) => {
      this.ensureMaps(t);
      this.theme = t;
      this.status = `Published version ${t.version} — login screen will use this branding.`;
      this.preview();
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
