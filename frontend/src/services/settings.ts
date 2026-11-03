export const LANGUAGES: { code: string; label: string }[] = [
  { code: 'ta', label: 'Tamil' },
  { code: 'en', label: 'English' },
  { code: 'mixed', label: 'Tanglish' },
]

export function mixTotal(enabled: string[], mix: Record<string, number>): number {
  return enabled.reduce((sum, code) => sum + (Number(mix[code]) || 0), 0)
}
