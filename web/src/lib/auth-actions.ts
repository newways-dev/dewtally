'use server'

import { headers } from 'next/headers'
import { redirect } from 'next/navigation'

import { auth } from '@/lib/auth'
import { env } from '@/lib/env'
import { getAccessToken } from '@/lib/session'

/**
 * Logs out of dewtally and of Keycloak. Without the Keycloak part, the next "login" would succeed without
 * a password, and switching from Anna to Ben would be impossible.
 */
export async function logout() {
  const token = await getAccessToken()
  await auth.api.signOut({
    headers: await headers(),
    body: { disableRedirect: true },
  })

  const url = new URL(`${env.keycloakIssuer}/protocol/openid-connect/logout`)
  url.searchParams.set('client_id', env.keycloakClientId)
  url.searchParams.set('post_logout_redirect_uri', `${env.appUrl}/login`)
  if (token?.idToken) {
    // Tells Keycloak which session to end, so it does not ask "Do you want to log out?"
    url.searchParams.set('id_token_hint', token.idToken)
  }
  redirect(url.toString())
}
