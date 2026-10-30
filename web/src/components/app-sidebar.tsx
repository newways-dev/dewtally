'use client'

import { Building2, FileText, Upload } from 'lucide-react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useTranslations } from 'next-intl'
import type { ReactNode } from 'react'

import { LogoMark } from '@/components/logo'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
  useSidebar,
} from '@/components/ui/sidebar'

const items = [
  { href: '/invoices', key: 'invoices', icon: FileText },
  { href: '/upload', key: 'upload', icon: Upload },
  { href: '/suppliers', key: 'suppliers', icon: Building2 },
] as const

// shadcn Sidebar: collapses to icons on a desktop (Ctrl+B or the rail), slides in as a panel on a phone.
// The user menu at the bottom is passed in from the server.
export function AppSidebar({ footer }: { footer: ReactNode }) {
  const t = useTranslations('nav')
  const pathname = usePathname()
  const { setOpenMobile } = useSidebar()

  return (
    <Sidebar collapsible='icon' aria-label={t('main')}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size='lg'
              render={<Link href='/invoices' />}
              onClick={() => setOpenMobile(false)}
            >
              <LogoMark className='size-7! shrink-0' />
              <span className='text-[17px] font-semibold tracking-tight'>
                dewtally
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>
              {items.map(({ href, key, icon: Icon }) => {
                const active =
                  pathname === href || pathname.startsWith(`${href}/`)
                return (
                  <SidebarMenuItem key={href}>
                    <SidebarMenuButton
                      render={
                        <Link
                          href={href}
                          aria-current={active ? 'page' : undefined}
                        />
                      }
                      isActive={active}
                      tooltip={t(key)}
                      onClick={() => setOpenMobile(false)}
                    >
                      <Icon aria-hidden='true' />
                      <span>{t(key)}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                )
              })}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter>{footer}</SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}
