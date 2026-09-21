import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { OfflineQueueService } from '../../core/offline-queue.service';

@Component({
  selector: 'sf-offline',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './offline.component.html',
  styleUrls: ['../../shared/admin-page.scss', './offline.component.scss'],
})
export class OfflineComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly queue = inject(OfflineQueueService);
  private sub?: Subscription;

  loading = true;
  featureEnabled = false;
  error = '';
  status = '';
  busy = false;
  online = true;

  maxQueueSize = 200;
  syncBatchSize = 25;
  autoSyncOnReconnect = true;
  allowedEntityTypes: any[] = [];
  cacheKeys: any[] = [];
  batches: any[] = [];
  queueItems: any[] = [];

  sampleEntityType = 'ATTENDANCE_MARK';

  ngOnInit(): void {
    this.sub = this.queue.isOnline$.subscribe((o) => {
      const wasOffline = !this.online && o;
      this.online = o;
      if (wasOffline && this.autoSyncOnReconnect && this.featureEnabled) {
        this.syncNow();
      }
    });
    this.queue.items$.subscribe((items) => (this.queueItems = items));
    this.reload();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/config/offline/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.maxQueueSize = boot.maxQueueSize ?? 200;
        this.syncBatchSize = boot.syncBatchSize ?? 25;
        this.autoSyncOnReconnect = boot.autoSyncOnReconnect !== false;
        this.allowedEntityTypes = boot.allowedEntityTypes ?? [];
        this.cacheKeys = boot.cacheKeys ?? [];
        if (this.allowedEntityTypes.length) {
          this.sampleEntityType = this.allowedEntityTypes[0].entityType;
        }
        this.loading = false;
        if (this.featureEnabled) {
          this.loadBatches();
          this.registerServiceWorker();
        }
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Offline bootstrap failed';
        // Do NOT imply plan feature is off on transport/server errors
        const msg = String(this.error).toUpperCase();
        this.featureEnabled = !(
          msg.includes('FEATURE_DISABLED') || msg.includes('NOT ENABLED FOR THIS PLAN')
        );
      },
    });
  }

  loadBatches(): void {
    this.api.get<any[]>('/api/config/offline/batches').subscribe({
      next: (list) => (this.batches = list ?? []),
      error: () => (this.batches = []),
    });
  }

  enqueueSample(): void {
    const def = this.allowedEntityTypes.find((e) => e.entityType === this.sampleEntityType);
    if (!def) {
      this.error = 'Select an allow-listed entity type';
      return;
    }
    if (this.queueItems.length >= this.maxQueueSize) {
      this.error = `Queue full (maxQueueSize=${this.maxQueueSize})`;
      return;
    }
    const body = this.sampleBody(def.entityType);
    this.queue.enqueue({
      entityType: def.entityType,
      method: def.method,
      path: def.path,
      body,
      label: `Sample ${def.entityType}`,
    });
    this.status = `Queued ${def.entityType}`;
  }

  remove(id: string): void {
    this.queue.remove(id);
  }

  clearQueue(): void {
    this.queue.clear();
    this.status = 'Local queue cleared';
  }

  async syncNow(): Promise<void> {
    if (!this.online) {
      this.error = 'Cannot sync while offline';
      return;
    }
    this.busy = true;
    this.error = '';
    try {
      const res = await this.queue.sync(this.syncBatchSize);
      this.busy = false;
      if (res.status === 'EMPTY') {
        this.status = 'Nothing to sync';
      } else {
        this.status = `Sync ${res.status}: ${res.successCount} ok, ${res.failureCount} failed`;
      }
      this.loadBatches();
    } catch (err: any) {
      this.busy = false;
      this.error = err?.error?.message ?? err?.message ?? 'Sync failed';
    }
  }

  private registerServiceWorker(): void {
    if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) {
      return;
    }
    navigator.serviceWorker.register('/offline-sw.js').catch(() => undefined);
  }

  private sampleBody(entityType: string): unknown {
    const stamp = new Date().toISOString().slice(0, 10);
    if (entityType === 'ATTENDANCE_MARK') {
      return {
        answers: {
          classSection: 'Grade 8-A',
          attendanceDate: stamp,
          studentName: 'Offline Student',
          admissionNo: 'ADM-OFF-1',
          status: 'PRESENT',
          attendancePercent: 92,
          email: 'offline@example.com',
          mobile: '9999900099',
        },
      };
    }
    if (entityType === 'FEE_PAYMENT') {
      return {
        answers: {
          studentName: 'Offline Student',
          admissionNo: 'ADM-OFF-1',
          feeHead: 'Tuition',
          amount: 1000,
          paymentMode: 'CASH',
          email: 'offline@example.com',
          mobile: '9999900099',
        },
      };
    }
    return {
      answers: {
        studentName: 'Offline Student',
        admissionNo: 'ADM-OFF-1',
        email: 'offline@example.com',
        mobile: '9999900099',
      },
    };
  }
}
