// Languages of the UI. The choice is kept in a cookie: dewtally is an app behind a login,
// so there is no need for /de/... URLs for search engines.
export const locales = ['en', 'de'] as const
export type Locale = (typeof locales)[number]

export const defaultLocale: Locale = 'en'
export const localeCookie = 'NEXT_LOCALE'

export function isLocale(value: string | undefined): value is Locale {
  return locales.includes(value as Locale)
}
