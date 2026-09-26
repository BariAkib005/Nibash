import { Link, Navigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'
import { formatDate, taka } from '../lib/format'
import PublicSite from '../components/PublicSite'
import { Badge } from '../components/DataTable'
import { EmptyState, Skeleton } from '../components/ui'
import Icon from '../components/Icon'
import type { ApiRentalApplication } from '../types'

const STATUS: Record<string, { label: string; tone: 'amber' | 'green' | 'slate' }> = {
  pending: { label: 'Waiting for the building', tone: 'amber' },
  approved: { label: 'Approved', tone: 'green' },
  rejected: { label: 'Not this time', tone: 'slate' },
  let: { label: 'Let to someone else', tone: 'slate' },
}

/**
 * A renter's requests and what the buildings said. It is also where anyone signed in without a
 * building lands, so once a request is approved it points them into their new building.
 */
export default function MyApplications() {
  const { user, building, loading } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['rental-applications', user?.id],
    queryFn: () => api.rentalApplications(),
    enabled: Boolean(user),
  })
  const withdraw = useMutation({
    mutationFn: (id: number) => api.withdrawApplication(id),
    onSuccess: () => {
      toast.success('Request withdrawn.')
      queryClient.invalidateQueries({ queryKey: ['rental-applications'] })
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not withdraw it.'),
  })

  if (!loading && !user) {
    return <Navigate to="/login" replace state={{ from: '/flats/mine' }} />
  }

  const rows = data?.results ?? []
  const approved = rows.find((a) => a.status === 'approved')
  const home = building?.name ?? approved?.building_name
  const firstName = user?.name.split(' ')[0] ?? ''

  return (
    <PublicSite>
      <section className="site-container features-section">
        <div className="section-intro">
          <div>
            <p className="eyebrow">
              <span className="eyebrow-line" /> YOUR REQUESTS
            </p>
            <h2>{firstName ? `Hello, ${firstName}.` : 'Your requests.'}</h2>
          </div>
          <p>The flats you’ve asked about, and what each building said.</p>
        </div>

        {home && (
          <div className="mb-10 flex flex-wrap items-center justify-between gap-5 rounded-md border border-brand-200 bg-brand-50 px-6 py-5">
            <div className="flex items-center gap-4">
              <span className="visual-note-icon">
                <Icon name="building" size={22} />
              </span>
              <div>
                <p className="text-sm font-semibold text-slate-900">You’re a resident of {home}.</p>
                <p className="mt-1 text-xs text-slate-600">Bills, notices, visitors and the rest are in your building’s workspace.</p>
              </div>
            </div>
            {/* A full load, so the session picks up the building the approval just gave them. */}
            <a href="/app" className="action-link">
              Open your building <Icon name="arrow" size={16} />
            </a>
          </div>
        )}

        {loading || isLoading ? (
          <div className="space-y-3">
            {[0, 1].map((i) => <Skeleton key={i} className="h-20 w-full" />)}
          </div>
        ) : rows.length ? (
          <div className="role-list">
            {rows.map((application, i) => (
              <ApplicationRow
                key={application.id}
                number={i + 1}
                application={application}
                withdrawing={withdraw.isPending}
                onWithdraw={() => withdraw.mutate(application.id)}
              />
            ))}
          </div>
        ) : (
          <EmptyState
            icon="home"
            title="No requests yet"
            body="Find a flat you like and ask for it — the building replies here."
            action={
              <Link to="/flats" className="action-link">
                Browse flats <Icon name="arrow" size={16} />
              </Link>
            }
          />
        )}
      </section>
    </PublicSite>
  )
}

function ApplicationRow({
  number,
  application,
  withdrawing,
  onWithdraw,
}: {
  number: number
  application: ApiRentalApplication
  withdrawing: boolean
  onWithdraw: () => void
}) {
  const status = STATUS[application.listing_let ? 'let' : application.status]
  const open = application.status === 'pending' && !application.listing_let

  return (
    <div className="flex-wrap sm:flex-nowrap">
      <span>{String(number).padStart(2, '0')}</span>
      <section className="min-w-0">
        <h3>
          {open ? (
            <Link to={`/flats/${application.listing}`} className="hover:underline hover:underline-offset-4">
              {application.listing_title}
            </Link>
          ) : (
            application.listing_title
          )}
        </h3>
        <p>
          {application.building_name} · {taka(application.rent)} a month · from {formatDate(application.available_from)}
        </p>
        <p>
          Asked {formatDate(application.requested_at)}
          {application.message ? ` · “${application.message}”` : ''}
        </p>
      </section>
      <span className="flex shrink-0 items-center gap-3">
        <Badge tone={status.tone}>{status.label}</Badge>
        {open && (
          <button type="button" className="text-link !text-[11px]" disabled={withdrawing} onClick={onWithdraw}>
            Withdraw
          </button>
        )}
      </span>
    </div>
  )
}
