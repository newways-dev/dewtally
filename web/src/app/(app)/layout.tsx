import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'
import { getTranslations } from 'next-intl/server'

import { AppSidebar } from '@/components/app-sidebar'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
import { UserMenu } from '@/components/user-menu'
import { getCurrentUser } from '@/lib/session'

// Frame of every page behind the login. Without a session, nothing here is rendered: straight to /login.
// Each data request checks the token again (lib/api.ts), so an expired session is caught there too.
export default async function AppLayout({ children }: LayoutProps<'/'>) {
  const user = await getCurrentUser()
  if (!user) {
    redirect('/login')
  }
  const t = await getTranslations()
  // The sidebar remembers whether it was collapsed (cookie set by shadcn's SidebarProvider)
  const sidebarOpen = (await cookies()).get('sidebar_state')?.value !== 'false'
  const role = user.roles[0]

  return (
    <SidebarProvider defaultOpen={sidebarOpen}>
      <AppSidebar
        footer={
          <UserMenu
            name={user.name}
            role={role ? t(`roles.${role}`) : t('roles.none')}
            signOutLabel={t('nav.signOut')}
          />
        }
      />
      <SidebarInset>
        <div className='flex min-w-0 flex-1 flex-col gap-6 px-4 py-6 sm:px-8'>
          {children}
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
