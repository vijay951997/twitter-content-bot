import { XTEXT_URL_WEIGHT } from './xlength'

/** Converts an ISO instant to the value of an <input type="datetime-local"> in the given timezone. */
export function toLocalInput(iso: string | null, timeZone: string): string {
  const d = iso ? new Date(iso) : new Date(Date.now() + 3600_000)
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).formatToParts(d)
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? '00'
  return `${get('year')}-${get('month')}-${get('day')}T${get('hour') === '24' ? '00' : get('hour')}:${get('minute')}`
}

/** Converts a datetime-local value in a timezone with a fixed offset (e.g. Asia/Kolkata = +05:30) to ISO. */
export function fromLocalInput(value: string, timeZone: string): string {
  const guess = new Date(value + 'Z')
  const asTz = new Date(guess.toLocaleString('en-US', { timeZone }))
  const asUtc = new Date(guess.toLocaleString('en-US', { timeZone: 'UTC' }))
  return new Date(guess.getTime() - (asTz.getTime() - asUtc.getTime())).toISOString()
}

export function hasLink(text: string): boolean {
  return /https?:\/\/\S+/i.test(text)
}

export const LINK_COST_NOTE = `Posts with a link cost about 13× more on X's pay-per-use API (~$0.20 vs ~$0.015). Each link also counts as ${XTEXT_URL_WEIGHT} characters.`

export const ACTION_LABELS: Record<string, string> = {
  DRAFT_GENERATED: 'Draft generated',
  DRAFT_EDITED: 'Edited',
  DRAFT_APPROVED: 'Approved and scheduled',
  DRAFT_REJECTED: 'Rejected',
  DRAFT_UNSCHEDULED: 'Unscheduled',
  DRAFT_SAVED_FOR_LATER: 'Saved for later',
  DRAFT_UNSAVED: 'Removed from saved',
  DRAFT_REGENERATED: 'Replaced by regenerated drafts',
  DRAFT_CANCELLED: 'Cancelled',
  POST_PUBLISHED: 'Published to X',
  PUBLISH_ATTEMPT_FAILED: 'Publish attempt failed',
  PUBLISH_BLOCKED: 'Publishing blocked by safety check',
  PUBLISH_RETRY_REQUESTED: 'Retry requested',
  PUBLISH_LOCK_RECOVERED: 'Recovered after interruption',
}
