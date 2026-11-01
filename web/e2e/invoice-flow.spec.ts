import { readFileSync } from 'node:fs'
import path from 'node:path'

import { expect, test } from '@playwright/test'

const SAMPLE = path.resolve('..', 'api', 'rechnung.xml')

// The API recognises a file it has seen before (SHA-256) and returns the old invoice.
// A unique invoice number makes every run upload a new file and get a new invoice.
function uniqueInvoice() {
  const xml = readFileSync(SAMPLE, 'utf8')
  const suffix = `E2E-${Date.now()}`
  const patterns = [
    // UBL: the first cbc:ID is the invoice number
    /(<cbc:ID>)([^<]+)(<\/cbc:ID>)/,
    // CII: the ID of the exchanged document
    /(<rsm:ExchangedDocument>\s*<ram:ID>)([^<]+)(<\/ram:ID>)/,
  ]
  for (const pattern of patterns) {
    if (pattern.test(xml)) {
      let number = ''
      const changed = xml.replace(
        pattern,
        (_match, open: string, value: string, close: string) => {
          number = `${value}-${suffix}`
          return `${open}${number}${close}`
        },
      )
      return {
        fileName: `e2e-${suffix}.xml`,
        buffer: Buffer.from(changed, 'utf8'),
        number,
      }
    }
  }
  throw new Error(`No invoice number found in ${SAMPLE}`)
}

// One story across two people: the clerk enters, the approver decides.
test.describe.configure({ mode: 'serial' })

let invoiceUrl = ''

test.describe('clerk', () => {
  test.use({ storageState: 'e2e/.auth/anna.json' })

  test('uploads an e-invoice and corrects it', async ({ page }) => {
    const invoice = uniqueInvoice()

    await page.goto('/upload')
    await page.locator('input[type="file"]').setInputFiles({
      name: invoice.fileName,
      mimeType: 'application/xml',
      buffer: invoice.buffer,
    })

    // Processed right away or in the background: the link appears in the list as soon as the invoice exists
    const row = page.getByRole('row').filter({ hasText: invoice.fileName })
    const open = row.getByRole('link', { name: 'Open invoice' })
    await expect(open).toBeVisible({ timeout: 30_000 })
    await open.click()

    await page.waitForURL(/\/invoices\/[0-9a-f-]{36}$/)
    invoiceUrl = page.url()
    await expect(page.getByText(invoice.number).first()).toBeVisible()

    // A clerk enters data, but never decides
    await expect(
      page.getByRole('button', { name: 'Approve', exact: true }),
    ).toHaveCount(0)

    await page.getByRole('button', { name: 'Correct data' }).click()
    await page.getByLabel('Buyer reference').fill('E2E-REF')
    await page.getByRole('button', { name: 'Save and check again' }).click()

    await expect(page.getByText('Changes saved and checked')).toBeVisible()
    await expect(page.getByText('anna corrected buyerReference')).toBeVisible()
  })
})

test.describe('approver', () => {
  test.use({ storageState: 'e2e/.auth/ben.json' })

  test('approves the corrected invoice', async ({ page }) => {
    expect(invoiceUrl, 'the clerk test must run first').not.toBe('')
    await page.goto(invoiceUrl)

    // An approver decides, but never changes the data
    await expect(
      page.getByRole('button', { name: 'Correct data' }),
    ).toHaveCount(0)

    await page.getByRole('button', { name: 'Approve', exact: true }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()

    // If the IBAN is not a verified one, approving needs an explicit confirmation and a note
    const confirm = dialog.getByRole('checkbox')
    if (await confirm.isVisible()) {
      await confirm.check()
      await dialog
        .getByRole('textbox')
        .fill('Called the supplier on the known phone number (E2E test)')
    }
    await dialog.getByRole('button', { name: 'Approve', exact: true }).click()

    await expect(page.getByText('Invoice approved for payment')).toBeVisible()
    await expect(
      page.getByText('ben approved the invoice for payment'),
    ).toBeVisible()
    // Decided: no more actions
    await expect(
      page.getByRole('button', { name: 'Approve', exact: true }),
    ).toHaveCount(0)
  })
})
