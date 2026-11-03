import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../services/api'
import { useApi } from '../hooks/useApi'
import { useAuth } from '../hooks/useAuth'
import { ArticleCard } from '../components/ArticleCard'
import { SourceForm } from '../components/SourceForm'
import { ErrorBanner } from '../components/ErrorBanner'
import { FETCH_STATUS_TONE, SOURCE_TYPE_LABELS, TIER_LABELS } from '../services/news'
import type { AnalysisStatus, Article, FetchRun, GenerationResult, NewsSource, NewsSourceInput, Page, ScheduleSettings } from '../types/api'

const FILTERS: { value: '' | AnalysisStatus; label: string }[] = [
  { value: '', label: 'All relevant' },
  { value: 'NEW', label: 'Not analysed' },
  { value: 'ANALYSED', label: 'Analysed' },
  { value: 'FAILED', label: 'Failed' },
]

function errorText(e: unknown) {
  return e instanceof ApiError ? e.message : 'Request failed'
}

export function NewsPage() {
  const { user } = useAuth()
  const canAct = user?.role === 'ADMIN' || user?.role === 'REVIEWER'
  const isAdmin = user?.role === 'ADMIN'
  const schedule = useApi<ScheduleSettings>('/api/settings/schedule')
  const tz = schedule.data?.timezone ?? 'Asia/Kolkata'

  const [tab, setTab] = useState<'articles' | 'sources'>('articles')
  const [filter, setFilter] = useState<'' | AnalysisStatus>('')
  const [articles, setArticles] = useState<Article[]>([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [sources, setSources] = useState<NewsSource[]>([])
  const [editing, setEditing] = useState<NewsSource | 'new' | null>(null)
  const [fetching, setFetching] = useState(false)
  const [run, setRun] = useState<FetchRun | null>(null)
  const [drafted, setDrafted] = useState<GenerationResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  const loadArticles = useCallback(async (p: number, replace: boolean) => {
    try {
      const q = new URLSearchParams({ page: String(p), size: '20', relevantOnly: 'true' })
      if (filter) q.set('status', filter)
      const res = await api.get<Page<Article>>(`/api/news/articles?${q}`)
      setArticles((prev) => (replace ? res.items : [...prev, ...res.items]))
      setPage(p)
      setHasMore(p + 1 < res.totalPages)
    } catch (e) { setError(errorText(e)) }
  }, [filter])

  const loadSources = useCallback(async () => {
    try { setSources(await api.get<NewsSource[]>('/api/news/sources')) } catch (e) { setError(errorText(e)) }
  }, [])

  useEffect(() => { void loadArticles(0, true) }, [loadArticles])
  useEffect(() => { void loadSources() }, [loadSources])

  const fetchNow = async (sourceId?: string) => {
    setFetching(true); setError(null)
    try {
      setRun(await api.post<FetchRun>(sourceId ? `/api/news/sources/${sourceId}/fetch` : '/api/news/fetch'))
      await Promise.all([loadArticles(0, true), loadSources()])
    } catch (e) { setError(errorText(e)) } finally { setFetching(false) }
  }

  const saveSource = async (s: NewsSourceInput) => {
    setError(null)
    try {
      if (editing === 'new') await api.post('/api/news/sources', s)
      else if (editing) await api.put(`/api/news/sources/${editing.id}`, s)
      setEditing(null)
      await loadSources()
    } catch (e) { setError(errorText(e)) }
  }

  const replaceArticle = (a: Article) => setArticles((list) => list.map((x) => (x.id === a.id ? a : x)))

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">News</h1>
          <p className="text-sm text-muted">Reports from your sources, fact-checked against each other before they become drafts.</p>
        </div>
        {canAct && (
          <button disabled={fetching} onClick={() => void fetchNow()}
                  className="rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white disabled:opacity-50">
            {fetching ? 'Fetching…' : 'Fetch latest news'}
          </button>
        )}
      </header>

      <ErrorBanner message={error} />

      {run && (
        <div className="rounded-xl border border-line bg-white p-4 text-sm">
          <div className="font-medium">{run.newArticles} new article{run.newArticles === 1 ? '' : 's'} ({run.relevantArticles} relevant) from {run.sourcesChecked} source{run.sourcesChecked === 1 ? '' : 's'}</div>
          <ul className="mt-2 space-y-0.5 text-xs">
            {run.sources.map((s) => (
              <li key={s.source}><span className="font-medium">{s.source}</span>: <span className={FETCH_STATUS_TONE[s.status] ?? ''}>{s.message ?? s.status}</span></li>
            ))}
          </ul>
        </div>
      )}

      {drafted && (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
          {drafted.created.length} draft{drafted.created.length === 1 ? '' : 's'} created{drafted.discarded.length > 0 && `, ${drafted.discarded.length} discarded by checks`}.{' '}
          <Link to="/drafts" className="font-medium underline">Review drafts</Link>
        </div>
      )}

      <div className="flex gap-1 border-b border-line">
        {(['articles', 'sources'] as const).map((t) => (
          <button key={t} onClick={() => setTab(t)}
                  className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${tab === t ? 'border-accent text-accent' : 'border-transparent text-muted hover:text-ink'}`}>
            {t === 'articles' ? 'Articles' : `Sources (${sources.filter((s) => s.enabled).length} on)`}
          </button>
        ))}
      </div>

      {tab === 'articles' && (
        <>
          <div className="flex flex-wrap gap-2">
            {FILTERS.map((f) => (
              <button key={f.label} onClick={() => setFilter(f.value)}
                      className={`rounded-full px-3 py-1 text-sm ${filter === f.value ? 'bg-ink text-white' : 'border border-line bg-white text-muted hover:text-ink'}`}>
                {f.label}
              </button>
            ))}
          </div>
          {articles.length === 0 && (
            <div className="rounded-xl border border-dashed border-line bg-white p-8 text-center text-sm text-muted">
              No articles yet. Click <span className="font-medium text-ink">Fetch latest news</span>.
            </div>
          )}
          <div className="space-y-4">
            {articles.map((a) => (
              <ArticleCard key={a.id} article={a} timezone={tz} canAct={canAct} onChange={replaceArticle} onDrafted={setDrafted} />
            ))}
          </div>
          {hasMore && (
            <button onClick={() => void loadArticles(page + 1, false)} className="w-full rounded-lg border border-line bg-white py-2 text-sm hover:bg-paper">
              Load more
            </button>
          )}
          <p className="text-xs text-muted">
            A claim is <b>Verified</b> only when an official source states it or two independent publishers report it.
            Matching reports is AI-assisted; the status itself follows fixed rules. Only titles, links and an original
            summary are stored — never article text.
          </p>
        </>
      )}

      {tab === 'sources' && (
        <div className="space-y-4">
          {editing ? (
            <SourceForm initial={editing === 'new' ? undefined : editing} onSave={saveSource} onCancel={() => setEditing(null)} />
          ) : isAdmin && (
            <button onClick={() => setEditing('new')} className="rounded-lg border border-line bg-white px-4 py-2 text-sm font-medium hover:bg-paper">
              + Add source
            </button>
          )}
          <div className="overflow-x-auto rounded-xl border border-line bg-white">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-line text-xs uppercase tracking-wide text-muted">
                <tr><th className="px-4 py-3">Source</th><th className="px-4 py-3">Reliability</th><th className="px-4 py-3">Last fetch</th><th className="px-4 py-3"></th></tr>
              </thead>
              <tbody className="divide-y divide-line">
                {sources.map((s) => (
                  <tr key={s.id} className={s.enabled ? '' : 'opacity-60'}>
                    <td className="px-4 py-3">
                      <div className="font-medium">{s.name}{!s.enabled && <span className="ml-2 text-xs font-normal text-muted">(off)</span>}</div>
                      <div className="text-xs text-muted">{SOURCE_TYPE_LABELS[s.sourceType]} · {s.language} · every {s.fetchIntervalMinutes} min</div>
                    </td>
                    <td className="px-4 py-3 text-xs">{TIER_LABELS[s.reliabilityTier]}{s.primarySource && ' · primary'}</td>
                    <td className="px-4 py-3 text-xs">
                      {s.lastFetchedAt ? (
                        <>
                          <div className={FETCH_STATUS_TONE[s.lastFetchStatus ?? ''] ?? ''}>{s.lastFetchMessage ?? s.lastFetchStatus}</div>
                          <div className="text-muted">{new Date(s.lastFetchedAt).toLocaleString('en-IN', { timeZone: tz })}</div>
                        </>
                      ) : <span className="text-muted">Never</span>}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right text-xs">
                      {canAct && s.enabled && <button disabled={fetching} onClick={() => void fetchNow(s.id)} className="mr-3 text-accent hover:underline">Fetch</button>}
                      {isAdmin && <button onClick={() => setEditing(s)} className="text-accent hover:underline">Edit</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="text-xs text-muted">Feeds are read only where the site's robots.txt allows it. Unchanged feeds aren't downloaded again.</p>
        </div>
      )}
    </div>
  )
}
