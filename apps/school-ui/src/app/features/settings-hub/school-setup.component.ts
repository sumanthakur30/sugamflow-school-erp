import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';
import { previewAdmissionNumber } from './admission-number.util';

@Component({
  selector: 'sf-school-setup',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './school-setup.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class SchoolSetupComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);

  busy = false;
  error = '';
  admissionStatus = '';
  nameStatus = '';
  schoolName = '';
  sessionId = '';
  private themeDoc: Record<string, unknown> | null = null;
  private admissionDoc: Record<string, unknown> | null = null;

  prefix = 'ADM';
  separator = '-';
  yearDigits = 4;
  includeBranch = true;
  width = 4;

  ngOnInit(): void {
    this.sessionId = this.auth.getSessionId();
    this.api.get<any>('/api/config/design-studio/theme').subscribe({
      next: (theme) => {
        this.themeDoc = theme;
        this.schoolName = String(theme?.branding?.schoolName || '');
      },
      error: () => {
        this.schoolName = '';
      },
    });
    this.api.get<any>('/api/config/modules/admission').subscribe({
      next: (doc) => {
        this.admissionDoc = doc;
        const settings = (doc?.settings ?? {}) as Record<string, unknown>;
        this.prefix = String(settings['admissionNoPrefix'] ?? 'ADM');
        this.separator = String(settings['admissionNoSeparator'] ?? '-');
        this.yearDigits = Number(settings['admissionNoYearDigits'] ?? 4);
        this.includeBranch = settings['admissionNoIncludeBranch'] !== false;
        this.width = Number(settings['admissionNoWidth'] ?? 4);
      },
      error: () => {
        this.error = 'Admission settings could not be loaded.';
      },
    });
  }

  saveSchoolName(): void {
    const name = this.schoolName.trim();
    if (!name) {
      this.error = 'Enter the school name.';
      return;
    }
    const theme = {
      ...(this.themeDoc || {}),
      branding: {
        ...((this.themeDoc?.['branding'] as Record<string, unknown>) || {}),
        schoolName: name,
      },
    };
    this.busy = true;
    this.error = '';
    this.api.put('/api/config/design-studio/theme', theme).subscribe({
      next: (saved) => {
        this.busy = false;
        this.themeDoc = saved as Record<string, unknown>;
        this.nameStatus = 'School name saved.';
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'School name was not saved.';
      },
    });
  }

  preview(): string {
    return previewAdmissionNumber(
      {
        admissionNoPrefix: this.prefix,
        admissionNoSeparator: this.separator,
        admissionNoYearDigits: Number(this.yearDigits),
        admissionNoIncludeBranch: this.includeBranch,
        admissionNoWidth: Number(this.width),
      },
      'MAIN',
      1,
    );
  }

  saveAdmissionFormat(): void {
    if (!this.admissionDoc) {
      this.error = 'Admission settings are not loaded yet.';
      return;
    }
    const settings = {
      ...((this.admissionDoc['settings'] as Record<string, unknown>) ?? {}),
      admissionNoPrefix: this.prefix.trim() || 'ADM',
      admissionNoSeparator: this.separator.trim() || '-',
      admissionNoYearDigits: Number(this.yearDigits),
      admissionNoIncludeBranch: this.includeBranch,
      admissionNoWidth: Number(this.width),
      generateAdmissionNo: true,
    };
    this.busy = true;
    this.error = '';
    this.api
      .put('/api/config/modules/admission', {
        ...this.admissionDoc,
        moduleKey: 'admission',
        settings,
      })
      .subscribe({
        next: (saved) => {
          this.busy = false;
          this.admissionDoc = saved as Record<string, unknown>;
          this.admissionStatus = `Saved. The next generated number looks like ${this.preview()}. Numbers already issued stay as they are.`;
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Admission number format was not saved.';
        },
      });
  }

}
