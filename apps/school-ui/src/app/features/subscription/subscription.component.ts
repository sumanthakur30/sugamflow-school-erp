import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EntitlementsService } from '../../core/entitlements.service';
import { SubscriptionApiService } from './subscription-api.service';

type AdminTab = 'plans' | 'catalog' | 'license' | 'usage';

interface CatalogDraft {
  code?: string;
  name?: string;
  businessTypeCode?: string;
  moduleCode?: string;
  unit?: string;
  aggregation?: string;
  description?: string;
  sortOrder?: number;
}

@Component({
  selector: 'sf-subscription',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './subscription.component.html',
  styleUrls: ['../../shared/admin-page.scss', './subscription.component.scss'],
})
export class SubscriptionComponent implements OnInit {
  private readonly api = inject(SubscriptionApiService);
  private readonly entitlementsSvc = inject(EntitlementsService);

  tab: AdminTab = 'plans';
  status = '';
  error = '';

  plans: any[] = [];
  entitlements: any = null;
  selectedPlan: any = null;
  planProjection: any = null;
  editingLimitsText = '';
  editingFlagsText = '';

  businessTypes: any[] = [];
  modules: any[] = [];
  features: any[] = [];
  catalogLimits: any[] = [];
  catalogBusinessType = '';
  catalogModule = '';
  catalogKind: 'modules' | 'features' | 'limits' = 'modules';
  catalogDraft: CatalogDraft | null = null;

  license: any = null;
  renewDays = 365;
  licenseNotes = '';
  enforcementEnabled = false;

  usageLimits: any = null;
  usageEvents: any[] = [];

  ngOnInit(): void {
    this.reloadPlans();
    this.reloadEntitlements();
  }

  setTab(tab: AdminTab): void {
    this.tab = tab;
    this.status = '';
    this.error = '';
    if (tab === 'catalog') {
      this.reloadCatalog();
    } else if (tab === 'license') {
      this.reloadLicense();
    } else if (tab === 'usage') {
      this.reloadUsage();
    } else if (tab === 'plans') {
      this.reloadPlans();
      this.reloadEntitlements();
    }
  }

  assign(planId: string): void {
    this.clearMessages();
    this.api.assignPlan(planId).subscribe({
      next: () => {
        this.status = `Plan assigned: ${planId}`;
        this.reloadEntitlements();
        this.entitlementsSvc.load().subscribe();
        this.reloadLicense();
      },
      error: (e) => this.fail(e),
    });
  }

  selectPlan(plan: any): void {
    this.selectedPlan = {
      ...plan,
      limits: { ...(plan.limits ?? {}) },
      featureFlags: { ...(plan.featureFlags ?? {}) },
    };
    this.editingLimitsText = JSON.stringify(this.selectedPlan.limits ?? {}, null, 2);
    this.editingFlagsText = JSON.stringify(this.selectedPlan.featureFlags ?? {}, null, 2);
    this.planProjection = null;
    this.api.planProjection(plan.id).subscribe({
      next: (p) => (this.planProjection = p),
      error: () => (this.planProjection = null),
    });
  }

  saveSelectedPlan(): void {
    if (!this.selectedPlan) {
      return;
    }
    this.clearMessages();
    try {
      this.selectedPlan.limits = JSON.parse(this.editingLimitsText || '{}');
      this.selectedPlan.featureFlags = JSON.parse(this.editingFlagsText || '{}');
    } catch {
      this.error = 'Limits / feature flags must be valid JSON objects';
      return;
    }
    this.api.savePlan(this.selectedPlan).subscribe({
      next: (saved) => {
        this.status = `Saved plan ${saved.id}`;
        this.reloadPlans();
        this.selectPlan(saved);
        this.reloadEntitlements();
        this.entitlementsSvc.load().subscribe();
      },
      error: (e) => this.fail(e),
    });
  }

  flagEntries(): [string, boolean][] {
    return Object.entries(this.entitlements?.featureFlags ?? {}) as [string, boolean][];
  }

  limitEntries(): [string, number][] {
    return Object.entries(this.entitlements?.limits ?? {}) as [string, number][];
  }

  onBusinessTypeChange(): void {
    this.catalogModule = '';
    this.reloadCatalogLists();
  }

  onModuleChange(): void {
    this.reloadCatalogLists();
  }

