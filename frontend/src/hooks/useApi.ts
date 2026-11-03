import { useCallback, useEffect, useState } from 'react'
import { api } from '../services/api'

/** Loads a GET endpoint and exposes data, error and a reload function. */
export function useApi<T>(url: string) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await api.get<T>(url))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Request failed')
    } finally {
      setLoading(false)
    }
  }, [url])

  useEffect(() => { void reload() }, [reload])

  return { data, error, loading, reload, setData }
}
