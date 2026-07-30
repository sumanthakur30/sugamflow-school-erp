import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api.service';

/** Thin client for platform subscription APIs (catalog / plans / license / usage). */
@Injectable({ providedIn: 'root' })
export class SubscriptionApiService {
  private readonly api = inject(ApiService);

  listPlans(): Observable<any[]> {
    return this.api.get<any[]>('/api/subscription/plans');
  }

  getPlan(planId: string): Observable<any> {
    return this.api.get(`/api/subscription/plans/${encodeURIComponent(planId)}`);
  }

  savePlan(plan: any): Observable<any> {
    if (plan?.id) {
      return this.api.put(`/api/subscription/plans/${encodeURIComponent(plan.id)}`, plan);
    }
    return this.api.post('/api/subscription/plans', plan);
  }

  assignPlan(planId: string): Observable<any> {
    return this.api.put(`/api/subscription/tenants/current/plan/${encodeURIComponent(planId)}`, {});
  }

  entitlements(): Observable<any> {
    return this.api.get('/api/subscription/tenants/current/entitlements');
  }

  planProjection(planId: string): Observable<any> {
    return this.api.get(`/api/subscription/plans/${encodeURIComponent(planId)}/projection`);
  }

  listBusinessTypes(): Observable<any[]> {
    return this.api.get<any[]>('/api/subscription/catalog/business-types');
  }

  upsertBusinessType(body: Record<string, unknown>): Observable<any> {
    return this.api.put('/api/subscription/catalog/business-types', body);
  }

  listModules(businessType?: string): Observable<any[]> {
    const q = businessType ? `?businessType=${encodeURIComponent(businessType)}` : '';
    return this.api.get<any[]>(`/api/subscription/catalog/modules${q}`);
  }

  upsertModule(body: Record<string, unknown>): Observable<any> {
    return this.api.put('/api/subscription/catalog/modules', body);
  }

  listFeatures(module?: string): Observable<any[]> {
    const q = module ? `?module=${encodeURIComponent(module)}` : '';
    return this.api.get<any[]>(`/api/subscription/catalog/features${q}`);
  }

  upsertFeature(body: Record<string, unknown>): Observable<any> {
    return this.api.put('/api/subscription/catalog/features', body);
  }

  listCatalogLimits(businessType?: string): Observable<any[]> {
    const q = businessType ? `?businessType=${encodeURIComponent(businessType)}` : '';
    return this.api.get<any[]>(`/api/subscription/catalog/limits${q}`);
  }

  upsertLimit(body: Record<string, unknown>): Observable<any> {
    return this.api.put('/api/subscription/catalog/limits', body);
  }

  getLicense(): Observable<any> {
    return this.api.get('/api/subscription/tenants/current/license');
  }

  updateLicense(body: Record<string, unknown>): Observable<any> {
    return this.api.put('/api/subscription/tenants/current/license', body);
  }

  renewLicense(body: Record<string, unknown>): Observable<any> {
    return this.api.post('/api/subscription/tenants/current/license/renew', body);
  }

  suspendLicense(notes?: string): Observable<any> {
    return this.api.post('/api/subscription/tenants/current/license/suspend', { notes });
  }

  resumeLicense(): Observable<any> {
    return this.api.post('/api/subscription/tenants/current/license/resume', {});
  }

  cancelLicense(notes?: string): Observable<any> {
    return this.api.post('/api/subscription/tenants/current/license/cancel', { notes });
  }

  listUsageLimits(): Observable<any> {
    return this.api.get('/api/subscription/tenants/current/limits');
  }

  listUsage(): Observable<any> {
    return this.api.get('/api/subscription/tenants/current/usage');
  }

  usageEvents(limitCode?: string): Observable<any[]> {
    const q = limitCode ? `?limitCode=${encodeURIComponent(limitCode)}` : '';
    return this.api.get<any[]>(`/api/subscription/tenants/current/usage/events${q}`);
  }
}
