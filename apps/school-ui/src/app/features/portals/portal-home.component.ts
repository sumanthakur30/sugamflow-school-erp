import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { PortalBootstrap, PortalContextService } from './portal-context.service';

@Component({
  selector: 'sf-portal-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './portal-home.component.html',
  styleUrls: ['../../shared/admin-page.scss', './portal-home.component.scss'],
})
export class PortalHomeComponent implements OnInit, OnDestroy {
  private readonly portalCtx = inject(PortalContextService);
  private sub?: Subscription;
  boot: PortalBootstrap | null = null;

  ngOnInit(): void {
    this.sub = this.portalCtx.bootstrap$.subscribe((b) => (this.boot = b));
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  summaryValue(key?: string): string {
    if (!key || !this.boot?.summary) {
      return '—';
    }
    const v = this.boot.summary[key];
    return v == null ? '—' : String(v);
  }

  profileEntries(): Array<{ key: string; value: string }> {
    const p = this.boot?.profile ?? {};
    return Object.keys(p).map((key) => ({ key, value: String(p[key] ?? '') }));
  }
}
