import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EntitlementsService } from '../../core/entitlements.service';
import { SubscriptionApiService } from './subscription-api.service';

type TenantTab = 'overview' | 'usage';

/**
 * School tenant view only. Plan/catalog configure and assign live in
 * SugamFlow Super Admin → Platform Subscription — never duplicate that here.
 */
@Component({
  selector: 'sf-subscription',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './subscription.component.html',
  styleUrls: ['../../shared/admin-page.scss', './subscription.component.scss'],
})
export class SubscriptionComponent implements OnInit {
  private readonly api = inject(SubscriptionApiService);
  private readonly entitlementsSvc = inject(EntitlementsService);

  tab: TenantTab = 'overview';
  error = '';
  loading = false;

  entitlements: any = null;
  license: any = null;
  usageLimits: any = null;
  usageEvents: any[] = [];

  ngOnInit(): void {
    this.reloadOverview();
  }

  setTab(tab: TenantTab): void {
    this.tab = tab;
    this.error = '';
    if (tab === 'overview') {
      this.reloadOverview();
    } else {
      this.reloadUsage();
    }
  }

  planId(): string {
    return (
      this.entitlements?.planId ||
      this.license?.planId ||
      this.entitlementsSvc.current()?.planId ||
      '—'
    );
  }

  entitlementSource(): string {
    return this.entitlements?.entitlementSource || '—';
  }

  modules(): string[] {
    const m = this.entitlements?.modules;
    return Array.isArray(m) ? m : [];
  }

  flagEntries(): [string, boolean][] {
    return Object.entries(this.entitlements?.featureFlags ?? {}) as [string, boolean][];
  }

  limitEntries(): [string, number][] {
    return Object.entries(this.entitlements?.limits ?? {}) as [string, number][];
  }

  enabledFeatureCount(): number {
    return this.flagEntries().filter(([, on]) => !!on).length;
  }

  private reloadOverview(): void {
    this.loading = true;
    this.error = '';
    this.api.entitlements().subscribe({
      next: (e) => {
        this.entitlements = e;
        this.loading = false;
      },
      error: (err) => {
        this.entitlements = null;
        this.loading = false;
        this.fail(err);
      },
    });
    this.api.getLicense().subscribe({
      next: (lic) => (this.license = lic),
      error: () => (this.license = null),
    });
    this.entitlementsSvc.load().subscribe();
  }

  private reloadUsage(): void {
    this.api.listUsageLimits().subscribe({
      next: (u) => (this.usageLimits = u),
      error: () => (this.usageLimits = null),
    });
    this.api.usageEvents().subscribe({
      next: (ev) => (this.usageEvents = ev ?? []),
      error: () => (this.usageEvents = []),
    });
  }

  private fail(err: any): void {
    this.error =
      err?.error?.message || err?.message || (typeof err === 'string' ? err : 'Request failed');
  }
}
