import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

@Component({
  selector: 'sf-report-card-insights',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './report-card-insights.component.html',
  styleUrl: './report-card-insights.component.scss',
})
export class ReportCardInsightsComponent {
  @Input({ required: true }) insight: any;

  trendLabel(): string {
    const trend = String(this.insight?.summary?.trend || '');
    if (trend === 'IMPROVING') return 'Improving';
    if (trend === 'DECLINING') return 'Needs attention';
    if (trend === 'STABLE') return 'Stable';
    return 'First trend point';
  }

  trendClass(): string {
    return String(this.insight?.summary?.trend || '').toLowerCase().replace('_', '-');
  }

  changeLabel(): string {
    const value = Number(this.insight?.summary?.changePercentagePoints);
    if (!Number.isFinite(value)) return 'Not enough history';
    return `${value > 0 ? '+' : ''}${value.toFixed(1)} points`;
  }

  barWidth(value: unknown): number {
    const number = Number(value);
    return Number.isFinite(number) ? Math.max(2, Math.min(100, number)) : 2;
  }
}
