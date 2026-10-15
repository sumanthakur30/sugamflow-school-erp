import { Component, ElementRef, HostListener, inject, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { ApiService } from '../core/api.service';

interface StudentHit {
  id: string;
  fullName?: string;
  admissionNo?: string;
  fatherName?: string;
  parentName?: string;
  mobile?: string;
  classSection?: string;
  status?: string;
}

@Component({
  selector: 'sf-student-global-search',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="global-search" [class.open]="open && hits.length">
      <input
        #box
        type="search"
        name="studentSearch"
        [(ngModel)]="q"
        (ngModelChange)="onQuery($event)"
        (focus)="open = true"
        placeholder="Search student by name, father, admission no, or mobile"
        aria-label="Search students"
        autocomplete="off"
      />
      @if (open && hits.length) {
        <ul class="global-search-results" role="listbox">
          @for (hit of hits; track hit.id) {
            <li>
              <button type="button" (click)="openStudent(hit)">
                <strong>{{ hit.fullName || hit.admissionNo }}</strong>
                <span>
                  {{ hit.admissionNo }}
                  @if (hit.classSection) {
                    · {{ hit.classSection }}
                  }
                  @if (hit.fatherName || hit.parentName) {
                    · {{ hit.fatherName || hit.parentName }}
                  }
                  @if (hit.mobile) {
                    · {{ hit.mobile }}
                  }
                </span>
              </button>
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: `
    .global-search {
      position: relative;
      min-width: 16rem;
      flex: 1;
      max-width: 28rem;
    }
    input {
      width: 100%;
      border: 1px solid #d6d3d1;
      border-radius: 999px;
      padding: 0.45rem 0.9rem;
      background: #fff;
      font: inherit;
      font-size: 0.85rem;
    }
    .global-search-results {
      position: absolute;
      z-index: 30;
      top: calc(100% + 0.25rem);
      left: 0;
      right: 0;
      margin: 0;
      padding: 0.25rem;
      list-style: none;
      background: #fff;
      border: 1px solid #e7e5e4;
      border-radius: 0.75rem;
      box-shadow: 0 12px 30px rgba(15, 23, 42, 0.12);
    }
    button {
      width: 100%;
      text-align: left;
      border: 0;
      background: transparent;
      padding: 0.45rem 0.6rem;
      border-radius: 0.5rem;
      cursor: pointer;
    }
    button:hover {
      background: #f5f5f4;
    }
    strong,
    span {
      display: block;
    }
    span {
      color: #57534e;
      font-size: 0.75rem;
    }
  `,
})
export class StudentGlobalSearchComponent {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly queries = new Subject<string>();
  readonly box = viewChild<ElementRef<HTMLInputElement>>('box');

  q = '';
  hits: StudentHit[] = [];
  open = false;

  constructor() {
    this.queries
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((q) => {
          const term = q.trim();
          if (term.length < 2) {
            return of({ items: [] as StudentHit[] });
          }
          return this.api
            .getPage<StudentHit>('/api/student/students/search', 0, 8, { q: term })
            .pipe(
              catchError(() =>
                of({
                  items: [] as StudentHit[],
                  page: 0,
                  size: 8,
                  totalElements: 0,
                  totalPages: 0,
                  hasNext: false,
                }),
              ),
            );
        }),
      )
      .subscribe((page) => {
        this.hits = page.items ?? [];
        this.open = this.q.trim().length >= 2;
      });
  }

  onQuery(value: string): void {
    this.queries.next(value);
  }

  openStudent(hit: StudentHit): void {
    this.open = false;
    this.q = '';
    this.hits = [];
    void this.router.navigate(['/admin/students', hit.id, '360']);
  }

  @HostListener('document:click', ['$event'])
  closeOutside(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.open = false;
    }
  }
}
