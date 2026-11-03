import { describe, expect, it } from 'vitest'
import { fromLocalInput, hasLink, toLocalInput } from './review'
import { xLength } from './xlength'

describe('IST datetime conversion', () => {
  it('round-trips through Asia/Kolkata', () => {
    const iso = '2026-11-04T02:30:00.000Z' // 08:00 IST
    expect(toLocalInput(iso, 'Asia/Kolkata')).toBe('2026-11-04T08:00')
    expect(fromLocalInput('2026-11-04T08:00', 'Asia/Kolkata')).toBe(iso)
  })
})

describe('xLength', () => {
  it('matches backend weighting', () => {
    expect(xLength('abc')).toBe(3)
    expect(xLength('see https://www.tn.gov.in/a/very/long/path')).toBe(4 + 23)
    expect(xLength('👍')).toBe(2)
    expect(xLength('தமிழ்')).toBe([...'தமிழ்'].length)
  })
})

describe('hasLink', () => {
  it('detects links', () => {
    expect(hasLink('read https://a.in')).toBe(true)
    expect(hasLink('no link here')).toBe(false)
  })
})
