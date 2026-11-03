import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, ApiError } from '../services/api'
import type { XAccountStatus } from '../types/api'

const REASONS: Record<string, string> = {
  denied: 'You cancelled on X, so nothing was connected.',
  state: 'The sign-in link expired or was used twice. Try Connect again.',
  expired: 'The sign-in took longer than 10 minutes. Try Connect again.',
  code: 'X did not return an authorisation code. Try again.',
  exchange: 'X refused the connection. Check X_CLIENT_ID, X_CLIENT_SECRET and the callback URL in your X app.',
}

export function XConnectionPanel({ canEdit }: { canEdit: boolean }) {
  const [params, setParams] = useSearchParams()
  const [status, setStatus] = useState<XAccountStatus | null>(null)
  const [publishing, setPublishing] = useState<boolean | null>(null)
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null)
  const [busy, setBusy] = useState(false)

  const load = async () => {
    setStatus(await api.get<XAccountStatus>('/api/x/status'))
    setPublishing((await api.get<{ enabled: boolean }>('/api/settings/publishing')).enabled)
  }

  useEffect(() => {
    void load()
    const x = params.get('x')
    if (x === 'connected') setMsg({ ok: true, text: 'X account connected.' })
    if (x === 'error') setMsg({ ok: false, text: REASONS[params.get('reason') ?? ''] ?? 'Connecting X failed.' })
    if (x) { params.delete('x'); params.delete('reason'); setParams(params, { replace: true }) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const disconnect = async () => {
    setBusy(true)
    try { setStatus(await api.post<XAccountStatus>('/api/x/disconnect')); setMsg({ ok: true, text: 'Disconnected. Tokens were revoked and deleted.' }) }
    catch (e) { setMsg({ ok: false, text: e instanceof ApiError ? e.message : 'Disconnect failed' }) }
    finally { setBusy(false) }
  }

  const togglePublishing = async (on: boolean) => {
    setBusy(true)
    try { setPublishing((await api.put<{ enabled: boolean }>('/api/settings/publishing', { enabled: on })).enabled) }
    catch (e) { setMsg({ ok: false, text: e instanceof ApiError ? e.message : 'Could not change the switch' }) }
    finally { setBusy(false) }
  }

  if (!status) return null
  const ready = status.appConfigured && status.encryptionKeyConfigured

  return (
    <section className="space-y-4 rounded-xl border border-line bg-white p-5">
      <div>
        <h2 className="font-semibold">X account</h2>
        <p className="text-xs text-muted">You sign in on x.com. This app never sees your X password; tokens are stored encrypted.</p>
      </div>
      {msg && <div className={`rounded-lg px-3 py-2 text-sm ${msg.ok ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-700'}`}>{msg.text}</div>}

      {!ready && (
        <div className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">
          Add these to <code>.env</code> and restart the backend: <b>{status.missing}</b>
        </div>
      )}

      {status.connected ? (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="text-sm">Connected as <b>@{status.username}</b></div>
          {canEdit && <button disabled={busy} onClick={() => void disconnect()} className="rounded-lg border border-line px-3 py-1.5 text-sm hover:bg-paper">Disconnect</button>}
        </div>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="text-sm text-muted">
            {status.status === 'EXPIRED' ? `Connection to @${status.username} expired. Reconnect to keep posting.` : 'Not connected.'}
          </div>
          {canEdit && (
            <a href="/api/x/connect" aria-disabled={!ready}
               className={`rounded-lg px-4 py-2 text-sm font-medium text-white ${ready ? 'bg-ink hover:opacity-90' : 'pointer-events-none bg-slate-300'}`}>
              {status.status === 'EXPIRED' ? 'Reconnect X' : 'Connect X account'}
            </a>
          )}
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4">
        <div>
          <div className="text-sm font-medium">Publishing switch</div>
          <div className="text-xs text-muted">
            {publishing ? 'On: approved posts go out at their scheduled time.' : 'Off: approved posts wait. Turn on when you are ready.'}
            {' '}Only posts you approved are ever sent.
          </div>
        </div>
        {canEdit && (
          <button disabled={busy || publishing === null} onClick={() => void togglePublishing(!publishing)}
                  className={`relative h-6 w-11 rounded-full transition ${publishing ? 'bg-emerald-600' : 'bg-slate-300'}`}
                  role="switch" aria-checked={!!publishing} aria-label="Publishing switch">
            <span className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition ${publishing ? 'left-[22px]' : 'left-0.5'}`} />
          </button>
        )}
      </div>
    </section>
  )
}
