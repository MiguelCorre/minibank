import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth';

function withBearer(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * Anonymous auth endpoints must NOT carry a bearer token: the resource
 * server authenticates any presented token before authorization, so a stale
 * access token would 401 the refresh call itself.
 */
const ANONYMOUS_AUTH_PATHS = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh', '/api/auth/logout'];

function isAnonymousAuthCall(url: string): boolean {
  return ANONYMOUS_AUTH_PATHS.some(path => url.startsWith(path));
}

/**
 * Attaches the access token to API calls; on 401 it refreshes the session
 * once (rotating the refresh token) and retries the original request.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const token = auth.accessToken();
  const request =
    token && req.url.startsWith('/api') && !isAnonymousAuthCall(req.url) ? withBearer(req, token) : req;

  return next(request).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401 || req.url.startsWith('/api/auth/') || !auth.session()) {
        return throwError(() => err);
      }
      return auth.refreshSession().pipe(
        switchMap(session => next(withBearer(req, session.accessToken))),
        catchError(refreshErr => {
          auth.logout();
          router.navigate(['/login']);
          return throwError(() => refreshErr);
        })
      );
    })
  );
};
