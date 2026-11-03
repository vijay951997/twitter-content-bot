import { useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { ApiError } from '../services/api'
import { ErrorBanner } from '../components/ErrorBanner'

export function LoginPage() {
  const { user, login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to="/" replace />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(username, password)
    } catch (err) {
      setError(err instanceof ApiError && err.status === 401 ? 'Wrong username or password.' : 'Login failed. Is the backend running?')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <form onSubmit={submit} className="w-full max-w-sm space-y-4 rounded-2xl border border-line bg-white p-8">
        <div>
          <h1 className="text-xl font-semibold">X Content Desk</h1>
          <p className="text-sm text-muted">Nothing is posted without your approval.</p>
        </div>
        <ErrorBanner message={error} />
        <label className="block text-sm">
          <span className="font-medium">Username</span>
          <input className="mt-1 w-full rounded-lg border border-line px-3 py-2" value={username}
                 onChange={(e) => setUsername(e.target.value)} autoComplete="username" required />
        </label>
        <label className="block text-sm">
          <span className="font-medium">Password</span>
          <input type="password" className="mt-1 w-full rounded-lg border border-line px-3 py-2" value={password}
                 onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
        </label>
        <button disabled={busy} className="w-full rounded-lg bg-accent px-4 py-2 font-medium text-white disabled:opacity-60">
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  )
}
