import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Account, LedgerEntry, OpenAccountRequest, Transfer, TransferRequest } from './models';

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

  ledger(accountId: string): Observable<LedgerEntry[]> {
    return this.http.get<LedgerEntry[]>(`/api/accounts/${accountId}/ledger`);
  }

  transfer(idempotencyKey: string, request: TransferRequest): Observable<Transfer> {
    return this.http.post<Transfer>('/api/transfers', request, {
      headers: { 'Idempotency-Key': idempotencyKey }
    });
  }
}
