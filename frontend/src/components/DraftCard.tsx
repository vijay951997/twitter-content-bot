import { useState } from 'react'
import type { Draft } from '../types/api'
import { StatusBadge } from './StatusBadge'
import { LANGUAGE_LABELS, RISK_LABELS, SLOT_LABELS, VERIFICATION_LABELS, isSafeLink } from '../services/drafts'

function fmt(iso: string | null, tz: string) {
  return iso ? new Date(iso).toLocaleString('en-IN', { timeZone: tz, dateStyle: 'medium', timeStyle: 'short' }) : '–'
}

export function DraftCard({ draft, timezone }: { draft: Draft; timezone: string }) {
  const [open, setOpen] = useState(false)
  const v = VERIFICATION_LABELS[draft.verificationStatus]
  const over = draft.weightedLength > draft.maxLength

  return (
    <article className="rounded-xl border border-line bg-white p-5">
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <StatusBadge status={draft.status} />
        <span className={`rounded-full px-2 py-0.5 font-medium ${v.tone}`}>{v.label}</span>
        <span className="rounded-full bg-paper px-2 py-0.5 font-medium">{LANGUAGE_LABELS[draft.language]}</span>
        <span className="rounded-full bg-paper px-2 py-0.5 font-medium">{draft.category}</span>
        {draft.slot && <span className="text-muted">{SLOT_LABELS[draft.slot]} · {fmt(draft.proposedPublishAt, timezone)}</span>}
      </div>

      <p lang={draft.language === 'ta' ? 'ta' : 'en'} className="mt-3 whitespace-pre-wrap text-[15px] leading-relaxed">
        {draft.content}
      </p>

      <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted">
        <span className={over ? 'font-medium text-red-600' : ''}>{draft.weightedLength}/{draft.maxLength} characters</span>
        {draft.qualityEstimate !== null && <span>AI quality estimate: {Math.round(draft.qualityEstimate * 100)}% (estimate only)</span>}
        <span>{draft.sourceUrls.length} source{draft.sourceUrls.length === 1 ? '' : 's'}</span>
        <span>Created {fmt(draft.createdAt, timezone)}</span>
      </div>

      {(draft.riskFlags.length > 0 || draft.duplicateWarning) && (
        <div className="mt-3 flex flex-wrap gap-2">
          {draft.riskFlags.map((f) => (
            <span key={f} className="rounded-md bg-amber-50 px-2 py-1 text-xs font-medium text-amber-800">⚠ {RISK_LABELS[f] ?? f}</span>
          ))}
          {draft.duplicateWarning && (
            <span className="rounded-md bg-amber-50 px-2 py-1 text-xs font-medium text-amber-800">⚠ {draft.duplicateWarning}</span>
          )}
        </div>
      )}

      <button onClick={() => setOpen(!open)} className="mt-3 text-xs font-medium text-accent hover:underline">
        {open ? 'Hide details' : 'Sources and claims'}
      </button>

      {open && (
        <div className="mt-3 space-y-3 border-t border-line pt-3 text-sm">
          <section>
            <h3 className="text-xs font-semibold uppercase tracking-wide text-muted">Sources</h3>
            {draft.sourceUrls.length === 0 ? (
              <p className="text-muted">None — this draft must not state facts as established.</p>
            ) : (
              <ul className="mt-1 space-y-1">
                {draft.sourceUrls.map((u) => (
                  <li key={u} className="break-all">
                    {isSafeLink(u) ? <a href={u} target="_blank" rel="noopener noreferrer" className="text-accent hover:underline">{u}</a> : u}
                  </li>
                ))}
              </ul>
            )}
          </section>
          <section>
            <h3 className="text-xs font-semibold uppercase tracking-wide text-muted">Claims backed by your source material</h3>
            {draft.verifiedClaims.length === 0 ? <p className="text-muted">None</p> :
              <ul className="mt-1 list-disc pl-5">{draft.verifiedClaims.map((c, i) => <li key={i}>{c}</li>)}</ul>}
          </section>
          <section>
            <h3 className="text-xs font-semibold uppercase tracking-wide text-muted">Unverified claims</h3>
            {draft.unverifiedClaims.length === 0 ? <p className="text-muted">None</p> :
              <ul className="mt-1 list-disc pl-5 text-amber-800">{draft.unverifiedClaims.map((c, i) => <li key={i}>{c}</li>)}</ul>}
          </section>
          {draft.sourceNotes && (
            <section>
              <h3 className="text-xs font-semibold uppercase tracking-wide text-muted">Source material given</h3>
              <p className="mt-1 whitespace-pre-wrap text-muted">{draft.sourceNotes}</p>
            </section>
          )}
          <p className="text-xs text-muted">
            Claim labels come from the AI reading your material — they are not independent fact-checks.
            Model {draft.aiModel ?? '–'} · prompt {draft.promptVersion ?? '–'}
          </p>
        </div>
      )}
    </article>
  )
}
