import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-subscription',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './subscription.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class SubscriptionComponent implements OnInit {
  private readonly api = inject(ApiService);
  plans: any[] = [];
  entitlements: any = null;
  status = '';

  ngOnInit(): void {
    this.api.get<any[]>('/api/subscription/plans').subscribe((p) => (this.plans = p));
    this.reloadEntitlements();
  }

  assign(planId: string): void {
    this.api.put(`/api/subscription/tenants/current/plan/${planId}`, {}).subscribe(() => {
      this.status = `Plan assigned: ${planId}`;
      this.reloadEntitlements();
    });
  }

  private reloadEntitlements(): void {
    this.api.get('/api/subscription/tenants/current/entitlements').subscribe((e) => {
      this.entitlements = e;
    });
  }

  flagEntries(): [string, boolean][] {
    return Object.entries(this.entitlements?.featureFlags ?? {}) as [string, boolean][];
  }
}
