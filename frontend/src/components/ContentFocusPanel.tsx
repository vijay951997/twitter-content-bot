import { useEffect, useState } from 'react'
import { api, ApiError } from '../services/api'
import type { ContentFocus } from '../types/api'

const input = 'mt-1 block w-full rounded-lg border border-line px-3 py-2 text-sm disabled:bg-paper'

function list(text: string): string[] {
  return Array.from(new Set(text.split(/[,\n]+/).map((s) => s.trim()).filter(Boolean)))
}

export function ContentFocusPanel({ canEdit }: { canEdit: boolean }) {
  const [f, setF] = useState<ContentFocus | null>(null)
  const [tags, setTags] = useState('')
  const [keywords, setKeywords] = useState('')
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    void api.get<ContentFocus>('/api/settings/focus').then((v) => {
      setF(v); setTags(v.hashtags.join(', ')); setKeywords(v.focusKeywords.join(', '))
    })
  }, [])

  if (!f) return null
  const set = <K extends keyof ContentFocus>(k: K, v: ContentFocus[K]) => setF({ ...f, [k]: v })

  const save = async () => {
    setBusy(true); setMsg(null)
    try {
      const body = { ...f, hashtags: list(tags).map((t) => (t.startsWith('#') ? t : `#${t}`)), focusKeywords: list(keywords) }
      const v = await api.put<ContentFocus>('/api/settings/focus', body)
      setF(v); setTags(v.hashtags.join(', ')); setKeywords(v.focusKeywords.join(', '))
      setMsg({ ok: true, text: 'Content focus saved. New drafts will use it.' })
    } catch (e) {
      const err = e instanceof ApiError ? (e.body?.fieldErrors?.map((x) => `${x.field}: ${x.message}`).join(', ') || e.message) : 'Save failed'
      setMsg({ ok: false, text: err })
    } finally { setBusy(false) }
  }

  return (
    <section className="space-y-4 rounded-xl border border-line bg-white p-5">
      <div>
        <h2 className="font-semibold">Content focus</h2>
        <p className="text-xs text-muted">
          Whose voice the drafts use and what they cover. The honesty rules can't be changed here: no made-up facts,
          figures or quotes, and no personal attacks. Fact-checking stays neutral.
        </p>
      </div>
      {msg && <div className={`rounded-lg px-3 py-2 text-sm ${msg.ok ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-700'}`}>{msg.text}</div>}

      <label className="block text-sm"><span className="font-medium">Voice</span>
        <textarea disabled={!canEdit} rows={3} maxLength={1500} className={input} value={f.voice} onChange={(e) => set('voice', e.target.value)} /></label>

      <div className="grid gap-4 md:grid-cols-3">
        <label className="text-sm"><span className="font-medium">Morning theme</span>
          <textarea disabled={!canEdit} rows={3} maxLength={300} className={input} value={f.themeMorning} onChange={(e) => set('themeMorning', e.target.value)} /></label>
        <label className="text-sm"><span className="font-medium">Afternoon theme</span>
          <textarea disabled={!canEdit} rows={3} maxLength={300} className={input} value={f.themeAfternoon} onChange={(e) => set('themeAfternoon', e.target.value)} /></label>
        <label className="text-sm"><span className="font-medium">Evening theme</span>
          <textarea disabled={!canEdit} rows={3} maxLength={300} className={input} value={f.themeEvening} onChange={(e) => set('themeEvening', e.target.value)} /></label>
      </div>

      <div className="grid gap-4 md:grid-cols-[1fr_auto]">
        <label className="text-sm"><span className="font-medium">Hashtags</span> <span className="text-xs text-muted">(most relevant first, comma separated)</span>
          <input disabled={!canEdit} className={input} value={tags} onChange={(e) => setTags(e.target.value)} /></label>
        <label className="text-sm"><span className="font-medium">Max per post</span>
          <input type="number" min={0} max={5} disabled={!canEdit} className={`${input} w-24`} value={f.maxHashtags}
                 onChange={(e) => set('maxHashtags', Number(e.target.value))} /></label>
      </div>
      <p className="-mt-2 text-xs text-muted">
        The AI picks the most relevant ones from this list and may add one for the specific scheme or event.
        Live trending data isn't used: the X API charges per lookup. Update this list when you see a tag trending.
      </p>

      <label className="block text-sm"><span className="font-medium">Focus keywords</span> <span className="text-xs text-muted">(English or Tamil, comma separated)</span>
        <textarea disabled={!canEdit} rows={2} className={input} value={keywords} onChange={(e) => setKeywords(e.target.value)} /></label>
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" disabled={!canEdit} checked={f.focusOnly} onChange={(e) => set('focusOnly', e.target.checked)} />
        Only keep news that matches these keywords (otherwise matching news is preferred, others kept)
      </label>

      {canEdit && (
        <button disabled={busy} onClick={() => void save()} className="rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white disabled:opacity-50">
          {busy ? 'Saving…' : 'Save content focus'}
        </button>
      )}
    </section>
  )
}
