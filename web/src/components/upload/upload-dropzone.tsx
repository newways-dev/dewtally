'use client'

import {
  CircleAlert,
  CircleCheck,
  Clock,
  Copy,
  FilePlus,
  FileUp,
} from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { type DragEvent, useRef, useState } from 'react'

import { Button, buttonVariants } from '@/components/ui/button'
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@/components/ui/empty'
import {
  Item,
  ItemActions,
  ItemContent,
  ItemDescription,
  ItemGroup,
  ItemMedia,
  ItemTitle,
} from '@/components/ui/item'
import { Spinner } from '@/components/ui/spinner'
import { cn } from '@/lib/utils'

type State = 'uploading' | 'created' | 'queued' | 'duplicate' | 'error'
type ErrorKey = 'unsupported' | 'tooLarge' | 'forbidden' | 'failed'
type Entry = {
  id: number
  name: string
  state: State
  invoiceId?: string | null
  error?: ErrorKey
}

const ALLOWED = /\.(pdf|xml)$/i
let nextId = 0

async function upload(
  file: File,
): Promise<Pick<Entry, 'state' | 'invoiceId' | 'error'>> {
  const body = new FormData()
  body.append('file', file)
  try {
    const response = await fetch('/files', { method: 'POST', body })
    const data = await response.json().catch(() => ({}))
    switch (response.status) {
      case 201:
        return { state: 'created', invoiceId: data.invoiceId }
      case 202:
        return { state: 'queued' }
      case 200:
        return { state: 'duplicate', invoiceId: data.invoiceId }
      case 403:
        return { state: 'error', error: 'forbidden' }
      case 413:
        return { state: 'error', error: 'tooLarge' }
      default:
        return { state: 'error', error: 'failed' }
    }
  } catch {
    return { state: 'error', error: 'failed' }
  }
}

const icons = {
  uploading: <Spinner />,
  created: <CircleCheck className='text-status-accepted-foreground' />,
  queued: <Clock />,
  duplicate: <Copy />,
  error: <CircleAlert className='text-destructive' />,
}

// Drop zone and the list of files uploaded in this visit. Files go one after another, not all at once,
// so a big batch does not flood the server.
export function UploadDropzone() {
  const t = useTranslations('upload')
  const router = useRouter()
  const input = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [entries, setEntries] = useState<Entry[]>([])
  const busy = entries.some((entry) => entry.state === 'uploading')

  function update(id: number, change: Partial<Entry>) {
    setEntries((current) =>
      current.map((entry) =>
        entry.id === id ? { ...entry, ...change } : entry,
      ),
    )
  }

  async function handle(files: FileList | null) {
    if (!files || files.length === 0) {
      return
    }
    const batch = Array.from(files).map((file) => ({ file, id: nextId++ }))
    setEntries((current) => [
      ...batch.map(({ file, id }) =>
        ALLOWED.test(file.name)
          ? { id, name: file.name, state: 'uploading' as const }
          : {
              id,
              name: file.name,
              state: 'error' as const,
              error: 'unsupported' as const,
            },
      ),
      ...current,
    ])
    for (const { file, id } of batch) {
      if (ALLOWED.test(file.name)) {
        update(id, await upload(file))
      }
    }
    // Show the new documents in the list below
    router.refresh()
  }

  function drop(event: DragEvent) {
    event.preventDefault()
    setDragging(false)
    void handle(event.dataTransfer.files)
  }

  return (
    <div className='flex flex-col gap-4'>
      <Empty
        onDragOver={(event) => {
          event.preventDefault()
          setDragging(true)
        }}
        onDragLeave={(event) => {
          if (
            !event.currentTarget.contains(event.relatedTarget as Node | null)
          ) {
            setDragging(false)
          }
        }}
        onDrop={drop}
        className={cn(
          'border-2 border-dashed transition-colors',
          dragging && 'border-primary bg-brand-soft',
        )}
      >
        <EmptyHeader>
          <EmptyMedia variant='icon'>
            <FileUp aria-hidden='true' />
          </EmptyMedia>
          <EmptyTitle>{t('dropTitle')}</EmptyTitle>
          <EmptyDescription>{t('dropHint')}</EmptyDescription>
        </EmptyHeader>
        <EmptyContent>
          <Button onClick={() => input.current?.click()} disabled={busy}>
            {busy ? <Spinner /> : <FilePlus aria-hidden='true' />}
            {t('choose')}
          </Button>
          <input
            ref={input}
            type='file'
            accept='.pdf,.xml,application/pdf,application/xml,text/xml'
            multiple
            className='sr-only'
            tabIndex={-1}
            onChange={(event) => {
              void handle(event.target.files)
              // Allows choosing the same file again
              event.target.value = ''
            }}
          />
        </EmptyContent>
      </Empty>

      {entries.length > 0 && (
        <ItemGroup className='gap-2'>
          {entries.map((entry) => (
            <Item key={entry.id} variant='outline' size='sm'>
              <ItemMedia variant='icon'>{icons[entry.state]}</ItemMedia>
              <ItemContent>
                <ItemTitle className='font-mono text-sm'>
                  {entry.name}
                </ItemTitle>
                <ItemDescription>
                  {entry.state === 'error'
                    ? t(`errors.${entry.error ?? 'failed'}`)
                    : t(`state.${entry.state}`)}
                </ItemDescription>
              </ItemContent>
              {entry.invoiceId && (
                <ItemActions>
                  <Link
                    href={`/invoices/${entry.invoiceId}`}
                    className={buttonVariants({
                      variant: 'outline',
                      size: 'sm',
                    })}
                  >
                    {t('openInvoice')}
                  </Link>
                </ItemActions>
              )}
            </Item>
          ))}
        </ItemGroup>
      )}
    </div>
  )
}
