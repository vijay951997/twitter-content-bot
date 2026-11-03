import { describe, expect, it } from 'vitest'
import { usableClaims } from './news'
import type { Claim } from '../types/api'

const c = (type: Claim['type'], verificationStatus: Claim['verificationStatus']) =>
  ({ type, verificationStatus } as Claim)

describe('usableClaims', () => {
  it('counts verified and single-source facts, never opinions or conflicts', () => {
    expect(usableClaims([
      c('FACT', 'VERIFIED'), c('STATISTIC', 'PARTIALLY_VERIFIED'), c('OPINION', 'VERIFIED'),
      c('FACT', 'CONFLICTING_INFORMATION'), c('FACT', 'UNVERIFIED'),
    ])).toBe(2)
  })
})
