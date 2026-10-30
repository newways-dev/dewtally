'use client'

import { useLocale } from 'next-intl'
import { useTransition } from 'react'

import { locales } from '@/i18n/config'
import { setLocale } from '@/i18n/actions'
import { cn } from '@/lib/utils'

// EN | DE segmented switch, like in the design.
export function LocaleSwitch({ label }: { label: string }) {
  const current = useLocale()
  const [pending, startTransition] = useTransition()

  return (
    <div
      role='group'
      aria-label={label}
      className='inline-flex rounded-lg border bg-secondary p-0.5'
    >
      {locales.map((locale) => {
        const active = locale === current
        return (
          <button
            key={locale}
            type='button'
            aria-pressed={active}
            disabled={pending}
            onClick={() => startTransition(() => setLocale(locale))}
            className={cn(
              'min-w-11 rounded-md px-2 py-1.5 text-sm font-medium uppercase transition-colors',
              active
                ? 'bg-card text-foreground shadow-sm'
                : 'text-muted-foreground hover:text-foreground',
            )}
          >
            {locale}
          </button>
        )
      })}
    </div>
  )
}
