'use client'

import { Moon, Sun } from 'lucide-react'
import { useTheme } from 'next-themes'

import { Button } from '@/components/ui/button'

// Switches between light and dark. Both icons are always rendered and CSS shows the right one,
// so the server and the browser render the same HTML (no hydration mismatch).
export function ThemeToggle({ label }: { label: string }) {
  const { resolvedTheme, setTheme } = useTheme()

  return (
    <Button
      variant='outline'
      size='icon'
      aria-label={label}
      title={label}
      onClick={() => setTheme(resolvedTheme === 'dark' ? 'light' : 'dark')}
    >
      <Moon className='dark:hidden' aria-hidden='true' />
      <Sun className='hidden dark:block' aria-hidden='true' />
    </Button>
  )
}
