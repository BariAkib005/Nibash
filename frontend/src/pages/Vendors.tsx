import { useMemo, useState } from 'react'
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
import { formatDate } from '../lib/format'
import type { ApiReview, ApiVendor } from '../types'

/** Gulshan 2 — where the demo buildings stand; the nearby search starts here until the browser says otherwise. */
const BUILDING_AREA = { lat: 23.7925, lng: 90.4078, label: 'the building' }
const RADII = [1, 3, 5, 10]

/**
 * The vendor catalogue (spec §8.2): the building's own vendors plus the global ones, best rated
 * first, with residents' reviews and a distance search for "a plumber near us, now".
 */
export default function Vendors() {
  const { user } = useAuth()
  const { currentId } = useBuilding()
  const toast = useToast()
  const queryClient = useQueryClient()
  const canManage = user?.role === 'admin' || user?.role === 'committee'
  const [serviceFilter, setServiceFilter] = useState('')
  const [reviewing, setReviewing] = useState<ApiVendor | null>(null)
  const [adding, setAdding] = useState(false)

  const { data: services } = useQuery({ queryKey: ['services'], queryFn: () => api.services() })
  const { data: vendors, isLoading } = useQuery({
    queryKey: ['vendors', currentId, serviceFilter],
    queryFn: () => api.vendors({ building_id: currentId, service_id: serviceFilter ? Number(serviceFilter) : undefined }),
    enabled: Boolean(currentId),
  })
  const { data: reviews } = useQuery({
    queryKey: ['reviews', currentId],
    queryFn: () => api.reviews({ building_id: currentId }),
    enabled: Boolean(currentId),
  })

  const reviewsByVendor = useMemo(() => {
    const grouped = new Map<number, ApiReview[]>()
    for (const review of reviews?.results ?? []) {
      grouped.set(review.vendor, [...(grouped.get(review.vendor) ?? []), review])
    }
    return grouped
  }, [reviews])

  const serviceList = services?.results ?? []

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Vendors"
        subtitle="Trusted tradespeople for the building, rated by your neighbours."
        actions={
          canManage && (
            <Button onClick={() => setAdding(true)}>
              <Icon name="plus" size={16} />
              Add vendor
            </Button>
          )
        }
      />

      <NearbySearch services={serviceList} />

      <section className="space-y-4">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="dashboard-section-title">All vendors</h2>
            <p className="mt-1 text-[11px] text-slate-500">Best rated first. Global vendors are shared across every building.</p>
          </div>
          <div className="w-full max-w-[220px]">
            <Select label="Service" value={serviceFilter} onChange={(e) => setServiceFilter(e.target.value)}>
              <option value="">All services</option>
              {serviceList.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </Select>
          </div>
        </div>

        {isLoading ? (
          <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
            {[0, 1, 2].map((i) => <Skeleton key={i} className="h-40 w-full" />)}
          </div>
        ) : vendors?.results.length ? (
          <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
            {vendors.results.map((vendor) => {
              const vendorReviews = reviewsByVendor.get(vendor.id) ?? []
              return (
                <Card key={vendor.id} className="flex flex-col">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-slate-900">{vendor.name}</p>
                      <div className="mt-1.5 flex flex-wrap gap-1.5">
                        <Badge tone="blue">{vendor.service_name}</Badge>
                        {vendor.building === null && <Badge tone="slate">Global</Badge>}
                      </div>
                    </div>
                    <span className="grid h-9 w-9 shrink-0 place-items-center rounded-md border border-slate-200 text-brand-700">
                      <Icon name="store" size={17} />
                    </span>
                  </div>
                  <Stars value={vendor.rating} className="mt-4" />
                  <p className="mt-1 text-[11px] text-slate-500">
                    {vendorReviews.length
                      ? `${vendorReviews.length} review${vendorReviews.length === 1 ? '' : 's'} from your building`
                      : 'No reviews from your building yet'}
                  </p>
                  <div className="mt-auto flex items-center justify-between gap-2 pt-5">
                    {vendor.contact_info ? (
                      <a href={`tel:${vendor.contact_info.replace(/\s/g, '')}`} className="text-link !text-[11px]">
                        <Icon name="phone" size={14} />
                        {vendor.contact_info}
                      </a>
                    ) : (
                      <span className="text-[11px] text-slate-400">No contact on file</span>
                    )}
                    <button type="button" className="text-link !text-[11px]" onClick={() => setReviewing(vendor)}>
                      Reviews
                      <Icon name="arrow" size={13} />
                    </button>
                  </div>
                </Card>
              )
            })}
          </div>
        ) : (
          <EmptyState
            icon="store"
            title="No vendors yet"
            body="Add the plumbers, electricians and lift engineers the building already trusts."
            action={canManage ? <Button onClick={() => setAdding(true)}>Add vendor</Button> : undefined}
          />
        )}
      </section>

      {reviewing && (
        <ReviewsDialog
          vendor={reviewing}
          reviews={reviewsByVendor.get(reviewing.id) ?? []}
          onClose={() => setReviewing(null)}
          onReviewed={() => {
            toast.success('Thanks — your review is posted.')
            queryClient.invalidateQueries({ queryKey: ['reviews'] })
            queryClient.invalidateQueries({ queryKey: ['vendors'] })
          }}
        />
      )}
      <AddVendorDialog
        open={adding}
        buildingId={currentId}
        services={serviceList}
        onClose={() => setAdding(false)}
        onDone={(vendor) => {
          toast.success(`${vendor.name} added.`)
          queryClient.invalidateQueries({ queryKey: ['vendors'] })
          setAdding(false)
        }}
      />
    </div>
  )
}

