import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';

@Component({
  selector: 'sf-app-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app-users.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class AppUsersComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthSessionService);

  rows: any[] = [];
  error = '';
  message = '';
  draft = { roleCode: 'STUDENT', username: '', displayName: '', subjectRef: '', installed: true };

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.get<any[]>('/api/student/app-users').subscribe({
      next: (rows) => {
        this.rows = rows || [];
        this.error = '';
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Could not load app users';
      },
    });
  }

  save(): void {
    this.api.post<any>('/api/student/app-users', this.draft).subscribe({
      next: () => {
        this.message = 'Saved';
        this.draft = { roleCode: 'STUDENT', username: '', displayName: '', subjectRef: '', installed: true };
        this.load();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Save failed';
      },
    });
  }

  reset(row: any): void {
    const shopId = this.auth.getSession()?.shopId || '';
    this.api
      .post<any>('/api/student/app-users/reset-password', {
        username: row.username,
        roleCode: row.roleCode,
        displayName: row.displayName,
        subjectRef: row.subjectRef,
        installed: row.installed,
      })
      .subscribe({
        next: () => {
          this.http
            .post('/api/v1/auth/password-reset/admin', {
              shopId,
              username: row.username,
              sendEmail: false,
              actorUsername: this.auth.getSession()?.username || '',
            })
            .subscribe({
              next: () => {
                this.message = `Password reset started for ${row.username}`;
              },
              error: (err) => {
                this.error = err?.error?.message ?? 'Auth password reset failed';
              },
            });
        },
        error: (err) => {
          this.error = err?.error?.message ?? 'Could not record the reset';
        },
      });
  }
}
