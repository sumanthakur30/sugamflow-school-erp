import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { pageMeta } from './list-controls';

@Component({
  selector: 'sf-list-pager',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './list-pager.component.html',
  styleUrls: ['./list-pager.component.scss'],
})
export class ListPagerComponent {
  /** Zero-based page index. */
  @Input() pageIndex = 0;
  @Input() pageSize = 50;
  @Input() totalElements = 0;
  @Input() hasNext = false;
  /** Noun for meta line, e.g. "students", "collections". */
  @Input() itemLabel = 'records';
  @Input() showMeta = true;

  @Output() pageIndexChange = new EventEmitter<number>();
  @Output() pageChange = new EventEmitter<number>();

  get from(): number {
    return pageMeta(this.pageIndex, this.pageSize, this.totalElements).from;
  }

  get to(): number {
    return pageMeta(this.pageIndex, this.pageSize, this.totalElements).to;
  }

  get totalPages(): number {
    return pageMeta(this.pageIndex, this.pageSize, this.totalElements).totalPages;
  }

  get pageDisplay(): number {
    return this.pageIndex + 1;
  }

  get canPrev(): boolean {
    return this.pageIndex > 0;
  }

  get canNext(): boolean {
    return !!this.hasNext || this.pageIndex + 1 < this.totalPages;
  }

  goFirst(): void {
    if (!this.canPrev) return;
    this.emitPage(0);
  }

  goPrev(): void {
    if (!this.canPrev) return;
    this.emitPage(this.pageIndex - 1);
  }

  goNext(): void {
    if (!this.canNext) return;
    this.emitPage(this.pageIndex + 1);
  }

  goLast(): void {
    if (!this.canNext) return;
    this.emitPage(Math.max(0, this.totalPages - 1));
  }

  private emitPage(index: number): void {
    this.pageIndex = index;
    this.pageIndexChange.emit(index);
    this.pageChange.emit(index);
  }
}
