import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { ApiError } from '../lib/api'
import { Alert, Button, Field } from '../components/ui'
import AuthLayout from '../components/AuthLayout'

/** These become `enabled_modules` on the new building (spec §4.2). */
const AVAILABLE_MODULES = [
  'Finance',
  'Visitors',
  'Maintenance',
  'Messaging',
  'Documents',
  'Units & Occupancy',
  'Assets & Compliance',
  'Parking & Access',
]

/**
 * Mirrors the server's password policy (spec §4.2) so the user sees problems while typing rather
 * than after a round trip. The server still validates — this is a courtesy, not the gate.
 */
function checkPassword(password: string, email: string, name: string) {
  const checks = [
    { label: 'At least 8 characters', ok: password.length >= 8 },
    { label: 'Not entirely numbers', ok: password.length > 0 && !/^\d+$/.test(password) },
    { label: 'Not a common password', ok: password.length > 0 && !isCommon(password) },
    { label: 'Not similar to your name or email', ok: password.length > 0 && !isSimilar(password, email, name) },
  ]
  const passed = checks.filter((c) => c.ok).length
  return { checks, passed, strong: passed === checks.length }
}

const COMMON = new Set([
  'password', 'password1', 'password123', '12345678', '123456789', '1234567890',
  'qwerty123', 'qwertyuiop', 'abc12345', 'iloveyou', 'admin123', 'welcome1',
  'letmein1', 'passw0rd', 'changeme', 'nibash123',
])

function isCommon(password: string) {
  return COMMON.has(password.toLowerCase())
}

function isSimilar(password: string, email: string, name: string) {
  const lower = password.toLowerCase()
  const candidates = [
    email.toLowerCase(),
    email.split('@')[0]?.toLowerCase() ?? '',
    ...name.toLowerCase().split(/\s+/),
  ].filter((c) => c.length >= 3)
  return candidates.some((c) => lower.includes(c) || c.includes(lower))
}

export default function Signup() {
  const { signup } = useAuth()
  const navigate = useNavigate()

  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [buildingName, setBuildingName] = useState('')
  const [modules, setModules] = useState<string[]>(AVAILABLE_MODULES)
  const [error, setError] = useState<ApiError | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const strength = useMemo(() => checkPassword(password, email, name), [password, email, name])

  function toggleModule(module: string) {
    setModules((current) =>
      current.includes(module) ? current.filter((m) => m !== module) : [...current, module],
    )
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await signup({
        name: name.trim(),
        email: email.trim(),
        password,
        building_name: buildingName.trim() || undefined,
        modules,
      })
      navigate('/app', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err : new ApiError(0, 'Unable to reach the server.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Create your workspace"
      subtitle="You'll be the admin of your first building. It takes about a minute."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-brand-700 hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        {error && <Alert>{error.message}</Alert>}

        <Field
          label="Your name"
          autoComplete="name"
          placeholder="Ayesha Rahman"
          value={name}
          error={error?.fieldError('name')}
          onChange={(e) => setName(e.target.value)}
          required
        />

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

        <div>
          <Field
            label="Password"
            type="password"
            autoComplete="new-password"
            placeholder="At least 8 characters"
            value={password}
            error={error?.fieldError('password')}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          {password.length > 0 && <PasswordMeter strength={strength} />}
        </div>

        <Field
          label="Building name"
          placeholder="Gulshan Lakeview Heights"
          value={buildingName}
          onChange={(e) => setBuildingName(e.target.value)}
          hint="Optional — we'll call it “Nibash Demo Tower” if you leave it blank."
        />

        <fieldset>
          <legend className="text-sm font-medium text-slate-700">Modules to enable</legend>
          <p className="mt-1 text-xs text-slate-500">
            Turn anything on or off later in settings. {modules.length} of {AVAILABLE_MODULES.length} selected.
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            {AVAILABLE_MODULES.map((module) => {
              const selected = modules.includes(module)
              return (
                <button
                  key={module}
                  type="button"
                  onClick={() => toggleModule(module)}
                  aria-pressed={selected}
                  className={`rounded-full border px-3 py-1.5 text-xs font-medium transition ${
                    selected
                      ? 'border-brand-600 bg-brand-600 text-white'
                      : 'border-slate-300 bg-white text-slate-600 hover:border-brand-400'
                  }`}
                >
                  {selected && <span aria-hidden="true">✓ </span>}
                  {module}
                </button>
              )
            })}
          </div>
        </fieldset>

        <Button type="submit" loading={submitting} className="w-full py-3">
          {submitting ? 'Creating your workspace…' : 'Create workspace'}
        </Button>

        <p className="text-center text-xs text-slate-500">
          No credit card required. You can invite your committee once you're in.
        </p>
      </form>
    </AuthLayout>
  )
}

function PasswordMeter({ strength }: { strength: ReturnType<typeof checkPassword> }) {
  const colors = ['bg-red-400', 'bg-orange-400', 'bg-amber-400', 'bg-emerald-500']
  return (
    <div className="mt-2.5 space-y-2">
      <div className="flex gap-1" aria-hidden="true">
        {[0, 1, 2, 3].map((index) => (
          <span
            key={index}
            className={`h-1.5 flex-1 rounded-full transition-colors ${
              index < strength.passed ? colors[strength.passed - 1] : 'bg-slate-200'
            }`}
          />
        ))}
      </div>
      <ul className="space-y-1">
        {strength.checks.map((check) => (
          <li key={check.label} className={`flex items-center gap-1.5 text-xs ${check.ok ? 'text-emerald-700' : 'text-slate-500'}`}>
            <span aria-hidden="true">{check.ok ? '✓' : '○'}</span>
            {check.label}
          </li>
        ))}
      </ul>
    </div>
  )
}