function NearbySearch({ services }: { services: { id: number; name: string }[] }) {
  const toast = useToast()
  const [service, setService] = useState('')
  const [radius, setRadius] = useState(5)
  const [origin, setOrigin] = useState(BUILDING_AREA)
  const [locating, setLocating] = useState(false)
  const effectiveService = service || String(services[0]?.id ?? '')

  const { data, isFetching } = useQuery({
    queryKey: ['vendors-nearby', effectiveService, radius, origin.lat, origin.lng],
    queryFn: () => api.nearbyVendors({ service_id: Number(effectiveService), lat: origin.lat, lng: origin.lng, radius_km: radius }),
    enabled: Boolean(effectiveService),
  })

  const locate = () => {
    if (!navigator.geolocation) {
      toast.error('This browser cannot share its location.')
      return
    }
    setLocating(true)
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setOrigin({ lat: position.coords.latitude, lng: position.coords.longitude, label: 'your location' })
        setLocating(false)
      },
      () => {
        toast.error('Location was not shared — searching from the building instead.')
        setLocating(false)
      },
      { timeout: 8000 },
    )
  }

  return (
    <Card>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="dashboard-section-title">Find someone nearby</h2>
          <p className="mt-1 text-[11px] text-slate-500">
            Nearest first, within {radius} km of {origin.label}.
          </p>
        </div>
        <Button variant="ghost" className="!px-2 !py-1 text-xs" loading={locating} onClick={locate}>
          <Icon name="pin" size={15} />
          Use my location
        </Button>
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_auto]">
        <Select label="I need" value={effectiveService} onChange={(e) => setService(e.target.value)}>
          {services.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </Select>
        <div className="space-y-1.5">
          <span className="block text-sm font-medium text-slate-700">Within</span>
          <div className="flex gap-1" role="group" aria-label="Search radius">
            {RADII.map((km) => (
              <button
                key={km}
                type="button"
                aria-pressed={radius === km}
                onClick={() => setRadius(km)}
                className={`rounded-md border px-3 py-2.5 text-xs font-medium transition
                  ${radius === km ? 'border-brand-700 bg-brand-700 text-white' : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'}`}
              >
                {km} km
              </button>
            ))}
          </div>
        </div>
      </div>

      <ul className="mt-4 divide-y divide-slate-100">
        {isFetching && !data ? (
          <li className="py-3"><Skeleton className="h-10 w-full" /></li>
        ) : data?.results.length ? (
          data.results.map((vendor) => (
            <li key={vendor.id} className="flex items-center gap-3 py-3">
              <span className="grid h-9 w-9 shrink-0 place-items-center rounded-md bg-brand-50 text-brand-700">
                <Icon name="pin" size={16} />
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-xs font-medium text-slate-800">{vendor.name}</p>
                <Stars value={vendor.rating} small />
              </div>
              <span className="text-xs font-semibold tabular-nums text-slate-700">{vendor.distance_km} km</span>
              {vendor.contact_info && (
                <a href={`tel:${vendor.contact_info.replace(/\s/g, '')}`} className="text-link !text-[11px]">Call</a>
              )}
            </li>
          ))
        ) : (
          <li className="py-6 text-center text-sm text-slate-500">
            Nobody within {radius} km. Try a wider radius.
          </li>
        )}
      </ul>
    </Card>
  )
}

