import { ApiError, apiRequest } from '@/lib/api'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/**
 * Original document of an invoice, for the viewer and the download button. The browser cannot call the API
 * itself (the token stays on the server), so this route fetches the file with the user's token and passes it on.
 */
export async function GET(
  _request: Request,
  { params }: RouteContext<'/files/[id]'>,
) {
  const { id } = await params
  if (!UUID.test(id)) {
    return new Response('Not found', { status: 404 })
  }

  let upstream: Response
  try {
    upstream = await apiRequest(`/api/documents/${id}/content`)
  } catch (error) {
    const status = error instanceof ApiError ? error.status : 502
    return new Response(null, { status })
  }

  return new Response(upstream.body, {
    status: 200,
    headers: {
      'Content-Type':
        upstream.headers.get('content-type') ?? 'application/octet-stream',
      'Content-Disposition':
        upstream.headers.get('content-disposition') ?? 'inline',
      // The type was decided by the backend from the file itself; the browser must not guess another one
      'X-Content-Type-Options': 'nosniff',
      // Even if a file is opened directly, it cannot run scripts or reach this site
      'Content-Security-Policy': 'sandbox',
      // Invoices are confidential: no copy in browser or proxy caches
      'Cache-Control': 'no-store',
    },
  })
}
