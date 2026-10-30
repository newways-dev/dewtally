import { getTranslations } from 'next-intl/server'

import { PageHeader } from '@/components/page-header'

export default async function UploadPage() {
  const t = await getTranslations('upload')

  return <PageHeader title={t('title')} subtitle={t('subtitle')} />
}
