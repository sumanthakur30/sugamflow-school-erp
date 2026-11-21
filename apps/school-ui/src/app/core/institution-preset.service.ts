import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { ApiService } from './api.service';
import { AuthSessionService } from './auth-session.service';

export type InstitutionPreset = 'small' | 'large' | 'coaching' | 'college';

const PRESETS: InstitutionPreset[] = ['small', 'large', 'coaching', 'college'];

export function isInstitutionPreset(value: string): value is InstitutionPreset {
  return (PRESETS as string[]).includes(value);
}

@Injectable({ providedIn: 'root' })
export class InstitutionPresetService {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);
  readonly preset$ = new BehaviorSubject<InstitutionPreset>('small');

  current(): InstitutionPreset {
    return this.preset$.value;
  }

  load(): void {
    const stored = this.readLocal();
    if (stored) {
      this.preset$.next(stored);
    }
    this.api.get<any>('/api/config/modules/institution').subscribe({
      next: (doc) => {
        const value = String(doc?.settings?.preset || '').toLowerCase();
        if (isInstitutionPreset(value)) {
          this.preset$.next(value);
          this.writeLocal(value);
        }
      },
      error: () => undefined,
    });
  }

  save(preset: InstitutionPreset): Observable<InstitutionPreset> {
    this.preset$.next(preset);
    this.writeLocal(preset);
    return this.api.get<any>('/api/config/modules/institution').pipe(
      catchError(() => of({ moduleKey: 'institution', settings: { enabled: true } })),
      switchMap((doc) =>
        this.api.put('/api/config/modules/institution', {
          ...(doc || {}),
          moduleKey: 'institution',
          settings: { ...((doc && doc.settings) || {}), enabled: true, preset },
        }),
      ),
      tap(() => this.writeLocal(preset)),
      map(() => preset),
    );
  }

  private storageKey(): string {
    return `sf.institutionPreset.${this.auth.getOrganizationId() || 'school'}`;
  }

  private readLocal(): InstitutionPreset | null {
    const value = String(localStorage.getItem(this.storageKey()) || '').toLowerCase();
    return isInstitutionPreset(value) ? value : null;
  }

  private writeLocal(preset: InstitutionPreset): void {
    localStorage.setItem(this.storageKey(), preset);
  }
}
