import { redirect } from 'next/navigation'

import { AppSidebar } from '@/components/app-sidebar'
import { UserMenu } from '@/components/user-menu'
import { getCurrentUser } from '@/lib/session'

// Frame of every page behind the login. Without a session, nothing here is rendered: straight to /login.
// Each data request checks the token again (lib/api.ts), so an expired session is caught there too.
export default async function AppLayout({ children }: LayoutProps<'/'>) {
  const user = await getCurrentUser()
  if (!user) {
    redirect('/login')
  }

  return (
    <div className='flex min-h-full flex-1 flex-wrap'>
      <AppSidebar footer={<UserMenu user={user} />} />
      <main className='flex min-w-0 flex-[999_1_560px] flex-col gap-6 px-4 py-6 sm:px-8'>
        {children}
      </main>
    </div>
  )
}
