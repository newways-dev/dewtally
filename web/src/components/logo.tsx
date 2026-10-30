// dewtally mark: five tally marks, the way people count by hand.
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg
      width='28'
      height='28'
      viewBox='0 0 28 28'
      aria-hidden='true'
      className={className}
    >
      <rect width='28' height='28' rx='8' className='fill-primary' />
      <path
        d='M8 8v12M12 8v12M16 8v12M20 8v12M6 18L22 10'
        className='stroke-primary-foreground'
        strokeWidth='2'
        strokeLinecap='round'
      />
    </svg>
  )
}

export function Logo() {
  return (
    <span className='flex items-center gap-2.5'>
      <LogoMark className='shrink-0' />
      <span className='text-[17px] font-semibold tracking-tight'>dewtally</span>
    </span>
  )
}
