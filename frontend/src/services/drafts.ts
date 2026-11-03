import type { LanguageCode, Slot, VerificationStatus } from '../types/api'

export const SLOT_LABELS: Record<Slot, string> = {
  MORNING: 'Morning',
  AFTERNOON: 'Afternoon',
  EVENING: 'Evening',
}

export const LANGUAGE_LABELS: Record<LanguageCode, string> = {
  ta: 'Tamil',
  en: 'English',
  mixed: 'Tanglish',
}

export const VERIFICATION_LABELS: Record<VerificationStatus, { label: string; tone: string }> = {
  VERIFIED: { label: 'Verified', tone: 'bg-emerald-100 text-emerald-800' },
  PARTIALLY_VERIFIED: { label: 'Supported by your sources', tone: 'bg-blue-100 text-blue-800' },
  UNVERIFIED: { label: 'Unverified', tone: 'bg-slate-100 text-slate-700' },
  CONFLICTING_INFORMATION: { label: 'Conflicting sources', tone: 'bg-red-100 text-red-800' },
}

export const RISK_LABELS: Record<string, string> = {
  UNVERIFIED_ALLEGATION: 'Unverified allegation',
  PERSONAL_ATTACK: 'Personal attack',
  UNSUPPORTED_STATISTIC: 'Unsupported statistic',
  UNATTRIBUTED_QUOTE: 'Unattributed quote',
  INFLAMMATORY: 'Inflammatory',
  POSSIBLY_OUTDATED: 'Possibly outdated',
  OPINION_AS_FACT: 'Opinion stated as fact',
  AI_URL_REMOVED: 'AI-invented link removed',
  HAS_UNVERIFIED_CLAIMS: 'Has unverified claims',
}

/** Splits a textarea of links (one per line or comma separated) into a clean list. */
export function parseUrls(text: string): string[] {
  return Array.from(new Set(text.split(/[\s,]+/).map((s) => s.trim()).filter(Boolean)))
}

/** Only http(s) links are rendered as clickable. */
export function isSafeLink(url: string): boolean {
  try {
    const u = new URL(url)
    return u.protocol === 'http:' || u.protocol === 'https:'
  } catch {
    return false
  }
}
