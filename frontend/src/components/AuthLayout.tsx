import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Logo } from './ui'

/** Split layout shared by login and signup: form on the left, brand panel on the right. */
export default function AuthLayout({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string
  subtitle: string
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <div className="flex min-h-screen">
      <div className="flex w-full flex-col justify-center px-6 py-12 lg:w-1/2 lg:px-16">
        <div className="mx-auto w-full max-w-md">
          <Link to="/" className="inline-block rounded focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500">
            <Logo />
          </Link>

          <h1 className="mt-10 text-2xl font-bold tracking-tight text-slate-900">{title}</h1>
          <p className="mt-1.5 text-sm text-slate-600">{subtitle}</p>

          <div className="mt-8">{children}</div>

          {footer && <p className="mt-8 text-sm text-slate-600">{footer}</p>}
        </div>
      </div>

      {/* Brand panel — hidden on small screens so the form owns the viewport. */}
      <aside className="relative hidden w-1/2 overflow-hidden bg-gradient-to-br from-brand-900 to-brand-700 lg:block">
        <div
          className="pointer-events-none absolute inset-0 opacity-25"
          style={{ backgroundImage: 'radial-gradient(circle at 30% 30%, #22d3ee 0, transparent 50%)' }}
          aria-hidden="true"
        />
        <div className="relative flex h-full flex-col justify-center px-16 text-white">
          <blockquote className="max-w-md text-2xl font-semibold leading-snug">
            “One deployment, many buildings, strict isolation — every record belongs to a building,
            and you only ever see yours.”
          </blockquote>
          <ul className="mt-10 space-y-3 text-sm text-brand-100/85">
            {[
              'Invoices, checkout and monthly service charges',
              'QR visitor passes and gate analytics',
              'Tickets that assign themselves to the right staff',
              'Bookings with live conflict detection',
            ].map((item) => (
              <li key={item} className="flex gap-2">
                <span className="text-brand-300" aria-hidden="true">✓</span>
                {item}
              </li>
            ))}
          </ul>
        </div>
      </aside>
    </div>
  )
}
