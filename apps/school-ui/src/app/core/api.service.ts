import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../environments/environment';

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  get<T>(path: string): Observable<T> {
    return this.http.get<ApiResponse<T>>(`${this.base}${path}`).pipe(map((r) => r.data));
  }

  /** Paged list helper — normalizes PageResult envelopes. */
  getPage<T>(path: string, page = 0, size = 50): Observable<PageResult<T>> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http
      .get<ApiResponse<PageResult<T> | T[]>>(`${this.base}${path}`, { params })
      .pipe(
        map((r) => {
          const data = r.data as PageResult<T> | T[];
          if (Array.isArray(data)) {
            return {
              items: data,
              page: 0,
              size: data.length,
              totalElements: data.length,
              totalPages: 1,
              hasNext: false,
            };
          }
          return data;
        }),
      );
  }

  /** Convenience: items only from a paged (or legacy array) endpoint. */
  getItems<T>(path: string, page = 0, size = 50): Observable<T[]> {
    return this.getPage<T>(path, page, size).pipe(map((p) => p.items ?? []));
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.put<ApiResponse<T>>(`${this.base}${path}`, body).pipe(map((r) => r.data));
  }

  post<T>(path: string, body: unknown): Observable<T> {
    return this.http.post<ApiResponse<T>>(`${this.base}${path}`, body).pipe(map((r) => r.data));
  }

  getBlob(path: string): Observable<Blob> {
    return this.http.get(`${this.base}${path}`, { responseType: 'blob' });
  }
}
