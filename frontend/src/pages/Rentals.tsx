import { useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import { useCurrentResident } from '../lib/resident'
import { useToast } from '../lib/toast'
import { Badge } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import { Button, Card, EmptyState, Field, Modal, Select, Skeleton, TextArea } from '../components/ui'
import Icon from '../components/Icon'
import { formatDate, taka } from '../lib/format'
import type { ApiListing, ApiRentalRequest } from '../types'

const CITIES = ['Dhaka', 'Chattogram', 'Sylhet', 'Khulna', 'Rajshahi']
const REQUEST_TONE = { pending: 'amber', approved: 'green', rejected: 'slate' } as const

/**
 * Rentals (spec §8.14): residents list a flat, neighbours ask about it, and the lister approves or
 * declines. The rent guide reads the ML estimator's cached figure for a city (§8.22).
 */
export default function Rentals() {
  const { user } = useAuth()
  const { currentId } = useBuilding()
  const { resident } = useCurrentResident()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [listing, setListing] = useState(false)

  const { data: listings, isLoading } = useQuery({
    queryKey: ['listings', currentId],
    queryFn: () => api.listings({ building_id: currentId }),
    enabled: Boolean(currentId),
  })
  const { data: requests } = useQuery({
    queryKey: ['rental-requests', currentId],
    queryFn: () => api.rentalRequests({ building_id: currentId }),
    enabled: Boolean(currentId),
  })

  const requestList = useMemo(() => requests?.results ?? [], [requests])
  const incoming = requestList.filter((r) => r.lister_user === user?.id)
  const mine = requestList.filter((r) => r.tenant_user === user?.id)
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['rental-requests'] })
    queryClient.invalidateQueries({ queryKey: ['listings'] })
  }
  const fail = (fallback: string) => (error: unknown) =>
    toast.error(error instanceof ApiError ? error.message : fallback)

  const ask = useMutation({
    mutationFn: (id: number) => api.requestRental(id),
    onSuccess: (r) => {
      toast.success(`Request sent for “${r.listing_title}”.`)
      refresh()
    },
    onError: fail('Could not send the request.'),
  })
  const decide = useMutation({
    mutationFn: ({ id, status }: { id: number; status: 'approved' | 'rejected' }) => api.decideRental(id, status),
    onSuccess: (r) => {
      toast.success(`${r.tenant_name}'s request ${r.status}.`)
      refresh()
    },
    onError: fail('Could not update the request.'),
  })
  const withdraw = useMutation({
    mutationFn: (id: number) => api.withdrawRental(id),
    onSuccess: () => {
      toast.success('Request withdrawn.')
      refresh()
    },
    onError: fail('Could not withdraw the request.'),
  })

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Rentals"
        subtitle="Flats for rent in the building — list yours, or ask about one."
        actions={
          resident && (
            <Button onClick={() => setListing(true)}>
              <Icon name="plus" size={16} />
              List a flat
            </Button>
          )
        }
      />

      <RentGuide />

      <section className="space-y-3">
        <h2 className="dashboard-section-title">Available flats</h2>
        {isLoading ? (
          <div className="grid gap-3 md:grid-cols-2">{[0, 1].map((i) => <Skeleton key={i} className="h-52 w-full" />)}</div>
        ) : listings?.results.length ? (
          <div className="grid gap-3 md:grid-cols-2">
            {listings.results.map((item) => (
              <ListingCard
                key={item.id}
                listing={item}
                mine={item.lister_user === user?.id}
                myRequest={mine.find((r) => r.listing === item.id)}
                requestCount={incoming.filter((r) => r.listing === item.id && r.status === 'pending').length}
                canAsk={Boolean(resident)}
                asking={ask.isPending}
                onAsk={() => ask.mutate(item.id)}
              />
            ))}
          </div>
        ) : (
          <EmptyState
            icon="home"
            title="Nothing listed right now"
            body="When a neighbour lists a flat it appears here. Moving out? List yours."
            action={resident ? <Button onClick={() => setListing(true)}>List a flat</Button> : undefined}
          />
        )}
      </section>

      {(incoming.length > 0 || mine.length > 0) && (
        <div className="grid gap-5 lg:grid-cols-2">
          {incoming.length > 0 && (
            <RequestList
              title="Requests for your flats"
              hint="Approve one to go ahead; the others stay on file."
              requests={incoming}
              render={(r) =>
                r.status === 'pending' ? (
                  <span className="flex gap-1">
                    <Button className="px-2.5 py-1 text-xs" disabled={decide.isPending} onClick={() => decide.mutate({ id: r.id, status: 'approved' })}>
                      Approve
                    </Button>
                    <Button variant="ghost" className="px-2 py-1 text-xs" disabled={decide.isPending} onClick={() => decide.mutate({ id: r.id, status: 'rejected' })}>
                      Decline
                    </Button>
                  </span>
                ) : (
                  <Badge tone={REQUEST_TONE[r.status]}>{r.status}</Badge>
                )
              }
              who={(r) => r.tenant_name}
            />
          )}
          {mine.length > 0 && (
            <RequestList
              title="Your requests"
              hint="The person who listed the flat will get back to you."
              requests={mine}
              render={(r) => (
                <span className="flex items-center gap-2">
                  <Badge tone={REQUEST_TONE[r.status]}>{r.status}</Badge>
                  {r.status === 'pending' && (
                    <button type="button" className="text-link !text-[11px]" disabled={withdraw.isPending} onClick={() => withdraw.mutate(r.id)}>
                      Withdraw
                    </button>
                  )}
                </span>
              )}
              who={(r) => r.listing_title}
            />
          )}
        </div>
      )}

      <ListingDialog
        open={listing}
        buildingId={currentId}
        onClose={() => setListing(false)}
        onDone={(l) => {
          toast.success(`“${l.title}” is listed.`)
          refresh()
          setListing(false)
        }}
      />
    </div>
  )
}

