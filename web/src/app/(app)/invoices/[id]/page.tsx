import {
  AlertTriangle,
  ArrowLeft,
  CircleCheck,
  CircleX,
  Info,
} from 'lucide-react'
import Link from 'next/link'
import { notFound } from 'next/navigation'
import { getFormatter, getTranslations } from 'next-intl/server'

import { InvoiceDocument } from '@/components/invoice-document'
import { InvoiceActions } from '@/components/review/invoice-actions'
import { StatusBadge } from '@/components/status-badge'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { ApiError, apiFetch } from '@/lib/api'
import type { components } from '@/lib/api-types'
import { getCurrentUser } from '@/lib/session'
import { cn } from '@/lib/utils'

type Invoice = components['schemas']['InvoiceDetailsResponse']
type Change = components['schemas']['InvoiceChangeResponse']
type Document = components['schemas']['DocumentResponse']

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

// Another company's invoice answers 404 in the API (its existence is not revealed): the same here.
async function load(id: string) {
  try {
    const invoice = await apiFetch<Invoice>(`/api/invoices/${id}`)
    const [changes, document] = await Promise.all([
      apiFetch<Change[]>(`/api/invoices/${id}/changes`),
      apiFetch<Document>(`/api/documents/${invoice.documentId}`),
    ])
    return { invoice, changes, document }
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound()
    }
    throw error
  }
}

const findingStyles = {
  ERROR: {
    icon: CircleX,
    className:
      'border-status-invalid bg-status-invalid text-status-invalid-foreground',
  },
  WARNING: {
    icon: AlertTriangle,
    className:
      'border-status-review bg-status-review text-status-review-foreground',
  },
  INFO: { icon: Info, className: '' },
} as const

