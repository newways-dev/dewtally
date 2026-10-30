import { getTranslations } from 'next-intl/server'

import { PageHeader } from '@/components/page-header'

export default async function SuppliersPage() {
  const t = await getTranslations('suppliers')

  return <PageHeader title={t('title')} subtitle={t('subtitle')} />
}
