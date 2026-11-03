import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

const nav = [
  { to: '/', label: 'Overview', end: true },
  { to: '/news', label: 'News' },
  { to: '/drafts', label: 'Drafts' },
  { to: '/settings', label: 'Settings' },
  { to: '/audit', label: 'Audit log' },
]

export function AppLayout() {
  const { user, logout } = useAuth()
  return (
    <div className="min-h-screen md:flex">
      <aside className="border-b border-line bg-white md:w-56 md:border-b-0 md:border-r">
        <div className="flex items-center justify-between px-5 py-4 md:block">
          <div>
            <div className="text-sm font-bold tracking-tight">X Content Desk</div>
            <div className="text-xs text-muted">Human-approved publishing</div>
          </div>
        </div>
        <nav className="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-col md:pb-0">
          {nav.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              end={n.end}
              className={({ isActive }) =>
                `whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium ${
                  isActive ? 'bg-accent/10 text-accent' : 'text-muted hover:bg-paper hover:text-ink'
                }`
              }
            >
              {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="hidden px-5 py-6 text-xs text-muted md:block">
          <div className="font-medium text-ink">{user?.displayName ?? user?.username}</div>
          <div>{user?.role}</div>
          <button onClick={() => void logout()} className="mt-3 text-accent hover:underline">Sign out</button>
        </div>
      </aside>
      <main className="flex-1 px-4 py-6 md:px-10 md:py-8">
        <div className="mx-auto max-w-5xl">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
