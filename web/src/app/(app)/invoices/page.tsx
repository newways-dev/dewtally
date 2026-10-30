import { ChevronLeft, ChevronRight, Plus, Search } from 'lucide-react'
import Link from 'next/link'
import { getFormatter, getTranslations } from 'next-intl/server'

import { PageHeader } from '@/components/page-header'
import { StatusBadge, type InvoiceStatus } from '@/components/status-badge'
import { buttonVariants } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { apiFetch } from '@/lib/api'
import type { components } from '@/lib/api-types'
import { cn } from '@/lib/utils'
import { Badge } from '@/components/ui/badge'

type InvoiceCounts = components['schemas']['InvoiceCountsResponse']
type InvoicePage = components['schemas']['PageResponseInvoiceSummaryResponse']

const PAGE_SIZE = 20
const tabs = [
  'NEEDS_REVIEW',
  'INVALID',
  'VALID',
  'ACCEPTED',
  'REJECTED',
] as const satisfies InvoiceStatus[]
const cards = [
  'NEEDS_REVIEW',
  'INVALID',
  'VALID',
  'ACCEPTED',
] as const satisfies InvoiceStatus[]
const cardDots: Record<(typeof cards)[number], string> = {
  NEEDS_REVIEW: 'bg-status-review-foreground',
  INVALID: 'bg-status-invalid-foreground',
  VALID: 'bg-status-valid-foreground',
  ACCEPTED: 'bg-status-accepted-foreground',
}

type Filter = { status?: InvoiceStatus; q?: string; page?: number }

// Filter, search and page live in the URL: links can be shared, Back works, F5 keeps the filter.
// That is why tabs and paging are links styled as shadcn buttons, not the Tabs component (which switches in place).
function listHref({ status, q, page }: Filter): string {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  if (q) params.set('q', q)
  if (page && page > 1) params.set('page', String(page))
  const query = params.toString()
  return query ? `/invoices?${query}` : '/invoices'
}

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value
}

