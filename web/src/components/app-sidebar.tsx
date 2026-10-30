'use client'

import { Building2, FileText, Upload } from 'lucide-react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useTranslations } from 'next-intl'

import { Logo } from '@/components/logo'
import { cn } from '@/lib/utils'

const items = [
  { href: '/invoices', key: 'invoices', icon: FileText },
  { href: '/upload', key: 'upload', icon: Upload },
  { href: '/suppliers', key: 'suppliers', icon: Building2 },
] as const

// Main navigation. A client component only because it highlights the current page.
export function AppSidebar() {
  const t = useTranslations('nav')
  const pathname = usePathname()

  return (
    <nav
      aria-label={t('main')}
      className='flex flex-[1_1_232px] flex-col gap-6 border-sidebar-border bg-sidebar px-4 py-5 text-sidebar-foreground md:max-w-[260px] md:border-r'
    >
      <Link href='/invoices' className='rounded-lg px-2 py-1'>
        <Logo />
      </Link>

      <ul className='flex flex-col gap-0.5'>
        {items.map(({ href, key, icon: Icon }) => {
          const active = pathname === href || pathname.startsWith(`${href}/`)
          return (
            <li key={href}>
              <Link
                href={href}
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'flex min-h-10 items-center gap-2.5 rounded-lg px-2.5 py-2 transition-colors',
                  active
                    ? 'bg-sidebar-accent font-medium text-sidebar-accent-foreground'
                    : 'text-muted-foreground hover:bg-accent hover:text-foreground',
                )}
              >
                <Icon className='size-[18px]' aria-hidden='true' />
                {t(key)}
              </Link>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
