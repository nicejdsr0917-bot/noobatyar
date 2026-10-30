import { test, expect } from '@playwright/test';
import { fa } from '../lib/i18n/messages';

const SLUG = 'Noobatyar-E2E1';
const TOKEN = 'e2e-token';

test.describe('business profile', () => {
  test('renders the business name', async ({ page }) => {
    await page.goto(`/b/${SLUG}`);
    await expect(page.getByRole('heading', { name: 'کلینیک آزمون' }).first()).toBeVisible();
  });

  test('unknown business shows the not-available screen', async ({ page }) => {
    const res = await page.goto('/b/Noobatyar-NOPE');
    expect(res?.status()).toBe(404);
  });
});

test.describe('booking flow', () => {
  test('signed-out visitor logs in with a mocked OTP, picks a slot and reaches checkout', async ({ page }) => {
    // Booking needs an account: the page bounces to login and comes back.
    await page.goto(`/b/${SLUG}/book`);
    await page.waitForURL(/\/auth\/login/);
    await page.getByPlaceholder('09123456789').fill('09123456789');
    await page.locator('button.btn-primary').click();

    const boxes = page.locator('input[aria-label]');
    await expect(boxes.first()).toBeVisible();
    await boxes.first().fill('123456'); // paste-style spread, auto-submits

    await page.waitForURL(new RegExp(`/b/${SLUG}/book`));

    await page.locator('.time-slot').first().click();
    await expect(page.locator('.time-slot.selected')).toBeVisible();
    // The owner's service menu appears once a slot is picked.
    await expect(page.getByText('جرم‌گیری')).toBeVisible();
    await expect(page.getByText('ترمیم')).toBeVisible();
    await page.locator('button.btn-primary').click();

    await page.waitForURL(/\/checkout\/55/, { timeout: 15_000 });
    await expect(page.getByText(fa.paymentAndConfirmation).first()).toBeVisible();
  });
});

test.describe('payment result', () => {
  test('subscription success', async ({ page }) => {
    await page.goto('/home/payment-result?trackId=1&success=1');
    await expect(page.getByRole('heading', { name: fa.paymentSuccessful })).toBeVisible();
    await expect(page.getByText(fa.yourSubscriptionIsNowActive)).toBeVisible();
  });

  test('gateway reports failure', async ({ page }) => {
    await page.goto('/home/payment-result?trackId=1&success=2');
    await expect(page.getByRole('heading', { name: fa.paymentFailed })).toBeVisible();
  });

  test('cancelled by user never hits the API', async ({ page }) => {
    await page.goto('/home/payment-result?trackId=1&success=0');
    await expect(page.getByText(fa.paymentWasCancelledOrFailed)).toBeVisible();
  });

  test('missing trackId', async ({ page }) => {
    await page.goto('/home/payment-result');
    await expect(page.getByText(fa.paymentIdNotFound)).toBeVisible();
  });

  test('add-on pack success and failure', async ({ page }) => {
    await page.goto('/home/payment-result-addon?trackId=2&success=1');
    await expect(page.getByRole('heading', { name: fa.paymentSuccessful })).toBeVisible();
    await page.goto('/home/payment-result-addon?trackId=2&success=2');
    await expect(page.getByRole('heading', { name: fa.paymentFailed })).toBeVisible();
  });
});

test.describe('appointments list', () => {
  test('signed-in visitor sees their appointment', async ({ page }) => {
    await page.addInitScript((tok) => localStorage.setItem('visitor_token', tok), TOKEN);
    await page.goto('/appointments');
    await expect(page.getByText('کلینیک آزمون').first()).toBeVisible();
  });

  test('without a token it redirects to login', async ({ page }) => {
    await page.goto('/appointments');
    await page.waitForURL(/\/auth\/login/);
  });
});
