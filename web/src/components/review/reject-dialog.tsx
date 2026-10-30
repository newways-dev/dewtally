'use client'

import { CircleX } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useState } from 'react'

import { useReviewAction } from '@/components/review/use-review-action'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import { rejectInvoice } from '@/lib/review-actions'

// A rejection goes back to the supplier, so it always needs a reason.
export function RejectDialog({ invoiceId }: { invoiceId: string }) {
  const t = useTranslations('review')
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const { pending, run } = useReviewAction()

  function change(next: boolean) {
    setOpen(next)
    if (!next) {
      setReason('')
    }
  }

  function submit() {
    run(
      () => rejectInvoice(invoiceId, reason.trim()),
      t('rejected'),
      () => change(false),
    )
  }

  return (
    <Dialog open={open} onOpenChange={change}>
      <DialogTrigger render={<Button variant='outline' />}>
        <CircleX aria-hidden='true' />
        {t('reject')}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('rejectTitle')}</DialogTitle>
          <DialogDescription>{t('rejectText')}</DialogDescription>
        </DialogHeader>
        <Field>
          <FieldLabel htmlFor='reject-reason'>{t('reason')}</FieldLabel>
          <Textarea
            id='reject-reason'
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            maxLength={2000}
            required
          />
          <FieldDescription>{t('reasonHint')}</FieldDescription>
        </Field>
        <DialogFooter>
          <DialogClose render={<Button variant='outline' />}>
            {t('cancel')}
          </DialogClose>
          <Button
            variant='destructive'
            onClick={submit}
            disabled={pending || reason.trim() === ''}
          >
            {pending && <Spinner />}
            {t('rejectConfirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
