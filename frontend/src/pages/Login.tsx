import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { ApiError } from '../lib/api'
import { Alert, Button, Field } from '../components/ui'
import AuthLayout from '../components/AuthLayout'

/** Seeded accounts (spec §4.4) — quick-fill buttons land in Week 2 when the seeder exists. */
const DEMO_ACCOUNTS = [
  { label: 'Admin', email: 'admin1@nibash.bd' },
  { label: 'Committee', email: 'committee1@nibash.bd' },
  { label: 'Resident', email: 'resident1@nibash.bd' },
  { label: 'Guard', email: 'guard1@nibash.bd' },
  { label: 'Staff', email: 'staff1@nibash.bd' },
]
const DEMO_PASSWORD = 'Nibash@2026'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // Bounce back to whatever the user was trying to reach before we redirected them here.
  const from = (location.state as { from?: string } | null)?.from ?? '/app'

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(email.trim(), password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err : new ApiError(0, 'Unable to reach the server.'))
    } finally {
      setSubmitting(false)
    }
  }

  function fillDemo(demoEmail: string) {
    setEmail(demoEmail)
    setPassword(DEMO_PASSWORD)
    setError(null)
  }

  return (
    <AuthLayout
      title="Welcome back"
      subtitle="Sign in to your building workspace."
      footer={
        <>
          New here?{' '}
          <Link to="/signup" className="font-semibold text-brand-700 hover:underline">
            Create a workspace
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        {error && !error.fieldError('email') && !error.fieldError('password') && (
          <Alert>{error.message}</Alert>
        )}

        <Field
          label="Email"
          type="email"
          autoComplete="email"
          placeholder="you@building.com"
          value={email}
          error={error?.fieldError('email')}
          onChange={(e) => setEmail(e.target.value)}
          required
        />

        <Field
          label="Password"
          type="password"
          autoComplete="current-password"
          placeholder="••••••••"
          value={password}
          error={error?.fieldError('password')}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        <Button type="submit" loading={submitting} className="w-full py-3">
          {submitting ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>

      <div className="mt-8 rounded-lg border border-dashed border-slate-300 bg-slate-50/70 p-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Demo accounts</p>
        <div className="mt-3 flex flex-wrap gap-2">
          {DEMO_ACCOUNTS.map((account) => (
            <button
              key={account.email}
              type="button"
              onClick={() => fillDemo(account.email)}
              className="rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 transition hover:border-brand-400 hover:text-brand-800"
            >
              {account.label}
            </button>
          ))}
        </div>
      </div>
    </AuthLayout>
  )
}
