'use client'

import dynamic from 'next/dynamic'

import { Skeleton } from '@/components/ui/skeleton'

// pdf.js needs browser APIs, so the viewer is loaded in the browser only, never rendered on the server
export const PdfPreview = dynamic(() => import('@/components/pdf-viewer'), {
  ssr: false,
  loading: () => <Skeleton className='h-[75vh] min-h-[520px] w-full' />,
})
