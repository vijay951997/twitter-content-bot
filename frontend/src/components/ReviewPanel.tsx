import { useState } from 'react'
import { api, ApiError } from '../services/api'
import { fromLocalInput, hasLink, LINK_COST_NOTE, toLocalInput } from '../services/review'
import { xLength, X_MAX } from '../services/xlength'
import type { Draft, GenerationResult } from '../types/api'

interface Props {
  draft: Draft
  timezone: string
  onChange: (d: Draft) => void
  onRegenerated: (r: GenerationResult) => void
}

type Mode = null | 'edit' | 'approve' | 'reject'

function err(e: unknown) {
  return e instanceof ApiError ? e.message : 'Request failed'
}

/** Warnings the reviewer must tick before approving (the backend enforces the same list). */
export function approvalWarnings(d: Draft): string[] {
  const w: string[] = []
  if (d.verificationStatus === 'CONFLICTING_INFORMATION') w.push('Sources conflict on this topic')
  if (d.unverifiedClaims.length > 0) w.push('It contains unverified claims')
  if (d.riskFlags.some((f) => f !== 'HAS_UNVERIFIED_CLAIMS')) w.push('It has risk warnings')
  if (d.editedByHuman) w.push('You edited it — re-check the facts')
  if (d.duplicateWarning) w.push('It is similar to an earlier draft')
  if (hasLink(d.content)) w.push('It contains a link (costs more on X)')
  return w
}

