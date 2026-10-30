'use client'

import 'react-pdf/dist/Page/AnnotationLayer.css'
import 'react-pdf/dist/Page/TextLayer.css'

import { Minus, Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useEffect, useRef, useState } from 'react'
import { Document, Page, pdfjs } from 'react-pdf'

import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

// pdf.js parses the file in a background worker; it must be configured in the module that renders the PDF
pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString()

const ZOOM = [0.5, 0.75, 1, 1.25, 1.5, 2]
const PAGE_RATIO = 1.414 // A4 height / width, for placeholders

// Draws every page of the PDF in the page itself (no browser plugin), fitted to the width of the card.
export default function PdfViewer({ url }: { url: string }) {
  const t = useTranslations('detail')
  const box = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(0)
  const [pages, setPages] = useState(0)
  const [zoom, setZoom] = useState(ZOOM.indexOf(1))

  // Follow the width of the box, so pages fit when the window or the sidebar changes
  useEffect(() => {
    const element = box.current
    if (!element) return
    const observer = new ResizeObserver(([entry]) =>
      setWidth(Math.floor(entry.contentRect.width)),
    )
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  const pageWidth = width * ZOOM[zoom]
  const placeholder = (
    <Skeleton className='w-full' style={{ height: pageWidth * PAGE_RATIO }} />
  )

  return (
    <div className='flex flex-col gap-2'>
      <div className='flex items-center justify-between gap-2'>
        <span className='text-sm text-muted-foreground'>
          {pages > 0 && t('pageCount', { count: pages })}
        </span>
        <div className='flex items-center gap-1'>
          <Button
            variant='ghost'
            size='icon-sm'
            aria-label={t('zoomOut')}
            disabled={zoom === 0}
            onClick={() => setZoom(zoom - 1)}
          >
            <Minus />
          </Button>
          <span className='w-12 text-center font-mono text-xs tabular-nums'>
            {Math.round(ZOOM[zoom] * 100)}%
          </span>
          <Button
            variant='ghost'
            size='icon-sm'
            aria-label={t('zoomIn')}
            disabled={zoom === ZOOM.length - 1}
            onClick={() => setZoom(zoom + 1)}
          >
            <Plus />
          </Button>
        </div>
      </div>

      <div
        ref={box}
        className='h-[75vh] min-h-[520px] overflow-auto rounded-md'
      >
        {width > 0 && (
          <Document
            file={url}
            suspense={false}
            onLoadSuccess={({ numPages }) => setPages(numPages)}
            loading={placeholder}
            error={
              <p className='p-6 text-center text-muted-foreground'>
                {t('pdfError')}
              </p>
            }
            // Links inside the PDF open in a new tab and get no access to this page
            externalLinkTarget='_blank'
            externalLinkRel='noopener noreferrer'
            // w-fit + min-w-full: centered when small, scrollable sideways when zoomed in
            className='mx-auto flex w-fit min-w-full flex-col items-center gap-3'
          >
            {Array.from({ length: pages }, (_, index) => (
              <Page
                key={index}
                pageNumber={index + 1}
                width={pageWidth}
                loading={placeholder}
                className='overflow-hidden rounded-sm border shadow-sm'
              />
            ))}
          </Document>
        )}
      </div>
    </div>
  )
}
