import type { PostStatus } from '../types/api'

const styles: Record<PostStatus, string> = {
  GENERATED: 'bg-slate-100 text-slate-700',
  UNDER_REVIEW: 'bg-amber-100 text-amber-800',
  APPROVED: 'bg-blue-100 text-blue-800',
  REJECTED: 'bg-red-100 text-red-700',
  SCHEDULED: 'bg-indigo-100 text-indigo-800',
  PUBLISHING: 'bg-violet-100 text-violet-800',
  PUBLISHED: 'bg-emerald-100 text-emerald-800',
  FAILED: 'bg-red-100 text-red-800',
  CANCELLED: 'bg-slate-100 text-slate-500',
}

export function StatusBadge({ status }: { status: PostStatus }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${styles[status]}`}>
      {status.replace('_', ' ')}
    </span>
  )
}
