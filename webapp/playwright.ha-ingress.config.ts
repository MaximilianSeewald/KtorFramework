import { defineConfig, devices } from '@playwright/test';

// noinspection JSUnusedGlobalSymbols
export default defineConfig({
  testDir: './tests/ha-ingress',
  timeout: 60_000,
  expect: {
    timeout: 10_000,
  },
  fullyParallel: false,
  workers: 1,
  reporter: process.env['CI'] ? 'dot' : 'list',
  use: {
    ...devices['Desktop Chrome'],
    baseURL: 'http://127.0.0.1',
    trace: 'retain-on-failure',
  },
});
