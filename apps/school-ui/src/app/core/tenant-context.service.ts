import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, filter, map, of, race, take, tap, timer } from 'rxjs';

/**
 * Coordinates post-login campus header sync.
 * BranchSwitcher marks ready after /branches/bootstrap; feature pages wait so
 * the first KPI/list calls do not race stale X-Branch-Id.
 */
@Injectable({ providedIn: 'root' })
export class TenantContextService {
  private readonly campusReady$ = new BehaviorSubject<boolean>(false);

  /** Max wait if branch switcher never mounts or bootstrap hangs. */
  private readonly readyTimeoutMs = 6000;

  markCampusPending(): void {
    this.campusReady$.next(false);
  }

  markCampusReady(): void {
    this.campusReady$.next(true);
  }

  isCampusReady(): boolean {
    return this.campusReady$.value;
  }

  /**
   * Emits once when campus headers are synced, or after a safety timeout.
   * Safe to subscribe from feature pages on every navigation.
   */
  whenCampusReady(): Observable<void> {
    if (this.campusReady$.value) {
      return of(undefined);
    }
    return race(
      this.campusReady$.pipe(
        filter((ready) => ready),
        take(1),
        map(() => undefined),
      ),
      timer(this.readyTimeoutMs).pipe(map(() => undefined)),
    ).pipe(
      take(1),
      tap(() => {
        if (!this.campusReady$.value) {
          this.campusReady$.next(true);
        }
      }),
    );
  }
}
