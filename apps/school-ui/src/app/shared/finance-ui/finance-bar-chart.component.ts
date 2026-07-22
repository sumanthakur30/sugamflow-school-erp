import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChartSlice } from './finance-dashboard.models';

@Component({
  selector: 'sf-finance-bar-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="fbar" role="img" [attr.aria-label]="title || 'Bar chart'">
      @if (title) {
        <h4 class="fbar-title">{{ title }}</h4>
      }
      @if (!slices.length) {
        <p class="muted">No data yet.</p>
      } @else {
        <div class="fbar-rows">
          @for (s of slices; track s.key) {
            <div class="fbar-row">
              <span class="fbar-label" [title]="s.label">{{ s.label }}</span>
              <div class="fbar-track">
                <div class="fbar-fill" [style.width.%]="pct(s.value)" [style.background]="s.color || fill"></div>
              </div>
              <span class="fbar-val">{{ format(s.value) }}</span>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [
    `
      .fbar-title {
        margin: 0 0 0.75rem;
        font-size: 0.92rem;
      }
      .fbar-rows {
        display: flex;
        flex-direction: column;
        gap: 0.45rem;
      }
      .fbar-row {
        display: grid;
        grid-template-columns: 5.5rem 1fr 4.5rem;
        gap: 0.5rem;
        align-items: center;
      }
      .fbar-label {
        font-size: 0.78rem;
        font-weight: 600;
        color: #475569;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .fbar-track {
        height: 0.55rem;
        border-radius: 999px;
        background: #e2e8f0;
        overflow: hidden;
      }
      .fbar-fill {
        height: 100%;
        border-radius: 999px;
        min-width: 2px;
      }
      .fbar-val {
        font-size: 0.78rem;
        font-weight: 700;
        text-align: right;
        font-variant-numeric: tabular-nums;
      }
      .muted {
        color: #64748b;
        margin: 0;
      }
    `,
  ],
})
export class FinanceBarChartComponent {
  @Input() title = '';
  @Input() slices: ChartSlice[] = [];
  @Input() fill = 'var(--sf-primary)';
  @Input() money = true;

  pct(v: number): number {
    const max = Math.max(...this.slices.map((s) => s.value), 1);
    return Math.max(2, Math.round((v / max) * 100));
  }

  format(v: number): string {
    if (!this.money) return String(v);
    return v.toLocaleString('en-IN', { maximumFractionDigits: 0 });
  }
}
