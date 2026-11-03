import { useApi } from '../hooks/useApi'
import type { Overview, SystemStatus } from '../types/api'
import { StatCard } from '../components/StatCard'
import { ErrorBanner } from '../components/ErrorBanner'

function Check({ ok, label, detail }: { ok: boolean; label: string; detail?: string }) {
  return (
    <li className="flex items-center justify-between py-2 text-sm">
      <span>{label}{detail && <span className="ml-2 text-muted">{detail}</span>}</span>
      <span className={ok ? 'text-emerald-600' : 'text-muted'}>{ok ? 'Ready' : 'Not set up'}</span>
    </li>
  )
}

export function OverviewPage() {
  const overview = useApi<Overview>('/api/dashboard/overview')
  const status = useApi<SystemStatus>('/api/system/status')
  const o = overview.data
  const s = status.data

  return (
    <div className="space-y-8">
      <header>
        <h1 className="text-2xl font-semibold">Overview</h1>
        <p className="text-sm text-muted">Drafts, approvals and publishing at a glance.</p>
      </header>

      <ErrorBanner message={overview.error ?? status.error} />

      <section className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <StatCard label="Total drafts" value={o?.totalDrafts ?? '–'} />
        <StatCard label="Pending approval" value={o?.pendingApprovals ?? '–'} tone="warn" />
        <StatCard label="Approved" value={o?.approved ?? '–'} />
        <StatCard label="Scheduled" value={o?.scheduled ?? '–'} />
        <StatCard label="Published" value={o?.published ?? '–'} tone="good" />
        <StatCard label="Rejected" value={o?.rejected ?? '–'} />
        <StatCard label="Failed" value={o?.failed ?? '–'} tone="bad" />
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        <div className="rounded-xl border border-line bg-white p-5">
          <h2 className="font-semibold">Upcoming scheduled posts</h2>
          {o && o.upcoming.length === 0 && (
            <p className="mt-3 text-sm text-muted">Nothing scheduled. Approved drafts will appear here.</p>
          )}
          <ul className="mt-3 divide-y divide-line">
            {o?.upcoming.map((p) => (
              <li key={p.id} className="py-3 text-sm">
                <div className="text-xs text-muted">
                  {new Date(p.publishAt).toLocaleString('en-IN', { timeZone: s?.timezone ?? 'Asia/Kolkata' })} · {p.language} · {p.category}
                </div>
                <div className="mt-1">{p.preview}</div>
              </li>
            ))}
          </ul>
        </div>

        <div className="rounded-xl border border-line bg-white p-5">
          <h2 className="font-semibold">System readiness</h2>
          {s && (
            <ul className="mt-2 divide-y divide-line">
              <Check ok={s.openAiConfigured} label="OpenAI API" detail={s.openAiModel ?? undefined} />
              <Check ok={s.xApiConfigured} label="X API app credentials" />
              <Check ok={s.xAccountConnected} label="X account connected" />
              <li className="flex items-center justify-between py-2 text-sm">
                <span>Publishing switch</span>
                <span className={s.publishingEnabled ? 'text-emerald-600' : 'text-muted'}>
                  {s.publishingEnabled ? 'On (approved posts only)' : 'Off'}
                </span>
              </li>
            </ul>
          )}
          <p className="mt-3 text-xs text-muted">Build stage: {s?.phase.replace('_', ' ') ?? '–'} · Timezone {s?.timezone ?? '–'}</p>
        </div>
      </section>
    </div>
  )
}
