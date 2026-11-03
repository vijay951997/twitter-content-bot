export function PhaseNotice({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-xl border border-dashed border-line bg-white p-6 text-sm text-muted">
      <div className="font-semibold text-ink">{title}</div>
      <div className="mt-1">{children}</div>
    </div>
  )
}
