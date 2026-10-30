import 'server-only'

import { betterAuth } from 'better-auth'
import { nextCookies } from 'better-auth/next-js'
import { genericOAuth, keycloak } from 'better-auth/plugins'

import { env } from '@/lib/env'

// Login through Keycloak (authorization code with PKCE), handled entirely on the Next.js server.
// No database: the session and the Keycloak tokens live in encrypted cookies (JWE).
// The browser never sees a token, so a script injected into the page cannot steal one.
export const auth = betterAuth({
  session: {
    cookieCache: {
      enabled: true,
      maxAge: 8 * 60 * 60, // one working day
      strategy: 'jwe',
      refreshCache: true, // extended while the user keeps working
    },
  },
  account: {
    // Keycloak's access and refresh tokens, kept server-readable only, to call the dewtally API
    storeAccountCookie: true,
  },
  plugins: [
    genericOAuth({
      config: [
        keycloak({
          clientId: env.keycloakClientId,
          clientSecret: env.keycloakClientSecret,
          issuer: env.keycloakIssuer,
          pkce: true,
        }),
      ],
    }),
    // Lets server actions set cookies; must be the last plugin
    nextCookies(),
  ],
})
