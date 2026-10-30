'use client'

import { useLocale } from 'next-intl'
import { useTransition } from 'react'

import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { setLocale } from '@/i18n/actions'
import { locales } from '@/i18n/config'

const names = { en: 'English', de: 'Deutsch' } as const

// EN | DE as a shadcn ToggleGroup. Base UI keeps the value as an array, even for a single choice.
export function LocaleSwitch({ label }: { label: string }) {
  const current = useLocale()
  const [pending, startTransition] = useTransition()

  return (
    <ToggleGroup
      aria-label={label}
      variant='outline'
      size='sm'
      spacing={0}
      value={[current]}
      disabled={pending}
      onValueChange={(value) => {
        const next = value[0]
        // Clicking the active language would clear the selection: ignore that
        if (typeof next === 'string' && next !== current) {
          startTransition(() => setLocale(next))
        }
      }}
    >
      {locales.map((locale) => (
        <ToggleGroupItem
          key={locale}
          value={locale}
          aria-label={names[locale]}
          className='min-w-10 uppercase'
        >
          {locale}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  )
}
