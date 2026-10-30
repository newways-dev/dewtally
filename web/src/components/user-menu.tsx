'use client'

import { ChevronsUpDown, LogOut } from 'lucide-react'

import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from '@/components/ui/sidebar'
import { logout } from '@/lib/auth-actions'

// Who is logged in, at the bottom of the sidebar; the menu holds the logout.
export function UserMenu({
  name,
  role,
  signOutLabel,
}: {
  name: string
  role: string
  signOutLabel: string
}) {
  const { isMobile } = useSidebar()
  const initials = name
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger render={<SidebarMenuButton size='lg' />}>
            <Avatar>
              <AvatarFallback className='bg-brand-soft text-[13px] font-semibold text-brand-soft-foreground'>
                {initials}
              </AvatarFallback>
            </Avatar>
            <span className='grid flex-1 text-left leading-tight'>
              <span className='truncate font-medium'>{name}</span>
              <span className='truncate text-xs text-muted-foreground'>
                {role}
              </span>
            </span>
            <ChevronsUpDown className='ml-auto' aria-hidden='true' />
          </DropdownMenuTrigger>
          <DropdownMenuContent
            side={isMobile ? 'bottom' : 'right'}
            align='end'
            sideOffset={8}
            className='min-w-56'
          >
            <DropdownMenuGroup>
              <DropdownMenuLabel>
                <span className='block text-sm font-medium text-foreground'>
                  {name}
                </span>
                <span className='block font-normal'>{role}</span>
              </DropdownMenuLabel>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={() => logout()}>
              <LogOut aria-hidden='true' />
              {signOutLabel}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
