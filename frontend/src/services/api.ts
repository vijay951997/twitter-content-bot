import type { ApiErrorBody } from '../types/api'

export class ApiError extends Error {
  readonly status: number
  readonly body?: ApiErrorBody

  constructor(status: number, message: string, body?: ApiErrorBody) {
    super(message)
    this.status = status
    this.body = body
  }
}

export function readCookie(cookieString: string, name: string): string | null {
  for (const part of cookieString.split(';')) {
    const [k, ...v] = part.trim().split('=')
    if (k === name) return decodeURIComponent(v.join('='))
  }
  return null
}

const xsrf = () => readCookie(document.cookie, 'XSRF-TOKEN')

/** Ensures the XSRF-TOKEN cookie exists before a state-changing request. */
async function primeCsrf(): Promise<void> {
  if (!xsrf()) await fetch('/api/auth/csrf', { credentials: 'same-origin' })
}

async function request<T>(method: string, url: string, body?: unknown, form?: URLSearchParams): Promise<T> {
  const headers: Record<string, string> = {}
  if (method !== 'GET') {
    await primeCsrf()
    const token = xsrf()
    if (token) headers['X-XSRF-TOKEN'] = token
  }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const res = await fetch(url, {
    method,
    headers,
    credentials: 'same-origin',
    body: form ?? (body !== undefined ? JSON.stringify(body) : undefined),
  })

  if (!res.ok) {
    let parsed: ApiErrorBody | undefined
    try { parsed = await res.json() } catch { /* empty body */ }
    throw new ApiError(res.status, parsed?.message ?? res.statusText, parsed)
  }
  if (res.status === 204 || res.headers.get('content-length') === '0') return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T>(url: string) => request<T>('GET', url),
  put: <T>(url: string, body: unknown) => request<T>('PUT', url, body),
  post: <T>(url: string, body?: unknown) => request<T>('POST', url, body),
  login: (username: string, password: string) =>
    request<void>('POST', '/api/auth/login', undefined, new URLSearchParams({ username, password })),
  logout: () => request<void>('POST', '/api/auth/logout'),
}
