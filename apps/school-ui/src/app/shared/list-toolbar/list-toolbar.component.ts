import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  DEFAULT_PAGE_SIZES,
  ListSortOption,
  ListStatusOption,
  toggleSortDir,
} from './list-controls';

@Component({
  selector: 'sf-list-toolbar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './list-toolbar.component.html',
  styleUrls: ['./list-toolbar.component.scss'],
})
export class ListToolbarComponent {
  readonly pageSizes = DEFAULT_PAGE_SIZES;

  @Input() searchPlaceholder = 'Search…';
  @Input() q = '';
  @Input() sortBy = 'updatedAt';
  @Input() sortDir: 'ASC' | 'DESC' = 'DESC';
  @Input() pageSize = 50;
  @Input() sortOptions: ListSortOption[] = [
    { key: 'updatedAt', label: 'Updated' },
    { key: 'status', label: 'Status' },
  ];
  /** When set, shows a Stock-style status dropdown (ALL + options). */
  @Input() statusOptions: ListStatusOption[] | null = null;
  @Input() status = '';
  @Input() statusLabel = 'Status';
  @Input() searching = false;
  @Input() showSearch = true;
  @Input() showSort = true;
  @Input() showPageSize = true;
  @Input() clearEnabled = false;
  /** When true, advanced filter slot is expanded. */
  @Input() filtersOpen = false;
  @Input() showFiltersToggle = false;
  /**
   * On narrow screens, hide status/sort/page-size behind a Filters toggle
   * so search results appear sooner (admission/staff lists on phone).
   */
  @Input() compactOnMobile = true;
  /** Mobile drawer open state for status/sort/page-size. */
  @Input() extrasOpen = false;

  @Output() qChange = new EventEmitter<string>();
  @Output() sortByChange = new EventEmitter<string>();
  @Output() sortDirChange = new EventEmitter<'ASC' | 'DESC'>();
  @Output() pageSizeChange = new EventEmitter<number>();
  @Output() statusChange = new EventEmitter<string>();
  @Output() filtersOpenChange = new EventEmitter<boolean>();
  @Output() extrasOpenChange = new EventEmitter<boolean>();
  @Output() search = new EventEmitter<void>();
  @Output() clear = new EventEmitter<void>();

  readonly extrasPanelId = `list-extras-${Math.random().toString(36).slice(2, 9)}`;

  get showStatus(): boolean {
    return Array.isArray(this.statusOptions) && this.statusOptions.length > 0;
  }

  get hasExtras(): boolean {
    return this.showStatus || this.showSort || this.showPageSize;
  }

  get activeExtraCount(): number {
    let n = 0;
    if (this.status) {
      n += 1;
    }
    if (this.sortBy && this.sortBy !== 'updatedAt') {
      n += 1;
    }
    if (this.pageSize && this.pageSize !== 50) {
      n += 1;
    }
    return n;
  }

  onQInput(value: string): void {
    this.q = value;
    this.qChange.emit(value);
  }

  onSortBy(value: string): void {
    this.sortBy = value;
    this.sortByChange.emit(value);
    this.search.emit();
  }

  onPageSize(value: string | number): void {
    this.pageSize = Number(value);
    this.pageSizeChange.emit(this.pageSize);
  }

  onStatus(value: string): void {
    this.status = value;
    this.statusChange.emit(value);
    this.search.emit();
  }

  toggleDir(): void {
    this.sortDir = toggleSortDir(this.sortDir);
    this.sortDirChange.emit(this.sortDir);
    this.search.emit();
  }

  toggleFilters(): void {
    this.filtersOpen = !this.filtersOpen;
    this.filtersOpenChange.emit(this.filtersOpen);
  }

  toggleExtras(): void {
    this.extrasOpen = !this.extrasOpen;
    this.extrasOpenChange.emit(this.extrasOpen);
  }

  submitSearch(): void {
    this.search.emit();
  }

  clearAll(): void {
    this.clear.emit();
  }
}
