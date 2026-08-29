import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import NotificationBell from './NotificationBell'
import SosButton from './SosButton'
import { Logo } from './ui'
import type { Role } from '../types'

interface NavItem {
  label: string
  to: string
  icon: string
  roles: Role[]
  soon?: boolean
}

const ALL: Role[] = ['admin', 'committee', 'resident', 'guard', 'staff']

const NAV: NavItem[] = [
  { label: 'Dashboard', to: '/app', icon: '📊', roles: ALL },
  { label: 'Units', to: '/app/units', icon: '🏢', roles: ['admin', 'committee'] },
  { label: 'Residents', to: '/app/residents', icon: '🏠', roles: ['admin', 'committee'] },
  { label: 'Directory', to: '/app/directory', icon: '📇', roles: ALL },
  { label: 'Staff', to: '/app/staff', icon: '🧰', roles: ['admin', 'committee'] },
  { label: 'Invoices', to: '/app/invoices', icon: '💳', roles: ['admin', 'committee', 'resident'] },
  { label: 'Expenses', to: '/app/expenses', icon: '🧮', roles: ['admin', 'committee'] },
  { label: 'Maintenance', to: '/app/tickets', icon: '🔧', roles: ALL },
  { label: 'Notices', to: '/app/notices', icon: '📌', roles: ALL },
  { label: 'Polls', to: '/app/polls', icon: '🗳️', roles: ALL },
  { label: 'Events', to: '/app/events', icon: '🎉', roles: ALL },
  { label: 'Bookings', to: '/app/bookings', icon: '🏛️', roles: ['admin', 'committee', 'resident'] },
  { label: 'Expected visitors', to: '/app/appointments', icon: '🎟️', roles: ['admin', 'committee', 'resident'] },
  { label: 'Visitor log', to: '/app/visitors', icon: '🛡️', roles: ['admin', 'committee', 'guard'] },
  { label: 'Gate scan', to: '/app/scan', icon: '📷', roles: ['admin', 'committee', 'guard'] },
  { label: 'Gate log', to: '/app/gate', icon: '🚧', roles: ['admin', 'committee', 'guard'] },
  { label: 'Chat', to: '/app/chat', icon: '💬', roles: ALL, soon: true },
  { label: 'Parking', to: '/app/parking', icon: '🅿️', roles: ['admin', 'committee', 'resident'], soon: true },
  { label: 'Settings', to: '/app/settings', icon: '⚙️', roles: ALL },
]

export default function AppShell() {
  const { user, logout } = useAuth()
  const { buildings, current, select } = useBuilding()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(false)

  if (!user) return null

  const items = NAV.filter((item) => item.roles.includes(user.role))

  async function handleLogout() {
    await logout()
    navigate('/', { replace: true })
  }

  return (
    <div className="flex min-h-screen bg-slate-50">
      <aside
        className={`fixed inset-y-0 left-0 z-40 w-64 transform border-r border-slate-200 bg-white transition-transform lg:static lg:translate-x-0 ${
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex h-14 items-center border-b border-slate-200 px-5">
          <Logo />
        </div>

        <nav className="space-y-0.5 p-3">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/app'}
              onClick={() => setSidebarOpen(false)}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${
                  isActive ? 'bg-brand-50 text-brand-800' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                }`
              }
            >
              <span aria-hidden="true">{item.icon}</span>
              <span className="flex-1">{item.label}</span>
              {item.soon && (
                <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-slate-500">
                  soon
                </span>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="absolute bottom-0 w-full border-t border-slate-200 p-3">
          <p className="px-3 text-[11px] uppercase tracking-wide text-slate-400">Signed in as</p>
          <p className="truncate px-3 text-sm font-medium text-slate-800">{user.name}</p>
          <p className="px-3 text-xs capitalize text-slate-500">{user.role}</p>
        </div>
      </aside>

      {sidebarOpen && (
        <button
          type="button"
          aria-label="Close navigation"
          className="fixed inset-0 z-30 bg-slate-900/30 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-14 items-center justify-between gap-3 border-b border-slate-200 bg-white px-4 lg:px-6">
          <div className="flex min-w-0 items-center gap-3">
            <button
              type="button"
              onClick={() => setSidebarOpen(true)}
              className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 lg:hidden"
              aria-label="Open navigation"
            >
              ☰
            </button>

            {/* Building switcher — only rendered when the caller can actually see more than one. */}
            {buildings.length > 1 ? (
              <select
                aria-label="Switch building"
                value={current?.id ?? ''}
                onChange={(e) => select(Number(e.target.value))}
                className="max-w-[16rem] truncate rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-sm font-medium"
              >
                {buildings.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
            ) : (
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold text-slate-900">
                  {current?.name ?? 'No building yet'}
                </p>
                {current?.address && <p className="truncate text-xs text-slate-500">{current.address}</p>}
              </div>
            )}
          </div>

          {/* Alerts and SOS sit next to the user menu: reachable from every screen, always. */}
          <div className="flex items-center gap-1">
            <SosButton />
            <NotificationBell />
          </div>

          <div className="relative">
            <button
              type="button"
              onClick={() => setMenuOpen((open) => !open)}
              className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-slate-100"
              aria-haspopup="menu"
              aria-expanded={menuOpen}
            >
              <span className="grid h-8 w-8 place-items-center rounded-full bg-brand-700 text-xs font-bold text-white">
                {user.name.charAt(0).toUpperCase()}
              </span>
              <span className="hidden sm:inline">{user.name}</span>
              <span aria-hidden="true" className="text-slate-400">▾</span>
            </button>

            {menuOpen && (
              <>
                <button
                  type="button"
                  aria-label="Close menu"
                  className="fixed inset-0 z-10 cursor-default"
                  onClick={() => setMenuOpen(false)}
                />
                <div role="menu" className="absolute right-0 z-20 mt-1 w-52 rounded-lg border border-slate-200 bg-white py-1 shadow-lg">
                  <div className="border-b border-slate-100 px-3 py-2">
                    <p className="truncate text-sm font-medium text-slate-800">{user.name}</p>
                    <p className="truncate text-xs text-slate-500">{user.email}</p>
                  </div>
                  <button
                    type="button"
                    role="menuitem"
                    onClick={handleLogout}
                    className="w-full px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-50"
                  >
                    Sign out
                  </button>
                </div>
              </>
            )}
          </div>
        </header>

        <main className="flex-1 p-4 lg:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
