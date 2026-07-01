import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { BankApi } from '../../core/bank-api';
import { Account, Transfer, problemMessage } from '../../core/models';

@Component({
  selector: 'app-transfer-page',
  imports: [CurrencyPipe, ReactiveFormsModule, RouterLink],
  templateUrl: './transfer-page.html'
})
export class TransferPage {
  private readonly api = inject(BankApi);
  private readonly fb = inject(FormBuilder);

  readonly accounts = signal<Account[]>([]);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly completed = signal<Transfer | null>(null);

  /**
   * One key per business attempt: kept across retries of a failed request
   * (safe to resubmit — the backend deduplicates), renewed after success.
   */
  readonly idempotencyKey = signal(crypto.randomUUID());

  readonly form = this.fb.nonNullable.group({
    fromAccountId: ['', Validators.required],
    toAccountId: ['', Validators.required],
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    description: ['']
  });

  constructor() {
    this.api.listAccounts().subscribe({
      next: accounts => this.accounts.set(accounts),
      error: err => this.error.set(problemMessage(err, 'Could not load accounts'))
    });
  }

  submit(): void {
    const value = this.form.getRawValue();
    if (this.form.invalid || value.amount === null) {
      this.form.markAllAsTouched();
      return;
    }
    if (value.fromAccountId === value.toAccountId) {
      this.error.set('Source and destination accounts must differ');
      return;
    }
    this.submitting.set(true);
    this.api
      .transfer(this.idempotencyKey(), {
        fromAccountId: value.fromAccountId,
        toAccountId: value.toAccountId,
        amount: value.amount,
        description: value.description || undefined
      })
      .subscribe({
        next: transfer => {
          this.submitting.set(false);
          this.completed.set(transfer);
          this.error.set(null);
          this.form.reset({ fromAccountId: '', toAccountId: '', amount: null, description: '' });
          this.idempotencyKey.set(crypto.randomUUID());
        },
        error: err => {
          this.submitting.set(false);
          this.error.set(problemMessage(err, 'Transfer failed — retrying is safe, the key is unchanged'));
        }
      });
  }

  newTransfer(): void {
    this.completed.set(null);
  }

  label(account: Account): string {
    return `${account.holderName} — ${account.accountNumber.slice(0, 8)}… (${account.balance} ${account.currency})`;
  }
}
