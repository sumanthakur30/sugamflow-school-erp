import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface PortalBootstrap {
  portalKey: string;
  featureEnabled: boolean;
  title?: string;
  subtitle?: string;
  roleCode?: string;
  profile?: Record<string, unknown>;
  summary?: Record<string, unknown>;
  notices?: Array<{ id?: string; title?: string; body?: string }>;
  nav?: Array<{ id: string; label: string; route: string }>;
  widgets?: Array<{
    id: string;
    type: string;
    title: string;
    valueKey?: string;
    route?: string;
    order?: number;
  }>;
  sections?: Record<
    string,
    { title?: string; apiPath?: string; emptyMessage?: string }
  >;
}

@Injectable({ providedIn: 'root' })
export class PortalContextService {
  private readonly boot$ = new BehaviorSubject<PortalBootstrap | null>(null);

  readonly bootstrap$ = this.boot$.asObservable();

  set(portalKey: string, boot: PortalBootstrap): void {
    this.boot$.next({ ...boot, portalKey });
  }

  clear(): void {
    this.boot$.next(null);
  }

  current(): PortalBootstrap | null {
    return this.boot$.value;
  }
}
