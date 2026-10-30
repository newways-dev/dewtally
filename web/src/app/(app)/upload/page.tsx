import { FileStack, Info } from 'lucide-react'
import Link from 'next/link'
import { getFormatter, getTranslations } from 'next-intl/server'

import { AutoRefresh } from '@/components/upload/auto-refresh'
import { UploadDropzone } from '@/components/upload/upload-dropzone'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardAction, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@/components/ui/empty'
import { Spinner } from '@/components/ui/spinner'
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
import { getCurrentUser } from '@/lib/session'

type Document = components['schemas']['DocumentResponse']
type DocumentPage = { items?: Document[] }

const formats: Record<string, string> = {
  UBL: 'XRechnung (UBL)',
  CII: 'XRechnung (CII)',
  ZUGFERD: 'ZUGFeRD',
  PDF: 'PDF',
}

const statusVariant = {
  RECEIVED: 'secondary',
  PROCESSED: 'outline',
  FAILED: 'destructive',
} as const

export default async function UploadPage() {
  const t = await getTranslations('upload')
  const format = await getFormatter()
  const [user, page] = await Promise.all([
    getCurrentUser(),
    apiFetch<DocumentPage>('/api/documents?size=20'),
  ])

  // Same rule as in the API: only clerks enter invoices
  const canUpload = user?.roles.includes('clerk') ?? false
  const documents = page.items ?? []
  const processing = documents.some(
    (document) => document.processingStatus === 'RECEIVED',
  )

  return (
    <>
      <header className='flex flex-col gap-1.5'>
        <h1 className='text-2xl font-semibold tracking-tight'>{t('title')}</h1>
        <p className='text-muted-foreground'>{t('description')}</p>
      </header>

      {canUpload ? (
        <UploadDropzone />
      ) : (
        <Alert>
          <Info aria-hidden='true' />
          <AlertTitle>{t('readOnly')}</AlertTitle>
          <AlertDescription>{t('readOnlyText')}</AlertDescription>
        </Alert>
      )}

      <Card className='gap-0 py-0 shadow-none'>
        <CardHeader className='border-b py-4'>
          <CardTitle>{t('recent')}</CardTitle>
          {processing && (
            <CardAction>
              <Badge variant='outline' className='font-normal'>
                <Spinner />
                {t('processing')}
              </Badge>
            </CardAction>
          )}
        </CardHeader>

        {documents.length === 0 ? (
          <Empty>
            <EmptyHeader>
              <EmptyMedia variant='icon'>
                <FileStack aria-hidden='true' />
              </EmptyMedia>
              <EmptyTitle>{t('empty')}</EmptyTitle>
              <EmptyDescription>{t('emptyText')}</EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          <Table>
            <TableHeader>
              <TableRow className='hover:bg-transparent'>
                <TableHead className='pl-6'>{t('columns.file')}</TableHead>
                <TableHead>{t('columns.format')}</TableHead>
                <TableHead>{t('columns.uploaded')}</TableHead>
                <TableHead>{t('columns.status')}</TableHead>
                <TableHead className='pr-6 text-right'>
                  {t('columns.invoice')}
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {documents.map((document) => (
                <TableRow key={document.id}>
                  <TableCell className='max-w-[320px] pl-6'>
                    <div className='truncate font-mono text-sm'>
                      {document.fileName}
                    </div>
                    <div className='text-xs text-subtle'>
                      {format.number((document.sizeBytes ?? 0) / 1024, {
                        style: 'unit',
                        unit: 'kilobyte',
                        maximumFractionDigits: 0,
                      })}
                    </div>
                  </TableCell>
                  <TableCell>
                    <div>
                      {formats[document.sourceFormat ?? ''] ??
                        document.sourceFormat ??
                        '—'}
                    </div>
                    {document.profile && (
                      <div className='text-xs text-subtle'>
                        {document.profile}
                      </div>
                    )}
                  </TableCell>
                  <TableCell className='text-muted-foreground'>
                    {document.uploadedAt
                      ? format.dateTime(new Date(document.uploadedAt), {
                          dateStyle: 'short',
                          timeStyle: 'short',
                        })
                      : '—'}
                  </TableCell>
                  <TableCell>
                    {document.processingStatus && (
                      <Badge
                        variant={statusVariant[document.processingStatus]}
                        className='font-normal'
                      >
                        {document.processingStatus === 'RECEIVED' && (
                          <Spinner />
                        )}
                        {t(`status.${document.processingStatus}`)}
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell className='pr-6 text-right'>
                    {document.invoiceId ? (
                      <Link
                        href={`/invoices/${document.invoiceId}`}
                        className={buttonVariants({
                          variant: 'ghost',
                          size: 'sm',
                        })}
                      >
                        {t('openInvoice')}
                      </Link>
                    ) : (
                      <span className='text-subtle'>—</span>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      <AutoRefresh active={processing} />
    </>
  )
}