function ReviewsDialog({
  vendor,
  reviews,
  onClose,
  onReviewed,
}: {
  vendor: ApiVendor
  reviews: ApiReview[]
  onClose: () => void
  onReviewed: () => void
}) {
  const toast = useToast()
  const { resident } = useCurrentResident()
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')

  const post = useMutation({
    mutationFn: () => api.createReview({ vendor: vendor.id, rating, comment: comment.trim() || undefined }),
    onSuccess: () => {
      setRating(0)
      setComment('')
      onReviewed()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not post the review.'),
  })

  return (
    <Modal open title={vendor.name} onClose={onClose}>
      <div className="flex items-center gap-3">
        <Stars value={vendor.rating} />
        <span className="text-xs text-slate-500">{vendor.service_name}</span>
      </div>

      {reviews.length ? (
        <ul className="max-h-60 divide-y divide-slate-100 overflow-y-auto rounded-md border border-slate-200">
          {reviews.map((review) => (
            <li key={review.id} className="px-3 py-3">
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-medium text-slate-800">{review.resident_name}</span>
                <span className="text-[10px] text-slate-500">{formatDate(review.created_at)}</span>
              </div>
              <Stars value={review.rating} small />
              {review.comment && <p className="mt-1.5 text-xs leading-relaxed text-slate-600">{review.comment}</p>}
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-slate-500">No reviews from your building yet.</p>
      )}

      {resident ? (
        <div className="space-y-3 border-t border-slate-100 pt-4">
          <p className="text-sm font-medium text-slate-700">Your review</p>
          <div className="flex gap-1" role="radiogroup" aria-label="Rating">
            {[1, 2, 3, 4, 5].map((n) => (
              <button
                key={n}
                type="button"
                role="radio"
                aria-checked={rating === n}
                aria-label={`${n} star${n === 1 ? '' : 's'}`}
                onClick={() => setRating(n)}
                className={n <= rating ? 'text-brand-600' : 'text-slate-300 hover:text-brand-400'}
              >
                <Icon name="star" size={24} fill="currentColor" />
              </button>
            ))}
          </div>
          <TextArea label="Comment (optional)" rows={3} value={comment} onChange={(e) => setComment(e.target.value)} />
          <div className="flex justify-end">
            <Button disabled={!rating} loading={post.isPending} onClick={() => post.mutate()}>Post review</Button>
          </div>
        </div>
      ) : (
        <p className="border-t border-slate-100 pt-4 text-xs text-slate-500">Only residents can post reviews.</p>
      )}
    </Modal>
  )
}

function AddVendorDialog({
  open,
  buildingId,
  services,
  onClose,
  onDone,
}: {
  open: boolean
  buildingId: number | undefined
  services: { id: number; name: string }[]
  onClose: () => void
  onDone: (vendor: ApiVendor) => void
}) {
  const toast = useToast()
  const [name, setName] = useState('')
  const [service, setService] = useState('')
  const [contact, setContact] = useState('')
  const [lat, setLat] = useState('')
  const [lng, setLng] = useState('')

  const create = useMutation({
    mutationFn: () =>
      api.createVendor({
        building: buildingId,
        service: Number(service || services[0]?.id),
        name: name.trim(),
        contact_info: contact.trim() || undefined,
        latitude: lat || undefined,
        longitude: lng || undefined,
      }),
    onSuccess: (vendor) => {
      setName('')
      setContact('')
      setLat('')
      setLng('')
      onDone(vendor)
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not add the vendor.'),
  })

  return (
    <Modal
      open={open}
      title="Add a vendor"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={create.isPending} disabled={!name.trim() || !services.length} onClick={() => create.mutate()}>
            Add vendor
          </Button>
        </>
      }
    >
      <Field label="Name" value={name} maxLength={150} onChange={(e) => setName(e.target.value)} />
      <Select label="Service" value={service || String(services[0]?.id ?? '')} onChange={(e) => setService(e.target.value)}>
        {services.map((s) => (
          <option key={s.id} value={s.id}>{s.name}</option>
        ))}
      </Select>
      <Field label="Phone" type="tel" placeholder="+8801700000000" value={contact} onChange={(e) => setContact(e.target.value)} />
      <div className="grid grid-cols-2 gap-3">
        <Field label="Latitude" inputMode="decimal" placeholder="23.7925" value={lat} onChange={(e) => setLat(e.target.value)} />
        <Field label="Longitude" inputMode="decimal" placeholder="90.4078" value={lng} onChange={(e) => setLng(e.target.value)} />
      </div>
      <p className="text-xs text-slate-500">Coordinates put the vendor in the nearby search. Right-click a spot in any map app to copy them.</p>
    </Modal>
  )
}

function Stars({ value, small = false, className = '' }: { value: string | number | null; small?: boolean; className?: string }) {
  const rating = value === null || value === undefined ? null : Number(value)
  const size = small ? 12 : 16
  return (
    <span className={`flex items-center gap-1 ${className}`} aria-label={rating === null ? 'Not rated' : `Rated ${rating} out of 5`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Icon
          key={n}
          name="star"
          size={size}
          fill="currentColor"
          className={rating !== null && n <= Math.round(rating) ? 'text-brand-600' : 'text-slate-200'}
        />
      ))}
      <span className={`ml-1 tabular-nums text-slate-600 ${small ? 'text-[10px]' : 'text-xs font-medium'}`}>
        {rating === null ? 'Not rated' : rating.toFixed(1)}
      </span>
    </span>
  )
}
