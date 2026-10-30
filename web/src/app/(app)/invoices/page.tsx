import { Plus } from 'lucide-react'
import Link from 'next/link'
import { getTranslations } from 'next-intl/server'

import { PageHeader } from '@/components/page-header'
import { buttonVariants } from '@/components/ui/button'

export default async function InvoicesPage() {
  const t = await getTranslations('invoices')

  return (
    <PageHeader
      title={t('title')}
      subtitle={t('subtitle')}
      actions={
        // Navigation is a link, styled like the primary button
        <Link href='/upload' className={buttonVariants()}>
          <Plus aria-hidden='true' />
          {t('upload')}
        </Link>
      }
    />
  )
}
