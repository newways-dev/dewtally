import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests against the local stack: Docker (Postgres, Keycloak, RabbitMQ) and the API must be running.
 * The web app is started here if it is not running yet.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // The flow builds on itself (upload, then correct, then approve): one step after another
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:3000',
    // Texts in the tests are English
    locale: 'en-US',
    timezoneId: 'Europe/Berlin',
    // On failure: a recording of every step to open with "pnpm exec playwright show-trace"
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'setup', testMatch: /.*\.setup\.ts/ },
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      dependencies: ['setup'],
    },
  ],
  webServer: {
    command: 'pnpm dev',
    url: 'http://localhost:3000/login',
    reuseExistingServer: true,
    timeout: 120_000,
  },
})
