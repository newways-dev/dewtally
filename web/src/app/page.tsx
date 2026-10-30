import { Button } from '@/components/ui/button'

// Temporary page to check colours and fonts in both themes; replaced by the app shell in step 2.5.
const statuses = [
  {
    label: 'Needs review',
    className: 'bg-status-review text-status-review-foreground',
  },
  {
    label: 'Invalid',
    className: 'bg-status-invalid text-status-invalid-foreground',
  },
  { label: 'Valid', className: 'bg-status-valid text-status-valid-foreground' },
  {
    label: 'Accepted',
    className: 'bg-status-accepted text-status-accepted-foreground',
  },
  {
    label: 'Rejected',
    className: 'bg-status-rejected text-status-rejected-foreground',
  },
]

function Preview() {
  return (
    <div className='flex flex-1 flex-col gap-6 bg-background p-8 text-foreground'>
      <div>
        <h1 className='text-2xl font-semibold tracking-tight'>Invoices</h1>
        <p className='text-muted-foreground'>Incoming invoices of Demo GmbH</p>
        <p className='text-sm text-subtle'>Subtle text for hints</p>
      </div>

      <div className='flex flex-wrap gap-2'>
        <Button>Approve for payment</Button>
        <Button variant='secondary'>Correct data</Button>
        <Button variant='outline'>Download</Button>
        <Button variant='destructive'>Reject</Button>
      </div>

      <div className='flex flex-wrap gap-2'>
        {statuses.map((status) => (
          <span
            key={status.label}
            className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ${status.className}`}
          >
            <span
              aria-hidden='true'
              className='size-1.5 rounded-full bg-current'
            />
            {status.label}
          </span>
        ))}
      </div>

      <div className='rounded-xl border bg-card p-4'>
        <div className='text-sm text-muted-foreground'>Amount due</div>
        <div className='font-mono text-3xl font-medium tracking-tight tabular-nums'>
          4.284,00 €
        </div>
        <div className='font-mono text-sm text-subtle'>
          DE02 1203 0000 0000 2020 51
        </div>
      </div>

      <div className='w-fit rounded-lg bg-brand-soft px-3 py-2 font-medium text-brand-soft-foreground'>
        Active menu item
      </div>
    </div>
  )
}

export default function Home() {
  return (
    <main className='grid flex-1 md:grid-cols-2'>
      <Preview />
      <div className='dark flex'>
        <Preview />
      </div>
    </main>
  )
}
