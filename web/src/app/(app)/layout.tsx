import { AppSidebar } from '@/components/app-sidebar'

// Frame of every page behind the login: navigation on the left (on top on a phone), content on the right.
export default function AppLayout({ children }: LayoutProps<'/'>) {
  return (
    <div className='flex min-h-full flex-1 flex-wrap'>
      <AppSidebar />
      <main className='flex min-w-0 flex-[999_1_560px] flex-col gap-6 px-4 py-6 sm:px-8'>
        {children}
      </main>
    </div>
  )
}
