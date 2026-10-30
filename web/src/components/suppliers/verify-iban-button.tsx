'use client'

import { ShieldCheck } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useState } from 'react'

import { useReviewAction } from '@/components/review/use-review-action'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { verifySupplierIban } from '@/lib/supplier-actions'

// The second pair of eyes: confirming an IBAN is a statement that the account was checked with the supplier.
export function VerifyIbanButton({
  supplierId,
  iban,
}: {
  supplierId: string
  iban: string
}) {
  const t = useTranslations('suppliers')
  const [open, setOpen] = useState(false)
  const { pending, run } = useReviewAction()

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger render={<Button variant='outline' size='sm' />}>
        <ShieldCheck aria-hidden='true' />
        {t('verify')}
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t('verifyTitle')}</AlertDialogTitle>
          <AlertDialogDescription>{t('verifyText')}</AlertDialogDescription>
        </AlertDialogHeader>
        <p className='rounded-md bg-muted px-3 py-2 text-center font-mono'>
          {iban.replace(/(.{4})(?=.)/g, '$1 ')}
        </p>
        <AlertDialogFooter>
          <AlertDialogCancel>{t('cancel')}</AlertDialogCancel>
          <Button
            disabled={pending}
            onClick={() =>
              run(
                () => verifySupplierIban(supplierId, iban),
                t('verified'),
                () => setOpen(false),
              )
            }
          >
            {pending && <Spinner />}
            {t('verifyConfirm')}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
