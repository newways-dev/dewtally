'use client'

import { Pencil, Plus, Trash2 } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { type FormEvent, useState } from 'react'

import { useReviewAction } from '@/components/review/use-review-action'
import { Button } from '@/components/ui/button'
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
  FieldLegend,
  FieldSet,
} from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import { Spinner } from '@/components/ui/spinner'
import type { components } from '@/lib/api-types'
import { type CorrectionInput, correctInvoice } from '@/lib/review-actions'

type Invoice = components['schemas']['InvoiceDetailsResponse']
type HeaderField = keyof CorrectionInput['data']
type LineField = keyof CorrectionInput['lines'][number]
type LineForm = Record<LineField, string> & { key: number }
type FormState = { header: Record<HeaderField, string>; lines: LineForm[] }

const HEADER: { name: HeaderField; type: 'text' | 'date' }[] = [
  { name: 'supplierName', type: 'text' },
  { name: 'supplierVatId', type: 'text' },
  { name: 'iban', type: 'text' },
  { name: 'invoiceNumber', type: 'text' },
  { name: 'issueDate', type: 'date' },
  { name: 'dueDate', type: 'date' },
  { name: 'currency', type: 'text' },
  { name: 'buyerReference', type: 'text' },
]
const TOTALS: HeaderField[] = [
  'netAmount',
  'taxAmount',
  'grossAmount',
  'allowanceTotal',
  'chargeTotal',
]
const LINE_FIELDS: {
  name: LineField
  label: 'quantity' | 'unitCode' | 'unitPrice' | 'lineNet' | 'taxRate'
}[] = [
  { name: 'quantity', label: 'quantity' },
  { name: 'unitCode', label: 'unitCode' },
  { name: 'unitPrice', label: 'unitPrice' },
  { name: 'netAmount', label: 'lineNet' },
  { name: 'taxRate', label: 'taxRate' },
]

let nextKey = 0

const text = (value: unknown) => (value == null ? '' : String(value))
const clean = (value: string) => value.trim() || null

function emptyLine(): LineForm {
  return {
    key: nextKey++,
    description: '',
    quantity: '',
    unitCode: '',
    unitPrice: '',
    netAmount: '',
    taxRate: '',
  }
}

function toForm(invoice: Invoice): FormState {
  const header = Object.fromEntries(
    [...HEADER.map((field) => field.name), ...TOTALS].map((name) => [
      name,
      text(invoice[name]),
    ]),
  ) as Record<HeaderField, string>
  const lines = (invoice.lines ?? []).map((line) => ({
    key: nextKey++,
    description: text(line.description),
    quantity: text(line.quantity),
    unitCode: text(line.unitCode),
    unitPrice: text(line.unitPrice),
    netAmount: text(line.netAmount),
    taxRate: text(line.taxRate),
  }))
  return { header, lines }
}

// Accepts "1234.56", "1234,56" and "1.234,56". Empty means "no value" (null), undefined means "not a number".
function toDecimal(value: string): string | null | undefined {
  const compact = value.replace(/\s/g, '')
  if (compact === '') {
    return null
  }
  const normalized = compact.includes(',')
    ? compact.replace(/\./g, '').replace(',', '.')
    : compact
  return /^-?\d+(\.\d+)?$/.test(normalized) ? normalized : undefined
}

