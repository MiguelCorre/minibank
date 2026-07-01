import { Routes } from '@angular/router';

import { AccountDetailPage } from './pages/account-detail/account-detail-page';
import { AccountsPage } from './pages/accounts/accounts-page';
import { TransferPage } from './pages/transfer/transfer-page';

export const routes: Routes = [
  { path: '', component: AccountsPage, title: 'minibank — Accounts' },
  { path: 'accounts/:id', component: AccountDetailPage, title: 'minibank — Account' },
  { path: 'transfer', component: TransferPage, title: 'minibank — Transfer' },
  { path: '**', redirectTo: '' }
];
