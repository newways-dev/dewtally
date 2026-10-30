import { FileQuestion } from 'lucide-react'
import Link from 'next/link'
import { getTranslations } from 'next-intl/server'

import { buttonVariants } from '@/components/ui/button'
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@/components/ui/empty'

// Unknown id, or an invoice of another company: both look the same, so nothing is revealed.
export default async function InvoiceNotFound() {
  const t = await getTranslations('detail')
  return (
    <Empty>
      <EmptyHeader>
        <EmptyMedia variant='icon'>
          <FileQuestion aria-hidden='true' />
        </EmptyMedia>
        <EmptyTitle>{t('notFound')}</EmptyTitle>
        <EmptyDescription>{t('notFoundText')}</EmptyDescription>
      </EmptyHeader>
      <EmptyContent>
        <Link
          href='/invoices'
          className={buttonVariants({ variant: 'outline' })}
        >
          {t('back')}
        </Link>
      </EmptyContent>
    </Empty>
  )
}
