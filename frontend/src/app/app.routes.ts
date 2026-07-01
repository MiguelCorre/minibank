import { Routes } from '@angular/router';

import { authGuard } from './core/auth-guard';
import { AccountDetailPage } from './pages/account-detail/account-detail-page';
import { AccountsPage } from './pages/accounts/accounts-page';
import { LoginPage } from './pages/login/login-page';
import { TransferPage } from './pages/transfer/transfer-page';

export const routes: Routes = [
  { path: 'login', component: LoginPage, title: 'minibank — Sign in' },
  { path: '', component: AccountsPage, canActivate: [authGuard], title: 'minibank — Accounts' },
  { path: 'accounts/:id', component: AccountDetailPage, canActivate: [authGuard], title: 'minibank — Account' },
  { path: 'transfer', component: TransferPage, canActivate: [authGuard], title: 'minibank — Transfer' },
  { path: '**', redirectTo: '' }
];
