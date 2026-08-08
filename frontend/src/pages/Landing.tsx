import { Link } from 'react-router-dom'
import { useReveal } from '../lib/useReveal'
import { Button, Logo } from '../components/ui'
import { useAuth } from '../lib/auth'

const MODULES = [
  {
    icon: '💳',
    title: 'Finance',
    body: 'Invoices with line items, one-click checkout, monthly service-charge generation, utility bills, expense tracking with receipts and monthly reports.',
  },
  {
    icon: '🛡️',
    title: 'Security & Visitors',
    body: 'Pre-authorised visitor appointments with QR gate passes, guard scan check-in/out, gate event log with hourly analytics, SOS alerts and intercom logs.',
  },
  {
    icon: '🔧',
    title: 'Maintenance',
    body: 'Tickets that auto-assign to the right staff member, photo evidence, a status board, staff registry and attendance check-in/out.',
  },
  {
    icon: '📣',
    title: 'Community',
    body: 'Notice board with pinning and search, one-vote-per-resident polls, real-time group chat, events with RSVPs and a privacy-aware resident directory.',
  },
  {
    icon: '🏛️',
    title: 'Facilities',
    body: 'Book the rooftop lounge or community hall with live conflict detection, plus a visual parking grid and resident vehicle registry.',
  },
  {
    icon: '📊',
    title: 'Dashboard',
    body: 'Collection rate, outstanding balance, occupancy, open tickets and expected visitors — every building metric in one place.',
  },
]

const ROLES = [
  { role: 'Admin', body: 'Full operations and configuration', icon: '👤' },
  { role: 'Committee', body: 'Finance approvals, notices, governance', icon: '🤝' },
  { role: 'Resident', body: 'Bills, bookings, visitors, community', icon: '🏠' },
  { role: 'Guard', body: 'Visitor scanning and gate security', icon: '🛂' },
  { role: 'Staff', body: 'Assigned tickets and attendance', icon: '🧰' },
]

const PROBLEMS = [
  'Service charges tracked in spreadsheets, disputed every month',
  'Payments in cash with no receipt trail or audit history',
  'Visitors waved through the gate with nothing logged',
  'Maintenance requests lost in WhatsApp threads',
  'Every building managed in isolation, nothing shared',
]

