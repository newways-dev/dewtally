import { AcceptDialog } from '@/components/review/accept-dialog'
import { CorrectionSheet } from '@/components/review/correction-sheet'
import { RejectDialog } from '@/components/review/reject-dialog'
import type { components } from '@/lib/api-types'
import type { Role } from '@/lib/session'

type Invoice = components['schemas']['InvoiceDetailsResponse']

// The same rules as in the API: only open invoices can be changed or decided on.
const OPEN = ['VALID', 'INVALID', 'NEEDS_REVIEW']
const ACCEPTABLE = ['VALID', 'NEEDS_REVIEW']

// Buttons only for what the user may do. Hiding them is for convenience; the API checks role and status itself.
export function InvoiceActions({
  invoice,
  roles,
}: {
  invoice: Invoice
  roles: Role[]
}) {
  const status = invoice.status ?? ''
  if (!invoice.id || !OPEN.includes(status)) {
    return null
  }
  const canCorrect = roles.includes('clerk')
  const canDecide = roles.includes('approver')
  const ibanRisk = (invoice.issues ?? []).some(
    (issue) => issue.ruleId === 'DT-IBAN-CHANGED',
  )

  if (!canCorrect && !canDecide) {
    return null
  }
  return (
    <div className='flex flex-wrap justify-end gap-2'>
      {canCorrect && <CorrectionSheet invoice={invoice} />}
      {canDecide && <RejectDialog invoiceId={invoice.id} />}
      {canDecide && ACCEPTABLE.includes(status) && (
        <AcceptDialog invoiceId={invoice.id} ibanRisk={ibanRisk} />
      )}
    </div>
  )
}
