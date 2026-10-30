'use server'

import { revalidatePath } from 'next/cache'
import { unstable_rethrow } from 'next/navigation'

import { ApiError, apiRequest } from '@/lib/api'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export type ActionResult =
  | { ok: true }
  | { ok: false; status: number; detail?: string }

export type DecisionInput = { comment?: string; ibanConfirmed?: boolean }

// Amounts travel as text ("1234.56"): exact like BigDecimal, no floating point rounding on the way
export type CorrectionInput = {
  version: number
  data: {
    invoiceNumber: string | null
    issueDate: string | null
    dueDate: string | null
    currency: string | null
    supplierName: string | null
    supplierVatId: string | null
    buyerReference: string | null
    iban: string | null
    netAmount: string | null
    taxAmount: string | null
    grossAmount: string | null
    allowanceTotal: string | null
    chargeTotal: string | null
  }
  lines: {
    description: string | null
    quantity: string | null
    unitCode: string | null
    unitPrice: string | null
    netAmount: string | null
    taxRate: string | null
  }[]
}

// Server Actions run on the Next.js server with the user's token. The API checks role, status and rules;
// its answer is returned as a plain result, so the browser can show a fitting message.
async function send(
  id: string,
  method: 'POST' | 'PUT',
  action: string,
  body: unknown,
): Promise<ActionResult> {
  // The id comes from the browser: only a real UUID may become part of the API path
  if (!UUID.test(id)) {
    return { ok: false, status: 404 }
  }
  try {
    await apiRequest(`/api/invoices/${id}/${action}`, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
  } catch (error) {
    // Keeps Next.js redirects working, e.g. to /login when the session has ended
    unstable_rethrow(error)
    if (error instanceof ApiError) {
      return { ok: false, status: error.status, detail: error.detail }
    }
    console.error('Review action failed', error)
    return { ok: false, status: 503 }
  }
  // Show the new status, history and version right away
  revalidatePath(`/invoices/${id}`)
  revalidatePath('/invoices')
  return { ok: true }
}

export async function acceptInvoice(
  id: string,
  input: DecisionInput,
): Promise<ActionResult> {
  return send(id, 'POST', 'accept', {
    comment: input.comment,
    ibanConfirmed: input.ibanConfirmed,
  })
}

export async function rejectInvoice(
  id: string,
  comment: string,
): Promise<ActionResult> {
  return send(id, 'POST', 'reject', { comment })
}

export async function correctInvoice(
  id: string,
  input: CorrectionInput,
): Promise<ActionResult> {
  return send(id, 'PUT', 'data', input)
}