function RentGuide() {
  const [city, setCity] = useState('Dhaka')
  const { data, isLoading, isError } = useQuery({
    queryKey: ['price-estimate', city],
    queryFn: () => api.priceEstimate(city),
    retry: false,
  })

  return (
    <section className="dashboard-intro">
      <div>
        <p className="eyebrow !mt-0 !mb-3">RENT GUIDE</p>
        <h2>
          {isLoading ? 'Checking…' : data?.estimate ? `About ${taka(data.estimate)} a month` : 'No estimate yet'}
        </h2>
        <p>
          {isError
            ? 'The estimator is not available right now.'
            : data?.estimate
              ? `Typical asking rent for a family flat in ${city}, from estimator ${data.model_version}.`
              : `There is no cached figure for ${city} yet — it will appear once the model has run for it.`}
        </p>
      </div>
      <div className="w-full max-w-[200px]">
        <Select label="City" value={city} onChange={(e) => setCity(e.target.value)}>
          {CITIES.map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </Select>
      </div>
    </section>
  )
}

function ListingCard({
  listing,
  mine,
  myRequest,
  requestCount,
  canAsk,
  asking,
  onAsk,
}: {
  listing: ApiListing
  mine: boolean
  myRequest: ApiRentalRequest | undefined
  requestCount: number
  canAsk: boolean
  asking: boolean
  onAsk: () => void
}) {
  return (
    <Card className="flex flex-col">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-slate-900">{listing.title}</p>
          <p className="mt-1 text-[11px] text-slate-500">
            {listing.unit_number ? `Unit ${listing.unit_number} · ` : ''}Listed by {mine ? 'you' : listing.resident_name}
          </p>
        </div>
        {mine && <Badge tone="blue">Yours</Badge>}
      </div>
      <p className="metric-value !mt-4 !text-[26px]">
        {taka(listing.rent)}
        <span className="ml-1 text-xs font-normal tracking-normal text-slate-500">/ month</span>
      </p>
      <p className="mt-3 line-clamp-3 text-xs leading-relaxed text-slate-600">{listing.description}</p>
      <div className="mt-auto flex flex-wrap items-center justify-between gap-2 pt-5">
        <span className="flex items-center gap-1.5 text-[11px] text-slate-500">
          <Icon name="calendar" size={14} />
          From {formatDate(listing.available_from)}
        </span>
        {mine ? (
          <span className="text-[11px] text-slate-600">
            {requestCount ? `${requestCount} pending request${requestCount === 1 ? '' : 's'}` : 'No requests yet'}
          </span>
        ) : myRequest ? (
          <Badge tone={REQUEST_TONE[myRequest.status]}>Request {myRequest.status}</Badge>
        ) : canAsk ? (
          <Button className="px-3 py-1.5 text-xs" loading={asking} onClick={onAsk}>Request to rent</Button>
        ) : null}
      </div>
    </Card>
  )
}

function RequestList({
  title,
  hint,
  requests,
  render,
  who,
}: {
  title: string
  hint: string
  requests: ApiRentalRequest[]
  render: (r: ApiRentalRequest) => ReactNode
  who: (r: ApiRentalRequest) => string
}) {
  return (
    <Card>
      <h2 className="dashboard-section-title">{title}</h2>
      <p className="mt-2 text-[11px] text-slate-500">{hint}</p>
      <ul className="mt-4 divide-y divide-slate-100">
        {requests.map((r) => (
          <li key={r.id} className="flex items-center gap-3 py-3">
            <span className="user-avatar">{who(r).charAt(0).toUpperCase()}</span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-medium text-slate-800">{who(r)}</p>
              <p className="mt-0.5 text-[10px] text-slate-500">Asked {formatDate(r.requested_at)}</p>
            </div>
            {render(r)}
          </li>
        ))}
      </ul>
    </Card>
  )
}

function ListingDialog({
  open,
  buildingId,
  onClose,
  onDone,
}: {
  open: boolean
  buildingId: number | undefined
  onClose: () => void
  onDone: (listing: ApiListing) => void
}) {
  const toast = useToast()
  const { resident } = useCurrentResident()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [rent, setRent] = useState('')
  const [from, setFrom] = useState('')
  const [unit, setUnit] = useState('')

  const { data: units } = useQuery({
    queryKey: ['units', buildingId, 'listing-picker'],
    queryFn: () => api.units({ building_id: buildingId }),
    enabled: open && Boolean(buildingId),
  })
  const selectedUnit = unit || String(resident?.unit ?? '')
  const badRent = rent !== '' && !(Number(rent) > 0)

  const create = useMutation({
    mutationFn: () =>
      api.createListing({
        building: buildingId,
        title: title.trim(),
        description: description.trim(),
        rent,
        available_from: from,
        unit: selectedUnit ? Number(selectedUnit) : undefined,
      }),
    onSuccess: (l) => {
      setTitle('')
      setDescription('')
      setRent('')
      setFrom('')
      onDone(l)
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not list the flat.'),
  })

  return (
    <Modal
      open={open}
      title="List a flat"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            loading={create.isPending}
            disabled={!title.trim() || !description.trim() || !rent || badRent || !from}
            onClick={() => create.mutate()}
          >
            List it
          </Button>
        </>
      }
    >
      <Field label="Headline" placeholder="Bright 2BHK on floor 3" value={title} maxLength={150} onChange={(e) => setTitle(e.target.value)} />
      <TextArea label="Description" rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
      <div className="grid grid-cols-2 gap-3">
        <Field label="Rent per month (৳)" inputMode="decimal" value={rent} onChange={(e) => setRent(e.target.value)} error={badRent ? 'Must be positive' : undefined} />
        <Field label="Available from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
      </div>
      <Select label="Unit" value={selectedUnit} onChange={(e) => setUnit(e.target.value)}>
        <option value="">Not tied to a unit</option>
        {(units?.results ?? []).map((u) => (
          <option key={u.id} value={u.id}>{u.unit_number}</option>
        ))}
      </Select>
    </Modal>
  )
}
