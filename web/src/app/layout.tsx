import type { Metadata } from 'next'
import { Geist, Geist_Mono } from 'next/font/google'
import { NextIntlClientProvider } from 'next-intl'
import { getLocale } from 'next-intl/server'
import './globals.css'
import { ThemeProvider } from '@/components/theme-provider'
import { cn } from '@/lib/utils'

const geistSans = Geist({
  variable: '--font-geist-sans',
  subsets: ['latin'],
})

const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
})

export const metadata: Metadata = {
  title: 'dewtally',
  description: 'Incoming invoices and e-invoices, checked before anyone pays.',
}

export default async function RootLayout({ children }: LayoutProps<'/'>) {
  const locale = await getLocale()

  return (
    // lang tells screen readers and the browser's spell check which language the page is in.
    // suppressHydrationWarning: next-themes sets the "dark" class before React starts, on purpose.
    <html
      lang={locale}
      className={cn(
        'h-full antialiased',
        geistSans.variable,
        geistMono.variable,
      )}
      suppressHydrationWarning
    >
      <body className='min-h-full flex flex-col'>
        <NextIntlClientProvider>
          <ThemeProvider
            attribute='class'
            defaultTheme='system'
            enableSystem
            disableTransitionOnChange
          >
            {children}
          </ThemeProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  )
}
