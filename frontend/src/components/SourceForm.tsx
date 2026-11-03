import { useState, type FormEvent } from 'react'
import { SOURCE_TYPE_LABELS, TIER_LABELS } from '../services/news'
import { LANGUAGE_LABELS } from '../services/drafts'
import type { LanguageCode, NewsSourceInput, SourceType } from '../types/api'

const EMPTY: NewsSourceInput = {
  name: '', baseUrl: '', feedUrl: '', sourceType: 'TAMIL_NEWS', language: 'ta', primarySource: false,
  reliabilityTier: 2, reliabilityNotes: '', enabled: true, fetchIntervalMinutes: 60, requiresKeywordMatch: true,
}

const input = 'mt-1 block w-full rounded-lg border border-line px-3 py-2 text-sm'

export function SourceForm({ initial, onSave, onCancel }: {
  initial?: NewsSourceInput
  onSave: (s: NewsSourceInput) => Promise<void>
  onCancel: () => void
}) {
  const [s, setS] = useState<NewsSourceInput>(initial ?? EMPTY)
  const [busy, setBusy] = useState(false)
  const set = <K extends keyof NewsSourceInput>(k: K, v: NewsSourceInput[K]) => setS({ ...s, [k]: v })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    try { await onSave(s) } finally { setBusy(false) }
  }

  return (
    <form onSubmit={submit} className="space-y-4 rounded-xl border border-line bg-white p-5">
      <div className="grid gap-4 md:grid-cols-2">
        <label className="text-sm"><span className="font-medium">Name</span>
          <input required maxLength={150} className={input} value={s.name} onChange={(e) => set('name', e.target.value)} /></label>
        <label className="text-sm"><span className="font-medium">Type</span>
          <select className={input} value={s.sourceType} onChange={(e) => set('sourceType', e.target.value as SourceType)}>
            {(Object.keys(SOURCE_TYPE_LABELS) as SourceType[]).map((t) => <option key={t} value={t}>{SOURCE_TYPE_LABELS[t]}</option>)}
          </select></label>
        <label className="text-sm"><span className="font-medium">Website URL</span>
          <input required className={input} value={s.baseUrl} onChange={(e) => set('baseUrl', e.target.value)} placeholder="https://" /></label>
        <label className="text-sm"><span className="font-medium">RSS / Atom feed URL</span>
          <input className={input} value={s.feedUrl ?? ''} onChange={(e) => set('feedUrl', e.target.value || null)} placeholder="https://…/rss (leave empty for a manual source)" /></label>
        <label className="text-sm"><span className="font-medium">Language</span>
          <select className={input} value={s.language} onChange={(e) => set('language', e.target.value as LanguageCode)}>
            {(['ta', 'en', 'mixed'] as LanguageCode[]).map((l) => <option key={l} value={l}>{LANGUAGE_LABELS[l]}</option>)}
          </select></label>
        <label className="text-sm"><span className="font-medium">Reliability</span>
          <select className={input} value={s.reliabilityTier}
                  onChange={(e) => { const t = Number(e.target.value); setS({ ...s, reliabilityTier: t, primarySource: t === 1 ? s.primarySource : false }) }}>
            {[1, 2, 3].map((t) => <option key={t} value={t}>{TIER_LABELS[t]}</option>)}
          </select></label>
        <label className="text-sm"><span className="font-medium">Check every (minutes)</span>
          <input type="number" min={15} max={1440} className={input} value={s.fetchIntervalMinutes}
                 onChange={(e) => set('fetchIntervalMinutes', Number(e.target.value))} /></label>
        <label className="text-sm"><span className="font-medium">Notes</span>
          <input maxLength={1000} className={input} value={s.reliabilityNotes ?? ''} onChange={(e) => set('reliabilityNotes', e.target.value)} /></label>
      </div>
      <div className="flex flex-wrap gap-x-6 gap-y-2 text-sm">
        <label className="flex items-center gap-2"><input type="checkbox" checked={s.enabled} onChange={(e) => set('enabled', e.target.checked)} /> Enabled</label>
        <label className="flex items-center gap-2"><input type="checkbox" checked={s.requiresKeywordMatch}
               onChange={(e) => set('requiresKeywordMatch', e.target.checked)} /> Only keep Tamil Nadu-related items</label>
        <label className={`flex items-center gap-2 ${s.reliabilityTier !== 1 ? 'text-muted' : ''}`}>
          <input type="checkbox" disabled={s.reliabilityTier !== 1} checked={s.primarySource}
                 onChange={(e) => set('primarySource', e.target.checked)} /> Official primary source (government / EC / party)
        </label>
      </div>
      <p className="text-xs text-muted">Social media and blog platforms can't be added. Posts there are never treated as verified facts.</p>
      <div className="flex gap-2">
        <button disabled={busy} className="rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white disabled:opacity-50">
          {busy ? 'Saving…' : 'Save source'}
        </button>
        <button type="button" onClick={onCancel} className="rounded-lg border border-line px-4 py-2 text-sm">Cancel</button>
      </div>
    </form>
  )
}
