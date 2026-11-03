import { describe, expect, it } from 'vitest'
import { readCookie } from './api'
import { mixTotal } from './settings'

describe('readCookie', () => {
  it('finds a cookie among several', () => {
    expect(readCookie('a=1; XSRF-TOKEN=abc%3D; b=2', 'XSRF-TOKEN')).toBe('abc=')
  })
  it('returns null when missing', () => {
    expect(readCookie('a=1', 'XSRF-TOKEN')).toBeNull()
  })
})

describe('mixTotal', () => {
  it('sums only enabled languages', () => {
    expect(mixTotal(['ta', 'en'], { ta: 60, en: 40, mixed: 10 })).toBe(100)
  })
})
