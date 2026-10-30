'use server'

import { revalidatePath } from 'next/cache'
import { unstable_rethrow } from 'next/navigation'

import { ApiError, apiRequest } from '@/lib/api'
import type { ActionResult } from '@/lib/review-actions'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const IBAN = /^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$/

const normalizeIban = (value: string) => value.replace(/\s/g, '').toUpperCase()

// Same pattern as the review actions: the API checks role and rules, the answer comes back as a plain result.
async function send(path: string, body?: unknown): Promise<ActionResult> {
  try {
    await apiRequest(`/api/suppliers${path}`, {
      method: 'POST',
      headers:
        body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (error) {
    unstable_rethrow(error)
    if (error instanceof ApiError) {
      return { ok: false, status: error.status, detail: error.detail }
    }
    console.error('Supplier action failed', error)
    return { ok: false, status: 503 }
  }
  revalidatePath('/suppliers')
  return { ok: true }
}

export async function createSupplier(input: {
  name: string
  vatId?: string
  iban?: string
}): Promise<ActionResult> {
  return send('', { name: input.name, vatId: input.vatId, iban: input.iban })
}

export async function addSupplierIban(
  id: string,
  iban: string,
): Promise<ActionResult> {
  if (!UUID.test(id)) {
    return { ok: false, status: 404 }
  }
  return send(`/${id}/ibans`, { iban })
}

export async function verifySupplierIban(
  id: string,
  iban: string,
): Promise<ActionResult> {
  const clean = normalizeIban(iban)
  // Both values become part of the API path: only a real id and an IBAN-shaped value are allowed
  if (!UUID.test(id) || !IBAN.test(clean)) {
    return { ok: false, status: 404 }
  }
  return send(`/${id}/ibans/${clean}/verify`)
}

export async function blockSupplier(id: string): Promise<ActionResult> {
  return UUID.test(id) ? send(`/${id}/block`) : { ok: false, status: 404 }
}

export async function unblockSupplier(id: string): Promise<ActionResult> {
  return UUID.test(id) ? send(`/${id}/unblock`) : { ok: false, status: 404 }
}
