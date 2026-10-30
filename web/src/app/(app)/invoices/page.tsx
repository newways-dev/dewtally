import { Plus } from 'lucide-react'
import Link from 'next/link'
import { getTranslations } from 'next-intl/server'

import { PageHeader } from '@/components/page-header'
import { buttonVariants } from '@/components/ui/button'
import { apiFetch } from '@/lib/api'
import type { components } from '@/lib/api-types'
import { cn } from '@/lib/utils'

// Generated from the API description: a renamed field in the backend breaks this build, not the screen
type InvoiceCounts = components['schemas']['InvoiceCountsResponse']

export default async function InvoicesPage() {
  const t = await getTranslations('invoices')
  // Temporary: proves the whole chain (session → token → API → Row-Level Security). The real list follows in step 4.
  const counts = await apiFetch<InvoiceCounts>('/api/invoices/counts')

  return (
    <>
      <PageHeader
        title={t('title')}
        subtitle={t('subtitle')}
        actions={
          // Navigation is a link, styled like the primary button: icon and text in one line
          <Link
            href='/upload'
            className={cn(
              buttonVariants(),
              'inline-flex items-center gap-1.5 whitespace-nowrap',
            )}
          >
            <Plus aria-hidden='true' />
            {t('upload')}
          </Link>
        }
      />
      <p className='text-muted-foreground'>
        {t('total')}:{' '}
        <span className='font-mono text-foreground'>{counts.total}</span>
      </p>
    </>
  )
}
