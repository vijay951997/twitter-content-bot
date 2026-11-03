import { useCallback, useEffect, useState } from 'react'
import { api } from '../services/api'
import { useApi } from '../hooks/useApi'
import { GeneratePanel } from '../components/GeneratePanel'
import { DraftCard } from '../components/DraftCard'
import { ErrorBanner } from '../components/ErrorBanner'
import type { ContentSettings, Draft, GenerationResult, Page, PostStatus, ScheduleSettings } from '../types/api'

const FILTERS: { value: '' | PostStatus; label: string }[] = [
  { value: '', label: 'All' },
  { value: 'GENERATED', label: 'New' },
  { value: 'UNDER_REVIEW', label: 'In review' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'PUBLISHED', label: 'Published' },
]

export function DraftsPage() {
  const content = useApi<ContentSettings>('/api/settings/content')
  const schedule = useApi<ScheduleSettings>('/api/settings/schedule')
  const [filter, setFilter] = useState<'' | PostStatus>('')
  const [drafts, setDrafts] = useState<Draft[]>([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastResult, setLastResult] = useState<GenerationResult | null>(null)

  const load = useCallback(async (nextPage: number, replace: boolean) => {
    setError(null)
    try {
      const q = new URLSearchParams({ page: String(nextPage), size: '20' })
      if (filter) q.set('status', filter)
      const res = await api.get<Page<Draft>>(`/api/drafts?${q}`)
      setDrafts((prev) => (replace ? res.items : [...prev, ...res.items]))
      setPage(nextPage)
      setHasMore(nextPage + 1 < res.totalPages)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load drafts')
    }
  }, [filter])

  useEffect(() => { void load(0, true) }, [load])

  const onGenerated = (r: GenerationResult) => {
    setLastResult(r)
    void load(0, true)
  }

  const tz = schedule.data?.timezone ?? 'Asia/Kolkata'

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Drafts</h1>
        <p className="text-sm text-muted">AI drafts waiting for your review. Nothing here is posted to X.</p>
      </header>

      {content.data && <GeneratePanel enabledLanguages={content.data.enabledLanguages} onGenerated={onGenerated} />}

      {lastResult && (
        <div className="rounded-xl border border-line bg-white p-4 text-sm">
          <div className="font-medium">
            {lastResult.created.length} draft{lastResult.created.length === 1 ? '' : 's'} saved
            {lastResult.discarded.length > 0 && `, ${lastResult.discarded.length} discarded by checks`}
          </div>
          {lastResult.discarded.length > 0 && (
            <ul className="mt-2 space-y-1 text-xs text-muted">
              {lastResult.discarded.map((d, i) => (
                <li key={i}><span className="font-medium text-ink">{d.reason}</span>{d.preview && ` — “${d.preview}”`}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <ErrorBanner message={error} />

      <div className="flex flex-wrap gap-2">
        {FILTERS.map((f) => (
          <button key={f.label} onClick={() => setFilter(f.value)}
                  className={`rounded-full px-3 py-1 text-sm ${filter === f.value ? 'bg-ink text-white' : 'border border-line bg-white text-muted hover:text-ink'}`}>
            {f.label}
          </button>
        ))}
      </div>

      {drafts.length === 0 && !error && (
        <div className="rounded-xl border border-dashed border-line bg-white p-8 text-center text-sm text-muted">
          No drafts yet. Generate some above.
        </div>
      )}

      <div className="space-y-4">
        {drafts.map((d) => <DraftCard key={d.id} draft={d} timezone={tz} />)}
      </div>

      {hasMore && (
        <button onClick={() => void load(page + 1, false)} className="w-full rounded-lg border border-line bg-white py-2 text-sm hover:bg-paper">
          Load more
        </button>
      )}

      <p className="text-xs text-muted">"Generate today's 3 drafts" uses the newest fact-checked article for each slot, or a discussion post when there is none. Approve, edit and reject actions arrive in Phase 4. Publishing to X arrives in Phase 5.</p>
    </div>
  )
}
