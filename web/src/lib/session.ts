import 'server-only'

import { headers } from 'next/headers'
import { cache } from 'react'

import { auth } from '@/lib/auth'

export type Role = 'clerk' | 'approver' | 'admin'

export type CurrentUser = {
  name: string
  username: string
  tenantId: string
  roles: Role[]
}

const knownRoles: Role[] = ['admin', 'approver', 'clerk']

/**
 * The Keycloak access token of the logged-in user, refreshed by Better Auth when it has expired.
 * Null when nobody is logged in.
 */
export const getAccessToken = cache(
  async (): Promise<{ accessToken: string; idToken?: string } | null> => {
    try {
      const token = await auth.api.getAccessToken({
        body: { useAccountCookie: true },
        headers: await headers(),
      })
      return token?.accessToken
        ? { accessToken: token.accessToken, idToken: token.idToken }
        : null
    } catch {
      return null
    }
  },
)

// Reads the claims of a JWT. Not a security check: the token came straight from Keycloak and is only used
// for display here. The API verifies its signature on every request that touches data.
function claimsOf(token: string): Record<string, unknown> {
  const payload = token.split('.')[1] ?? ''
  return JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'))
}

/**
 * Who is logged in: name, company and roles, the same way the API sees them. Cached per request.
 */
export const getCurrentUser = cache(async (): Promise<CurrentUser | null> => {
  const session = await auth.api.getSession({ headers: await headers() })
  if (!session) {
    return null
  }
  const token = await getAccessToken()
  if (!token) {
    return null
  }
  const claims = claimsOf(token.accessToken)
  const realmRoles =
    (claims.realm_access as { roles?: string[] } | undefined)?.roles ?? []
  return {
    name: session.user.name || String(claims.preferred_username ?? ''),
    username: String(claims.preferred_username ?? ''),
    tenantId: String(claims.tenant_id ?? ''),
    roles: knownRoles.filter((role) => realmRoles.includes(role)),
  }
})