// The clerk's form: the whole invoice is sent, the API finds out what changed and checks everything again.
export function CorrectionSheet({ invoice }: { invoice: Invoice }) {
  const t = useTranslations('review')
  const tf = useTranslations('review.fields')
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<FormState>(() => toForm(invoice))
  const [invalid, setInvalid] = useState<Set<string>>(new Set())
  const { pending, run } = useReviewAction()

  function change(next: boolean) {
    if (next) {
      // Always start from the invoice as it is now
      setForm(toForm(invoice))
      setInvalid(new Set())
    }
    setOpen(next)
  }

  function setHeader(name: HeaderField, value: string) {
    setForm((current) => ({
      ...current,
      header: { ...current.header, [name]: value },
    }))
  }

  function setLine(key: number, name: LineField, value: string) {
    setForm((current) => ({
      ...current,
      lines: current.lines.map((line) =>
        line.key === key ? { ...line, [name]: value } : line,
      ),
    }))
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const bad = new Set<string>()
    const decimal = (value: string, id: string) => {
      const result = toDecimal(value)
      if (result === undefined) {
        bad.add(id)
        return null
      }
      return result
    }
    const h = form.header

    const input: CorrectionInput = {
      version: invoice.version ?? 0,
      data: {
        invoiceNumber: clean(h.invoiceNumber),
        issueDate: clean(h.issueDate),
        dueDate: clean(h.dueDate),
        currency: clean(h.currency)?.toUpperCase() ?? null,
        supplierName: clean(h.supplierName),
        supplierVatId: clean(h.supplierVatId),
        buyerReference: clean(h.buyerReference),
        // IBANs are often typed in groups of four: keep them without spaces
        iban: clean(h.iban.replace(/\s/g, ''))?.toUpperCase() ?? null,
        netAmount: decimal(h.netAmount, 'netAmount'),
        taxAmount: decimal(h.taxAmount, 'taxAmount'),
        grossAmount: decimal(h.grossAmount, 'grossAmount'),
        allowanceTotal: decimal(h.allowanceTotal, 'allowanceTotal'),
        chargeTotal: decimal(h.chargeTotal, 'chargeTotal'),
      },
      lines: form.lines.map((line) => ({
        description: clean(line.description),
        quantity: decimal(line.quantity, `${line.key}.quantity`),
        unitCode: clean(line.unitCode),
        unitPrice: decimal(line.unitPrice, `${line.key}.unitPrice`),
        netAmount: decimal(line.netAmount, `${line.key}.netAmount`),
        taxRate: decimal(line.taxRate, `${line.key}.taxRate`),
      })),
    }

    setInvalid(bad)
    if (bad.size > 0 || !invoice.id) {
      return
    }
    const id = invoice.id
    run(
      () => correctInvoice(id, input),
      t('corrected'),
      () => setOpen(false),
    )
  }

  return (
    <Sheet open={open} onOpenChange={change}>
      <SheetTrigger render={<Button variant='outline' />}>
        <Pencil aria-hidden='true' />
        {t('correct')}
      </SheetTrigger>
      <SheetContent side='right' className='w-full gap-0 sm:max-w-2xl'>
        <SheetHeader className='border-b'>
          <SheetTitle>{t('correctTitle')}</SheetTitle>
          <SheetDescription>{t('correctText')}</SheetDescription>
        </SheetHeader>

        <form
          id='correction'
          onSubmit={submit}
          className='flex flex-1 flex-col gap-8 overflow-y-auto p-4'
        >
          <FieldSet>
            <FieldLegend>{t('header')}</FieldLegend>
            <FieldGroup className='grid gap-4 sm:grid-cols-2'>
              {HEADER.map(({ name, type }) => (
                <Field key={name}>
                  <FieldLabel htmlFor={`c-${name}`}>{tf(name)}</FieldLabel>
                  <Input
                    id={`c-${name}`}
                    type={type}
                    value={form.header[name]}
                    onChange={(event) => setHeader(name, event.target.value)}
                    className={
                      name === 'iban' || name === 'supplierVatId'
                        ? 'font-mono'
                        : undefined
                    }
                  />
                </Field>
              ))}
            </FieldGroup>
          </FieldSet>

          <FieldSet>
            <FieldLegend>{t('totals')}</FieldLegend>
            <FieldGroup className='grid gap-4 sm:grid-cols-3'>
              {TOTALS.map((name) => {
                const wrong = invalid.has(name)
                return (
                  <Field key={name} data-invalid={wrong || undefined}>
                    <FieldLabel htmlFor={`c-${name}`}>{tf(name)}</FieldLabel>
                    <Input
                      id={`c-${name}`}
                      inputMode='decimal'
                      value={form.header[name]}
                      onChange={(event) => setHeader(name, event.target.value)}
                      aria-invalid={wrong || undefined}
                      className='font-mono'
                    />
                    {wrong && <FieldError>{t('notNumber')}</FieldError>}
                  </Field>
                )
              })}
            </FieldGroup>
          </FieldSet>

          <FieldSet>
            <FieldLegend>{t('lines')}</FieldLegend>
            <div className='flex flex-col gap-4'>
              {form.lines.length === 0 && (
                <p className='text-sm text-muted-foreground'>{t('noLines')}</p>
              )}
              {form.lines.map((line, index) => (
                <div
                  key={line.key}
                  className='flex flex-col gap-3 rounded-lg border p-3'
                >
                  <div className='flex items-end gap-2'>
                    <Field className='flex-1'>
                      <FieldLabel htmlFor={`c-${line.key}-description`}>
                        {t('line', { number: index + 1 })} · {tf('description')}
                      </FieldLabel>
                      <Input
                        id={`c-${line.key}-description`}
                        value={line.description}
                        onChange={(event) =>
                          setLine(line.key, 'description', event.target.value)
                        }
                      />
                    </Field>
                    <Button
                      type='button'
                      variant='ghost'
                      size='icon'
                      aria-label={t('removeLine', { number: index + 1 })}
                      onClick={() =>
                        setForm((current) => ({
                          ...current,
                          lines: current.lines.filter(
                            (l) => l.key !== line.key,
                          ),
                        }))
                      }
                    >
                      <Trash2 />
                    </Button>
                  </div>
                  <div className='grid grid-cols-2 gap-3 sm:grid-cols-5'>
                    {LINE_FIELDS.map(({ name, label }) => {
                      const id = `${line.key}.${name}`
                      const wrong = invalid.has(id)
                      return (
                        <Field key={name} data-invalid={wrong || undefined}>
                          <FieldLabel htmlFor={`c-${id}`}>
                            {tf(label)}
                          </FieldLabel>
                          <Input
                            id={`c-${id}`}
                            inputMode={
                              name === 'unitCode' ? undefined : 'decimal'
                            }
                            value={line[name]}
                            onChange={(event) =>
                              setLine(line.key, name, event.target.value)
                            }
                            aria-invalid={wrong || undefined}
                            className='font-mono'
                          />
                          {wrong && <FieldError>{t('notNumber')}</FieldError>}
                        </Field>
                      )
                    })}
                  </div>
                </div>
              ))}
              <Button
                type='button'
                variant='outline'
                className='w-fit'
                onClick={() =>
                  setForm((current) => ({
                    ...current,
                    lines: [...current.lines, emptyLine()],
                  }))
                }
              >
                <Plus aria-hidden='true' />
                {t('addLine')}
              </Button>
            </div>
          </FieldSet>
        </form>

        <SheetFooter className='border-t sm:flex-row sm:justify-end'>
          <SheetClose render={<Button variant='outline' />}>
            {t('cancel')}
          </SheetClose>
          <Button type='submit' form='correction' disabled={pending}>
            {pending && <Spinner />}
            {t('save')}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}
