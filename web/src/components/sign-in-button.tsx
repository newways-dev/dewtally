'use client'

import { LockKeyhole } from 'lucide-react'
import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { authClient } from '@/lib/auth-client'

// Starts the login: the browser goes to Keycloak, enters the password there, and comes back to /invoices.
export function SignInButton({ label }: { label: string }) {
  const [pending, setPending] = useState(false)

  return (
    <Button
      size='lg'
      className='w-full'
      disabled={pending}
      onClick={async () => {
        setPending(true)
        await authClient.signIn.social({
          provider: 'keycloak',
          callbackURL: '/invoices',
          errorCallbackURL: '/login?error=login',
        })
      }}
    >
      <LockKeyhole aria-hidden='true' />
      {label}
    </Button>
  )
}
