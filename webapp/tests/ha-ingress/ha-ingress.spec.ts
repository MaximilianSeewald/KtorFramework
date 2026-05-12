import { expect, test } from '@playwright/test';
import { startHaIngressApp } from './ha-ingress-harness';

test.describe.configure({ mode: 'serial' });

test.describe('Home Assistant ingress integration', () => {
  let app: Awaited<ReturnType<typeof startHaIngressApp>>;

  test.beforeAll(async () => {
    app = await startHaIngressApp();
  });

  test.afterAll(async () => {
    await app?.stop();
  });

  test('loads the HA app, auto-logins, and uses API plus websocket through ingress', async ({ page }) => {
    const apiRequests: string[] = [];
    const wsRequests: string[] = [];
    page.on('request', (request) => {
      const url = request.url();
      if (url.includes('/api/hassio_ingress/test-token/api/')) {
        apiRequests.push(url);
      }
    });
    page.on('websocket', (webSocket) => wsRequests.push(webSocket.url()));

    const rootResponse = await page.request.get(`${app.baseUrl}${app.ingressPath}`);
    expect(rootResponse.ok()).toBeTruthy();

    await page.goto(`${app.baseUrl}${app.ingressPath}`);

    await expect(page.getByRole('heading', { name: 'Shopping List' })).toBeVisible();
    await expect.poll(() => page.evaluate(() => localStorage.getItem('token'))).toBeTruthy();

    const token = await page.evaluate(() => localStorage.getItem('token'));
    const verifyResponse = await page.request.get(`${app.baseUrl}${app.ingressPath}api/verify`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    expect(verifyResponse.ok()).toBeTruthy();
    await expect.poll(() => apiRequests.some((url) => url.endsWith('/api/ha/session'))).toBeTruthy();
    await expect.poll(() => apiRequests.some((url) => url.endsWith('/api/shoppingList'))).toBeTruthy();
    await expect.poll(() => wsRequests.some((url) => url.includes('/api/hassio_ingress/test-token/api/shoppingListWS'))).toBeTruthy();

    const itemName = `Ingress Milk ${Date.now()}`;
    await page.getByPlaceholder('Item name').fill(itemName);
    await page.getByRole('button', { name: /add/i }).first().click();
    await expect(page.getByText(itemName)).toBeVisible();

    await page.goto(`${app.baseUrl}${app.ingressPath}#/recipeList`);
    await expect(page.getByRole('heading', { name: 'Recipes' })).toBeVisible();
  });
});
