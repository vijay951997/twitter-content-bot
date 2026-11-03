import { useEffect, useState } from 'react'
import { useApi } from '../hooks/useApi'
import { useAuth } from '../hooks/useAuth'
import { api, ApiError } from '../services/api'
import { LANGUAGES, mixTotal } from '../services/settings'
import type { ContentSettings, ScheduleSettings } from '../types/api'
import { ErrorBanner } from '../components/ErrorBanner'
import { XConnectionPanel } from '../components/XConnectionPanel'

function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    const fields = e.body?.fieldErrors?.map((f) => `${f.field}: ${f.message}`).join(', ')
    return fields ? `${e.message} (${fields})` : e.message
  }
  return 'Save failed'
}

const slots: { key: keyof ScheduleSettings; label: string; hint: string }[] = [
  { key: 'morning', label: 'Morning', hint: 'Governance, announcements, infrastructure' },
  { key: 'afternoon', label: 'Afternoon', hint: 'TN politics, TVK announcements, legislature' },
  { key: 'evening', label: 'Evening', hint: 'Civic accountability, public-interest analysis' },
]

export function SettingsPage() {
  const { user } = useAuth()
  const canEdit = user?.role === 'ADMIN'
  const schedule = useApi<ScheduleSettings>('/api/settings/schedule')
  const content = useApi<ContentSettings>('/api/settings/content')

  const [sched, setSched] = useState<ScheduleSettings | null>(null)
  const [cont, setCont] = useState<ContentSettings | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const [err, setErr] = useState<string | null>(null)

  useEffect(() => { if (schedule.data) setSched(schedule.data) }, [schedule.data])
  useEffect(() => { if (content.data) setCont(content.data) }, [content.data])

  const save = async (fn: () => Promise<void>, label: string) => {
    setMsg(null); setErr(null)
    try { await fn(); setMsg(`${label} saved.`) } catch (e) { setErr(errorText(e)) }
  }

  const toggleLang = (code: string) => {
    if (!cont) return
    const enabled = cont.enabledLanguages.includes(code)
      ? cont.enabledLanguages.filter((c) => c !== code)
      : [...cont.enabledLanguages, code]
    const mix = Object.fromEntries(enabled.map((c) => [c, cont.languageMix[c] ?? 0]))
    setCont({ ...cont, enabledLanguages: enabled, languageMix: mix })
  }

  const total = cont ? mixTotal(cont.enabledLanguages, cont.languageMix) : 0
  const input = 'rounded-lg border border-line px-3 py-2 text-sm disabled:bg-paper'

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Settings</h1>
        <p className="text-sm text-muted">X account, publishing, schedule and languages. Every change is audited.</p>
      </header>

      <ErrorBanner message={err ?? schedule.error ?? content.error} />
      {msg && <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{msg}</div>}

      <XConnectionPanel canEdit={canEdit} />

      {sched && (
        <section className="rounded-xl border border-line bg-white p-5">
          <h2 className="font-semibold">Posting schedule</h2>
          <p className="text-xs text-muted">Drafts are prepared for these times. A draft that is not approved in time is skipped, never auto-posted.</p>
          <div className="mt-4 grid gap-4 md:grid-cols-3">
            {slots.map((s) => (
              <label key={s.key} className="text-sm">
                <span className="font-medium">{s.label}</span>
                <input type="time" disabled={!canEdit} className={`mt-1 block w-full ${input}`}
                       value={sched[s.key]} onChange={(e) => setSched({ ...sched, [s.key]: e.target.value })} />
                <span className="mt-1 block text-xs text-muted">{s.hint}</span>
              </label>
            ))}
          </div>
          <label className="mt-4 block text-sm">
            <span className="font-medium">Timezone</span>
            <input disabled={!canEdit} className={`mt-1 block w-full md:w-72 ${input}`} value={sched.timezone}
                   onChange={(e) => setSched({ ...sched, timezone: e.target.value })} />
          </label>
          {canEdit && (
            <button className="mt-4 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white"
                    onClick={() => void save(async () => setSched(await api.put('/api/settings/schedule', sched)), 'Schedule')}>
              Save schedule
            </button>
          )}
        </section>
      )}

      {cont && (
        <section className="rounded-xl border border-line bg-white p-5">
          <h2 className="font-semibold">Languages</h2>
          <div className="mt-4 space-y-3">
            {LANGUAGES.map((l) => {
              const on = cont.enabledLanguages.includes(l.code)
              return (
                <div key={l.code} className="flex items-center gap-4 text-sm">
                  <label className="flex w-32 items-center gap-2">
                    <input type="checkbox" disabled={!canEdit} checked={on} onChange={() => toggleLang(l.code)} />
                    {l.label}
                  </label>
                  {on && (
                    <label className="flex items-center gap-2">
                      <input type="number" min={0} max={100} disabled={!canEdit} className={`w-20 ${input}`}
                             value={cont.languageMix[l.code] ?? 0}
                             onChange={(e) => setCont({ ...cont, languageMix: { ...cont.languageMix, [l.code]: Number(e.target.value) } })} />
                      <span className="text-muted">%</span>
                    </label>
                  )}
                </div>
              )
            })}
          </div>
          <p className={`mt-2 text-xs ${total === 100 ? 'text-muted' : 'text-red-600'}`}>Total: {total}% (must be 100%)</p>

          <div className="mt-4 grid gap-4 md:grid-cols-2">
            <label className="text-sm">
              <span className="font-medium">Variations per topic</span>
              <input type="number" min={1} max={5} disabled={!canEdit} className={`mt-1 block w-24 ${input}`}
                     value={cont.variationsPerTopic} onChange={(e) => setCont({ ...cont, variationsPerTopic: Number(e.target.value) })} />
            </label>
            <label className="text-sm">
              <span className="font-medium">Duplicate check window (days)</span>
              <input type="number" min={1} max={365} disabled={!canEdit} className={`mt-1 block w-24 ${input}`}
                     value={cont.duplicateLookbackDays} onChange={(e) => setCont({ ...cont, duplicateLookbackDays: Number(e.target.value) })} />
            </label>
          </div>
          {canEdit && (
            <button disabled={total !== 100 || cont.enabledLanguages.length === 0}
                    className="mt-4 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
                    onClick={() => void save(async () => setCont(await api.put('/api/settings/content', cont)), 'Language settings')}>
              Save languages
            </button>
          )}
        </section>
      )}
    </div>
  )
}
