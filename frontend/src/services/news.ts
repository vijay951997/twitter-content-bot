import type { Claim, SourceType } from '../types/api'

export const SOURCE_TYPE_LABELS: Record<SourceType, string> = {
  TAMIL_NEWS: 'Tamil news',
  NATIONAL_NEWS: 'National news',
  GOVERNMENT: 'Government website',
  PRESS_RELEASE: 'Press releases',
  ELECTION_COMMISSION: 'Election Commission',
  PARTY_OFFICIAL: 'Official party site',
  LEGISLATIVE: 'Legislature',
}

export const TIER_LABELS: Record<number, string> = {
  1: 'Tier 1 · official primary source',
  2: 'Tier 2 · established news organisation',
  3: 'Tier 3 · other',
}

export const CLAIM_TYPE_LABELS: Record<Claim['type'], string> = {
  FACT: 'Fact',
  STATISTIC: 'Statistic',
  ATTRIBUTED_STATEMENT: 'Statement',
  OPINION: 'Opinion',
}

export const METHOD_LABELS: Record<string, string> = {
  PRIMARY_SOURCE: 'Official primary source',
  INDEPENDENT_SOURCES: 'Independent publishers agree',
  SINGLE_SOURCE: 'One publisher so far',
  CONFLICT: 'Sources disagree',
  OPINION: 'Opinion',
  LOW_RELIABILITY_SOURCE: 'Low-reliability source',
}

export const FETCH_STATUS_TONE: Record<string, string> = {
  OK: 'text-emerald-700',
  NOT_MODIFIED: 'text-emerald-700',
  BLOCKED_BY_ROBOTS: 'text-amber-700',
  HTTP_ERROR: 'text-red-700',
  NETWORK_ERROR: 'text-red-700',
  PARSE_ERROR: 'text-red-700',
  TOO_LARGE: 'text-red-700',
}

/** Claims that a draft can use: verified or single-source, but never opinion or conflicting. */
export function usableClaims(claims: Claim[]): number {
  return claims.filter((c) => c.type !== 'OPINION' &&
    (c.verificationStatus === 'VERIFIED' || c.verificationStatus === 'PARTIALLY_VERIFIED')).length
}
