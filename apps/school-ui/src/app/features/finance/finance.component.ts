import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-finance',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './finance.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class FinanceComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  error = '';
  status = '';
  busy = false;

  paymentGatewayEnabled = false;
  accountingEnabled = false;
  heads: any[] = [];
  structures: any[] = [];
  concessions: any[] = [];
  providers: any[] = [];
  transactions: any[] = [];

  // Demand preview
  structureKey = 'grade_8_annual';
  concessionKey = '';
  studentRef = 'ADM-FIN-1';
  studentName = 'Finance Student';
  classSection = 'Grade 8-A';
  demand: any = null;

  // Head editor
  headDraft = { definitionKey: '', label: '', category: 'ACADEMIC', refundable: true, enabled: true };

  // Intent
  intentAmount = 1000;
  lastIntent: any = null;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/fee/finance/bootstrap').subscribe({
      next: (boot) => {
        this.paymentGatewayEnabled = !!boot.paymentGatewayEnabled;
        this.accountingEnabled = !!boot.accountingEnabled;
        this.heads = boot.heads ?? [];
        this.structures = boot.structures ?? [];
        this.concessions = boot.concessions ?? [];
        this.providers = boot.providers ?? [];
        if (this.structures.length && !this.structureKey) {
          this.structureKey = this.structures[0].definitionKey || this.structures[0].payload?.definitionKey;
        }
        this.loading = false;
        this.loadTransactions();
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Finance bootstrap failed';
      },
    });
  }

  loadTransactions(): void {
    this.api.get<any[]>('/api/fee/finance/transactions').subscribe({
      next: (t) => (this.transactions = t ?? []),
      error: () => (this.transactions = []),
    });
  }

  payload(def: any): any {
    return def?.payload || def || {};
  }

  previewDemand(): void {
    this.busy = true;
    this.error = '';
    this.api
      .post<any>('/api/fee/finance/demands/preview', {
        structureKey: this.structureKey,
        concessionKey: this.concessionKey || null,
        studentRef: this.studentRef,
        studentName: this.studentName,
        classSection: this.classSection,
      })
      .subscribe({
        next: (d) => {
          this.busy = false;
          this.demand = d;
          this.status = `Demand net=${d.netAmount} ${d.currency}`;
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Demand preview failed';
        },
      });
  }

  saveHead(): void {
    const key = (this.headDraft.definitionKey || '').trim().toUpperCase();
    if (!key || !this.headDraft.label?.trim()) {
      this.error = 'Head key and label are required';
      return;
    }
    this.busy = true;
    this.api.put(`/api/fee/finance/heads/${encodeURIComponent(key)}`, { ...this.headDraft, definitionKey: key }).subscribe({
      next: () => {
        this.busy = false;
        this.status = `Saved head ${key}`;
        this.headDraft = { definitionKey: '', label: '', category: 'ACADEMIC', refundable: true, enabled: true };
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Save head failed';
      },
    });
  }

  createIntent(): void {
    if (!this.paymentGatewayEnabled) {
      this.error = 'FEATURE_MULTI_PAYMENT_GATEWAY is off';
      return;
    }
    this.busy = true;
    this.api
      .post<any>('/api/fee/finance/payments/intents', {
        amount: this.demand?.netAmount ?? this.intentAmount,
        studentRef: this.studentRef,
        providerKey: 'simulated',
        mode: 'UPI',
        idempotencyKey: crypto.randomUUID(),
        demand: this.demand,
      })
      .subscribe({
        next: (intent) => {
          this.busy = false;
          this.lastIntent = intent;
          this.status = `Intent ${intent.referenceNo} ${intent.status}`;
          this.loadTransactions();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Create intent failed';
        },
      });
  }

  simulateCapture(): void {
    if (!this.lastIntent?.id) return;
    this.busy = true;
    this.api.post<any>(`/api/fee/finance/payments/intents/${this.lastIntent.id}/simulate-capture`, {}).subscribe({
      next: (intent: any) => {
        this.busy = false;
        this.lastIntent = intent;
        this.status = `Captured ${intent.referenceNo}`;
        this.loadTransactions();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Capture failed';
      },
    });
  }
}
