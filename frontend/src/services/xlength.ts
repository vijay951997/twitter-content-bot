/** X weighted length (twitter-text v3): Latin/Tamil 1, others 2, URLs 23. Mirrors the backend. */
export const XTEXT_URL_WEIGHT = 23
export const X_MAX = 280

const LIGHT: [number, number][] = [[0, 4351], [8192, 8205], [8208, 8223], [8242, 8247]]

export function xLength(text: string): number {
  const nfc = text.normalize('NFC')
  let total = 0
  let last = 0
  const re = /https?:\/\/\S+/gi
  let m: RegExpExecArray | null
  const weigh = (s: string) => {
    let n = 0
    for (const ch of s) {
      const cp = ch.codePointAt(0)!
      n += LIGHT.some(([a, b]) => cp >= a && cp <= b) ? 1 : 2
    }
    return n
  }
  while ((m = re.exec(nfc)) !== null) {
    total += weigh(nfc.slice(last, m.index)) + XTEXT_URL_WEIGHT
    last = m.index + m[0].length
  }
  return total + weigh(nfc.slice(last))
}
