import { unstable_rethrow } from 'next/navigation'
import type { NextRequest } from 'next/server'

import { ApiError, apiRequest } from '@/lib/api'

/**
 * Upload of an invoice file from the upload page. The browser sends the file here, this route passes it on
 * to the API with the user's token. The API decides the format from the content and checks the user's role.
 */
export async function POST(request: NextRequest) {
  // Only our own pages may upload: another site must not send files in the name of a logged-in user (CSRF)
  if (request.headers.get('sec-fetch-site') !== 'same-origin') {
    return Response.json(
      { detail: 'Cross-site upload refused' },
      { status: 403 },
    )
  }

  const form = await request.formData()
  const file = form.get('file')
  if (!(file instanceof File) || file.size === 0) {
    return Response.json({ detail: 'No file' }, { status: 400 })
  }

  const body = new FormData()
  body.append('file', file, file.name)

  try {
    const response = await apiRequest('/api/documents', {
      method: 'POST',
      body,
    })
    // 201 processed now, 202 processed in the background, 200 the same file was uploaded before
    return Response.json(await response.json(), { status: response.status })
  } catch (error) {
    unstable_rethrow(error)
    if (error instanceof ApiError) {
      return Response.json({ detail: error.detail }, { status: error.status })
    }
    console.error('Upload failed', error)
    return Response.json({ detail: 'Upload failed' }, { status: 502 })
  }
}
