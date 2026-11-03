import { useApi } from '../hooks/useApi'
import type { AuditEntry } from '../types/api'
import { ErrorBanner } from '../components/ErrorBanner'

export function AuditPage() {
  const { data, error, loading } = useApi<AuditEntry[]>('/api/audit?limit=100')
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Audit log</h1>
        <p className="text-sm text-muted">Append-only record of every change. Entries cannot be edited or deleted.</p>
      </header>
      <ErrorBanner message={error} />
      <div className="overflow-x-auto rounded-xl border border-line bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-line text-xs uppercase tracking-wide text-muted">
            <tr><th className="px-4 py-3">When</th><th className="px-4 py-3">Actor</th><th className="px-4 py-3">Action</th><th className="px-4 py-3">Entity</th></tr>
          </thead>
          <tbody className="divide-y divide-line">
            {loading && <tr><td className="px-4 py-4 text-muted" colSpan={4}>Loading…</td></tr>}
            {data?.map((a) => (
              <tr key={a.id}>
                <td className="whitespace-nowrap px-4 py-3 text-muted">{new Date(a.createdAt).toLocaleString('en-IN')}</td>
                <td className="px-4 py-3">{a.actorType}</td>
                <td className="px-4 py-3 font-medium">{a.action}</td>
                <td className="px-4 py-3 text-muted">{a.entityType}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
