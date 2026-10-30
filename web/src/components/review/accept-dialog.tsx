'use client'

import { CircleCheck, ShieldAlert } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useState } from 'react'

import { useReviewAction } from '@/components/review/use-review-action'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
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
import {
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from '@/components/ui/field'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import { acceptInvoice } from '@/lib/review-actions'

// Approving releases money: with a changed IBAN it needs an explicit confirmation and a note on how it was checked.
export function AcceptDialog({
  invoiceId,
  ibanRisk,
}: {
  invoiceId: string
  ibanRisk: boolean
}) {
  const t = useTranslations('review')
  const [open, setOpen] = useState(false)
  const [comment, setComment] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const { pending, run } = useReviewAction()

  const needsComment = ibanRisk && confirmed
  const canSubmit =
    !pending && (!ibanRisk || (confirmed && comment.trim() !== ''))

  function change(next: boolean) {
    setOpen(next)
    if (!next) {
      setComment('')
      setConfirmed(false)
    }
  }

  function submit() {
    run(
      () =>
        acceptInvoice(invoiceId, {
          comment: comment.trim() || undefined,
          ibanConfirmed: ibanRisk ? confirmed : undefined,
        }),
      t('accepted'),
      () => change(false),
    )
  }

  return (
    <Dialog open={open} onOpenChange={change}>
      <DialogTrigger render={<Button />}>
        <CircleCheck aria-hidden='true' />
        {t('accept')}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('acceptTitle')}</DialogTitle>
          <DialogDescription>{t('acceptText')}</DialogDescription>
        </DialogHeader>

        {ibanRisk && (
          <Alert className='border-status-review bg-status-review text-status-review-foreground'>
            <ShieldAlert aria-hidden='true' />
            <AlertTitle>{t('ibanTitle')}</AlertTitle>
            <AlertDescription className='text-current'>
              {t('ibanText')}
            </AlertDescription>
          </Alert>
        )}

        <FieldGroup>
          {ibanRisk && (
            <Field orientation='horizontal'>
              <Checkbox
                id='iban-confirmed'
                checked={confirmed}
                onCheckedChange={(checked) => setConfirmed(checked)}
              />
              <FieldLabel htmlFor='iban-confirmed' className='font-normal'>
                {t('ibanConfirm')}
              </FieldLabel>
            </Field>
          )}
          <Field>
            <FieldLabel htmlFor='accept-comment'>
              {needsComment ? t('ibanHow') : t('comment')}
            </FieldLabel>
            <Textarea
              id='accept-comment'
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              maxLength={2000}
            />
            {needsComment && (
              <FieldDescription>{t('ibanHowHint')}</FieldDescription>
            )}
          </Field>
        </FieldGroup>

        <DialogFooter>
          <DialogClose render={<Button variant='outline' />}>
            {t('cancel')}
          </DialogClose>
          <Button onClick={submit} disabled={!canSubmit}>
            {pending && <Spinner />}
            {t('acceptConfirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
