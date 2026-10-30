'use client'

import { RotateCcw, ServerCrash } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { Button } from '@/components/ui/button'
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@/components/ui/empty'

// Shown instead of a page that failed to load (for example, the API is down). The menu stays usable.
export default function AppError({
  reset,
}: {
  error: Error & { digest?: string }
  reset: () => void
}) {
  const t = useTranslations('errors')
  return (
    <Empty>
      <EmptyHeader>
        <EmptyMedia variant='icon'>
          <ServerCrash aria-hidden='true' />
        </EmptyMedia>
        <EmptyTitle>{t('title')}</EmptyTitle>
        <EmptyDescription>{t('text')}</EmptyDescription>
      </EmptyHeader>
      <EmptyContent>
        <Button variant='outline' onClick={() => reset()}>
          <RotateCcw aria-hidden='true' />
          {t('retry')}
        </Button>
      </EmptyContent>
    </Empty>
  )
}
