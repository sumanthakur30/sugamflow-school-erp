import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';

@Component({
  selector: 'sf-student-360',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './student-360.component.html',
  styleUrls: ['../../shared/admin-page.scss', './student-360.component.scss'],
})
export class Student360Component implements OnInit {
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthSessionService);
  private readonly route = inject(ActivatedRoute);

  loading = true;
  error = '';
  resetMessage = '';
  portalUsername = '';
  tab = 'profile';
  data: any = null;
  studentId = '';
  remarkBody = '';
  messageBody = '';
  messageChannel = 'DESK';
  savingNote = false;
  apaarConsent = 'PENDING';
  apaarId = '';
  savingApaar = false;
  campuses: Array<{ branchKey: string; name: string }> = [];
  campusesLoaded = false;
  targetBranchId = '';
  transferring = false;
  feeQuote: any = null;
  feeAdjustment: any = null;
  feePeriodAdjustment: any = null;
  feeKind = 'RECURRING';
  feePercent: number | null = null;
  feeFixed: number | null = null;
  feeHeads = 'TUITION';
  feePeriod = '';
  feeFrom = '';
  feeTo = '';
  feeReasonCode = '';
  feeCustomReason = '';
  feeMessage = '';
  feeFormDirty = false;
  private suppressFeeDirty = false;
  readonly discountReasons = [
    'Sibling Discount',
    'Staff Ward Discount',
    'Alumni Ward Discount',
    'Merit / Scholarship Discount',
    'Financial Assistance',
    'Management Concession',
    'Economically Weaker Section (EWS) Concession',
    'Single Parent Concession',
    'Special Case / Management Approval',
    'Other',
  ];

  ngOnInit(): void {
    this.route.paramMap.subscribe((pm) => {
      this.studentId = pm.get('id') || '';
      if (this.studentId) {
        this.load();
        this.loadCampuses();
      }
    });
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>(`/api/student/students/${this.studentId}/360`).subscribe({
      next: (d) => {
        this.data = d;
        const answers = d?.student?.answers ?? {};
        this.apaarConsent = answers.apaarConsent || 'PENDING';
        this.apaarId = answers.apaarId || '';
        this.syncCampusTarget();
        this.portalUsername = this.admissionNo() || this.portalUsername;
        this.loading = false;
        this.loadFeeQuote();
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? err?.message ?? 'Failed to load Student 360';
      },
    });
  }

  setTab(t: string): void {
    this.tab = t;
  }

  loadCampuses(): void {
    this.campusesLoaded = false;
    this.api.get<any>('/api/config/branches/bootstrap').subscribe({
      next: (boot) => {
        this.campuses = (boot?.branches ?? [])
          .map((b: any) => ({
            branchKey: String(b.branchKey || ''),
            name: String(b.name || b.branchKey || ''),
          }))
          .filter((b: { branchKey: string }) => !!b.branchKey);
        this.campusesLoaded = true;
        this.syncCampusTarget();
      },
      error: () => {
        this.campusesLoaded = true;
      },
    });
  }

  private syncCampusTarget(): void {
    const current = String(this.data?.student?.branchId || this.auth.getBranchId() || '');
    if (!this.targetBranchId || this.targetBranchId === current) {
      this.targetBranchId = this.campuses.find((b) => b.branchKey !== current)?.branchKey || '';
    }
  }

  currentCampusName(): string {
    const key = String(this.data?.student?.branchId || '');
    const match = this.campuses.find((b) => b.branchKey === key);
    return match?.name || key || 'This campus';
  }

  otherCampuses(): Array<{ branchKey: string; name: string }> {
    const key = String(this.data?.student?.branchId || '');
    return this.campuses.filter((b) => b.branchKey !== key);
  }

  transferCampus(): void {
    if (!this.studentId || !this.targetBranchId) return;
    this.transferring = true;
    this.error = '';
    this.api
      .post('/api/student/lifecycle/campus-transfer', {
        studentId: this.studentId,
        targetBranchId: this.targetBranchId,
      })
      .subscribe({
        next: () => {
          this.transferring = false;
          this.load();
        },
        error: (err) => {
          this.transferring = false;
          this.error = err?.error?.message ?? 'Could not move the student to that campus';
        },
      });
  }

  saveApaar(): void {
    if (!this.studentId) return;
    this.savingApaar = true;
    this.error = '';
    this.api
      .post(`/api/student/students/${this.studentId}/apaar-consent`, {
        apaarConsent: this.apaarConsent,
        apaarId: this.apaarId,
      })
      .subscribe({
        next: () => {
          this.savingApaar = false;
          this.load();
        },
        error: (err) => {
          this.savingApaar = false;
          this.error = err?.error?.message ?? 'Could not save APAAR status';
        },
      });
  }

  addRemark(): void {
    const body = this.remarkBody.trim();
    if (!body || !this.studentId) return;
    this.savingNote = true;
    this.api.post(`/api/student/students/${this.studentId}/remarks`, { body }).subscribe({
      next: () => {
        this.remarkBody = '';
        this.savingNote = false;
        this.load();
        this.tab = 'remarks';
      },
      error: (err) => {
        this.savingNote = false;
        this.error = err?.error?.message ?? 'Could not save the remark';
      },
    });
  }

  addMessage(): void {
    const body = this.messageBody.trim();
    if (!body || !this.studentId) return;
    this.savingNote = true;
    this.api
      .post(`/api/student/students/${this.studentId}/messages`, {
        body,
        channel: this.messageChannel,
      })
      .subscribe({
        next: () => {
          this.messageBody = '';
          this.savingNote = false;
          this.load();
          this.tab = 'communication';
        },
        error: (err) => {
          this.savingNote = false;
          this.error = err?.error?.message ?? 'Could not log the message';
        },
      });
  }

  houseName(): string {
    return String(this.data?.summary?.house || this.data?.student?.answers?.house || '').trim();
  }

  houseClass(): string {
    const value = this.houseName().toLowerCase();
    return ['red', 'blue', 'green', 'yellow'].includes(value) ? `house-${value}` : 'house-other';
  }

  admissionNo(): string {
    return String(
      this.data?.summary?.admissionNo ||
        this.data?.student?.admissionNo ||
        this.data?.student?.answers?.admissionNo ||
        '',
    ).trim();
  }

  resetPortalPassword(): void {
    const username = this.portalUsername.trim();
    if (!username) {
      this.error = 'Enter the portal username';
      return;
    }
    const session = this.auth.getSession();
    this.api
      .post('/api/student/app-users/reset-password', {
        username,
        roleCode: 'STUDENT',
        displayName: this.data?.summary?.displayName,
        subjectRef: this.admissionNo(),
        installed: true,
      })
      .subscribe({
        next: () => {
          this.http
            .post('/api/v1/auth/password-reset/admin', {
              shopId: session?.shopId || '',
              username,
              sendEmail: false,
              actorUsername: session?.username || '',
            })
            .subscribe({
              next: () => {
                this.resetMessage = `Password reset started for ${username}`;
              },
              error: (err) => {
                this.error = err?.error?.message ?? 'Auth password reset failed';
              },
            });
        },
        error: (err) => {
          this.error = err?.error?.message ?? 'Could not record the app user';
        },
      });
  }

  loadFeeQuote(): void {
    const admissionNo = this.admissionNo();
    if (!admissionNo) return;
    this.refreshFeeQuote();
    this.api.get<any[]>(`/api/fee/finance/adjustments?admissionNo=${encodeURIComponent(admissionNo)}`).subscribe({
      next: (rows) => {
        const open = (rows ?? []).filter((row) => row.status === 'APPROVED' || row.status === 'PENDING');
        this.feeAdjustment = open.find((row) => (row.kind || 'RECURRING') !== 'PERIOD') ?? null;
        this.feePeriodAdjustment = open.find((row) => row.kind === 'PERIOD') ?? null;
        const periodBefore = this.feePeriod;
        if (!this.feeFormDirty) {
          this.applyOpenAdjustment();
        }
        if (this.feePeriod !== periodBefore) {
          this.refreshFeeQuote();
        }
      },
      error: () => {
        this.feeAdjustment = null;
        this.feePeriodAdjustment = null;
      },
    });
  }

  markFeeDirty(): void {
    if (!this.suppressFeeDirty) {
      this.feeFormDirty = true;
      this.feeMessage = '';
    }
  }

  onFeeKindChange(): void {
    this.feeMessage = '';
    const row = this.feeKind === 'PERIOD' ? this.feePeriodAdjustment : this.feeAdjustment;
    if (row) {
      this.feeFormDirty = false;
      this.applyOpenAdjustment();
    }
    this.refreshFeeQuote();
  }

  onFeePeriodChange(): void {
    this.markFeeDirty();
    this.refreshFeeQuote();
  }

  liveDiscountWarning(): string {
    const percent = this.enteredAmount(this.feePercent);
    const fixed = this.enteredAmount(this.feeFixed);
    if (percent != null && percent > 0 && fixed != null && fixed > 0) {
      return 'Fill in only one discount field.';
    }
    if (percent != null && (percent <= 0 || percent > 100)) {
      return 'Percentage must be greater than 0 and no more than 100.';
    }
    if (fixed != null && fixed <= 0) {
      return 'Fixed rupee discount must be greater than 0.';
    }
    const eligible = this.eligibleFee();
    if (fixed != null && fixed > 0 && eligible != null && fixed > eligible) {
      return 'Fixed discount cannot exceed the eligible fee.';
    }
    const pending = this.pendingAmount();
    if (fixed != null && fixed > 0 && pending > 0 && fixed > pending) {
      return 'Fixed discount cannot exceed the outstanding balance.';
    }
    if (this.feeFrom && this.feeTo && this.feeTo < this.feeFrom) {
      return 'Valid Until cannot be earlier than Valid From.';
    }
    return '';
  }

  discountPreview(): {
    eligible: number | null;
    applied: number | null;
    remaining: number | null;
    showMath: boolean;
  } {
    const eligible = this.eligibleFee();
    const warning = this.liveDiscountWarning();
    const percent = this.enteredAmount(this.feePercent);
    const fixed = this.enteredAmount(this.feeFixed);
    const single =
      !warning &&
      ((percent != null && percent > 0 && !(fixed != null && fixed > 0)) ||
        (fixed != null && fixed > 0 && !(percent != null && percent > 0)));
    if (eligible == null || !single) {
      return { eligible, applied: null, remaining: null, showMath: false };
    }
    const applied =
      percent != null && percent > 0 ? this.roundMoney((eligible * percent) / 100) : this.roundMoney(fixed ?? 0);
    return {
      eligible,
      applied,
      remaining: this.roundMoney(eligible - applied),
      showMath: true,
    };
  }

  selectedReasonLabel(): string {
    if (!this.feeReasonCode) return '';
    if (this.feeReasonCode === 'Other') {
      const custom = this.feeCustomReason.trim();
      return custom ? `Other — ${custom}` : 'Other';
    }
    return this.feeReasonCode;
  }

  formatRupee(value: number | null): string {
    if (value == null || Number.isNaN(value)) return '';
    const rounded = this.roundMoney(value);
    const digits = Number.isInteger(rounded) ? 0 : 2;
    return `₹${rounded.toLocaleString('en-IN', { minimumFractionDigits: digits, maximumFractionDigits: 2 })}`;
  }

  discountValueLabel(row: any): string {
    if (!row) return '';
    const amount = Number(row.value);
    if (row.mode === 'PERCENT') return `${row.value}%`;
    if (row.mode === 'FIXED_OFF') return this.formatRupee(amount);
    if (row.mode === 'CUSTOM_PAYABLE') return `custom payable ${this.formatRupee(amount)}`;
    return `${row.mode} ${row.value}`;
  }

  saveFeeAdjustment(): void {
    const admissionNo = this.admissionNo();
    const problem = this.saveDiscountError();
    if (!admissionNo || problem) {
      this.feeMessage = this.liveDiscountWarning() ? '' : problem || 'Enter the discount and a reason.';
      return;
    }
    const percent = this.enteredAmount(this.feePercent);
    const fixed = this.enteredAmount(this.feeFixed);
    const usePercent = percent != null && percent > 0;
    this.feeMessage = '';
    this.api
      .post('/api/fee/finance/adjustments', {
        admissionNo,
        kind: this.feeKind,
        mode: usePercent ? 'PERCENT' : 'FIXED_OFF',
        value: usePercent ? percent : fixed,
        periodKey: this.feeKind === 'PERIOD' ? this.feePeriod : undefined,
        effectiveFrom: this.feeFrom || undefined,
        effectiveTo: this.feeTo || undefined,
        appliesToHeads: this.feeHeadList(),
        reason: this.storedReason(),
      })
      .subscribe({
        next: () => {
          this.feeMessage =
            this.feeKind === 'PERIOD'
              ? 'Month discount saved. It applies only to that fee period. Issued receipts stay unchanged.'
              : 'Recurring discount saved. It applies to each eligible future period. Issued receipts stay unchanged.';
          this.feeFormDirty = false;
          this.loadFeeQuote();
        },
        error: (err) => (this.feeMessage = err?.error?.message ?? 'Could not save the fee adjustment'),
      });
  }

  removeFeeAdjustment(): void {
    const row = this.feeKind === 'PERIOD' ? this.feePeriodAdjustment : this.feeAdjustment;
    if (!row?.id) return;
    const reason = this.storedReason() || 'Removed from student fee profile';
    this.api.post(`/api/fee/finance/adjustments/${row.id}/remove`, { reason }).subscribe({
      next: () => {
        this.feeMessage = 'Adjustment removed. The next demand uses the standard fee.';
        this.feeFormDirty = false;
        this.loadFeeQuote();
      },
      error: (err) => (this.feeMessage = err?.error?.message ?? 'Could not remove the adjustment'),
    });
  }

  admissionQuery(): Record<string, string> {
    const admissionNo = this.admissionNo();
    return admissionNo ? { admissionNo } : {};
  }

  profileEntries(): Array<{ label: string; value: string }> {
    const answers = this.data?.student?.answers ?? {};
    return Object.entries(answers)
      .filter(([, value]) => value != null && value !== '' && typeof value !== 'object')
      .map(([key, value]) => ({
        label: this.prettyLabel(key),
        value: typeof value === 'boolean' ? (value ? 'Yes' : 'No') : String(value),
      }));
  }

  private refreshFeeQuote(): void {
    const admissionNo = this.admissionNo();
    if (!admissionNo) return;
    const preview: Record<string, string> = { studentRef: admissionNo };
    if (this.feeKind === 'PERIOD' && this.feePeriod) {
      preview['periodKey'] = this.feePeriod;
    }
    this.api
      .post<any>('/api/fee/finance/demands/preview', preview)
      .subscribe({ next: (quote) => (this.feeQuote = quote), error: () => (this.feeQuote = null) });
  }

  private applyOpenAdjustment(): void {
    const row = this.feeKind === 'PERIOD' ? this.feePeriodAdjustment : this.feeAdjustment;
    this.suppressFeeDirty = true;
    if (!row) {
      this.feePercent = null;
      this.feeFixed = null;
      this.feeReasonCode = '';
      this.feeCustomReason = '';
      this.suppressFeeDirty = false;
      return;
    }
    const heads = Array.isArray(row.appliesToHeads) ? row.appliesToHeads.filter(Boolean) : [];
    if (heads.length) {
      this.feeHeads = heads.join(', ');
    }
    this.feeFrom = row.effectiveFrom || '';
    this.feeTo = row.effectiveTo || '';
    if (row.periodKey) {
      this.feePeriod = String(row.periodKey).slice(0, 7);
    }
    const parsed = this.parseStoredReason(String(row.reason || ''));
    this.feeReasonCode = parsed.code;
    this.feeCustomReason = parsed.custom;
    if (row.mode === 'PERCENT') {
      this.feePercent = Number(row.value);
      this.feeFixed = null;
    } else if (row.mode === 'FIXED_OFF') {
      this.feeFixed = Number(row.value);
      this.feePercent = null;
    } else {
      this.feePercent = null;
      this.feeFixed = null;
    }
    this.suppressFeeDirty = false;
  }

  private saveDiscountError(): string {
    const live = this.liveDiscountWarning();
    if (live) return live;
    const percent = this.enteredAmount(this.feePercent);
    const fixed = this.enteredAmount(this.feeFixed);
    const hasPercent = percent != null && percent > 0;
    const hasFixed = fixed != null && fixed > 0;
    if (!hasPercent && !hasFixed) {
      return 'Enter a percentage or a fixed rupee discount.';
    }
    if (!this.feeReasonCode) {
      return 'Select the reason for granting this discount.';
    }
    if (this.feeReasonCode === 'Other' && this.feeCustomReason.trim().length < 3) {
      return 'Enter a custom reason.';
    }
    if (this.feeKind === 'PERIOD' && !this.feePeriod) {
      return 'Choose the month this discount applies to.';
    }
    return '';
  }

  private storedReason(): string {
    if (this.feeReasonCode === 'Other') {
      const custom = this.feeCustomReason.trim();
      return custom ? `Other: ${custom}` : '';
    }
    return this.feeReasonCode.trim();
  }

  private parseStoredReason(stored: string): { code: string; custom: string } {
    const text = stored.trim();
    if (!text) return { code: '', custom: '' };
    if (this.discountReasons.includes(text) && text !== 'Other') {
      return { code: text, custom: '' };
    }
    if (text.toLowerCase().startsWith('other:')) {
      return { code: 'Other', custom: text.slice(text.indexOf(':') + 1).trim() };
    }
    return { code: 'Other', custom: text };
  }

  private feeHeadList(): string[] {
    return this.feeHeads
      .split(',')
      .map((head) => head.trim().toUpperCase())
      .filter(Boolean);
  }

  private eligibleFee(): number | null {
    const lines = this.feeQuote?.lines;
    if (!Array.isArray(lines) || !lines.length) return null;
    const heads = this.feeHeadList();
    const matched = lines.filter((line: any) => {
      const key = String(line?.headKey || '').toUpperCase();
      return !!key && (heads.length === 0 || heads.includes(key));
    });
    if (!matched.length) return null;
    const sum = matched.reduce((total: number, line: any) => total + Number(line?.amount || 0), 0);
    if (!(sum > 0)) return null;
    return this.roundMoney(sum);
  }

  private pendingAmount(): number {
    const pending = Number(this.data?.fee?.pendingAmount);
    return Number.isFinite(pending) && pending > 0 ? pending : 0;
  }

  private enteredAmount(value: number | null): number | null {
    if (value == null || value === ('' as unknown)) return null;
    const amount = Number(value);
    return Number.isFinite(amount) ? amount : null;
  }

  private roundMoney(value: number): number {
    return Math.round(value * 100) / 100;
  }

  private prettyLabel(key: string): string {
    const map: Record<string, string> = {
      fullName: 'Full name',
      studentName: 'Student name',
      admissionNo: 'Admission No',
      classSection: 'Class / section',
      rollNo: 'Roll No',
      parentName: 'Parent',
      email: 'Email',
      mobile: 'Mobile',
      gender: 'Gender',
      house: 'House',
      dob: 'Date of birth',
      apaarId: 'APAAR ID',
      apaarConsent: 'APAAR consent',
      apaarConsentAt: 'APAAR recorded',
    };
    if (map[key]) return map[key];
    return key
      .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
      .replace(/[_-]+/g, ' ')
      .replace(/^./, (c) => c.toUpperCase());
  }
}
