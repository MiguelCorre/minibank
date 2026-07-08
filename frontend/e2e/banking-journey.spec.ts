import { expect, test } from '@playwright/test';
import { openAccount, registerAndSignIn, uniqueEmail } from './helpers';

test('full journey: open accounts, deposit, transfer, check the ledger', async ({ page }) => {
  await registerAndSignIn(page, uniqueEmail(), 'Journey User');

  // a fresh customer starts with no accounts
  await expect(page.getByText('No accounts yet')).toBeVisible();

  await openAccount(page, 'Main Account');
  await openAccount(page, 'Savings Account');

  // deposit into the main account
  await page.getByRole('row', { name: /Main Account/ }).getByRole('link', { name: 'Details' }).click();
  await expect(page.getByRole('heading', { name: 'Main Account' })).toBeVisible();
  await page.getByLabel('Deposit amount').fill('500');
  await page.getByRole('button', { name: 'Deposit' }).click();
  await expect(page.locator('.balance')).toHaveText(/€500\.00/);

  // transfer to the savings account (options are picked by text, the list
  // is newest-first so indexes would be brittle)
  await page.getByRole('link', { name: 'Transfer' }).click();
  const fromValue = await page.getByLabel('From')
      .locator('option', { hasText: 'Main Account' }).getAttribute('value');
  const toValue = await page.getByLabel('To')
      .locator('option', { hasText: 'Savings Account' }).getAttribute('value');
  await page.getByLabel('From').selectOption(fromValue!);
  await page.getByLabel('To').selectOption(toValue!);
  await page.getByLabel('Amount').fill('150');
  await page.getByLabel(/Description/).fill('to savings');
  await page.getByRole('button', { name: 'Transfer', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Transfer completed' })).toBeVisible();
  await expect(page.getByText('€150.00')).toBeVisible();

  // the source account shows the debit and the new balance
  await page.getByRole('link', { name: 'Source account' }).click();
  await expect(page.locator('.balance')).toHaveText(/€350\.00/);
  await expect(page.getByRole('cell', { name: 'DEBIT' })).toBeVisible();

  // the destination account received the money
  await page.getByRole('link', { name: 'Accounts', exact: true }).click();
  await page.getByRole('row', { name: /Savings Account/ }).getByRole('link', { name: 'Details' }).click();
  await expect(page.locator('.balance')).toHaveText(/€150\.00/);
  await expect(page.getByRole('cell', { name: 'CREDIT' })).toBeVisible();
});

test('the idempotency key is shown before a transfer', async ({ page }) => {
  await registerAndSignIn(page, uniqueEmail());
  await page.getByRole('link', { name: 'Transfer' }).click();
  await expect(page.getByText('Idempotency-Key')).toBeVisible();
  await expect(page.getByText('retrying never debits twice')).toBeVisible();
});
