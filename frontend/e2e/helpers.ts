import { Page, expect } from '@playwright/test';

/** Every test registers a fresh user so runs are isolated and repeatable. */
export function uniqueEmail(): string {
  return `e2e-${Date.now()}-${Math.floor(Math.random() * 100000)}@test.dev`;
}

export const PASSWORD = 'e2e-password-123';

export async function registerAndSignIn(page: Page, email: string, displayName = 'E2E User'): Promise<void> {
  await page.goto('/login');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByLabel('Display name').fill(displayName);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Create account' }).last().click();
  await expect(page.getByRole('heading', { name: 'Accounts' })).toBeVisible();
}

export async function openAccount(page: Page, holderName: string): Promise<void> {
  // exact: the "← All accounts" back-link also contains "Accounts"
  await page.getByRole('link', { name: 'Accounts', exact: true }).click();
  await page.getByLabel('Holder name').fill(holderName);
  await page.getByRole('button', { name: 'Open account' }).click();
  await expect(page.getByRole('cell', { name: holderName })).toBeVisible();
}
