import type { Locale } from '@/i18n/config'
import type messages from '../messages/en.json'

// Makes next-intl type-safe: a typo in a message key is a build error, not an empty spot on the screen.
// The English file is the reference; the German one must have the same keys.
declare module 'next-intl' {
  interface AppConfig {
    Locale: Locale
    Messages: typeof messages
  }
}