export default async function InvoicesPage({
  searchParams,
}: PageProps<'/invoices'>) {
  const t = await getTranslations('invoices')
  const format = await getFormatter()
  const params = await searchParams

  // Only known statuses reach the API; anything else in the URL is ignored
  const rawStatus = first(params.status)
  const status = (tabs as readonly string[]).includes(rawStatus ?? '')
    ? (rawStatus as InvoiceStatus)
    : undefined
  const q = first(params.q)?.trim() || undefined
  const page = Math.max(1, Number(first(params.page)) || 1)

  const query = new URLSearchParams({
    page: String(page - 1),
    size: String(PAGE_SIZE),
  })
  if (status) query.set('status', status)
  if (q) query.set('q', q)

  const [counts, list] = await Promise.all([
    apiFetch<InvoiceCounts>('/api/invoices/counts'),
    apiFetch<InvoicePage>(`/api/invoices?${query}`),
  ])
  const items = list.items ?? []
  const totalPages = list.totalPages ?? 0

  const formatDate = (date?: string) =>
    // LocalDate from the API ("2026-10-21"): a calendar day, shown without any time zone shift
    date
      ? format.dateTime(new Date(date), {
          dateStyle: 'medium',
          timeZone: 'UTC',
        })
      : '—'

  return (
    <>
      <PageHeader
        title={t('title')}
        subtitle={t('subtitle')}
        actions={
          <Link
            href='/upload'
            className={cn(
              buttonVariants(),
              'inline-flex items-center gap-1.5 whitespace-nowrap',
            )}
          >
            <Plus aria-hidden='true' />
            {t('upload')}
          </Link>
        }
      />

      <section
        aria-label={t('summary')}
        className='grid grid-cols-[repeat(auto-fit,minmax(190px,1fr))] gap-3'
      >
        {cards.map((card) => (
          <Link
            key={card}
            href={listHref({ status: card })}
            className='rounded-xl'
          >
            <Card className='h-full gap-1.5 p-4 shadow-none transition-colors hover:bg-accent'>
              <span className='flex items-center gap-2 text-sm text-muted-foreground'>
                <span
                  aria-hidden='true'
                  className={cn('size-2 rounded-full', cardDots[card])}
                />
                {t(`cards.${card}.label`)}
              </span>
              <span className='font-mono text-3xl leading-none font-medium tracking-tight tabular-nums'>
                {counts.byStatus?.[card] ?? 0}
              </span>
              <span className='text-xs text-subtle'>
                {t(`cards.${card}.hint`)}
              </span>
            </Card>
          </Link>
        ))}
      </section>

      <Card className='gap-0 overflow-hidden py-0 shadow-none'>
        <div className='flex flex-wrap items-center justify-between gap-3 border-b px-4 py-3'>
          <nav aria-label={t('filter')} className='flex flex-wrap gap-1'>
            <FilterTab
              href={listHref({ q })}
              active={!status}
              label={t('all')}
              count={counts.total ?? 0}
            />
            {tabs.map((tab) => (
              <FilterTab
                key={tab}
                href={listHref({ status: tab, q })}
                active={status === tab}
                label={t(`tabs.${tab}`)}
                count={counts.byStatus?.[tab] ?? 0}
              />
            ))}
          </nav>

          {/* A plain GET form: works without JavaScript, the search lands in the URL */}
          <form
            action='/invoices'
            role='search'
            className='relative flex min-w-52 flex-[0_1_300px]'
          >
            {status && <input type='hidden' name='status' value={status} />}
            <Search
              className='pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-subtle'
              aria-hidden='true'
            />
            <Input
              type='search'
              name='q'
              defaultValue={q}
              placeholder={t('search')}
              aria-label={t('search')}
              className='pl-9'
            />
          </form>
        </div>

        <Table className='min-w-[960px]'>
          <TableHeader>
            <TableRow className='hover:bg-transparent'>
              <TableHead className='pl-5'>{t('columns.supplier')}</TableHead>
              <TableHead>{t('columns.number')}</TableHead>
              <TableHead>{t('columns.issued')}</TableHead>
              <TableHead>{t('columns.due')}</TableHead>
              <TableHead className='text-right'>
                {t('columns.amount')}
              </TableHead>
              <TableHead>{t('columns.source')}</TableHead>
              <TableHead className='pr-5'>{t('columns.status')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((invoice) => {
              const issueCount = invoice.issueCount ?? 0
              return (
                <TableRow key={invoice.id} className='relative'>
                  <TableCell className='pl-5'>
                    {/* The link covers the whole row (after:absolute), so the row is one big click target */}
                    <Link
                      href={`/invoices/${invoice.id}`}
                      className='font-medium after:absolute after:inset-0 focus-visible:outline-none focus-visible:after:ring-2 focus-visible:after:ring-ring'
                    >
                      {invoice.supplierName ?? t('unknownSupplier')}
                    </Link>
                    {invoice.supplierVatId && (
                      <span className='block font-mono text-xs text-subtle'>
                        {invoice.supplierVatId}
                      </span>
                    )}
                  </TableCell>
                  <TableCell className='font-mono text-[13px]'>
                    {invoice.invoiceNumber ?? '—'}
                  </TableCell>
                  <TableCell className='text-muted-foreground'>
                    {formatDate(invoice.issueDate)}
                  </TableCell>
                  <TableCell className='text-muted-foreground'>
                    {formatDate(invoice.dueDate)}
                  </TableCell>
                  <TableCell className='text-right font-mono font-medium tabular-nums'>
                    {invoice.grossAmount != null
                      ? format.number(invoice.grossAmount, {
                          style: 'currency',
                          currency: invoice.currency ?? 'EUR',
                        })
                      : '—'}
                  </TableCell>
                  <TableCell>
                    {invoice.extractionMethod && (
                      <span className='flex items-center gap-1.5'>
                        <Badge
                          variant='outline'
                          className='font-normal text-muted-foreground'
                        >
                          {t(`source.${invoice.extractionMethod}`)}
                        </Badge>
                        {invoice.extractionMethod === 'LLM' &&
                          invoice.extractionConfidence != null && (
                            <span className='font-mono text-xs text-subtle'>
                              {format.number(invoice.extractionConfidence, {
                                style: 'percent',
                              })}
                            </span>
                          )}
                      </span>
                    )}
                  </TableCell>
                  <TableCell className='pr-5'>
                    <span className='flex flex-col items-start gap-0.5'>
                      {invoice.status && (
                        <StatusBadge status={invoice.status} />
                      )}
                      {issueCount > 0 && (
                        <span className='text-xs text-subtle'>
                          {t('findings', { count: issueCount })}
                        </span>
                      )}
                    </span>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
        {items.length === 0 && (
          <p className='px-5 py-10 text-center text-muted-foreground'>
            {t('empty')}
          </p>
        )}

        {totalPages > 1 && (
          <nav
            aria-label={t('pagination')}
            className='flex items-center justify-between gap-3 border-t px-4 py-3 text-sm'
          >
            <span className='text-muted-foreground'>
              {t('pageOf', { page, pages: totalPages })}
            </span>
            <span className='flex gap-2'>
              <PageLink
                href={
                  page > 1 ? listHref({ status, q, page: page - 1 }) : undefined
                }
                label={t('previous')}
              >
                <ChevronLeft aria-hidden='true' />
              </PageLink>
              <PageLink
                href={
                  page < totalPages
                    ? listHref({ status, q, page: page + 1 })
                    : undefined
                }
                label={t('next')}
              >
                <ChevronRight aria-hidden='true' />
              </PageLink>
            </span>
          </nav>
        )}
      </Card>
    </>
  )
}

function FilterTab({
  href,
  active,
  label,
  count,
}: {
  href: string
  active: boolean
  label: string
  count: number
}) {
  return (
    <Link
      href={href}
      aria-current={active ? 'page' : undefined}
      className={cn(
        buttonVariants({ variant: active ? 'secondary' : 'ghost', size: 'sm' }),
        'inline-flex items-center gap-1.5',
        !active && 'text-muted-foreground',
      )}
    >
      {label}
      <span className='font-mono text-xs text-subtle'>{count}</span>
    </Link>
  )
}

function PageLink({
  href,
  label,
  children,
}: {
  href?: string
  label: string
  children: React.ReactNode
}) {
  const className = cn(
    buttonVariants({ variant: 'outline', size: 'icon' }),
    'inline-flex items-center justify-center',
  )
  if (!href) {
    return (
      <span
        aria-disabled='true'
        className={cn(className, 'pointer-events-none opacity-40')}
      >
        <span className='sr-only'>{label}</span>
        {children}
      </span>
    )
  }
  return (
    <Link href={href} aria-label={label} className={className}>
      {children}
    </Link>
  )
}
