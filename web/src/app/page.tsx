import { getFormatter, getTranslations } from 'next-intl/server'

import { Button } from '@/components/ui/button'
import { LocaleSwitch } from '@/components/locale-switch'
import { ThemeToggle } from '@/components/theme-toggle'

// Temporary page to check colours, theme and languages; replaced by the app shell in step 2.5.
const statuses = [
  {
    key: 'NEEDS_REVIEW',
    className: 'bg-status-review text-status-review-foreground',
  },
  {
    key: 'INVALID',
    className: 'bg-status-invalid text-status-invalid-foreground',
  },
  { key: 'VALID', className: 'bg-status-valid text-status-valid-foreground' },
  {
    key: 'ACCEPTED',
    className: 'bg-status-accepted text-status-accepted-foreground',
  },
  {
    key: 'REJECTED',
    className: 'bg-status-rejected text-status-rejected-foreground',
  },
] as const

export default async function Home() {
  const t = await getTranslations()
  const format = await getFormatter()

  return (
    <main className='flex flex-1 flex-col gap-6 p-8'>
      <div className='flex flex-wrap items-start justify-between gap-4'>
        <div>
          <h1 className='text-2xl font-semibold tracking-tight'>
            {t('invoices.title')}
          </h1>
          <p className='text-muted-foreground'>{t('invoices.subtitle')}</p>
        </div>
        <div className='flex items-center gap-2'>
          <LocaleSwitch label={t('common.language')} />
          <ThemeToggle label={t('common.themeToggle')} />
        </div>
      </div>

      <div className='flex flex-wrap gap-2'>
        <Button>{t('actions.approve')}</Button>
        <Button variant='secondary'>{t('actions.correct')}</Button>
        <Button variant='outline'>{t('actions.download')}</Button>
        <Button variant='destructive'>{t('actions.reject')}</Button>
      </div>

      <div className='flex flex-wrap gap-2'>
        {statuses.map((status) => (
          <span
            key={status.key}
            className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ${status.className}`}
          >
            <span
              aria-hidden='true'
              className='size-1.5 rounded-full bg-current'
            />
            {t(`status.${status.key}`)}
          </span>
        ))}
      </div>

      <div className='w-fit rounded-xl border bg-card p-4'>
        <div className='text-sm text-muted-foreground'>
          {t('invoices.amountDue')}
        </div>
        <div className='font-mono text-3xl font-medium tracking-tight tabular-nums'>
          {format.number(4284, { style: 'currency', currency: 'EUR' })}
        </div>
        <div className='font-mono text-sm text-subtle'>
          DE02 1203 0000 0000 2020 51
        </div>
      </div>
    </main>
  )
}
