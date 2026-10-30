'use client'

import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useTransition } from 'react'
import { toast } from 'sonner'

import type { ActionResult } from '@/lib/review-actions'

// Runs a review action and turns the API's answer into a toast that says what to do next.
export function useReviewAction() {
  const t = useTranslations('review')
  const router = useRouter()
  const [pending, startTransition] = useTransition()

  function run(
    action: () => Promise<ActionResult>,
    success: string,
    onSuccess?: () => void,
  ) {
    startTransition(async () => {
      const result = await action()
      if (result.ok) {
        toast.success(success)
        onSuccess?.()
        return
      }
      switch (result.status) {
        case 400:
          toast.error(t('errors.invalid'), { description: result.detail })
          break
        case 403:
          toast.error(t('errors.forbidden'))
          break
        case 404:
          toast.error(t('errors.notFound'))
          break
        case 409:
          // Wrong status, unconfirmed IBAN or a parallel change: the API's text says which
          toast.error(t('errors.conflict'), {
            description: result.detail,
            action: { label: t('reload'), onClick: () => router.refresh() },
          })
          break
        default:
          toast.error(t('errors.failed'))
      }
    })
  }

  return { pending, run }
}
