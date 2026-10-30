import { LogOut } from 'lucide-react'
import { getTranslations } from 'next-intl/server'

import { Button } from '@/components/ui/button'
import { logout } from '@/lib/auth-actions'
import type { CurrentUser } from '@/lib/session'

// Who is logged in, at the bottom of the navigation, with the logout button.
export async function UserMenu({ user }: { user: CurrentUser }) {
  const t = await getTranslations()
  const initials = user.name
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()
  const role = user.roles[0]

  return (
    <div className='mt-auto flex items-center gap-2.5 border-t pt-3'>
      <span
        aria-hidden='true'
        className='flex size-8 shrink-0 items-center justify-center rounded-full bg-brand-soft text-[13px] font-semibold text-brand-soft-foreground'
      >
        {initials}
      </span>
      <span className='flex min-w-0 flex-1 flex-col leading-tight'>
        <span className='truncate font-medium'>{user.name}</span>
        <span className='truncate text-xs text-muted-foreground'>
          {role ? t(`roles.${role}`) : t('roles.none')}
        </span>
      </span>
      <form action={logout}>
        <Button
          type='submit'
          variant='ghost'
          size='icon'
          aria-label={t('nav.signOut')}
          title={t('nav.signOut')}
        >
          <LogOut aria-hidden='true' />
        </Button>
      </form>
    </div>
  )
}
