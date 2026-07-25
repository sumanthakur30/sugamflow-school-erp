import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  HostBinding,
  HostListener,
  Input,
  OnDestroy,
  OnInit,
  Output,
  ViewChild,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  Subject,
  Subscription,
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  of,
  switchMap,
  timeout,
} from 'rxjs';
import { ApiService, PageResult } from '../../core/api.service';
import { StaffLookupFilters, StaffLookupRow } from './staff-lookup.models';

@Component({
  selector: 'sf-staff-lookup',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './staff-lookup.component.html',
  styleUrls: ['./staff-lookup.component.scss'],
})
export class StaffLookupComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly search$ = new Subject<string>();
  private sub?: Subscription;
  private readonly uid = Math.random().toString(36).slice(2, 9);

  readonly inputId = `stf-input-${this.uid}`;
  readonly listId = `stf-list-${this.uid}`;

  @Input() placeholder = 'Search by name, employee ID, department, mobile…';
  @Input() minChars = 1;
  @Input() pageSize = 12;
  @Input() defaultStatus = 'ACTIVE';
  @Input() showFilters = true;
  @Input() disabled = false;
  @Input() label = 'Find staff';
  @Input() initialQuery = '';

  @Output() staffSelected = new EventEmitter<StaffLookupRow>();
  @Output() cleared = new EventEmitter<void>();

  @ViewChild('searchInput') searchInput?: ElementRef<HTMLInputElement>;

  q = '';
  open = false;
  searching = false;
  error = '';
  results: StaffLookupRow[] = [];
  highlightIndex = -1;
  selected: StaffLookupRow | null = null;
  emptyHint = '';

  filters: StaffLookupFilters = {
    status: 'ACTIVE',
    department: '',
  };
  showFilterPanel = false;

  @HostBinding('class.slu-host-open')
  get hostOpen(): boolean {
    return this.open && !this.selected;
  }

  ngOnInit(): void {
    this.filters.status = this.defaultStatus || 'ACTIVE';
    this.sub = this.search$
      .pipe(
        debounceTime(280),
        distinctUntilChanged(),
        switchMap((term) => {
          const t = term.trim();
          if (t.length < this.minChars && !this.filters.department?.trim()) {
            this.results = [];
            this.searching = false;
            this.open = false;
            this.emptyHint = '';
            return of(null);
          }
          this.searching = true;
          this.error = '';
          this.emptyHint = '';
          return this.searchStaff(t).pipe(
            catchError((err) => {
              this.error = err?.error?.message ?? 'Staff search failed';
              this.searching = false;
              return of([] as StaffLookupRow[]);
            }),
          );
        }),
      )
      .subscribe((items) => {
        if (items == null) {
          return;
        }
        this.results = items;
        this.searching = false;
        this.highlightIndex = this.results.length ? 0 : -1;
        this.open = true;
        if (!this.results.length) {
          this.emptyHint = 'No matching active staff.';
        }
      });
  }

  ngAfterViewInit(): void {
    if (this.initialQuery?.trim()) {
      this.q = this.initialQuery.trim();
      this.onQueryChange(this.q);
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  onQueryChange(value: string): void {
    this.q = value;
    if (this.selected) {
      this.selected = null;
      this.cleared.emit();
    }
    this.search$.next(value);
  }

  onFocus(): void {
    if (this.disabled) return;
    if (this.results.length || this.q.trim().length >= this.minChars || this.filters.department) {
      this.open = true;
    }
  }

  applyFilters(): void {
    this.search$.next(this.q);
    this.open = true;
  }

  clearSelection(): void {
    this.selected = null;
    this.q = '';
    this.results = [];
    this.open = false;
    this.highlightIndex = -1;
    this.emptyHint = '';
    this.cleared.emit();
    queueMicrotask(() => this.searchInput?.nativeElement?.focus());
  }

  selectRow(row: StaffLookupRow, event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    this.selected = row;
    this.q = row.fullName || row.name || row.employeeNo || '';
    this.open = false;
    this.results = [];
    this.highlightIndex = -1;
    this.error = '';
    this.staffSelected.emit(row);
  }

  @HostListener('document:click', ['$event'])
  onDocClick(ev: MouseEvent): void {
    if (!this.host.nativeElement.contains(ev.target as Node)) {
      this.open = false;
    }
  }

  onKeydown(ev: KeyboardEvent): void {
    if (this.disabled) return;
    if (ev.key === 'Escape') {
      this.open = false;
      return;
    }
    if (!this.open && (ev.key === 'ArrowDown' || ev.key === 'Enter')) {
      if (this.q.trim().length >= this.minChars || this.filters.department) {
        this.open = true;
        this.search$.next(this.q);
      }
      return;
    }
    if (!this.open || !this.results.length) return;
    if (ev.key === 'ArrowDown') {
      ev.preventDefault();
      this.highlightIndex = Math.min(this.highlightIndex + 1, this.results.length - 1);
    } else if (ev.key === 'ArrowUp') {
      ev.preventDefault();
      this.highlightIndex = Math.max(this.highlightIndex - 1, 0);
    } else if (ev.key === 'Enter') {
      ev.preventDefault();
      const row = this.results[this.highlightIndex];
      if (row) this.selectRow(row);
    }
  }

  trackById(_: number, row: StaffLookupRow): string {
    return row.id;
  }

  displayName(row: StaffLookupRow): string {
    return row.fullName || row.name || '—';
  }

  private searchStaff(term: string) {
    const params = {
      q: term || undefined,
      status: this.filters.status || undefined,
      department: this.filters.department || undefined,
    };
    return this.api.getPage<StaffLookupRow>('/api/staff/directory/staff', 0, this.pageSize, params).pipe(
      timeout(5000),
      map((page) => this.normalize(page)),
      catchError((err) => {
        this.error = err?.error?.message ?? 'Staff search failed';
        return of([] as StaffLookupRow[]);
      }),
    );
  }

  private normalize(page: PageResult<StaffLookupRow> | StaffLookupRow[]): StaffLookupRow[] {
    const items = Array.isArray(page) ? page : (page?.items ?? []);
    return (items || []).map((row) => ({
      ...row,
      id: String(row.id),
      fullName: row.fullName || row.name,
    }));
  }
}
