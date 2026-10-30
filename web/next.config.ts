import type { NextConfig } from 'next'
import createNextIntlPlugin from 'next-intl/plugin'

const nextConfig: NextConfig = {
  // cacheComponents is off on purpose: every page of dewtally depends on the logged-in user and their company,
  // and the language comes from a cookie in the root layout, so there is nothing to prerender.
  turbopack: {
    rules: {
      '*.css': {
        loaders: ['@tailwindcss/turbopack'],
        as: '*.css',
      },
    },
  },
}

// Connects next-intl; it finds the request configuration in src/i18n/request.ts
const withNextIntl = createNextIntlPlugin()

export default withNextIntl(nextConfig)
