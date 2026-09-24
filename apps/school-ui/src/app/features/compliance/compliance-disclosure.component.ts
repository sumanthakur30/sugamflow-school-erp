import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ComplianceApiService, DisclosurePackage } from './compliance-api.service';

@Component({
  selector: 'sf-compliance-disclosure',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  styleUrls: ['../../shared/admin-page.scss'],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h2>Disclosure Preview</h2>
          <p>Build and publish mandatory public disclosure to the school website CMS.</p>
        </div>
        <div style="display: flex; gap: 0.5rem; flex-wrap: wrap">
          <a routerLink="/admin/compliance" class="btn">Dashboard</a>
          <button class="btn" type="button" [disabled]="loading || busy" (click)="refresh()">
            Refresh preview
          </button>
          <button class="btn" type="button" [disabled]="busy || !pack" (click)="saveDraft()">
            Save CMS draft
          </button>
          <button class="btn primary" type="button" [disabled]="busy || !pack" (click)="publish()">
            {{ busy ? 'Publishing…' : 'Publish to website' }}
          </button>
        </div>
      </div>

      @if (error) {
        <div class="panel" style="border-color: #c45c5c"><p>{{ error }}</p></div>
      }
      @if (info) {
        <div class="panel"><p>{{ info }}</p></div>
      }

      <div class="panel" style="margin-bottom: 1rem">
        <form class="grid two" [formGroup]="form">
          <label>
            <span class="field-title">CMS slug</span>
            <input formControlName="slug" />
          </label>
          <label>
            <span class="field-title">Page title</span>
            <input formControlName="title" />
          </label>
        </form>
        @if (pack) {
          <p class="muted" style="margin-top: 0.75rem">
            Status: {{ pack.lastPublishStatus || 'NOT_PUBLISHED' }}
            @if (pack.lastPublishedAt) {
              · last published {{ pack.lastPublishedAt | date: 'medium' }}
            }
            @if (pack.publicUrlHint) {
              · public path <code>{{ pack.publicUrlHint }}</code>
            }
          </p>
        }
      </div>

      @if (loading) {
        <p class="muted">Building disclosure package…</p>
      }

      @if (pack) {
        @if (pack.warnings?.length) {
          <div class="panel" style="margin-bottom: 1rem">
            <h3 style="margin-top: 0; font-size: 1.1rem">Warnings</h3>
            <ul>
              @for (w of pack.warnings; track w) {
                <li>{{ w }}</li>
              }
            </ul>
          </div>
        }

        <div class="panel">
          <h3 style="margin-top: 0; font-size: 1.1rem">{{ pack.title }}</h3>
          <p class="muted">{{ pack.summary }}</p>
          <div class="disclosure-preview" [innerHTML]="pack.bodyHtml"></div>
        </div>
      }
    </section>
  `,
})
export class ComplianceDisclosureComponent implements OnInit {
  private readonly api = inject(ComplianceApiService);
  private readonly fb = inject(FormBuilder);

  form = this.fb.nonNullable.group({
    slug: ['mandatory-public-disclosure'],
    title: ['Mandatory Public Disclosure'],
  });

  loading = true;
  busy = false;
  error = '';
  info = '';
  pack: DisclosurePackage | null = null;

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading = true;
    this.error = '';
    this.api.disclosurePreview().subscribe({
      next: (p) => {
        this.pack = p;
        this.form.patchValue({
          slug: p.slug || 'mandatory-public-disclosure',
          title: p.title || 'Mandatory Public Disclosure',
        });
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error =
          err?.error?.message ||
          err?.message ||
          'Unable to preview disclosure. Check FEATURE_CBSE_COMPLIANCE.';
      },
    });
  }

  saveDraft(): void {
    this.push(false, 'Saved CMS draft (not public until Publish).');
  }

  publish(): void {
    this.push(true, 'Published to CMS. Website sitemap will pick up the published page.');
  }

  private push(publishNow: boolean, okMsg: string): void {
    this.busy = true;
    this.error = '';
    this.info = '';
    const v = this.form.getRawValue();
    this.api
      .publishDisclosure({
        slug: v.slug,
        title: v.title,
        publishNow,
      })
      .subscribe({
        next: (p) => {
          this.pack = p;
          this.busy = false;
          this.info = okMsg;
        },
        error: (err) => {
          this.busy = false;
          this.error =
            err?.error?.message ||
            err?.message ||
            'Publish failed. Ensure FEATURE_BOARD_DISCLOSURE and cms-service are available.';
        },
      });
  }
}
