import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import NotificationBell from './NotificationBell'
import SosButton from './SosButton'
import { Logo } from './ui'
import Icon from './Icon'
import type { IconName } from './Icon'
import type { Role } from '../types'

interface NavItem {
  label: string
  to: string
  icon: IconName
  roles: Role[]
  group: string
}
const ALL: Role[] = ['admin', 'committee', 'resident', 'guard', 'staff']
const NAV: NavItem[] = [
  { label: 'Overview', to: '/app', icon: 'overview', roles: ALL, group: 'Workspace' },
  { label: 'Portfolio', to: '/app/portfolio', icon: 'layers', roles: ['admin', 'committee'], group: 'Workspace' },
  { label: 'Units', to: '/app/units', icon: 'building', roles: ['admin', 'committee'], group: 'Workspace' },
  {
    label: 'Residents',
    to: '/app/residents',
    icon: 'people',
    roles: ['admin', 'committee'],
    group: 'Workspace',
  },
  { label: 'Directory', to: '/app/directory', icon: 'directory', roles: ALL, group: 'Workspace' },
  { label: 'Staff', to: '/app/staff', icon: 'people', roles: ['admin', 'committee'], group: 'Workspace' },
  { label: 'Documents', to: '/app/documents', icon: 'file', roles: ALL, group: 'Workspace' },
  { label: 'Activity', to: '/app/activity', icon: 'activity', roles: ['admin', 'committee'], group: 'Workspace' },
  {
    label: 'Invoices',
    to: '/app/invoices',
    icon: 'receipt',
    roles: ['admin', 'committee', 'resident'],
    group: 'Operations',
  },
  {
    label: 'Expenses',
    to: '/app/expenses',
    icon: 'wallet',
    roles: ['admin', 'committee'],
    group: 'Operations',
  },
  { label: 'My shift', to: '/app/shift', icon: 'calendar', roles: ['guard', 'staff'], group: 'Operations' },
  { label: 'Maintenance', to: '/app/tickets', icon: 'tool', roles: ALL, group: 'Operations' },
  {
    label: 'Utilities',
    to: '/app/utilities',
    icon: 'bolt',
    roles: ['admin', 'committee'],
    group: 'Operations',
  },
  { label: 'Facilities', to: '/app/facilities', icon: 'lift', roles: ALL, group: 'Operations' },
  { label: 'Vendors', to: '/app/vendors', icon: 'store', roles: ALL, group: 'Operations' },
  { label: 'Parking', to: '/app/parking', icon: 'car', roles: ALL, group: 'Operations' },
  { label: 'Notices', to: '/app/notices', icon: 'notice', roles: ALL, group: 'Community' },
  { label: 'Polls', to: '/app/polls', icon: 'poll', roles: ALL, group: 'Community' },
  { label: 'Events', to: '/app/events', icon: 'calendar', roles: ALL, group: 'Community' },
  {
    label: 'Bookings',
    to: '/app/bookings',
    icon: 'calendar',
    roles: ['admin', 'committee', 'resident'],
    group: 'Community',
  },
  { label: 'Chat', to: '/app/chat', icon: 'chat', roles: ALL, group: 'Community' },
  {
    label: 'Rentals',
    to: '/app/rentals',
    icon: 'home',
    roles: ['admin', 'committee', 'resident'],
    group: 'Community',
  },
  {
    label: 'Expected visitors',
    to: '/app/appointments',
    icon: 'visitor',
    roles: ['admin', 'committee', 'resident'],
    group: 'Security',
  },
  {
    label: 'Visitor log',
    to: '/app/visitors',
    icon: 'shield',
    roles: ['admin', 'committee', 'guard'],
    group: 'Security',
  },
  {
    label: 'Gate scan',
    to: '/app/scan',
    icon: 'scan',
    roles: ['admin', 'committee', 'guard'],
    group: 'Security',
  },
  {
    label: 'Gate log',
    to: '/app/gate',
    icon: 'gate',
    roles: ['admin', 'committee', 'guard'],
    group: 'Security',
  },
  { label: 'Safety & access', to: '/app/safety', icon: 'phone', roles: ALL, group: 'Security' },
  { label: 'Settings', to: '/app/settings', icon: 'settings', roles: ALL, group: 'Preferences' },
]

