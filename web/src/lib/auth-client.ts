import { createAuthClient } from 'better-auth/react'

// Browser side of Better Auth: only starts the login and the logout; it never handles tokens.
export const authClient = createAuthClient()
