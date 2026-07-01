import { Component, effect, inject, input, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { BankApi } from '../../core/bank-api';
import { Account, LedgerEntry, problemMessage } from '../../core/models';

@Component({
  selector: 'app-account-detail-page',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink],
  templateUrl: './account-detail-page.html'
})
export class AccountDetailPage {
  private readonly api = inject(BankApi);
  private readonly fb = inject(FormBuilder);

  /** Route param, bound by withComponentInputBinding(). */
  readonly id = input.required<string>();

  readonly account = signal<Account | null>(null);
  readonly ledger = signal<LedgerEntry[]>([]);
  readonly error = signal<string | null>(null);
  readonly depositing = signal(false);

  readonly depositForm = this.fb.nonNullable.group({
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]]
  });

  constructor() {
    effect(() => this.load(this.id()));
  }

  load(id: string): void {
    this.api.getAccount(id).subscribe({
      next: account => this.account.set(account),
      error: err => this.error.set(problemMessage(err, 'Account not found'))
    });
    this.api.ledger(id).subscribe({
      next: entries => this.ledger.set(entries),
      error: () => this.ledger.set([])
    });
  }

  deposit(): void {
    const amount = this.depositForm.getRawValue().amount;
    if (this.depositForm.invalid || amount === null) {
      this.depositForm.markAllAsTouched();
      return;
    }
    this.depositing.set(true);
    this.api.deposit(this.id(), amount).subscribe({
      next: () => {
        this.depositing.set(false);
        this.depositForm.reset();
        this.error.set(null);
        this.load(this.id());
      },
      error: err => {
        this.depositing.set(false);
        this.error.set(problemMessage(err, 'Deposit failed'));
      }
    });
  }
}