export default function Landing() {
  useReveal()
  const { user } = useAuth()

  return (
    <div className="min-h-screen bg-white">
      <SiteHeader signedIn={Boolean(user)} />

      {/* ---------------------------------------------------------------- hero */}
      <section className="relative overflow-hidden bg-gradient-to-b from-brand-950 via-brand-900 to-brand-800">
        <div
          className="pointer-events-none absolute inset-0 opacity-20"
          style={{
            backgroundImage:
              'radial-gradient(circle at 20% 20%, #22d3ee 0, transparent 45%), radial-gradient(circle at 80% 0%, #0891b2 0, transparent 40%)',
          }}
          aria-hidden="true"
        />
        <div className="relative mx-auto max-w-6xl px-6 py-24 sm:py-32">
          <div className="max-w-3xl">
            <span className="inline-flex items-center rounded-full border border-brand-400/40 bg-brand-400/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-brand-200">
              Multi-tenant building management
            </span>
            <h1 className="mt-6 text-4xl font-bold leading-tight tracking-tight text-white sm:text-6xl">
              Every building operation.
              <span className="block text-brand-300">One platform.</span>
            </h1>
            <p className="mt-6 max-w-2xl text-lg leading-relaxed text-brand-100/90">
              Nibash (Bangla: <span lang="bn">নিবাস</span> — “residence”) replaces the spreadsheets,
              cash books and scattered WhatsApp threads that run most apartment buildings — with
              finance, security, maintenance and community in a single cloud platform.
            </p>
            <div className="mt-10 flex flex-wrap gap-3">
              <Link to={user ? '/app' : '/signup'}>
                <Button className="px-6 py-3 text-base">
                  {user ? 'Open dashboard' : 'Get started free'} →
                </Button>
              </Link>
              <a href="#modules">
                <Button variant="secondary" className="border-white/30 bg-white/10 px-6 py-3 text-base text-white hover:bg-white/20">
                  See what it does
                </Button>
              </a>
            </div>
            <p className="mt-5 text-sm text-brand-200/80">
              Free to start · Your first building set up in under a minute
            </p>
          </div>

          <dl className="mt-16 grid max-w-3xl grid-cols-2 gap-6 sm:grid-cols-4">
            {[
              ['17', 'modules'],
              ['5', 'user roles'],
              ['1', 'platform, many buildings'],
              ['BDT', 'built for Bangladesh'],
            ].map(([value, label]) => (
              <div key={label}>
                <dt className="text-3xl font-bold text-white">{value}</dt>
                <dd className="mt-1 text-sm text-brand-200/80">{label}</dd>
              </div>
            ))}
          </dl>
        </div>
      </section>

      {/* ---------------------------------------------------------------- problem */}
      <section className="mx-auto max-w-6xl px-6 py-20">
        <div className="reveal grid gap-12 lg:grid-cols-2">
          <div>
            <SectionLabel>The problem</SectionLabel>
            <h2 className="mt-3 text-3xl font-bold tracking-tight text-slate-900">
              Buildings are still run on paper, cash and group chats
            </h2>
            <p className="mt-4 leading-relaxed text-slate-600">
              As cities like Dhaka grow upward, manual building management stops scaling. There is no
              single source of truth, so service charges get disputed, payments go untracked, and
              security has no record of who came through the gate.
            </p>
            <p className="mt-4 leading-relaxed text-slate-600">
              Residents want transparency. Committees need accurate collections and reporting. Both
              need a system that is available anywhere, always on, and safely shared across buildings.
            </p>
          </div>
          <ul className="space-y-3">
            {PROBLEMS.map((problem) => (
              <li key={problem} className="flex gap-3 rounded-lg border border-slate-200 bg-slate-50/70 px-4 py-3">
                <span className="mt-0.5 text-red-500" aria-hidden="true">✕</span>
                <span className="text-sm text-slate-700">{problem}</span>
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* ---------------------------------------------------------------- modules */}
      <section id="modules" className="border-y border-slate-200 bg-slate-50 py-20">
        <div className="mx-auto max-w-6xl px-6">
          <div className="reveal mx-auto max-w-2xl text-center">
            <SectionLabel>What you get</SectionLabel>
            <h2 className="mt-3 text-3xl font-bold tracking-tight text-slate-900">
              Everything a building actually needs
            </h2>
            <p className="mt-4 text-slate-600">
              Not a collection of disconnected tools — one system where a payment, a ticket and a
              gate scan all belong to the same building.
            </p>
          </div>

          <div className="mt-14 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {MODULES.map((module) => (
              <article
                key={module.title}
                className="reveal group rounded-xl border border-slate-200 bg-white p-6 shadow-sm transition hover:-translate-y-1 hover:border-brand-300 hover:shadow-md"
              >
                <span className="grid h-11 w-11 place-items-center rounded-lg bg-brand-50 text-xl" aria-hidden="true">
                  {module.icon}
                </span>
                <h3 className="mt-4 text-lg font-semibold text-slate-900">{module.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-slate-600">{module.body}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      {/* ---------------------------------------------------------------- roles */}
      <section className="mx-auto max-w-6xl px-6 py-20">
        <div className="reveal mx-auto max-w-2xl text-center">
          <SectionLabel>Built for everyone in the building</SectionLabel>
          <h2 className="mt-3 text-3xl font-bold tracking-tight text-slate-900">
            Five roles, one system
          </h2>
          <p className="mt-4 text-slate-600">
            Each person signs into the same platform and sees exactly the modules their role needs —
            nothing more.
          </p>
        </div>

        <div className="reveal mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          {ROLES.map((role) => (
            <div key={role.role} className="rounded-xl border border-slate-200 p-5 text-center transition hover:border-brand-300 hover:bg-brand-50/40">
              <span className="text-2xl" aria-hidden="true">{role.icon}</span>
              <h3 className="mt-3 font-semibold text-slate-900">{role.role}</h3>
              <p className="mt-1 text-xs leading-relaxed text-slate-600">{role.body}</p>
            </div>
          ))}
        </div>
      </section>

      {/* ---------------------------------------------------------------- trust */}
      <section className="border-y border-slate-200 bg-brand-950 py-20 text-white">
        <div className="mx-auto max-w-6xl px-6">
          <div className="reveal grid gap-12 lg:grid-cols-2">
            <div>
              <SectionLabel dark>Why it is safe to share</SectionLabel>
              <h2 className="mt-3 text-3xl font-bold tracking-tight">
                One deployment, many buildings, strict isolation
              </h2>
              <p className="mt-4 leading-relaxed text-brand-100/85">
                Nibash is multi-tenant by design. Every record belongs to a building, and every
                request is filtered to the buildings you actually belong to. A resident of one tower
                cannot read another tower’s invoices — not through a list, a detail page, or any
                shortcut.
              </p>
            </div>
            <ul className="space-y-4">
              {[
                ['Row-level tenant isolation', 'Enforced on every endpoint, including custom actions'],
                ['Role-based permissions', 'Writes to finance and governance are restricted to admin and committee'],
                ['Privacy by default', 'Directory contact details require explicit opt-in'],
                ['Audit trails', 'Document access and activity logged with user and timestamp'],
              ].map(([title, body]) => (
                <li key={title} className="flex gap-3 rounded-lg border border-white/10 bg-white/5 px-4 py-3">
                  <span className="mt-0.5 text-brand-300" aria-hidden="true">✓</span>
                  <span>
                    <span className="block text-sm font-semibold">{title}</span>
                    <span className="block text-sm text-brand-100/75">{body}</span>
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </section>

      {/* ---------------------------------------------------------------- CTA */}
      <section className="mx-auto max-w-4xl px-6 py-24 text-center">
        <div className="reveal">
          <h2 className="text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">
            Set up your building in under a minute
          </h2>
          <p className="mx-auto mt-4 max-w-xl text-slate-600">
            Create your workspace, pick the modules you want, and invite your committee. No credit
            card, no installation.
          </p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Link to={user ? '/app' : '/signup'}>
              <Button className="px-6 py-3 text-base">
                {user ? 'Open dashboard' : 'Create your workspace'} →
              </Button>
            </Link>
            <Link to="/login">
              <Button variant="secondary" className="px-6 py-3 text-base">
                I already have an account
              </Button>
            </Link>
          </div>
        </div>
      </section>

      <SiteFooter />
    </div>
  )
}

function SectionLabel({ children, dark = false }: { children: React.ReactNode; dark?: boolean }) {
  return (
    <span className={`text-xs font-bold uppercase tracking-widest ${dark ? 'text-brand-300' : 'text-brand-700'}`}>
      {children}
    </span>
  )
}

function SiteHeader({ signedIn }: { signedIn: boolean }) {
  return (
    <header className="sticky top-0 z-40 border-b border-slate-200/70 bg-white/85 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-3.5">
        <Link to="/" className="rounded focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500">
          <Logo />
        </Link>
        <nav className="flex items-center gap-2">
          <a href="#modules" className="hidden rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:text-slate-900 sm:block">
            Modules
          </a>
          {signedIn ? (
            <Link to="/app">
              <Button>Dashboard</Button>
            </Link>
          ) : (
            <>
              <Link to="/login">
                <Button variant="ghost">Log in</Button>
              </Link>
              <Link to="/signup">
                <Button>Get started</Button>
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  )
}

function SiteFooter() {
  return (
    <footer className="border-t border-slate-200 bg-slate-50">
      <div className="mx-auto flex max-w-6xl flex-col gap-4 px-6 py-10 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <Logo />
          <p className="mt-2 max-w-sm text-sm text-slate-600">
            Managing buildings, on the cloud. Built for the Bangladeshi market — BDT, Asia/Dhaka,
            Bangla and English.
          </p>
        </div>
        <p className="text-xs text-slate-500">
          © {new Date().getFullYear()} Nibash
        </p>
      </div>
    </footer>
  )
}
