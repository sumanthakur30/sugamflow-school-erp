import { Component, OnInit, inject } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WebsiteApiService } from '../core/website-api.service';
import { SeoService } from '../core/seo.service';

interface DisclosureDoc {
  title: string;
  category: string;
  fileUrl: string;
}

const CATEGORIES: Array<{ code: string; label: string }> = [
  { code: 'AFFILIATION', label: 'AFFILIATION / UPGRADATION LETTER' },
  { code: 'TRUST_REGISTRATION', label: 'SOCIETIES / TRUST / COMPANY REGISTRATION' },
  { code: 'NOC', label: 'NO OBJECTION CERTIFICATE (NOC)' },
  { code: 'RECOGNITION', label: 'RECOGNITION CERTIFICATE' },
  { code: 'BUILDING_SAFETY', label: 'BUILDING SAFETY CERTIFICATE' },
  { code: 'FIRE_SAFETY', label: 'FIRE SAFETY CERTIFICATE' },
  { code: 'DEO', label: 'DEO CERTIFICATE' },
  { code: 'WATER_HEALTH', label: 'VALIDITY OF WATER, HEALTH AND SANITATION CERTIFICATES' },
  { code: 'FEE_STRUCTURE', label: 'FEE STRUCTURE' },
  { code: 'ACADEMIC_CALENDAR', label: 'ANNUAL ACADEMIC CALENDAR' },
  { code: 'SMC', label: 'LIST OF SCHOOL MANAGEMENT COMMITTEE (SMC)' },
  { code: 'PTA', label: 'LIST OF PARENTS TEACHERS ASSOCIATION (PTA)' },
  { code: 'BOARD_RESULT', label: 'LAST THREE-YEAR RESULT OF BOARD EXAMINATION' },
];

@Component({
  selector: 'app-disclosure-page',
  standalone: true,
  imports: [NgFor, NgIf, RouterLink],
  template: `
    <section class="page">
      <nav class="crumb" aria-label="Breadcrumb">
        <a routerLink="/">Home</a>
        <span aria-hidden="true">›</span>
        <span>Mandatory Public Disclosure</span>
      </nav>
      <h1>CBSE Mandatory Public Disclosure</h1>
      <p class="lead" *ngIf="loading">Loading documents…</p>
      <div class="table-wrap" *ngIf="!loading">
        <table>
          <thead>
            <tr>
              <th>S. No.</th>
              <th>Information / Documents</th>
              <th>Details</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let row of rows; let i = index">
              <td data-label="S. No.">{{ i + 1 }}</td>
              <td data-label="Information / Documents"><strong>{{ row.label }}</strong></td>
              <td data-label="Details">
                <ng-container *ngIf="row.files.length; else empty">
                  <a
                    *ngFor="let file of row.files"
                    [href]="file.href"
                    target="_blank"
                    rel="noopener"
                    [attr.aria-label]="'Open ' + file.title"
                  >
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path
                        d="M3 6.5A2.5 2.5 0 0 1 5.5 4H10l2 2h6.5A2.5 2.5 0 0 1 21 8.5v9A2.5 2.5 0 0 1 18.5 20h-13A2.5 2.5 0 0 1 3 17.5v-11Z"
                      />
                    </svg>
                    <span>{{ file.title }}</span>
                  </a>
                </ng-container>
                <ng-template #empty><span class="empty">Not uploaded</span></ng-template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: [
    `
      .page {
        padding: 1.5rem 0 3rem;
      }
      .crumb {
        display: flex;
        gap: 0.4rem;
        align-items: center;
        color: #64748b;
        font-size: 0.92rem;
        margin-bottom: 1rem;
      }
      .crumb a {
        color: var(--sf-primary, #1b4d3e);
        text-decoration: none;
      }
      h1 {
        font-family: 'Fraunces', Georgia, serif;
        color: var(--sf-primary, #1b4d3e);
        margin: 0 0 1rem;
        font-size: clamp(1.6rem, 3vw, 2.2rem);
      }
      .lead {
        color: #64748b;
      }
      .table-wrap {
        overflow-x: auto;
      }
      table {
        width: 100%;
        border-collapse: collapse;
        background: #fff;
        min-width: 36rem;
      }
      th,
      td {
        border: 1px solid #d5ddd8;
        padding: 0.75rem 0.85rem;
        vertical-align: middle;
        text-align: left;
      }
      th {
        background: #f3f4f6;
        color: #1f2937;
        font-weight: 700;
      }
      td strong {
        color: #111827;
      }
      td:first-child,
      th:first-child {
        width: 5rem;
        text-align: center;
      }
      td:last-child {
        width: 16rem;
      }
      a {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        margin: 0.15rem 0.6rem 0.15rem 0;
        color: #1d4ed8;
        font-weight: 650;
        text-decoration: none;
      }
      svg {
        width: 1.7rem;
        height: 1.7rem;
        fill: #eab308;
        stroke: #a16207;
        stroke-width: 1;
      }
      .empty {
        color: #64748b;
      }
      @media (max-width: 720px) {
        table {
          min-width: 0;
        }
        thead {
          display: none;
        }
        tr {
          display: block;
          margin-bottom: 0.75rem;
          border: 1px solid #d5ddd8;
        }
        td {
          display: block;
          width: auto !important;
          text-align: left;
          border: 0;
          border-top: 1px solid #e5e7eb;
        }
        td:first-child {
          border-top: 0;
        }
        td::before {
          content: attr(data-label);
          display: block;
          font-size: 0.75rem;
          letter-spacing: 0.04em;
          text-transform: uppercase;
          color: #64748b;
          margin-bottom: 0.2rem;
        }
      }
    `,
  ],
})
export class DisclosurePageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  private readonly seo = inject(SeoService);
  loading = true;
  rows: Array<{ label: string; files: Array<{ title: string; href: string }> }> = CATEGORIES.map(
    (item) => ({ label: item.label, files: [] })
  );

  ngOnInit(): void {
    this.api.resolve().subscribe((site) => {
      this.seo.apply({
        title: `CBSE Mandatory Public Disclosure | ${site.displayName}`,
        description: site.seo?.['defaultDescription'] || 'CBSE mandatory public disclosure',
        canonical: window.location.origin + '/mandatory-public-disclosure',
      });
      this.api.listDocuments(100).subscribe({
        next: (docs) => {
          const grouped = new Map<string, DisclosureDoc[]>();
          for (const row of docs || []) {
            const category = String(row['category'] || '');
            const fileUrl = row['fileUrl'] == null ? '' : String(row['fileUrl']);
            if (!fileUrl) continue;
            const list = grouped.get(category) || [];
            list.push({
              title: String(row['title'] || 'Document'),
              category,
              fileUrl,
            });
            grouped.set(category, list);
          }
          this.rows = CATEGORIES.map((item) => ({
            label: item.label,
            files: (grouped.get(item.code) || []).map((file) => ({
              title: file.title,
              href: this.api.mediaUrl(file.fileUrl),
            })),
          }));
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        },
      });
    });
  }
}
