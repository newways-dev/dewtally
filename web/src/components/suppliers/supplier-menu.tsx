'use client'

import { Ban, LockOpen, MoreHorizontal, Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { type FormEvent, useState } from 'react'

import { useReviewAction } from '@/components/review/use-review-action'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import {
  addSupplierIban,
  blockSupplier,
  unblockSupplier,
} from '@/lib/supplier-actions'

export function SupplierMenu({
  supplierId,
  name,
  blocked,
}: {
  supplierId: string
  name: string
  blocked: boolean
}) {
  const t = useTranslations('suppliers')
  const [ibanOpen, setIbanOpen] = useState(false)
  const [blockOpen, setBlockOpen] = useState(false)
  const [iban, setIban] = useState('')
  const { pending, run } = useReviewAction()

  function changeIban(next: boolean) {
    setIbanOpen(next)
    if (!next) {
      setIban('')
    }
  }

  function submitIban(event: FormEvent) {
    event.preventDefault()
    run(
      () => addSupplierIban(supplierId, iban.trim()),
      t('ibanAdded'),
      () => changeIban(false),
    )
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger
          render={
            <Button
              variant='ghost'
              size='icon'
              aria-label={t('menu', { name })}
            />
          }
        >
          <MoreHorizontal />
        </DropdownMenuTrigger>
        <DropdownMenuContent align='end'>
          <DropdownMenuItem onClick={() => setIbanOpen(true)}>
            <Plus aria-hidden='true' />
            {t('addIban')}
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          {blocked ? (
            <DropdownMenuItem
              onClick={() =>
                run(() => unblockSupplier(supplierId), t('unblockedToast'))
              }
            >
              <LockOpen aria-hidden='true' />
              {t('unblock')}
            </DropdownMenuItem>
          ) : (
            <DropdownMenuItem
              variant='destructive'
              onClick={() => setBlockOpen(true)}
            >
              <Ban aria-hidden='true' />
              {t('block')}
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      <Dialog open={ibanOpen} onOpenChange={changeIban}>
        <DialogContent>
          <form onSubmit={submitIban} className='flex flex-col gap-6'>
            <DialogHeader>
              <DialogTitle>{t('addIbanTitle')}</DialogTitle>
              <DialogDescription>{name}</DialogDescription>
            </DialogHeader>
            <Field>
              <FieldLabel htmlFor='new-iban'>IBAN</FieldLabel>
              <Input
                id='new-iban'
                value={iban}
                onChange={(e) => setIban(e.target.value)}
                autoComplete='off'
                placeholder='DE89 3704 0044 0532 0130 00'
                className='font-mono'
                required
              />
              <FieldDescription>{t('ibanPendingHint')}</FieldDescription>
            </Field>
            <DialogFooter>
              <DialogClose render={<Button type='button' variant='outline' />}>
                {t('cancel')}
              </DialogClose>
              <Button type='submit' disabled={pending || iban.trim() === ''}>
                {pending && <Spinner />}
                {t('save')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog open={blockOpen} onOpenChange={setBlockOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('blockTitle', { name })}</AlertDialogTitle>
            <AlertDialogDescription>{t('blockText')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('cancel')}</AlertDialogCancel>
            <Button
              variant='destructive'
              disabled={pending}
              onClick={() =>
                run(
                  () => blockSupplier(supplierId),
                  t('blockedToast'),
                  () => setBlockOpen(false),
                )
              }
            >
              {pending && <Spinner />}
              {t('block')}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
