import { useState, type FormEvent } from 'react'
import { api, ApiError } from '../services/api'
import { LANGUAGE_LABELS, SLOT_LABELS, parseUrls } from '../services/drafts'
import type { GenerationResult, LanguageCode, Slot } from '../types/api'
import { ErrorBanner } from './ErrorBanner'

interface Props {
  enabledLanguages: string[]
  onGenerated: (result: GenerationResult) => void
}

const input = 'mt-1 block w-full rounded-lg border border-line px-3 py-2 text-sm'

export function GeneratePanel({ enabledLanguages, onGenerated }: Props) {
  const [slot, setSlot] = useState<Slot>('MORNING')
  const [language, setLanguage] = useState<'' | LanguageCode>('')
  const [topic, setTopic] = useState('')
  const [notes, setNotes] = useState('')
  const [urls, setUrls] = useState('')
  const [busy, setBusy] = useState<null | 'one' | 'daily'>(null)
  const [error, setError] = useState<string | null>(null)

  const run = async (kind: 'one' | 'daily') => {
    setBusy(kind)
    setError(null)
    try {
      const result = kind === 'one'
        ? await api.post<GenerationResult>('/api/drafts/generate', {
            slot,
            language: language || undefined,
            topic: topic || undefined,
            sourceNotes: notes || undefined,
            sourceUrls: parseUrls(urls),
          })
        : await api.post<GenerationResult>('/api/drafts/generate-daily')
      onGenerated(result)
      if (kind === 'one' && result.created.length > 0) {
        setTopic(''); setNotes(''); setUrls('')
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Generation failed')
    } finally {
      setBusy(null)
    }
  }

  const submit = (e: FormEvent) => { e.preventDefault(); void run('one') }

  return (
    <form onSubmit={submit} className="space-y-4 rounded-xl border border-line bg-white p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold">Generate drafts</h2>
          <p className="text-xs text-muted">
            Paste the facts you want covered, or draft from a fact-checked article on the News page.
            Without source material, the AI writes a discussion post with no factual claims.
          </p>
        </div>
        <button type="button" disabled={busy !== null} onClick={() => void run('daily')}
                className="rounded-lg border border-line px-3 py-2 text-sm font-medium hover:bg-paper disabled:opacity-50">
          {busy === 'daily' ? 'Generating 3 slots…' : "Generate today's 3 drafts"}
        </button>
      </div>

      <ErrorBanner message={error} />

      <div className="grid gap-4 md:grid-cols-3">
        <label className="text-sm">
          <span className="font-medium">Slot</span>
          <select className={input} value={slot} onChange={(e) => setSlot(e.target.value as Slot)}>
            {(Object.keys(SLOT_LABELS) as Slot[]).map((s) => <option key={s} value={s}>{SLOT_LABELS[s]}</option>)}
          </select>
        </label>
        <label className="text-sm">
          <span className="font-medium">Language</span>
          <select className={input} value={language} onChange={(e) => setLanguage(e.target.value as '' | LanguageCode)}>
            <option value="">Auto (from language mix)</option>
            {enabledLanguages.map((l) => (
              <option key={l} value={l}>{LANGUAGE_LABELS[l as LanguageCode] ?? l}</option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="font-medium">Topic <span className="font-normal text-muted">(optional)</span></span>
          <input className={input} maxLength={300} value={topic} onChange={(e) => setTopic(e.target.value)}
                 placeholder="e.g. Chennai suburban bus services" />
        </label>
      </div>

      <label className="block text-sm">
        <span className="font-medium">Source material <span className="font-normal text-muted">(optional — key points from a news report or press release)</span></span>
        <textarea className={`${input} min-h-28`} maxLength={8000} value={notes} onChange={(e) => setNotes(e.target.value)}
                  placeholder="Paste or summarise the facts. Only these facts can appear in the draft." />
      </label>

      <label className="block text-sm">
        <span className="font-medium">Source links <span className="font-normal text-muted">(optional, up to 5 — the only links a draft may cite)</span></span>
        <textarea className={`${input} min-h-16 font-mono text-xs`} value={urls} onChange={(e) => setUrls(e.target.value)}
                  placeholder="https://… (one per line). A dipr.tn.gov.in link counts as an official source." />
      </label>

      <button disabled={busy !== null} className="rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white disabled:opacity-50">
        {busy === 'one' ? 'Generating… (can take up to a minute)' : 'Generate drafts'}
      </button>
    </form>
  )
}
