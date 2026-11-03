interface Props { label: string; value: number | string; tone?: 'default' | 'warn' | 'good' | 'bad' }

const tones = {
  default: 'text-ink',
  warn: 'text-amber-600',
  good: 'text-emerald-600',
  bad: 'text-red-600',
}

export function StatCard({ label, value, tone = 'default' }: Props) {
  return (
    <div className="rounded-xl border border-line bg-white p-4">
      <div className="text-xs font-medium uppercase tracking-wide text-muted">{label}</div>
      <div className={`mt-2 text-3xl font-semibold tabular-nums ${tones[tone]}`}>{value}</div>
    </div>
  )
}