export function ReviewPanel({ draft: d, timezone, onChange, onRegenerated }: Props) {
  const [mode, setMode] = useState<Mode>(null)
  const [text, setText] = useState(d.content)
  const [when, setWhen] = useState(toLocalInput(d.proposedPublishAt, timezone))
  const [now, setNow] = useState(false)
  const [ack, setAck] = useState(false)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const run = async (fn: () => Promise<void>) => {
    setBusy(true); setError(null)
    try { await fn(); setMode(null) } catch (e) { setError(err(e)) } finally { setBusy(false) }
  }

  const reviewable = d.status === 'GENERATED' || d.status === 'UNDER_REVIEW'
  const editable = reviewable || d.status === 'SCHEDULED'
  const len = xLength(text)
  const warnings = approvalWarnings(d)
  const btn = 'rounded-lg px-3 py-1.5 text-xs font-medium disabled:opacity-50'

  return (
    <div className="mt-3 space-y-3 border-t border-line pt-3">
      {error && <p className="text-xs text-red-700">{error}</p>}

      {mode === null && (
        <div className="flex flex-wrap gap-2">
          {reviewable && (
            <button onClick={() => { setMode('approve'); setAck(false) }} className={`${btn} bg-emerald-600 text-white`}>Approve & schedule</button>
          )}
          {editable && (
            <button onClick={() => { setText(d.content); setMode('edit') }} className={`${btn} border border-line hover:bg-paper`}>Edit</button>
          )}
          {reviewable && (
            <>
              <button onClick={() => setMode('reject')} className={`${btn} border border-line text-red-700 hover:bg-red-50`}>Reject</button>
              <button disabled={busy} onClick={() => void run(async () => onRegenerated(await api.post(`/api/drafts/${d.id}/regenerate`)))}
                      className={`${btn} border border-line hover:bg-paper`}>{busy ? 'Regenerating…' : 'Regenerate'}</button>
              <button disabled={busy} onClick={() => void run(async () => onChange(await api.post(`/api/drafts/${d.id}/save-for-later?saved=${!d.savedForLater}`)))}
                      className={`${btn} border border-line hover:bg-paper`}>{d.savedForLater ? 'Unsave' : 'Save for later'}</button>
            </>
          )}
          {d.status === 'SCHEDULED' && (
            <button disabled={busy} onClick={() => void run(async () => onChange(await api.post(`/api/drafts/${d.id}/unschedule`, { version: d.version })))}
                    className={`${btn} border border-line hover:bg-paper`}>Unschedule</button>
          )}
          {d.status === 'FAILED' && (
            <>
              <button disabled={busy} onClick={() => void run(async () => onChange(await api.post(`/api/drafts/${d.id}/retry`, { version: d.version })))}
                      className={`${btn} bg-accent text-white`}>Retry posting</button>
              <button disabled={busy} onClick={() => void run(async () => onChange(await api.post(`/api/drafts/${d.id}/cancel`, { version: d.version })))}
                      className={`${btn} border border-line hover:bg-paper`}>Give up</button>
            </>
          )}
        </div>
      )}

      {mode === 'edit' && (
        <div className="space-y-2">
          {d.status === 'SCHEDULED' && (
            <p className="rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-800">Editing removes the approval and the schedule. You'll need to approve it again.</p>
          )}
          <textarea value={text} onChange={(e) => setText(e.target.value)} rows={4} lang={d.language === 'ta' ? 'ta' : 'en'}
                    className="w-full rounded-lg border border-line px-3 py-2 text-[15px]" />
          <div className="flex items-center justify-between text-xs">
            <span className={len > X_MAX ? 'font-medium text-red-600' : 'text-muted'}>{len}/{X_MAX}</span>
            <div className="flex gap-2">
              <button onClick={() => setMode(null)} className={`${btn} border border-line`}>Cancel</button>
              <button disabled={busy || len > X_MAX || !text.trim()} className={`${btn} bg-accent text-white`}
                      onClick={() => void run(async () => onChange(await api.put(`/api/drafts/${d.id}`, { content: text, version: d.version })))}>
                Save edit
              </button>
            </div>
          </div>
        </div>
      )}

      {mode === 'approve' && (
        <div className="space-y-3 rounded-lg border border-emerald-200 bg-emerald-50/50 p-3 text-sm">
          <div className="font-medium">Approve this exact text for posting</div>
          <div className="flex flex-wrap items-center gap-4">
            <label className="flex items-center gap-2">
              <input type="radio" checked={!now} onChange={() => setNow(false)} /> Post at
              <input type="datetime-local" disabled={now} value={when} onChange={(e) => setWhen(e.target.value)}
                     className="rounded-md border border-line px-2 py-1 text-sm" />
              <span className="text-xs text-muted">{timezone}</span>
            </label>
            <label className="flex items-center gap-2"><input type="radio" checked={now} onChange={() => setNow(true)} /> Post now</label>
          </div>
          {warnings.length > 0 && (
            <div className="rounded-md bg-amber-50 p-2 text-xs text-amber-900">
              <ul className="list-disc pl-5">{warnings.map((w) => <li key={w}>{w}</li>)}</ul>
              {hasLink(d.content) && <p className="mt-1">{LINK_COST_NOTE}</p>}
              <label className="mt-2 flex items-center gap-2 font-medium">
                <input type="checkbox" checked={ack} onChange={(e) => setAck(e.target.checked)} /> I've checked these
              </label>
            </div>
          )}
          <div className="flex gap-2">
            <button onClick={() => setMode(null)} className={`${btn} border border-line bg-white`}>Cancel</button>
            <button disabled={busy || (warnings.length > 0 && !ack)} className={`${btn} bg-emerald-600 text-white`}
                    onClick={() => void run(async () => onChange(await api.post(`/api/drafts/${d.id}/approve`, {
                      version: d.version, publishNow: now, acknowledgeWarnings: ack,
                      publishAt: now ? undefined : fromLocalInput(when, timezone),
                    })))}>
              {busy ? 'Approving…' : now ? 'Approve & post now' : 'Approve & schedule'}
            </button>
          </div>
        </div>
      )}

      {mode === 'reject' && (
        <div className="flex flex-wrap items-center gap-2">
          <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} placeholder="Reason (optional)"
                 className="min-w-48 flex-1 rounded-lg border border-line px-3 py-1.5 text-sm" />
          <button onClick={() => setMode(null)} className={`${btn} border border-line`}>Cancel</button>
          <button disabled={busy} className={`${btn} bg-red-600 text-white`}
                  onClick={() => void run(async () => onChange(await api.post(`/api/drafts/${d.id}/reject`, { version: d.version, reason })))}>
            Reject draft
          </button>
        </div>
      )}
    </div>
  )
}
