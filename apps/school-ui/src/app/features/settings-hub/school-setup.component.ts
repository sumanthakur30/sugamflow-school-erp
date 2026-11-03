import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
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

  busy = false;
  error = '';
  admissionStatus = '';
  feeStatus = '';
  private admissionDoc: Record<string, unknown> | null = null;

  prefix = 'ADM';
  separator = '-';
  yearDigits = 4;
  includeBranch = true;
  width = 4;

  className = '';
  tuition = '';
  frequency = 'Y';

  ngOnInit(): void {
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

  saveFeeStructure(): void {
    const className = this.className.trim();
    const amount = Number(this.tuition);
    if (!className) {
      this.error = 'Enter the class this fee applies to.';
      return;
    }
    if (!Number.isFinite(amount) || amount <= 0) {
      this.error = 'Tuition needs an amount greater than zero.';
      return;
    }
    const key = `FEE_${className.toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_|_$/g, '')}`;
    this.busy = true;
    this.error = '';
    this.api
      .put(`/api/fee/finance/structures/${encodeURIComponent(key)}`, {
        definitionKey: key,
        name: `${className} fees`,
        currency: 'INR',
        eligibility: { classSection: className },
        lines: [
          {
            headKey: 'TUITION',
            amount,
            frequency: this.frequency,
            active: true,
            discountAllowed: true,
            optional: false,
          },
        ],
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.feeStatus = `Saved ${className} tuition of ${amount} on the fee structure ${key}.`;
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Fee structure was not saved.';
        },
      });
  }
}
