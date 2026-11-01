import { test as setup } from '@playwright/test'

// Test users of the local Keycloak realm (password = user name). Never real accounts.
const users = ['anna', 'ben'] as const

for (const user of users) {
  setup(`log in as ${user}`, async ({ page }) => {
    await page.goto('/login')
    await page
      .getByRole('button', { name: 'Continue with company login' })
      .click()

    // Keycloak's own login form
    await page.locator('#username').fill(user)
    await page.locator('#password').fill(user)
    await page.locator('#kc-login').click()

    await page.waitForURL('**/invoices')
    // The session cookies are saved and reused by the tests; the folder is in .gitignore
    await page.context().storageState({ path: `e2e/.auth/${user}.json` })
  })
}
