import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  BoardDefinition,
  ComplianceApiService,
  ComplianceProfile,
  ComplianceTemplate,
} from './compliance-api.service';

@Component({
  selector: 'sf-compliance-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  styleUrls: ['../../shared/admin-page.scss'],
  template: `
    <section class="page">
      <div class="page-head">
        <h2>School compliance profile</h2>
        <p>Board pack, affiliation, UDISE+, principal and trust details for submissions.</p>
      </div>

      <div class="panel">
        <form class="grid" [formGroup]="form" (ngSubmit)="save()">
          <div class="grid two">
            <label>
              <span class="field-title">Board</span>
              <select formControlName="boardCode" (change)="onBoardChange()">
                @for (b of boards; track b.code) {
                  <option [value]="b.code">{{ b.name }} ({{ b.code }})</option>
                }
                @if (!boards.length) {
                  <option value="CBSE">CBSE</option>
                  <option value="ICSE">ICSE</option>
                  <option value="STATE">State</option>
                }
              </select>
            </label>
            <label>
              <span class="field-title">Active pack</span>
              <select formControlName="activePackKey">
                @for (p of packsForBoard; track p.packKey) {
                  <option [value]="p.packKey">{{ p.packKey }} — {{ p.title }}</option>
                }
              </select>
            </label>
          </div>
          <div class="grid two">
            <label>
              <span class="field-title">School name</span>
              <input formControlName="schoolName" maxlength="255" />
            </label>
            <label>
              <span class="field-title">Affiliation number</span>
              <input formControlName="affiliationNumber" maxlength="80" />
            </label>
          </div>
          <div class="grid two">
            <label>
              <span class="field-title">School code</span>
              <input formControlName="schoolCode" maxlength="80" />
            </label>
            <label>
              <span class="field-title">UDISE+</span>
              <input formControlName="udisePlus" maxlength="80" />
            </label>
          </div>
          <div class="grid two">
            <label>
              <span class="field-title">DISE code</span>
              <input formControlName="diseCode" maxlength="80" />
            </label>
            <span></span>
          </div>
          <label>
            <span class="field-title">Address</span>
            <textarea formControlName="addressLine" rows="2"></textarea>
          </label>
          <div class="grid two">
            <label>
              <span class="field-title">City</span>
              <input formControlName="city" />
            </label>
            <label>
              <span class="field-title">State / PIN</span>
              <div class="grid two">
                <input formControlName="stateCode" placeholder="State" />
                <input formControlName="pincode" placeholder="PIN" />
              </div>
            </label>
          </div>
          <div class="grid two">
            <label>
              <span class="field-title">Principal name</span>
              <input formControlName="principalName" />
            </label>
            <label>
              <span class="field-title">Principal mobile</span>
              <input formControlName="principalMobile" />
            </label>
          </div>
          <div class="grid two">
            <label>
              <span class="field-title">Principal email</span>
              <input formControlName="principalEmail" type="email" />
            </label>
            <label>
              <span class="field-title">School email</span>
              <input formControlName="schoolEmail" type="email" />
            </label>
          </div>
          <div class="grid two">
            <label>
              <span class="field-title">Trust / society</span>
              <input formControlName="trustSocietyName" />
            </label>
            <label>
              <span class="field-title">School phone</span>
              <input formControlName="schoolPhone" />
            </label>
          </div>
          <label>
            <span class="field-title">Recognition details</span>
            <textarea formControlName="recognitionDetails" rows="2"></textarea>
          </label>
          <div class="row" style="gap: 0.75rem; align-items: center">
            <button class="btn" type="submit" [disabled]="form.invalid || saving">
              {{ saving ? 'Saving…' : 'Save profile' }}
            </button>
            <a routerLink="/admin/compliance">Back to dashboard</a>
            @if (message) {
              <span class="muted">{{ message }}</span>
            }
          </div>
        </form>
      </div>
    </section>
  `,
})
export class ComplianceProfileComponent implements OnInit {
  private readonly api = inject(ComplianceApiService);
  private readonly fb = inject(FormBuilder);

