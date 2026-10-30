import { cookies, headers } from 'next/headers'
import { getRequestConfig } from 'next-intl/server'

import { defaultLocale, isLocale, localeCookie, type Locale } from './config'

// Picks the language for each request: the user's choice first, then the browser's preference.
async function resolveLocale(): Promise<Locale> {
  const chosen = (await cookies()).get(localeCookie)?.value
  if (isLocale(chosen)) {
    return chosen
  }
  const accepted = (await headers()).get('accept-language') ?? ''
  // "de-DE,de;q=0.9,en;q=0.8": the first listed language is the preferred one
  const preferred = accepted.split(',')[0]?.trim().slice(0, 2).toLowerCase()
  return isLocale(preferred) ? preferred : defaultLocale
}

export default getRequestConfig(async () => {
  const locale = await resolveLocale()
  return {
    locale,
    messages: (await import(`../../messages/${locale}.json`)).default,
  }
})
