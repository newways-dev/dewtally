import { Check } from 'lucide-react'
import { redirect } from 'next/navigation'
import { getTranslations } from 'next-intl/server'

import { LocaleSwitch } from '@/components/locale-switch'
import { Logo } from '@/components/logo'
import { SignInButton } from '@/components/sign-in-button'
import { ThemeToggle } from '@/components/theme-toggle'
import { getCurrentUser } from '@/lib/session'

const points = ['rules', 'ai', 'fourEyes'] as const
const demoUsers = [
  { user: 'anna', role: 'clerkDemo' },
  { user: 'ben', role: 'approverDemo' },
  { user: 'carla', role: 'adminDemo' },
  { user: 'otto', role: 'clerkOther' },
] as const

export default async function LoginPage({ searchParams }: PageProps<'/login'>) {
  if (await getCurrentUser()) {
    redirect('/invoices')
  }
  const t = await getTranslations('login')
  const common = await getTranslations('common')
  const { error } = await searchParams

  return (
    <div className='flex min-h-full flex-1 flex-wrap'>
      <section className='flex flex-[1_1_520px] flex-col justify-between gap-12 border-border bg-card px-6 py-10 sm:px-14 md:border-r'>
        <Logo />
        <div className='flex max-w-lg flex-col gap-7'>
          <h1 className='text-4xl leading-tight font-semibold tracking-tight'>
            {t('headline')}
          </h1>
          <ul className='flex flex-col gap-4'>
            {points.map((point) => (
              <li key={point} className='flex gap-3'>
                <span className='mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-md bg-brand-soft text-brand-soft-foreground'>
                  <Check className='size-3.5' aria-hidden='true' />
                </span>
                <span>
                  <span className='block font-medium'>
                    {t(`points.${point}.title`)}
                  </span>
                  <span className='text-muted-foreground'>
                    {t(`points.${point}.text`)}
                  </span>
                </span>
              </li>
            ))}
          </ul>
        </div>
        <p className='text-xs text-subtle'>{t('footer')}</p>
      </section>

      <main className='flex flex-[1_1_440px] flex-col px-6 py-6 sm:px-8'>
        <div className='flex justify-end gap-2'>
          <LocaleSwitch label={common('language')} />
          <ThemeToggle label={common('themeToggle')} />
        </div>
        <div className='flex flex-1 items-center justify-center py-8'>
          <div className='flex w-full max-w-sm flex-col gap-6'>
            <div>
              <h2 className='text-2xl font-semibold tracking-tight'>
                {t('title')}
              </h2>
              <p className='mt-1 text-muted-foreground'>{t('text')}</p>
            </div>
            {error && (
              <p
                role='alert'
                className='rounded-lg bg-status-invalid px-3 py-2 text-sm text-status-invalid-foreground'
              >
                {t('failed')}
              </p>
            )}
            <SignInButton label={t('continue')} />
            <p className='text-center text-xs text-subtle'>
              {t('redirectNote')}
            </p>
            <div className='flex flex-col gap-2 rounded-xl border border-dashed bg-card px-4 py-3.5'>
              <span className='text-sm font-medium'>{t('demoTitle')}</span>
              <dl className='grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm'>
                {demoUsers.map(({ user, role }) => (
                  <div key={user} className='contents'>
                    <dt className='font-mono'>{user}</dt>
                    <dd className='text-muted-foreground'>
                      {t(`demoRoles.${role}`)}
                    </dd>
                  </div>
                ))}
              </dl>
              <span className='text-xs text-subtle'>{t('demoHint')}</span>
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}
