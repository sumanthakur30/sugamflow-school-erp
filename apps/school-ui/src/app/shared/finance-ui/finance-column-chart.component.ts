import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface ColumnPoint {
  key: string;
  label: string;
  income: number;
  expense: number;
}

@Component({
  selector: 'sf-finance-column-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="fcol" role="img" [attr.aria-label]="title || 'Income versus expense chart'">
      @if (title) {
        <div class="fcol-head">
          <h4>{{ title }}</h4>
          <div class="fcol-legend">
            <span><i class="inc"></i> Income</span>
            <span><i class="exp"></i> Expense</span>
          </div>
        </div>
      }
      @if (!points.length) {
        <p class="muted">No trend data for this period.</p>
      } @else {
        <div class="fcol-chart">
          @for (p of points; track p.key) {
            <div class="fcol-group" [title]="tooltip(p)">
              <div class="fcol-bars">
                <div class="bar income" [style.height.%]="pct(p.income)"></div>
                <div class="bar expense" [style.height.%]="pct(p.expense)"></div>
              </div>
              <span class="fcol-label">{{ shortLabel(p.label) }}</span>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [
    `
      .fcol-head {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 0.75rem;
        margin-bottom: 0.85rem;
      }
      .fcol-head h4 {
        margin: 0;
        font-size: 0.92rem;
      }
      .fcol-legend {
        display: flex;
        gap: 0.85rem;
        font-size: 0.75rem;
        font-weight: 700;
        color: #64748b;
      }
      .fcol-legend i {
        display: inline-block;
        width: 0.55rem;
        height: 0.55rem;
        border-radius: 2px;
        margin-right: 0.3rem;
      }
      .fcol-legend .inc {
        background: #15803d;
      }
      .fcol-legend .exp {
        background: #b91c1c;
      }
      .fcol-chart {
        display: flex;
        align-items: flex-end;
        gap: 0.55rem;
        min-height: 180px;
        padding: 0.25rem 0.15rem 0;
        overflow-x: auto;
      }
      .fcol-group {
        flex: 1 0 2.6rem;
        min-width: 2.4rem;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.35rem;
      }
      .fcol-bars {
        display: flex;
        align-items: flex-end;
        justify-content: center;
        gap: 0.2rem;
        width: 100%;
        height: 150px;
        border-bottom: 1px solid #e2e8f0;
      }
      .bar {
        width: 0.7rem;
        min-height: 2px;
        border-radius: 4px 4px 0 0;
      }
      .bar.income {
        background: linear-gradient(180deg, #22c55e, #15803d);
      }
      .bar.expense {
        background: linear-gradient(180deg, #f87171, #b91c1c);
      }
      .fcol-label {
        font-size: 0.68rem;
        font-weight: 700;
        color: #64748b;
        white-space: nowrap;
      }
      .muted {
        margin: 0;
        color: #64748b;
      }
    `,
  ],
})
export class FinanceColumnChartComponent {
  @Input() title = '';
  @Input() points: ColumnPoint[] = [];

  pct(v: number): number {
    const max = Math.max(...this.points.flatMap((p) => [p.income, p.expense]), 1);
    return Math.max(2, Math.round((v / max) * 100));
  }

  shortLabel(label: string): string {
    const parts = String(label || '').split(' ');
    return parts[0] || label;
  }

  tooltip(p: ColumnPoint): string {
    return `${p.label}\nIncome: ₹${this.fmt(p.income)}\nExpense: ₹${this.fmt(p.expense)}`;
  }

  private fmt(v: number): string {
    return (v || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 });
  }
}
