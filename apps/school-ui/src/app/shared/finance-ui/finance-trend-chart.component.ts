import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TrendPoint } from './finance-dashboard.models';
import { formatInr } from './finance-metrics';

@Component({
  selector: 'sf-finance-trend-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="trend" (mouseleave)="hover = null">
      <svg
        viewBox="0 0 640 260"
        role="img"
        aria-label="Collection and pending fees"
        (mousemove)="move($event)"
      >
        @for (g of guides; track g) {
          <line class="grid" x1="36" [attr.x2]="604" [attr.y1]="g" [attr.y2]="g" />
        }
        @for (bar of bars; track bar.key) {
          <rect class="bar" [attr.x]="bar.x" [attr.y]="bar.y" [attr.width]="bar.w" [attr.height]="bar.h" rx="4" />
        }
        <path class="area" [attr.d]="area" />
        <path class="line" [attr.d]="line" />
        @for (p of dots; track p.key) {
          <circle class="dot" [attr.cx]="p.x" [attr.cy]="p.y" r="3.5" />
        }
        @for (label of labels; track label.key) {
          <text class="axis" [attr.x]="label.x" y="250" text-anchor="middle">{{ label.label }}</text>
        }
      </svg>
      @if (hover) {
        <div class="tip" [style.left.px]="tipX" [style.top.px]="tipY">
          <strong>{{ hover.label }}</strong>
          <span>Collected {{ money(hover.collected) }}</span>
          <span>Pending {{ money(hover.pending) }}</span>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .trend {
        position: relative;
      }
      svg {
        width: 100%;
        height: auto;
        display: block;
      }
      .grid {
        stroke: rgba(15, 23, 42, 0.08);
        stroke-dasharray: 3 4;
      }
      .bar {
        fill: #0b6e4f;
        opacity: 0.88;
      }
      .area {
        fill: rgba(37, 99, 235, 0.16);
        stroke: none;
      }
      .line {
        fill: none;
        stroke: #1d4e89;
        stroke-width: 2.4;
      }
      .dot {
        fill: #fff;
        stroke: #1d4e89;
        stroke-width: 2;
      }
      .axis {
        fill: #64748b;
        font-size: 11px;
      }
      .tip {
        position: absolute;
        z-index: 2;
        min-width: 10rem;
        padding: 0.55rem 0.7rem;
        border-radius: 12px;
        background: #0f172a;
        color: #f8fafc;
        box-shadow: 0 10px 24px rgba(15, 23, 42, 0.18);
        display: flex;
        flex-direction: column;
        gap: 0.15rem;
        font-size: 0.78rem;
        pointer-events: none;
      }
      .tip strong {
        font-size: 0.84rem;
      }
    `,
  ],
})
export class FinanceTrendChartComponent {
  @Input() points: TrendPoint[] = [];
  hover: TrendPoint | null = null;
  tipX = 0;
  tipY = 0;
  readonly money = formatInr;
  readonly guides = [28, 78, 128, 178];

  private readonly width = 640;
  private readonly height = 220;
  private readonly left = 36;
  private readonly right = 604;
  private readonly top = 16;
  private readonly bottom = 210;

  get max(): number {
    return Math.max(1, ...this.points.map((p) => Math.max(p.collected, p.pending)));
  }

  get bars() {
    const slot = this.slot();
    return this.points.map((p, i) => {
      const h = this.scale(p.collected);
      const w = Math.max(6, slot * 0.42);
      return {
        key: p.key,
        x: this.left + i * slot + (slot - w) / 2,
        y: this.bottom - h,
        w,
        h,
      };
    });
  }

  get dots() {
    return this.curvePoints();
  }

  get labels() {
    const slot = this.slot();
    const step = this.points.length > 16 ? 5 : this.points.length > 8 ? 2 : 1;
    return this.points
      .map((p, i) => ({ key: p.key, label: p.label, x: this.left + i * slot + slot / 2, i }))
      .filter((p) => p.i % step === 0);
  }

  get line(): string {
    const pts = this.curvePoints();
    if (!pts.length) return '';
    return pts.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ');
  }

  get area(): string {
    const pts = this.curvePoints();
    if (!pts.length) return '';
    const start = pts[0];
    const end = pts[pts.length - 1];
    return `${this.line} L ${end.x} ${this.bottom} L ${start.x} ${this.bottom} Z`;
  }

  move(event: MouseEvent): void {
    if (!this.points.length) return;
    const svg = event.currentTarget as SVGSVGElement;
    const rect = svg.getBoundingClientRect();
    const x = ((event.clientX - rect.left) / rect.width) * this.width;
    const slot = this.slot();
    const index = Math.min(this.points.length - 1, Math.max(0, Math.floor((x - this.left) / slot)));
    this.hover = this.points[index];
    this.tipX = Math.min(rect.width - 170, Math.max(8, event.clientX - rect.left + 12));
    this.tipY = Math.max(8, event.clientY - rect.top - 72);
  }

  private slot(): number {
    const count = Math.max(1, this.points.length);
    return (this.right - this.left) / count;
  }

  private scale(value: number): number {
    return ((this.bottom - this.top) * value) / this.max;
  }

  private curvePoints() {
    const slot = this.slot();
    return this.points.map((p, i) => ({
      key: p.key,
      x: this.left + i * slot + slot / 2,
      y: this.bottom - this.scale(p.pending),
    }));
  }
}
