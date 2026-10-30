'use client'

import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

// While documents are processed in the background, reload the server data every few seconds.
// Stops by itself as soon as nothing is waiting any more (the page then renders active=false).
export function AutoRefresh({
  active,
  seconds = 3,
}: {
  active: boolean
  seconds?: number
}) {
  const router = useRouter()
  useEffect(() => {
    if (!active) {
      return
    }
    const timer = setInterval(() => router.refresh(), seconds * 1000)
    return () => clearInterval(timer)
  }, [active, seconds, router])
  return null
}
