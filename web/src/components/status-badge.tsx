import { getTranslations } from 'next-intl/server'

import { Badge } from '@/components/ui/badge'
import type { components } from '@/lib/api-types'
import { cn } from '@/lib/utils'

// The generated type allows "undefined" (springdoc cannot know which record fields are never null);
// a badge is only drawn for a known status.
export type InvoiceStatus = NonNullable<
  components['schemas']['InvoiceSummaryResponse']['status']
>

const styles: Record<InvoiceStatus, string> = {
  RECEIVED: 'bg-status-rejected text-status-rejected-foreground',
  NEEDS_REVIEW: 'bg-status-review text-status-review-foreground',
  INVALID: 'bg-status-invalid text-status-invalid-foreground',
  VALID: 'bg-status-valid text-status-valid-foreground',
  ACCEPTED: 'bg-status-accepted text-status-accepted-foreground',
  REJECTED: 'bg-status-rejected text-status-rejected-foreground',
  EXPORTED: 'bg-status-accepted text-status-accepted-foreground',
}

// shadcn Badge in the status colours: always colour and word, so it does not depend on seeing colours.
export async function StatusBadge({
  status,
  className,
}: {
  status: InvoiceStatus
  className?: string
}) {
  const t = await getTranslations('status')
  return (
    <Badge
      className={cn(
        'gap-1.5 rounded-full border-transparent',
        styles[status],
        className,
      )}
    >
      <span aria-hidden='true' className='size-1.5 rounded-full bg-current' />
      {t(status)}
    </Badge>
  )
}
