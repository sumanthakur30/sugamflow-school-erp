import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChartSlice } from './finance-dashboard.models';

@Component({
  selector: 'sf-finance-donut-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="fdonut" role="img" [attr.aria-label]="title || 'Donut chart'">
      @if (title) {
        <h4 class="fdonut-title">{{ title }}</h4>
      }
      @if (!slices.length) {
        <p class="muted">No data yet.</p>
      } @else {
        <div class="fdonut-body">
          <svg viewBox="0 0 42 42" class="fdonut-svg" aria-hidden="true">
            @for (seg of segments; track seg.key) {
              <circle
                class="ring"
                cx="21"
                cy="21"
                r="15.915"
                fill="transparent"
                [attr.stroke]="seg.color"
                stroke-width="6"
                [attr.stroke-dasharray]="seg.dash"
                [attr.stroke-dashoffset]="seg.offset"
              />
            }
            <circle cx="21" cy="21" r="11" fill="#fff"></circle>
            <text x="21" y="21.5" text-anchor="middle" class="center">{{ centerLabel }}</text>
          </svg>
          <ul class="fdonut-legend">
            @for (s of slices; track s.key) {
              <li>
                <i [style.background]="s.color || '#64748b'"></i>
                <span>{{ s.label }}</span>
                <strong>{{ format(s.value) }}</strong>
              </li>
            }
          </ul>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .fdonut-title {
        margin: 0 0 0.75rem;
        font-size: 0.92rem;
      }
      .fdonut-body {
        display: grid;
        grid-template-columns: 8.5rem 1fr;
        gap: 0.85rem;
        align-items: center;
      }
      .fdonut-svg {
        width: 8.5rem;
        height: 8.5rem;
        transform: rotate(-90deg);
      }
      .ring {
        transition: stroke-dasharray 0.3s ease;
      }
      .center {
        transform: rotate(90deg);
        transform-origin: 21px 21px;
        font-size: 3.2px;
        font-weight: 700;
        fill: #0f172a;
      }
      .fdonut-legend {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
      }
      .fdonut-legend li {
        display: grid;
        grid-template-columns: 0.65rem 1fr auto;
        gap: 0.45rem;
        align-items: center;
        font-size: 0.8rem;
      }
      .fdonut-legend i {
        width: 0.55rem;
        height: 0.55rem;
        border-radius: 999px;
      }
      .fdonut-legend strong {
        font-variant-numeric: tabular-nums;
      }
      .muted {
        color: #64748b;
        margin: 0;
      }
      @media (max-width: 640px) {
        .fdonut-body {
          grid-template-columns: 1fr;
          justify-items: center;
        }
      }
    `,
  ],
})
export class FinanceDonutChartComponent {
  @Input() title = '';
  @Input() slices: ChartSlice[] = [];
  @Input() centerLabel = '';

  get segments(): Array<{ key: string; color: string; dash: string; offset: number }> {
    const total = this.slices.reduce((s, x) => s + x.value, 0) || 1;
    let cursor = 25; // SVG circle start offset convention
    return this.slices.map((s) => {
      const pct = (s.value / total) * 100;
      const seg = {
        key: s.key,
        color: s.color || '#64748b',
        dash: `${pct} ${100 - pct}`,
        offset: cursor,
      };
      cursor -= pct;
      return seg;
    });
  }

  format(v: number): string {
    return v.toLocaleString('en-IN', { maximumFractionDigits: 0 });
  }
}