  saving = false;
  message = '';
  boards: BoardDefinition[] = [];
  packs: ComplianceTemplate[] = [];

  form = this.fb.nonNullable.group({
    boardCode: ['CBSE', Validators.required],
    activePackKey: ['CBSE-2026.1', Validators.required],
    schoolName: [''],
    affiliationNumber: [''],
    schoolCode: [''],
    udisePlus: [''],
    diseCode: [''],
    addressLine: [''],
    city: [''],
    stateCode: [''],
    pincode: [''],
    principalName: [''],
    principalMobile: [''],
    principalEmail: [''],
    schoolPhone: [''],
    schoolEmail: [''],
    bankAccountName: [''],
    bankAccountNumber: [''],
    bankIfsc: [''],
    trustSocietyName: [''],
    recognitionDetails: [''],
    infrastructureNotes: [''],
  });

  get packsForBoard(): ComplianceTemplate[] {
    const board = this.form.controls.boardCode.value;
    return this.packs.filter((p) => p.boardCode === board);
  }

  ngOnInit(): void {
    this.api.listBoards().subscribe({
      next: (rows) => (this.boards = rows || []),
      error: () => (this.boards = []),
    });
    this.api.listPublishedPacks().subscribe({
      next: (rows) => {
        this.packs = rows || [];
        this.ensurePackForBoard();
      },
      error: () => (this.packs = []),
    });
    this.api.getProfile().subscribe({
      next: (p) => this.patch(p),
      error: (err) => {
        this.message = err?.error?.message || 'Failed to load profile';
      },
    });
  }

  onBoardChange(): void {
    this.ensurePackForBoard();
  }

  save(): void {
    if (this.form.invalid) return;
    this.saving = true;
    this.message = '';
    this.api.saveProfile(this.form.getRawValue() as ComplianceProfile).subscribe({
      next: (p) => {
        this.saving = false;
        this.patch(p);
        this.message = `Saved · ${p.boardCode} / ${p.activePackKey || '—'} · completeness ${p.profileCompletenessPercent ?? 0}%`;
      },
      error: (err) => {
        this.saving = false;
        this.message = err?.error?.message || 'Save failed';
      },
    });
  }

  private ensurePackForBoard(): void {
    const board = this.form.controls.boardCode.value;
    const current = this.form.controls.activePackKey.value;
    const forBoard = this.packs.filter((p) => p.boardCode === board);
    if (!forBoard.length) {
      this.form.controls.activePackKey.setValue(`${board}-2026.1`);
      return;
    }
    if (!forBoard.some((p) => p.packKey === current)) {
      this.form.controls.activePackKey.setValue(forBoard[0].packKey);
    }
  }

  private patch(p: ComplianceProfile): void {
    this.form.patchValue({
      boardCode: p.boardCode || 'CBSE',
      activePackKey: p.activePackKey || `${p.boardCode || 'CBSE'}-2026.1`,
      schoolName: p.schoolName || '',
      affiliationNumber: p.affiliationNumber || '',
      schoolCode: p.schoolCode || '',
      udisePlus: p.udisePlus || '',
      diseCode: p.diseCode || '',
      addressLine: p.addressLine || '',
      city: p.city || '',
      stateCode: p.stateCode || '',
      pincode: p.pincode || '',
      principalName: p.principalName || '',
      principalMobile: p.principalMobile || '',
      principalEmail: p.principalEmail || '',
      schoolPhone: p.schoolPhone || '',
      schoolEmail: p.schoolEmail || '',
      bankAccountName: p.bankAccountName || '',
      bankAccountNumber: p.bankAccountNumber || '',
      bankIfsc: p.bankIfsc || '',
      trustSocietyName: p.trustSocietyName || '',
      recognitionDetails: p.recognitionDetails || '',
      infrastructureNotes: p.infrastructureNotes || '',
    });
    this.ensurePackForBoard();
  }
}
