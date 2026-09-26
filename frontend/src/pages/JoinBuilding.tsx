import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { formatDate } from '../lib/format'
import { Alert, Button, Field, Skeleton } from '../components/ui'
import AuthLayout from '../components/AuthLayout'
import { ROLE_LABEL } from '../lib/invitations'

/**
 * The page an invitation link opens: /join#<token>. The token rides in the URL fragment, which
 * browsers never send to a server, so it stays out of access logs and Referer headers. The invitee
 * sets a password (or, if their email already has a Nibash account, confirms it) and lands in the
 * building's workspace.
 */
export default function JoinBuilding() {
  const { startSession } = useAuth()
  const navigate = useNavigate()
  const [token] = useState(() => decodeURIComponent(window.location.hash.replace(/^#/, '')).trim())
  const [name, setName] = useState<string | null>(null)
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const {
    data: invitation,
    error: lookupError,
    isLoading,
  } = useQuery({
    queryKey: ['invitation-preview', token],
    queryFn: () => api.previewInvitation(token),
    enabled: Boolean(token),
    retry: false,
    staleTime: Infinity,
  })

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const session = await api.acceptInvitation({
        token,
        password,
        name: name?.trim() || undefined,
        phone: phone.trim() || undefined,
      })
      startSession(session)
      // The link is spent; don't leave it in the address bar or history.
      window.history.replaceState(null, '', window.location.pathname)
      navigate('/app', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err : new ApiError(0, 'Unable to reach the server.'))
    } finally {
      setSubmitting(false)
    }
  }

  const footer = (
    <>
      Already joined?{' '}
      <Link to="/login" className="font-semibold text-brand-700 hover:underline">
        Sign in
      </Link>
    </>
  )

  if (!token || lookupError) {
    return (
      <AuthLayout title="This link doesn’t work" subtitle="The invitation couldn’t be opened." footer={footer}>
        <Alert>
          {lookupError instanceof ApiError
            ? lookupError.message
            : 'The link is incomplete. Open it again from the message you were sent, or ask for a new one.'}
        </Alert>
      </AuthLayout>
    )
  }

  if (isLoading || !invitation) {
    return (
      <AuthLayout title="Opening your invitation" subtitle="One moment…">
        <div className="space-y-4">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-11 w-full" />
          <Skeleton className="h-11 w-full" />
        </div>
      </AuthLayout>
    )
  }

  const joiningAs =
    invitation.role === 'staff' && invitation.staff_role
      ? `${ROLE_LABEL.staff} · ${invitation.staff_role}`
      : ROLE_LABEL[invitation.role]

  return (
    <AuthLayout
      title={`Join ${invitation.building_name}`}
      subtitle={
        invitation.account_exists
          ? 'You already have a Nibash account — confirm your password to add this building to it.'
          : 'Set a password and you’re in. It takes a few seconds.'
      }
      footer={footer}
    >
      <div className="mb-6 rounded-lg border border-slate-200 bg-slate-50/70 p-4 text-sm">
        <p className="font-semibold text-slate-900">{invitation.name}</p>
        <p className="mt-1 text-xs text-slate-600">
          {joiningAs}
          {invitation.unit_number ? ` · Flat ${invitation.unit_number}` : ''}
        </p>
        <p className="mt-2 text-xs text-slate-500">
          {invitation.building_address} · invitation valid until {formatDate(invitation.expires_at)}
        </p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        {error && <Alert>{error.message}</Alert>}

        <Field label="Email" type="email" value={invitation.email} readOnly disabled />

        {!invitation.account_exists && (
          <>
            <Field
              label="Your name"
              autoComplete="name"
              value={name ?? invitation.name}
              maxLength={100}
              onChange={(e) => setName(e.target.value)}
            />
            <Field
              label="Phone (optional)"
              inputMode="tel"
              autoComplete="tel"
              value={phone}
              maxLength={20}
              onChange={(e) => setPhone(e.target.value)}
            />
          </>
        )}

        <Field
          label={invitation.account_exists ? 'Your Nibash password' : 'Choose a password'}
          type="password"
          autoComplete={invitation.account_exists ? 'current-password' : 'new-password'}
          placeholder={invitation.account_exists ? '••••••••' : 'At least 8 characters'}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        <Button type="submit" loading={submitting} disabled={!password} className="w-full py-3">
          {submitting ? 'Joining…' : `Join ${invitation.building_name}`}
        </Button>
      </form>
    </AuthLayout>
  )
}
