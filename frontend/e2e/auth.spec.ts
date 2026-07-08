import { expect, test } from '@playwright/test';
import { PASSWORD, registerAndSignIn, uniqueEmail } from './helpers';

test('anonymous visitors are redirected to the login page', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: /Welcome to mini/ })).toBeVisible();
});

test('wrong credentials show an error and stay on the login page', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill(uniqueEmail());
  await page.getByLabel('Password').fill('definitely-wrong');
  await page.getByRole('button', { name: 'Sign in' }).last().click();
  await expect(page.getByText('Invalid email or password')).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
});

test('sign out ends the session and protects the app again', async ({ page }) => {
  await registerAndSignIn(page, uniqueEmail());
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login$/);

  await page.goto('/transfer');
  await expect(page).toHaveURL(/\/login$/);
});

test('registered users can sign back in', async ({ page }) => {
  const email = uniqueEmail();
  await registerAndSignIn(page, email, 'Returning User');
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login$/);
  // hard navigation: a fresh document sidesteps the SPA teardown races
  await page.goto('/login');

  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await expect(page.getByLabel('Email')).toHaveValue(email);
  await page.locator('form').getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Accounts' })).toBeVisible();
  await expect(page.getByText('Returning User')).toBeVisible();
});
