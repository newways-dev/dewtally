import { Download, FileText, FileX } from 'lucide-react'
import { getTranslations } from 'next-intl/server'

import { PdfPreview } from '@/components/pdf-preview'
import { buttonVariants } from '@/components/ui/button'
import {
  Card,
  CardAction,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
} from '@/components/ui/empty'
import { ApiError, apiRequest } from '@/lib/api'
import type { components } from '@/lib/api-types'

type Document = components['schemas']['DocumentResponse']

const MAX_XML_CHARS = 200_000

// Loads the XML text; a missing original file must not take down the whole invoice page.
async function loadXml(id: string): Promise<string | null> {
  try {
    const text = await (await apiRequest(`/api/documents/${id}/content`)).text()
    return text.length > MAX_XML_CHARS
      ? `${text.slice(0, MAX_XML_CHARS)}\n…`
      : text
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null
    }
    throw error
  }
}

// The original as the supplier sent it: PDFs drawn by pdf.js, XML as plain text (never opened as a page).
export async function InvoiceDocument({ document }: { document: Document }) {
  const t = await getTranslations('detail')
  const format = document.sourceFormat
  const isPdf = format === 'PDF' || format === 'ZUGFERD'
  const isXml = format === 'UBL' || format === 'CII'
  const fileUrl = `/files/${document.id}`

  const xml = isXml && document.id ? await loadXml(document.id) : null
  const missing = isXml && xml === null

  return (
    <Card className='gap-0 py-0 shadow-none'>
      <CardHeader className='border-b py-3'>
        <CardTitle className='flex min-w-0 items-center gap-2 text-sm font-normal text-muted-foreground'>
          <FileText className='size-4 shrink-0' aria-hidden='true' />
          <span className='truncate font-mono'>{document.fileName}</span>
        </CardTitle>
        {!missing && (
          <CardAction>
            <a
              href={fileUrl}
              download
              className={buttonVariants({ variant: 'ghost', size: 'sm' })}
            >
              <Download aria-hidden='true' />
              {t('download')}
            </a>
          </CardAction>
        )}
      </CardHeader>
      <CardContent className='bg-muted p-3'>
        {isPdf && <PdfPreview url={fileUrl} />}
        {xml !== null && (
          <pre className='h-[75vh] min-h-[520px] overflow-auto rounded-md border bg-card p-4 font-mono text-xs leading-relaxed whitespace-pre-wrap'>
            {xml}
          </pre>
        )}
        {!isPdf && xml === null && (
          <Empty className='min-h-[320px]'>
            <EmptyHeader>
              <EmptyMedia variant='icon'>
                <FileX aria-hidden='true' />
              </EmptyMedia>
              <EmptyDescription>
                {missing ? t('fileMissing') : t('noPreview')}
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        )}
      </CardContent>
    </Card>
  )
}
