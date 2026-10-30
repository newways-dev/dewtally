import { redirect } from 'next/navigation'

// The invoice list is the start page.
export default function Home() {
  redirect('/invoices')
}