  startCatalogCreate(): void {
    if (this.catalogKind === 'modules') {
      this.catalogDraft = {
        code: '',
        name: '',
        businessTypeCode: this.catalogBusinessType || '',
        description: '',
        sortOrder: 0,
      };
    } else if (this.catalogKind === 'features') {
      this.catalogDraft = {
        code: '',
        name: '',
        moduleCode: this.catalogModule || '',
        description: '',
        sortOrder: 0,
      };
    } else {
      this.catalogDraft = {
        code: '',
        name: '',
        businessTypeCode: this.catalogBusinessType || '',
        unit: 'COUNT',
        aggregation: 'NUMERIC',
        description: '',
        sortOrder: 0,
      };
    }
  }

  editCatalogRow(row: any): void {
    this.catalogDraft = { ...row };
  }

  saveCatalogDraft(): void {
    this.clearMessages();
    if (!this.catalogDraft) {
      return;
    }
    const body = { ...this.catalogDraft } as Record<string, unknown>;
    const req =
      this.catalogKind === 'modules'
        ? this.api.upsertModule(body)
        : this.catalogKind === 'features'
          ? this.api.upsertFeature(body)
          : this.api.upsertLimit(body);
    req.subscribe({
      next: () => {
        this.status = `Catalog ${this.catalogKind} saved`;
        this.catalogDraft = null;
        this.reloadCatalogLists();
      },
      error: (e) => this.fail(e),
    });
  }

  renew(): void {
    this.clearMessages();
    this.api
      .renewLicense({
        days: this.renewDays,
        enforcementEnabled: this.enforcementEnabled,
        notes: this.licenseNotes || undefined,
      })
      .subscribe({
        next: (lic) => {
          this.license = lic;
          this.status = `License renewed (${lic.resolvedStatus})`;
        },
        error: (e) => this.fail(e),
      });
  }

  saveLicenseFlags(): void {
    this.clearMessages();
    this.api
      .updateLicense({
        enforcementEnabled: this.enforcementEnabled,
        notes: this.licenseNotes || undefined,
      })
      .subscribe({
        next: (lic) => {
          this.license = lic;
          this.status = 'License settings saved';
        },
        error: (e) => this.fail(e),
      });
  }

  suspend(): void {
    this.clearMessages();
    this.api.suspendLicense(this.licenseNotes || 'Suspended from admin UI').subscribe({
      next: (lic) => {
        this.license = lic;
        this.status = 'License suspended';
      },
      error: (e) => this.fail(e),
    });
  }

  resume(): void {
    this.clearMessages();
    this.api.resumeLicense().subscribe({
      next: (lic) => {
        this.license = lic;
        this.enforcementEnabled = !!lic.enforcementEnabled;
        this.status = 'License resumed';
      },
      error: (e) => this.fail(e),
    });
  }

  private reloadPlans(): void {
    this.api.listPlans().subscribe({
      next: (p) => (this.plans = p ?? []),
      error: (e) => this.fail(e),
    });
  }

  private reloadEntitlements(): void {
    this.api.entitlements().subscribe({
      next: (e) => (this.entitlements = e),
      error: () => (this.entitlements = null),
    });
  }

  private reloadCatalog(): void {
    this.api.listBusinessTypes().subscribe({
      next: (rows) => {
        this.businessTypes = rows ?? [];
        if (!this.catalogBusinessType && this.businessTypes.length) {
          this.catalogBusinessType = this.businessTypes[0].code;
        }
        this.reloadCatalogLists();
      },
      error: (e) => this.fail(e),
    });
  }

  private reloadCatalogLists(): void {
    this.api.listModules(this.catalogBusinessType || undefined).subscribe({
      next: (rows) => (this.modules = rows ?? []),
      error: () => (this.modules = []),
    });
    this.api.listFeatures(this.catalogModule || undefined).subscribe({
      next: (rows) => (this.features = rows ?? []),
      error: () => (this.features = []),
    });
    this.api.listCatalogLimits(this.catalogBusinessType || undefined).subscribe({
      next: (rows) => (this.catalogLimits = rows ?? []),
      error: () => (this.catalogLimits = []),
    });
  }

  private reloadLicense(): void {
    this.api.getLicense().subscribe({
      next: (lic) => {
        this.license = lic;
        this.enforcementEnabled = !!lic?.enforcementEnabled;
        this.licenseNotes = lic?.notes ?? '';
      },
      error: (e) => this.fail(e),
    });
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

  private clearMessages(): void {
    this.status = '';
    this.error = '';
  }

  private fail(err: any): void {
    this.error =
      err?.error?.message || err?.message || (typeof err === 'string' ? err : 'Request failed');
  }
}
