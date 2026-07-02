import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Account, LedgerEntry, OpenAccountRequest, Page, Transfer, TransferRequest } from './models';

@Injectable({ providedIn: 'root' })
export class BankApi {
  private readonly http = inject(HttpClient);

  listAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>('/api/accounts');
  }

  getAccount(id: string): Observable<Account> {
    return this.http.get<Account>(`/api/accounts/${id}`);
  }

  openAccount(request: OpenAccountRequest): Observable<Account> {
    return this.http.post<Account>('/api/accounts', request);
  }

  deposit(accountId: string, amount: number): Observable<Account> {
    return this.http.post<Account>(`/api/accounts/${accountId}/deposits`, { amount });
  }

  ledger(accountId: string, page = 0, size = 20): Observable<Page<LedgerEntry>> {
    return this.http.get<Page<LedgerEntry>>(
      `/api/accounts/${accountId}/ledger?page=${page}&size=${size}`);
  }

  transfer(idempotencyKey: string, request: TransferRequest): Observable<Transfer> {
    return this.http.post<Transfer>('/api/transfers', request, {
      headers: { 'Idempotency-Key': idempotencyKey }
    });
  }

  statement(accountId: string, from: string, to: string, format: 'csv' | 'pdf'): Observable<Blob> {
    return this.http.get(
      `/api/accounts/${accountId}/statement?from=${from}&to=${to}&format=${format}`,
      { responseType: 'blob' });
  }
}
