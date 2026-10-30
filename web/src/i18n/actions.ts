'use server'

import { cookies } from 'next/headers'

import { isLocale, localeCookie } from './config'

// Server action: stores the chosen language. Setting a cookie here makes Next.js render the page again,
// so the new language appears without a full reload.
export async function setLocale(locale: string) {
  if (!isLocale(locale)) {
    return
  }
  ;(await cookies()).set(localeCookie, locale, {
    path: '/',
    maxAge: 60 * 60 * 24 * 365,
    sameSite: 'lax',
  })
}
