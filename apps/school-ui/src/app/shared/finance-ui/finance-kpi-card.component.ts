import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FinanceKpi } from './finance-dashboard.models';

@Component({
  selector: 'sf-finance-kpi-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <article
      class="fkpi"
      [class]="'fkpi tone-' + (kpi.tone || 'neutral')"
      [class.is-clickable]="clickable"
      [class.is-selected]="selected"
      [attr.role]="clickable ? 'button' : null"
      [attr.tabindex]="clickable ? 0 : null"
      [attr.aria-pressed]="clickable ? selected : null"
      (click)="activate()"
      (keyup.enter)="activate()"
      (keyup.space)="$event.preventDefault(); activate()"
    >
      <div class="fkpi-top">
        <span class="fkpi-label">{{ kpi.label }}</span>
        @if (kpi.trendLabel) {
          <span
            class="fkpi-trend"
            [class.up]="kpi.trendUp === true"
            [class.down]="kpi.trendUp === false"
          >
            {{ kpi.trendLabel }}
          </span>
        }
      </div>
      <div class="fkpi-value">{{ kpi.value }}</div>
      @if (kpi.hint) {
        <p class="fkpi-hint">{{ kpi.hint }}</p>
      }
      @if (kpi.actionLabel) {
        <span class="fkpi-action">{{ kpi.actionLabel }} →</span>
      }
    </article>
  `,
  styles: [
    `
      .fkpi {
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
        min-height: 7.5rem;
        padding: 0.95rem 1rem;
        border-radius: 12px;
        border: 1px solid color-mix(in srgb, var(--sf-menu) 14%, transparent);
        background: #fff;
      }
      .fkpi.is-clickable {
        cursor: pointer;
        transition:
          border-color 0.15s ease,
          box-shadow 0.15s ease,
          transform 0.12s ease;
      }
      .fkpi.is-clickable:hover,
      .fkpi.is-clickable:focus-visible {
        outline: none;
        border-color: color-mix(in srgb, var(--sf-primary) 35%, transparent);
        box-shadow: 0 6px 18px rgba(15, 23, 42, 0.08);
        transform: translateY(-1px);
      }
      .fkpi.is-selected {
        border-color: color-mix(in srgb, var(--sf-primary) 55%, transparent);
        box-shadow:
          0 0 0 2px color-mix(in srgb, var(--sf-primary) 22%, transparent),
          0 8px 20px rgba(15, 23, 42, 0.1);
      }
      .fkpi-top {
        display: flex;
        justify-content: space-between;
        gap: 0.5rem;
        align-items: flex-start;
      }
      .fkpi-label {
        font-size: 0.72rem;
        font-weight: 800;
        letter-spacing: 0.05em;
        text-transform: uppercase;
        color: #64748b;
      }
      .fkpi-value {
        font-size: 1.35rem;
        font-weight: 800;
        font-variant-numeric: tabular-nums;
        line-height: 1.2;
      }
      .fkpi-hint {
        margin: 0;
        font-size: 0.82rem;
        color: #64748b;
      }
      .fkpi-trend {
        font-size: 0.75rem;
        font-weight: 700;
        color: #64748b;
        white-space: nowrap;
      }
      .fkpi-trend.up {
        color: #166534;
      }
      .fkpi-trend.down {
        color: #b91c1c;
      }
      .fkpi-action {
        margin-top: auto;
        align-self: flex-start;
        color: var(--sf-primary);
        font-size: 0.82rem;
        font-weight: 700;
      }
      .tone-ok {
        border-color: color-mix(in srgb, #15803d 22%, transparent);
        background: linear-gradient(180deg, #f0fdf4, #fff 55%);
      }
      .tone-info {
        border-color: color-mix(in srgb, #2563eb 22%, transparent);
        background: linear-gradient(180deg, #eff6ff, #fff 55%);
      }
      .tone-warn {
        border-color: color-mix(in srgb, #c2410c 22%, transparent);
        background: linear-gradient(180deg, #fff7ed, #fff 55%);
      }
      .tone-danger {
        border-color: color-mix(in srgb, #b91c1c 22%, transparent);
        background: linear-gradient(180deg, #fef2f2, #fff 55%);
      }
      .tone-accent {
        border-color: color-mix(in srgb, #0f766e 22%, transparent);
        background: linear-gradient(180deg, #f0fdfa, #fff 55%);
      }
    `,
  ],
})
export class FinanceKpiCardComponent {
  @Input({ required: true }) kpi!: FinanceKpi;
  @Input() selected = false;
  @Output() action = new EventEmitter<FinanceKpi>();

  get clickable(): boolean {
    return !!(this.kpi?.actionTab || this.kpi?.actionLink || this.kpi?.key);
  }

  activate(): void {
    if (!this.clickable) return;
    this.action.emit(this.kpi);
  }
}