export default async function InvoicePage({
  params,
}: PageProps<'/invoices/[id]'>) {
  const { id } = await params
  if (!UUID.test(id)) {
    notFound()
  }
  const t = await getTranslations('detail')
  const format = await getFormatter()
  const [{ invoice, changes, document }, user] = await Promise.all([
    load(id),
    getCurrentUser(),
  ])

  const currency = invoice.currency ?? 'EUR'
  const money = (value?: number) =>
    value != null ? format.number(value, { style: 'currency', currency }) : '—'
  const day = (date?: string) =>
    date
      ? format.dateTime(new Date(date), {
          dateStyle: 'medium',
          timeZone: 'UTC',
        })
      : '—'
  const issues = invoice.issues ?? []
  const lines = invoice.lines ?? []

  return (
    <>
      <Link
        href='/invoices'
        className={cn(
          buttonVariants({ variant: 'ghost', size: 'sm' }),
          '-ml-2 w-fit text-muted-foreground',
        )}
      >
        <ArrowLeft aria-hidden='true' />
        {t('back')}
      </Link>

      <header className='flex flex-wrap items-end justify-between gap-4'>
        <div className='flex min-w-0 flex-col gap-1.5'>
          <div className='flex flex-wrap items-center gap-2.5'>
            <h1 className='text-2xl font-semibold tracking-tight'>
              {invoice.supplierName ?? t('unknownSupplier')}
            </h1>
            {invoice.status && <StatusBadge status={invoice.status} />}
          </div>
          <p className='text-muted-foreground'>
            <span className='font-mono'>{invoice.invoiceNumber ?? '—'}</span>
            {' · '}
            {t('issued', { date: day(invoice.issueDate) })}
            {' · '}
            {t('due', { date: day(invoice.dueDate) })}
          </p>
        </div>
        <div className='flex flex-col items-end gap-3'>
          <div className='text-right'>
            <div className='text-xs text-subtle'>{t('amountDue')}</div>
            <div className='font-mono text-3xl font-medium tracking-tight tabular-nums'>
              {money(invoice.grossAmount)}
            </div>
          </div>
          <InvoiceActions invoice={invoice} roles={user?.roles ?? []} />
        </div>
      </header>

      <div className='flex flex-wrap items-start gap-5'>
        <section
          aria-label={t('original')}
          className='min-w-0 flex-[1_1_480px]'
        >
          <InvoiceDocument document={document} />
        </section>

        <div className='flex min-w-0 flex-[1_1_420px] flex-col gap-4'>
          <section aria-label={t('findings')} className='flex flex-col gap-2'>
            {issues.length === 0 ? (
              <Alert className='border-status-accepted bg-status-accepted text-status-accepted-foreground'>
                <CircleCheck aria-hidden='true' />
                <AlertTitle>{t('noFindings')}</AlertTitle>
              </Alert>
            ) : (
              issues.map((issue, index) => {
                const style = findingStyles[issue.severity ?? 'INFO']
                const Icon = style.icon
                return (
                  <Alert
                    key={`${issue.ruleId}-${index}`}
                    className={style.className}
                  >
                    <Icon aria-hidden='true' />
                    <AlertTitle className='flex flex-wrap items-center gap-2'>
                      <span className='font-mono text-xs opacity-80'>
                        {issue.ruleId}
                      </span>
                    </AlertTitle>
                    <AlertDescription className='text-current'>
                      {issue.message}
                    </AlertDescription>
                  </Alert>
                )
              })
            )}
          </section>

          <Card className='shadow-none'>
            <CardHeader>
              <CardTitle>{t('supplier')}</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className='grid grid-cols-[minmax(120px,160px)_1fr] gap-x-4 gap-y-2.5'>
                <dt className='text-muted-foreground'>{t('name')}</dt>
                <dd>{invoice.supplierName ?? '—'}</dd>
                <dt className='text-muted-foreground'>{t('vatId')}</dt>
                <dd className='font-mono'>{invoice.supplierVatId ?? '—'}</dd>
                <dt className='text-muted-foreground'>{t('iban')}</dt>
                <dd className='font-mono break-all'>{invoice.iban ?? '—'}</dd>
                {invoice.buyerReference && (
                  <>
                    <dt className='text-muted-foreground'>
                      {t('buyerReference')}
                    </dt>
                    <dd className='font-mono'>{invoice.buyerReference}</dd>
                  </>
                )}
              </dl>
            </CardContent>
          </Card>

          <Card className='gap-0 py-0 shadow-none'>
            <CardHeader className='border-b py-4'>
              <CardTitle>{t('lines')}</CardTitle>
              {invoice.extractionMethod && (
                <Badge
                  variant='outline'
                  className='w-fit font-normal text-muted-foreground'
                >
                  {invoice.extractionMethod === 'LLM'
                    ? t('extraction.LLM', {
                        confidence: format.number(
                          invoice.extractionConfidence ?? 0,
                          { style: 'percent' },
                        ),
                      })
                    : t(`extraction.${invoice.extractionMethod}`)}
                </Badge>
              )}
            </CardHeader>
            <Table>
              <TableHeader>
                <TableRow className='hover:bg-transparent'>
                  <TableHead className='pl-6'>{t('description')}</TableHead>
                  <TableHead className='text-right'>{t('quantity')}</TableHead>
                  <TableHead className='pr-6 text-right'>{t('net')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lines.map((line) => (
                  <TableRow key={line.lineNo}>
                    <TableCell className='pl-6 whitespace-normal'>
                      {line.description ?? '—'}
                    </TableCell>
                    <TableCell className='text-right font-mono text-muted-foreground'>
                      {line.quantity != null
                        ? format.number(line.quantity)
                        : '—'}{' '}
                      {line.unitCode}
                    </TableCell>
                    <TableCell className='pr-6 text-right font-mono tabular-nums'>
                      {money(line.netAmount)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <dl className='grid grid-cols-[1fr_auto] gap-x-4 gap-y-1.5 border-t px-6 py-4'>
              {!!invoice.allowanceTotal && (
                <>
                  <dt className='text-muted-foreground'>{t('allowances')}</dt>
                  <dd className='text-right font-mono tabular-nums'>
                    −{money(invoice.allowanceTotal)}
                  </dd>
                </>
              )}
              {!!invoice.chargeTotal && (
                <>
                  <dt className='text-muted-foreground'>{t('charges')}</dt>
                  <dd className='text-right font-mono tabular-nums'>
                    {money(invoice.chargeTotal)}
                  </dd>
                </>
              )}
              <dt className='text-muted-foreground'>{t('netTotal')}</dt>
              <dd className='text-right font-mono tabular-nums'>
                {money(invoice.netAmount)}
              </dd>
              <dt className='text-muted-foreground'>{t('tax')}</dt>
              <dd className='text-right font-mono tabular-nums'>
                {money(invoice.taxAmount)}
              </dd>
              <dt className='font-medium'>{t('gross')}</dt>
              <dd className='text-right font-mono font-medium tabular-nums'>
                {money(invoice.grossAmount)}
              </dd>
            </dl>
          </Card>

          <Card className='shadow-none'>
            <CardHeader>
              <CardTitle>{t('history')}</CardTitle>
            </CardHeader>
            <CardContent>
              {changes.length === 0 ? (
                <p className='text-muted-foreground'>{t('noHistory')}</p>
              ) : (
                <ol className='flex flex-col gap-3'>
                  {changes.map((change, index) => (
                    <li
                      key={index}
                      className='grid grid-cols-[10px_1fr_auto] items-baseline gap-3'
                    >
                      <span
                        aria-hidden='true'
                        className='size-2 -translate-y-px rounded-full bg-primary'
                      />
                      <span className='min-w-0'>
                        {t(`changes.${change.changeType ?? 'CORRECTION'}`, {
                          who: change.changedBy ?? '?',
                          field: change.field ?? '',
                        })}
                        {change.changeType === 'CORRECTION' && (
                          <span className='block font-mono text-xs text-subtle'>
                            {change.oldValue ?? '—'} → {change.newValue ?? '—'}
                          </span>
                        )}
                        {change.comment && (
                          <span className='block text-sm text-muted-foreground'>
                            „{change.comment}“
                          </span>
                        )}
                      </span>
                      <span className='font-mono text-xs whitespace-nowrap text-subtle'>
                        {change.changedAt
                          ? format.dateTime(new Date(change.changedAt), {
                              dateStyle: 'short',
                              timeStyle: 'short',
                            })
                          : ''}
                      </span>
                    </li>
                  ))}
                </ol>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </>
  )
}
