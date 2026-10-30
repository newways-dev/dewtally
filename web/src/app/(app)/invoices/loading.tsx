import { Skeleton } from '@/components/ui/skeleton'

// Shown while the invoice list loads: the same shape as the page, so nothing jumps when data arrives.
export default function Loading() {
  return (
    <div className='flex flex-col gap-6' aria-busy='true'>
      <Skeleton className='h-9 w-56' />
      <div className='grid grid-cols-[repeat(auto-fit,minmax(190px,1fr))] gap-3'>
        {Array.from({ length: 4 }, (_, i) => (
          <Skeleton key={i} className='h-28 rounded-xl' />
        ))}
      </div>
      <Skeleton className='h-96 rounded-xl' />
    </div>
  )
}
