import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, shareReplay, tap, throwError } from 'rxjs';

export interface Session {
  accessToken: string;
  refreshToken: string;
  email: string;
  displayName: string;
}

const STORAGE_KEY = 'minibank.session';

function restoreSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed = raw ? (JSON.parse(raw) as Session) : null;
    return parsed?.accessToken && parsed?.refreshToken ? parsed : null;
  } catch {
    return null;
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private refreshInFlight: Observable<Session> | null = null;

  readonly session = signal<Session | null>(restoreSession());
  readonly user = computed(() => {
    const session = this.session();
    return session ? { email: session.email, displayName: session.displayName } : null;
  });

  accessToken(): string | null {
    return this.session()?.accessToken ?? null;
  }

  login(email: string, password: string): Observable<Session> {
    return this.http
      .post<Session>('/api/auth/login', { email, password })
      .pipe(tap(session => this.store(session)));
  }

  register(email: string, password: string, displayName: string): Observable<unknown> {
    return this.http.post('/api/auth/register', { email, password, displayName });
  }

  /**
   * Exchanges the refresh token for a new pair. Concurrent 401s share one
   * in-flight refresh so the rotated token is only spent once.
   */
  refreshSession(): Observable<Session> {
    const refreshToken = this.session()?.refreshToken;
    if (!refreshToken) {
      return throwError(() => new Error('no session to refresh'));
    }
    this.refreshInFlight ??= this.http
      .post<Session>('/api/auth/refresh', { refreshToken })
      .pipe(
        tap(session => this.store(session)),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay(1)
      );
    return this.refreshInFlight;
  }

  logout(): void {
    const refreshToken = this.session()?.refreshToken;
    if (refreshToken) {
      // best-effort server-side revocation
      this.http.post('/api/auth/logout', { refreshToken }).subscribe({ error: () => {} });
    }
    localStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
  }

  private store(session: Session): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    this.session.set(session);
  }
}
