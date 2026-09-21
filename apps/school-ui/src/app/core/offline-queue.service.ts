import { Injectable, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ApiService } from './api.service';

export interface OfflineQueueItem {
  id: string;
  entityType: string;
  method: string;
  path: string;
  body: unknown;
  createdAt: string;
  label?: string;
}

const QUEUE_KEY = 'sf.offline.queue';
const CLIENT_ID_KEY = 'sf.offline.clientId';

@Injectable({ providedIn: 'root' })
export class OfflineQueueService {
  private readonly api = inject(ApiService);
  private readonly online$ = new BehaviorSubject<boolean>(
    typeof navigator !== 'undefined' ? navigator.onLine : true,
  );
  private readonly queue$ = new BehaviorSubject<OfflineQueueItem[]>(this.readQueue());

  readonly isOnline$ = this.online$.asObservable();
  readonly items$ = this.queue$.asObservable();

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => this.online$.next(true));
      window.addEventListener('offline', () => this.online$.next(false));
    }
  }

  isOnline(): boolean {
    return this.online$.value;
  }

  clientId(): string {
    let id = localStorage.getItem(CLIENT_ID_KEY);
    if (!id) {
      id = crypto.randomUUID();
      localStorage.setItem(CLIENT_ID_KEY, id);
    }
    return id;
  }

  items(): OfflineQueueItem[] {
    return this.queue$.value;
  }

  enqueue(item: Omit<OfflineQueueItem, 'id' | 'createdAt'> & { id?: string }): OfflineQueueItem {
    const full: OfflineQueueItem = {
      id: item.id || crypto.randomUUID(),
      entityType: item.entityType,
      method: item.method,
      path: item.path,
      body: item.body,
      label: item.label,
      createdAt: new Date().toISOString(),
    };
    const next = [...this.readQueue(), full];
    this.writeQueue(next);
    return full;
  }

  clear(): void {
    this.writeQueue([]);
  }

  remove(id: string): void {
    this.writeQueue(this.readQueue().filter((i) => i.id !== id));
  }

  sync(batchSize = 25): Promise<{
    batchId?: string;
    status?: string;
    successCount?: number;
    failureCount?: number;
    items?: any[];
  }> {
    const pending = this.readQueue().slice(0, batchSize);
    if (!pending.length) {
      return Promise.resolve({ status: 'EMPTY', successCount: 0, failureCount: 0, items: [] });
    }
    return new Promise((resolve, reject) => {
      this.api
        .post<any>('/api/config/offline/sync', {
          clientId: this.clientId(),
          items: pending.map((p) => ({
            id: p.id,
            entityType: p.entityType,
            method: p.method,
            path: p.path,
            body: p.body,
            createdAt: p.createdAt,
          })),
        })
        .subscribe({
          next: (res) => {
            const syncedIds = new Set(
              (res.items || [])
                .filter((i: any) => i.status === 'SYNCED')
                .map((i: any) => i.clientItemId),
            );
            const remaining = this.readQueue().filter((q) => !syncedIds.has(q.id));
            this.writeQueue(remaining);
            resolve(res);
          },
          error: (err) => reject(err),
        });
    });
  }

  private readQueue(): OfflineQueueItem[] {
    try {
      const raw = localStorage.getItem(QUEUE_KEY);
      if (!raw) {
        return [];
      }
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  private writeQueue(items: OfflineQueueItem[]): void {
    localStorage.setItem(QUEUE_KEY, JSON.stringify(items));
    this.queue$.next(items);
  }
}
