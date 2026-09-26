import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'
import { formatDate, taka } from '../lib/format'
import PublicSite from '../components/PublicSite'
import { Alert, Button, Card, EmptyState, Field, Skeleton, TextArea } from '../components/ui'
import Icon from '../components/Icon'
import type { PublicListing } from '../types'

/** One published flat, and the way to ask for it — signing up on the spot if need be. */
export default function FlatDetail() {
  const { id } = useParams()
  const listingId = Number(id)
  const { data: listing, isLoading, error } = useQuery({
    queryKey: ['public-listing', listingId],
    queryFn: () => api.publicListing(listingId),
    enabled: Number.isInteger(listingId) && listingId > 0,
    retry: false,
  })

  return (
    <PublicSite>
      <section className="site-container py-12 lg:py-16">
        <Link to="/flats" className="text-link">
          <span aria-hidden="true">←</span> All flats
        </Link>

        {isLoading ? (
          <div className="mt-8 grid gap-12 lg:grid-cols-[1.15fr_1fr]">
            <Skeleton className="h-96 w-full" />
            <Skeleton className="h-72 w-full" />
          </div>
        ) : error || !listing ? (
          <div className="mt-8">
            <EmptyState
              icon="home"
              title="This flat isn’t listed any more"
              body={error instanceof ApiError ? error.message : 'It may have been let already.'}
              action={
                <Link to="/flats" className="action-link">
                  See the flats that are <Icon name="arrow" size={16} />
                </Link>
              }
            />
          </div>
        ) : (
          <div className="mt-8 grid items-start gap-12 lg:grid-cols-[1.15fr_1fr]">
            <FlatFacts listing={listing} />
            <ApplyPanel listing={listing} />
          </div>
        )}
      </section>
    </PublicSite>
  )
}

function FlatFacts({ listing }: { listing: PublicListing }) {
  const flat = [
    listing.unit_type,
    listing.size_sqft ? `${Number(listing.size_sqft).toLocaleString('en-US')} sq ft` : null,
    listing.floor != null ? `floor ${listing.floor}` : null,
    listing.unit_number ? `flat ${listing.unit_number}` : null,
  ].filter(Boolean)
  const rows = [
    ['Rent', `${taka(listing.rent)} a month`],
    ['Available', `From ${formatDate(listing.available_from)}`],
    ...(flat.length ? [['The flat', flat.join(' · ')]] : []),
    ['The building', `${listing.building_name} — ${listing.building_address}`],
  ]

  return (
    <div>
      <p className="eyebrow">
        <span className="eyebrow-line" /> {listing.building_name}
      </p>
      <h2>{listing.title}</h2>
      <p className="mt-6 max-w-xl whitespace-pre-line text-sm leading-7 text-slate-600">{listing.description}</p>
      <div className="role-list mt-10">
        {rows.map(([label, value], i) => (
          <div key={label}>
            <span>{String(i + 1).padStart(2, '0')}</span>
            <section>
              <h3>{label}</h3>
              <p>{value}</p>
            </section>
            <Icon name="check" size={18} />
          </div>
        ))}
      </div>
    </div>
  )
}

function ApplyPanel({ listing }: { listing: PublicListing }) {
  const { user } = useAuth()
  const { data: applications, isLoading } = useQuery({
    queryKey: ['rental-applications', user?.id],
    queryFn: () => api.rentalApplications(),
    enabled: Boolean(user),
  })
  const mine = applications?.results.find((a) => a.listing === listing.id)

  return (
    <Card className="lg:sticky lg:top-6">
      <h3 className="text-base font-semibold text-slate-900">Ask to rent this flat</h3>
      <p className="mt-1 text-xs leading-relaxed text-slate-500">
        The building sees your name, email and phone, and replies on your requests page.
      </p>
      <div className="mt-5">
        {!user ? (
          <AccountStep />
        ) : isLoading ? (
          <Skeleton className="h-32 w-full" />
        ) : mine && mine.status !== 'rejected' ? (
          <Sent status={mine.status} requestedAt={mine.requested_at} />
        ) : (
          <RequestStep listing={listing} askedBefore={Boolean(mine)} />
        )}
      </div>
    </Card>
  )
}