export default function AppShell() {
  const { user, logout } = useAuth()
  const { buildings, current, select } = useBuilding()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [mobile, setMobile] = useState(() => window.matchMedia('(max-width: 1023px)').matches)
  const sidebarRef = useRef<HTMLElement>(null)
  const menuTrigger = useRef<HTMLButtonElement>(null)
  const profileTrigger = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const media = window.matchMedia('(max-width: 1023px)')
    const onChange = () => {
      setMobile(media.matches)
      if (!media.matches) setSidebarOpen(false)
    }
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [])

  useEffect(() => {
    if (!sidebarOpen && !menuOpen) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setSidebarOpen(false)
        setMenuOpen(false)
        if (sidebarOpen) menuTrigger.current?.focus()
        else profileTrigger.current?.focus()
      }
      if (event.key === 'Tab' && sidebarOpen) {
        const links = sidebarRef.current?.querySelectorAll<HTMLElement>('a, button')
        if (!links?.length) return
        const first = links[0]
        const last = links[links.length - 1]
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault()
          last.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault()
          first.focus()
        }
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [sidebarOpen, menuOpen])

  useEffect(() => {
    if (!sidebarOpen) return
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    sidebarRef.current?.querySelector<HTMLElement>('a')?.focus()
    return () => {
      document.body.style.overflow = previous
    }
  }, [sidebarOpen])

  if (!user) return null
  const items = NAV.filter((item) => item.roles.includes(user.role))
  const groups = [...new Set(items.map((item) => item.group))]
  async function handleLogout() {
    await logout()
    navigate('/', { replace: true })
  }

  return (
    <div className="workspace">
      <a href="#workspace-content" className="skip-link">
        Skip to content
      </a>
      <aside
        ref={sidebarRef}
        inert={mobile && !sidebarOpen}
        id="workspace-navigation"
        aria-label="Workspace navigation"
        className={`workspace-sidebar ${sidebarOpen ? 'is-open' : ''}`}
      >
        <div className="sidebar-brand">
          <Link to="/" aria-label="Nibash home">
            <Logo />
          </Link>
          <button
            type="button"
            onClick={() => {
              setSidebarOpen(false)
              menuTrigger.current?.focus()
            }}
            aria-label="Close navigation"
            className="p-1 text-slate-500 lg:hidden"
          >
            <Icon name="close" size={18} />
          </button>
        </div>
        <p className="sidebar-caption">Building a better everyday</p>
        <nav className="workspace-nav">
          {groups.map((group) => (
            <div className="nav-group" key={group}>
              <p className="nav-group-label">{group}</p>
              {items
                .filter((item) => item.group === group)
                .map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.to === '/app'}
                    onClick={() => setSidebarOpen(false)}
                    className={({ isActive }) => (isActive ? 'active' : '')}
                  >
                    <Icon name={item.icon} size={17} />
                    <span>{item.label}</span>
                  </NavLink>
                ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-user">
          <span className="user-avatar">{user.name.charAt(0).toUpperCase()}</span>
          <div className="min-w-0">
            <strong>{user.name}</strong>
            <p>{user.role} workspace</p>
          </div>
        </div>
      </aside>
      {sidebarOpen && (
        <button
          type="button"
          tabIndex={-1}
          aria-label="Close navigation backdrop"
          className="fixed inset-0 z-30 bg-slate-950/30 lg:hidden"
          onClick={() => {
            setSidebarOpen(false)
            menuTrigger.current?.focus()
          }}
        />
      )}
      <div className="workspace-body">
        <header className="workspace-header">
          <div className="flex min-w-0 items-center gap-3">
            <button
              type="button"
              ref={menuTrigger}
              onClick={() => setSidebarOpen(true)}
              className="rounded-md p-1 text-slate-600 lg:hidden"
              aria-label="Open navigation"
              aria-expanded={sidebarOpen}
              aria-controls="workspace-navigation"
            >
              <Icon name="menu" />
            </button>
            <span className="hidden rounded-md border border-slate-200 p-2 text-brand-700 sm:block">
              <Icon name="building" size={18} />
            </span>
            <div className="min-w-0">
              <p className="mb-1 hidden text-[9px] uppercase tracking-widest text-slate-500 sm:block">
                Your building
              </p>
              {buildings.length > 1 ? (
                <select
                  aria-label="Switch building"
                  value={current?.id ?? ''}
                  onChange={(e) => select(Number(e.target.value))}
                  className="w-full max-w-64 truncate rounded border-0 bg-transparent py-1 pr-3 text-xs font-semibold outline-offset-2"
                >
                  <option value="" disabled>
                    Select a building
                  </option>
                  {buildings.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.name}
                    </option>
                  ))}
                </select>
              ) : (
                <p className="truncate text-xs font-semibold text-slate-800">
                  {current?.name ?? 'No building yet'}
                </p>
              )}
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-1 sm:gap-3">
            <SosButton />
            <NotificationBell />
            <div className="relative border-l border-slate-200 pl-2 sm:pl-4">
              <button
                type="button"
                ref={profileTrigger}
                onClick={() => setMenuOpen((open) => !open)}
                className="flex items-center gap-2 rounded-md p-1"
                aria-label="Account menu"
                aria-expanded={menuOpen}
              >
                <span className="user-avatar">{user.name.charAt(0).toUpperCase()}</span>
                <Icon name="chevron" size={12} className="hidden rotate-90 text-slate-500 sm:block" />
              </button>
              {menuOpen && (
                <>
                  <button
                    type="button"
                    aria-label="Close account menu"
                    className="fixed inset-0 z-10 cursor-default"
                    onClick={() => setMenuOpen(false)}
                  />
                  <div className="absolute right-0 z-20 mt-3 w-56 rounded-lg border border-slate-200 bg-white p-2 shadow-lg">
                    <div className="border-b border-slate-100 px-2 py-3">
                      <p className="truncate text-xs font-semibold">{user.name}</p>
                      <p className="mt-1 truncate text-xs text-slate-500">{user.email}</p>
                    </div>
                    <Link
                      to="/app/settings"
                      onClick={() => setMenuOpen(false)}
                      className="flex items-center gap-2 rounded p-2 text-xs hover:bg-slate-50"
                    >
                      <Icon name="settings" size={15} />
                      Settings
                    </Link>
                    <button
                      type="button"
                      onClick={handleLogout}
                      className="flex w-full items-center gap-2 rounded p-2 text-left text-xs hover:bg-slate-50"
                    >
                      <Icon name="logout" size={15} />
                      Sign out
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        </header>
        <main id="workspace-content" className="workspace-main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
