import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

export interface Session {
  token: string;
  email: string;
  displayName: string;
}

const STORAGE_KEY = 'minibank.session';

function restoreSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly session = signal<Session | null>(restoreSession());
  readonly user = computed(() => {
    const session = this.session();
    return session ? { email: session.email, displayName: session.displayName } : null;
  });

  token(): string | null {
    return this.session()?.token ?? null;
  }

  login(email: string, password: string): Observable<Session> {
    return this.http
      .post<Session>('/api/auth/login', { email, password })
      .pipe(tap(session => this.store(session)));
  }

  register(email: string, password: string, displayName: string): Observable<unknown> {
    return this.http.post('/api/auth/register', { email, password, displayName });
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
  }

  private store(session: Session): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    this.session.set(session);
  }
}
