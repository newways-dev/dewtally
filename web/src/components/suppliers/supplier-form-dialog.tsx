'use client'

import { Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { type FormEvent, useState } from 'react'

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
import {
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { createSupplier } from '@/lib/supplier-actions'

// Admin only: a new supplier, optionally with its first bank account (which starts as pending).
export function SupplierFormDialog() {
  const t = useTranslations('suppliers')
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [vatId, setVatId] = useState('')
  const [iban, setIban] = useState('')
  const { pending, run } = useReviewAction()

  function change(next: boolean) {
    setOpen(next)
    if (!next) {
      setName('')
      setVatId('')
      setIban('')
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    run(
      () =>
        createSupplier({
          name: name.trim(),
          vatId: vatId.trim() || undefined,
          iban: iban.trim() || undefined,
        }),
      t('created'),
      () => change(false),
    )
  }

  return (
    <Dialog open={open} onOpenChange={change}>
      <DialogTrigger render={<Button />}>
        <Plus aria-hidden='true' />
        {t('add')}
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={submit} className='flex flex-col gap-6'>
          <DialogHeader>
            <DialogTitle>{t('addTitle')}</DialogTitle>
            <DialogDescription>{t('addText')}</DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor='supplier-name'>{t('name')}</FieldLabel>
              <Input
                id='supplier-name'
                value={name}
                onChange={(e) => setName(e.target.value)}
                maxLength={300}
                required
              />
            </Field>
            <Field>
              <FieldLabel htmlFor='supplier-vat'>{t('vatId')}</FieldLabel>
              <Input
                id='supplier-vat'
                value={vatId}
                onChange={(e) => setVatId(e.target.value)}
                maxLength={30}
                placeholder='DE123456789'
                className='font-mono'
              />
            </Field>
            <Field>
              <FieldLabel htmlFor='supplier-iban'>{t('firstIban')}</FieldLabel>
              <Input
                id='supplier-iban'
                value={iban}
                onChange={(e) => setIban(e.target.value)}
                autoComplete='off'
                placeholder='DE89 3704 0044 0532 0130 00'
                className='font-mono'
              />
              <FieldDescription>{t('ibanPendingHint')}</FieldDescription>
            </Field>
          </FieldGroup>
          <DialogFooter>
            <DialogClose render={<Button type='button' variant='outline' />}>
              {t('cancel')}
            </DialogClose>
            <Button type='submit' disabled={pending || name.trim() === ''}>
              {pending && <Spinner />}
              {t('save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
