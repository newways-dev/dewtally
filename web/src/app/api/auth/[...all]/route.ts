import { toNextJsHandler } from 'better-auth/next-js'

import { auth } from '@/lib/auth'

// All Better Auth endpoints: sign-in start, the callback from Keycloak, session, sign-out
export const { GET, POST } = toNextJsHandler(auth)
