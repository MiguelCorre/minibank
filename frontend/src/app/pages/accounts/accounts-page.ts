import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { BankApi } from '../../core/bank-api';
import { Account, problemMessage } from '../../core/models';

@Component({
  selector: 'app-accounts-page',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink],
  templateUrl: './accounts-page.html'
})
export class AccountsPage {
  private readonly api = inject(BankApi);
  private readonly fb = inject(FormBuilder);

  readonly accounts = signal<Account[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly opening = signal(false);

  readonly openForm = this.fb.nonNullable.group({
    holderName: ['', [Validators.required, Validators.maxLength(80)]],
    currency: ['EUR', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]]
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.listAccounts().subscribe({
      next: accounts => {
        this.accounts.set(accounts);
        this.loading.set(false);
        this.error.set(null);
      },
      error: err => {
        this.error.set(problemMessage(err, 'Could not load accounts — is the API running?'));
        this.loading.set(false);
      }
    });
  }

  open(): void {
    if (this.openForm.invalid) {
      this.openForm.markAllAsTouched();
      return;
    }
    this.opening.set(true);
    this.api.openAccount(this.openForm.getRawValue()).subscribe({
      next: () => {
        this.opening.set(false);
        this.openForm.reset({ holderName: '', currency: 'EUR' });
        this.load();
      },
      error: err => {
        this.opening.set(false);
        this.error.set(problemMessage(err, 'Could not open the account'));
      }
    });
  }
}
