import type { ReactNode } from 'react'
import { getTranslations } from 'next-intl/server'

import { LocaleSwitch } from '@/components/locale-switch'
import { ThemeToggle } from '@/components/theme-toggle'

// Title of a page with its own actions on the right, plus language and theme switches.
export async function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string
  subtitle?: string
  actions?: ReactNode
}) {
  const t = await getTranslations('common')

  return (
    <header className='flex flex-wrap items-center gap-x-4 gap-y-3'>
      <div className='min-w-0 flex-[1_1_240px]'>
        <h1 className='text-2xl font-semibold tracking-tight'>{title}</h1>
        {subtitle && <p className='mt-0.5 text-muted-foreground'>{subtitle}</p>}
      </div>
      <div className='flex flex-wrap items-center gap-2'>
        <LocaleSwitch label={t('language')} />
        <ThemeToggle label={t('themeToggle')} />
        {actions}
      </div>
    </header>
  )
}
