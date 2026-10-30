import 'server-only'

import { redirect } from 'next/navigation'

import { env } from '@/lib/env'
import { getAccessToken } from '@/lib/session'

/** Error answer of the API in RFC 9457 format (application/problem+json). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly title: string,
    readonly detail?: string,
  ) {
    super(detail ?? title)
  }
}

/**
 * Calls the dewtally API from the Next.js server with the user's token.
 * The browser never talks to the API directly and never sees the token.
 */
export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const token = await getAccessToken()
  if (!token) {
    redirect('/login')
  }

  const response = await fetch(`${env.apiUrl}${path}`, {
    ...init,
    headers: { ...init.headers, Authorization: `Bearer ${token.accessToken}` },
    // Every company sees its own data: never share a cached answer between users
    cache: 'no-store',
  })

  if (response.status === 401) {
    // Token no longer accepted (for example, the Keycloak session ended): log in again
    redirect('/login')
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    throw new ApiError(
      response.status,
      problem.title ?? response.statusText,
      problem.detail,
    )
  }
  return (await response.json()) as T
}