/** Signed out: create a renter account, or sign in to an existing one — then the request form shows. */
function AccountStep() {
  const { login, startSession } = useAuth()
  const [mode, setMode] = useState<'new' | 'existing'>('new')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      if (mode === 'new') {
        startSession(
          await api.renterSignup({ name: name.trim(), email: email.trim(), password, phone: phone.trim() || undefined }),
        )
      } else {
        await login(email.trim(), password)
      }
    } catch (err) {
      setError(err instanceof ApiError ? err : new ApiError(0, 'Unable to reach the server.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="Your account">
        {(['new', 'existing'] as const).map((option) => (
          <button
            key={option}
            type="button"
            role="radio"
            aria-checked={mode === option}
            onClick={() => {
              setMode(option)
              setError(null)
            }}
            className={`rounded-lg border px-3 py-2.5 text-sm transition ${
              mode === option
                ? 'border-brand-600 bg-brand-50 font-semibold text-brand-800'
                : 'border-slate-300 bg-white text-slate-700 hover:border-brand-400'
            }`}
          >
            {option === 'new' ? 'New to Nibash' : 'I have an account'}
          </button>
        ))}
      </div>

      {error && !error.fieldError('email') && !error.fieldError('password') && !error.fieldError('name') && (
        <Alert>{error.message}</Alert>
      )}

      {mode === 'new' && (
        <Field
          label="Your name"
          autoComplete="name"
          value={name}
          maxLength={100}
          error={error?.fieldError('name')}
          onChange={(e) => setName(e.target.value)}
        />
      )}
      <Field
        label="Email"
        type="email"
        autoComplete="email"
        value={email}
        error={error?.fieldError('email')}
        onChange={(e) => setEmail(e.target.value)}
      />
      {mode === 'new' && (
        <Field
          label="Phone (optional)"
          inputMode="tel"
          autoComplete="tel"
          value={phone}
          maxLength={20}
          hint="Helps the building reach you quickly."
          onChange={(e) => setPhone(e.target.value)}
        />
      )}
      <Field
        label="Password"
        type="password"
        autoComplete={mode === 'new' ? 'new-password' : 'current-password'}
        placeholder={mode === 'new' ? 'At least 8 characters' : '••••••••'}
        value={password}
        error={error?.fieldError('password')}
        onChange={(e) => setPassword(e.target.value)}
      />
      <Button type="submit" loading={submitting} className="w-full py-3">
        {mode === 'new' ? 'Create account and continue' : 'Sign in and continue'}
      </Button>
    </form>
  )
}

function RequestStep({ listing, askedBefore }: { listing: PublicListing; askedBefore: boolean }) {
  const { user } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [message, setMessage] = useState('')

  const apply = useMutation({
    mutationFn: () => api.applyForFlat(listing.id, message.trim()),
    onSuccess: () => {
      toast.success('Request sent. The building will reply on your requests page.')
      queryClient.invalidateQueries({ queryKey: ['rental-applications'] })
    },
  })

  return (
    <div className="space-y-4">
      {apply.error && <Alert>{apply.error instanceof ApiError ? apply.error.message : 'Could not send the request.'}</Alert>}
      {askedBefore && (
        <p className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600">
          The building declined your last request for this flat. You can ask again if something has changed.
        </p>
      )}
      <TextArea
        label="A note for the building (optional)"
        rows={4}
        maxLength={1000}
        placeholder="Who will live there, when you’d like to move in, anything they should know."
        value={message}
        onChange={(e) => setMessage(e.target.value)}
      />
      <Button className="w-full py-3" loading={apply.isPending} onClick={() => apply.mutate()}>
        <Icon name="send" size={16} />
        Send request
      </Button>
      <p className="text-center text-[11px] text-slate-500">
        Signed in as {user?.name} · {user?.email}
      </p>
    </div>
  )
}

function Sent({ status, requestedAt }: { status: 'pending' | 'approved' | 'rejected'; requestedAt: string }) {
  return (
    <div className="space-y-4 text-sm">
      <div className="flex items-start gap-3 rounded-lg border border-brand-200 bg-brand-50 p-3.5">
        <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-brand-700 text-white">
          <Icon name="check" size={18} />
        </span>
        <div>
          <p className="font-semibold text-slate-900">
            {status === 'approved' ? 'Approved — welcome home.' : 'Request sent'}
          </p>
          <p className="mt-0.5 text-xs text-slate-600">
            {status === 'approved'
              ? 'You’re now a resident of this building.'
              : `You asked on ${formatDate(requestedAt)}. The building will reply on your requests page.`}
          </p>
        </div>
      </div>
      <Link to="/flats/mine" className="action-link w-full">
        {status === 'approved' ? 'Go to my requests' : 'See my requests'} <Icon name="arrow" size={16} />
      </Link>
    </div>
  )
}
