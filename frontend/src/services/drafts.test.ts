import { describe, expect, it } from 'vitest'
import { isSafeLink, parseUrls } from './drafts'

describe('parseUrls', () => {
  it('splits lines and commas and removes duplicates', () => {
    expect(parseUrls('https://a.in/x\nhttps://b.in, https://a.in/x\n\n')).toEqual(['https://a.in/x', 'https://b.in'])
  })
})

describe('isSafeLink', () => {
  it('allows only http and https', () => {
    expect(isSafeLink('https://tn.gov.in')).toBe(true)
    expect(isSafeLink('javascript:alert(1)')).toBe(false)
    expect(isSafeLink('not a url')).toBe(false)
  })
})
