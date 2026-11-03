import { useState } from 'react'
import { api, ApiError } from '../services/api'
import { isSafeLink, LANGUAGE_LABELS, VERIFICATION_LABELS } from '../services/drafts'
import { CLAIM_TYPE_LABELS, METHOD_LABELS, usableClaims } from '../services/news'
import type { Article, GenerationResult, LanguageCode } from '../types/api'

interface Props {
  article: Article
  timezone: string
  canAct: boolean
  onChange: (a: Article) => void
  onDrafted: (r: GenerationResult) => void
}

function host(url: string) {
  try { return new URL(url).hostname.replace(/^www\./, '') } catch { return url }
}

function Links({ urls, tone }: { urls: string[]; tone: string }) {
  return (
    <span className="flex flex-wrap gap-x-2">
      {urls.map((u) => isSafeLink(u)
        ? <a key={u} href={u} target="_blank" rel="noopener noreferrer" className={`${tone} hover:underline`}>{host(u)}</a>
        : <span key={u}>{u}</span>)}
    </span>
  )
}

export function ArticleCard({ article: a, timezone, canAct, onChange, onDrafted }: Props) {
  const [busy, setBusy] = useState<null | 'analyse' | 'verify' | 'draft'>(null)
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState(a.analysisStatus === 'ANALYSED')
  const when = a.publishedAt ?? a.retrievedAt
  const usable = usableClaims(a.claims)

  const act = async (kind: 'analyse' | 'verify' | 'draft') => {
    setBusy(kind); setError(null)
    try {
      if (kind === 'draft') {
        onDrafted(await api.post<GenerationResult>(`/api/news/articles/${a.id}/draft`, {}))
        onChange({ ...a, usedInDraft: true })
      } else {
        onChange(await api.post<Article>(`/api/news/articles/${a.id}/${kind}`))
        setOpen(true)
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Request failed')
    } finally {
      setBusy(null)
    }
  }

  return (
    <article className="rounded-xl border border-line bg-white p-5">
      <div className="flex flex-wrap items-center gap-2 text-xs text-muted">
        {a.focusMatch && <span className="rounded-full bg-amber-100 px-2 py-0.5 font-medium text-amber-900">★ Focus</span>}
        <span className="font-medium text-ink">{a.sourceName}</span>
        {a.primarySource && <span className="rounded-full bg-emerald-50 px-2 py-0.5 font-medium text-emerald-800">Official source</span>}
        <span>· {new Date(when).toLocaleString('en-IN', { timeZone: timezone, dateStyle: 'medium', timeStyle: 'short' })}</span>
        {a.category && <span className="rounded-full bg-paper px-2 py-0.5 font-medium text-ink">{a.category}</span>}
        {a.language && <span>{LANGUAGE_LABELS[a.language as LanguageCode]}</span>}
        {a.usedInDraft && <span className="rounded-full bg-blue-50 px-2 py-0.5 font-medium text-blue-800">Used in a draft</span>}
      </div>

      <h3 lang={a.language === 'ta' ? 'ta' : 'en'} className="mt-2 font-semibold leading-snug">
        {isSafeLink(a.url) ? <a href={a.url} target="_blank" rel="noopener noreferrer" className="hover:underline">{a.title}</a> : a.title}
      </h3>
      {a.summary && <p className="mt-1 text-sm text-muted">{a.summary}</p>}
      {a.analysisStatus === 'FAILED' && a.analysisError && <p className="mt-2 text-xs text-red-700">Analysis failed: {a.analysisError}</p>}
      {error && <p className="mt-2 text-xs text-red-700">{error}</p>}

      {canAct && a.analysisStatus !== 'IRRELEVANT' && (
        <div className="mt-3 flex flex-wrap gap-2">
          {a.analysisStatus !== 'ANALYSED' ? (
            <button disabled={busy !== null} onClick={() => void act('analyse')}
                    className="rounded-lg bg-accent px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50">
              {busy === 'analyse' ? 'Analysing and checking…' : 'Analyse & fact-check'}
            </button>
          ) : (
            <>
              <button disabled={busy !== null || usable === 0} onClick={() => void act('draft')}
                      title={usable === 0 ? 'No verified or attributable claims' : undefined}
                      className="rounded-lg bg-accent px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50">
                {busy === 'draft' ? 'Drafting…' : `Draft from this (${usable} usable claim${usable === 1 ? '' : 's'})`}
              </button>
              <button disabled={busy !== null} onClick={() => void act('verify')}
                      className="rounded-lg border border-line px-3 py-1.5 text-xs font-medium hover:bg-paper disabled:opacity-50">
                {busy === 'verify' ? 'Re-checking…' : 'Re-check against newer reports'}
              </button>
            </>
          )}
        </div>
      )}

      {a.analysisStatus === 'ANALYSED' && (
        <div className="mt-3 border-t border-line pt-3">
          <button onClick={() => setOpen(!open)} className="text-xs font-medium text-accent hover:underline">
            {open ? 'Hide claims' : `Show ${a.claims.length} claim${a.claims.length === 1 ? '' : 's'}`}
          </button>
          {open && (
            <ul className="mt-2 space-y-3">
              {a.claims.length === 0 && <li className="text-sm text-muted">No checkable claims found.</li>}
              {a.claims.map((c) => {
                const v = VERIFICATION_LABELS[c.verificationStatus]
                return (
                  <li key={c.id} className="text-sm">
                    <div className="flex flex-wrap items-center gap-2 text-xs">
                      <span className={`rounded-full px-2 py-0.5 font-medium ${v.tone}`}>
                        {c.verificationStatus === 'PARTIALLY_VERIFIED' ? 'Single source' : v.label}
                      </span>
                      <span className="text-muted">{CLAIM_TYPE_LABELS[c.type]}{c.attributedTo ? ` · ${c.attributedTo}` : ''}</span>
                      {c.verificationMethod && <span className="text-muted">· {METHOD_LABELS[c.verificationMethod] ?? c.verificationMethod}</span>}
                    </div>
                    <p className="mt-1">{c.text}</p>
                    {c.supportingUrls.length > 0 && (
                      <div className="mt-1 flex gap-1 text-xs text-muted">Reported by: <Links urls={c.supportingUrls} tone="text-accent" /></div>
                    )}
                    {c.conflictingUrls.length > 0 && (
                      <div className="mt-1 flex gap-1 text-xs text-red-700">Disputed by: <Links urls={c.conflictingUrls} tone="text-red-700" /></div>
                    )}
                    {c.verificationNotes && <p className="mt-1 text-xs text-muted">{c.verificationNotes}</p>}
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      )}
    </article>
  )
}
