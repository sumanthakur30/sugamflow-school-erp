import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { CommonModule } from '@angular/common';
import { DesignTheme, ThemeService } from '../../core/theme.service';
import { environment } from '../../environments/environment';

type BrandingAssetKey =
  | 'schoolLogo'
  | 'loginLogo'
  | 'favicon'
  | 'mobileSplash'
  | 'watermark'
  | 'backgroundImage';

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
  uploadingKey: BrandingAssetKey | null = null;
  uploadError = '';

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

  readonly imageSlots: Array<{
    key: BrandingAssetKey;
    type: string;
    label: string;
    hint: string;
    maxKb: number;
    scope: 'branding' | 'loginScreen';
  }> = [
    {
      key: 'schoolLogo',
      type: 'SCHOOL_LOGO',
      label: 'School Logo',
      hint: 'Header / shell — PNG, JPG, WEBP, SVG (max 512 KB)',
      maxKb: 512,
      scope: 'branding',
    },
    {
      key: 'loginLogo',
      type: 'LOGIN_LOGO',
      label: 'Login Logo',
      hint: 'Login screen — PNG, JPG, WEBP, SVG (max 512 KB)',
      maxKb: 512,
      scope: 'branding',
    },
    {
      key: 'favicon',
      type: 'FAVICON',
      label: 'Favicon',
      hint: 'Browser tab icon — ICO, PNG (max 512 KB)',
      maxKb: 512,
      scope: 'branding',
    },
    {
      key: 'mobileSplash',
      type: 'MOBILE_SPLASH',
      label: 'Mobile Splash',
      hint: 'Mobile splash image (max 1.5 MB)',
      maxKb: 1536,
      scope: 'branding',
    },
    {
      key: 'watermark',
      type: 'WATERMARK',
      label: 'Watermark',
      hint: 'Report / print watermark (max 512 KB)',
      maxKb: 512,
      scope: 'branding',
    },
    {
      key: 'backgroundImage',
      type: 'LOGIN_BG',
      label: 'Login Background',
      hint: 'Login page background image (max 1.5 MB)',
      maxKb: 1536,
      scope: 'loginScreen',
    },
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

  assetUrl(key: BrandingAssetKey): string {
    if (!this.theme) return '';
    if (key === 'backgroundImage') {
      return String(this.theme.loginScreen?.['backgroundImage'] || '');
    }
    return String(this.theme.branding?.[key] || '');
  }

  previewSrc(key: BrandingAssetKey): string {
    const raw = this.assetUrl(key).trim();
    if (!raw) return '';
    if (raw.startsWith('http://') || raw.startsWith('https://') || raw.startsWith('data:')) {
      return raw;
    }
    if (raw.startsWith('/')) {
      return `${environment.apiBaseUrl || ''}${raw}`;
    }
    return raw;
  }

  setAssetUrl(key: BrandingAssetKey, value: string): void {
    if (!this.theme) return;
    if (key === 'backgroundImage') {
      this.theme.loginScreen ??= {};
      this.theme.loginScreen['backgroundImage'] = value;
    } else {
      this.theme.branding ??= {};
      this.theme.branding[key] = value;
    }
    this.preview();
  }

  clearAsset(key: BrandingAssetKey): void {
    if (!this.theme) return;
    if (key === 'backgroundImage') {
      this.theme.loginScreen ??= {};
      this.theme.loginScreen['backgroundImage'] = '';
    } else {
      this.theme.branding ??= {};
      this.theme.branding[key] = '';
    }
    this.preview();
  }

  onFileSelected(ev: Event, slot: (typeof this.imageSlots)[number]): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || !this.theme) return;

    this.uploadError = '';
    if (file.size > slot.maxKb * 1024) {
      this.uploadError = `${slot.label} exceeds ${slot.maxKb} KB limit`;
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = String(reader.result || '');
      const contentBase64 = dataUrl.includes(',') ? dataUrl.split(',')[1] : dataUrl;
      this.uploadingKey = slot.key;
      this.api
        .post<{
          url: string;
          brandingField?: string;
          assetType?: string;
        }>('/api/config/design-studio/assets', {
          type: slot.type,
          fileName: file.name,
          contentType: file.type || 'application/octet-stream',
          contentBase64,
        })
        .subscribe({
          next: (row) => {
            this.uploadingKey = null;
            const url = row?.url || '';
            if (!url) {
              this.uploadError = 'Upload succeeded but no URL was returned';
              return;
            }
            if (slot.scope === 'loginScreen') {
              this.theme!.loginScreen ??= {};
              this.theme!.loginScreen['backgroundImage'] = url;
            } else {
              this.theme!.branding ??= {};
              this.theme!.branding[slot.key] = url;
            }
            this.status = `${slot.label} uploaded — Save Draft, then Publish to apply on login.`;
            this.preview();
          },
          error: (err) => {
            this.uploadingKey = null;
            this.uploadError =
              err?.error?.message || err?.message || `${slot.label} upload failed`;
          },
        });
    };
    reader.onerror = () => {
      this.uploadError = 'Could not read the selected file';
    };
    reader.readAsDataURL(file);
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
