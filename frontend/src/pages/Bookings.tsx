import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useBuilding } from '../lib/building'
import { useCurrentResident } from '../lib/resident'
import { useToast } from '../lib/toast'
import PageHeader from '../components/PageHeader'
import { Button, Card, EmptyState, Modal, Skeleton } from '../components/ui'
import { formatDateTime } from '../lib/format'
import type { ApiBooking } from '../types'

/** The calendar shows one working day's worth of hours, which keeps a week on one screen. */
const FIRST_HOUR = 7
const LAST_HOUR = 22
const HOURS = Array.from({ length: LAST_HOUR - FIRST_HOUR }, (_, i) => FIRST_HOUR + i)

function startOfWeek(date: Date): Date {
  const result = new Date(date)
  result.setHours(0, 0, 0, 0)
  // Monday-first: JS puts Sunday at 0, so shift it to the end.
  result.setDate(result.getDate() - ((result.getDay() + 6) % 7))
  return result
}

function addDays(date: Date, days: number): Date {
  const result = new Date(date)
  result.setDate(result.getDate() + days)
  return result
}

/** A local ISO string the API accepts — deliberately not toISOString(), which shifts to UTC. */
function localIso(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

function slotAt(day: Date, hour: number): Date {
  const result = new Date(day)
  result.setHours(hour, 0, 0, 0)
  return result
}

interface Selection {
  start: Date
  end: Date
}

export default function Bookings() {
  const { currentId } = useBuilding()
  const { residentId } = useCurrentResident()
  const toast = useToast()
  const queryClient = useQueryClient()

  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()))
  const [resourceId, setResourceId] = useState<string>('')
  const [selection, setSelection] = useState<Selection | null>(null)
  const [dragFrom, setDragFrom] = useState<Date | null>(null)

  const { data: resources, isLoading: resourcesLoading } = useQuery({
    queryKey: ['resources', currentId],
    queryFn: () => api.resources({ building_id: currentId }),
    enabled: Boolean(currentId),
  })

  const activeResource = resourceId || String(resources?.results[0]?.id ?? '')
  const weekEnd = addDays(weekStart, 7)

  const { data: availability, isLoading } = useQuery({
    queryKey: ['availability', activeResource, weekStart.toDateString()],
    queryFn: () => api.availability(Number(activeResource), localIso(weekStart), localIso(weekEnd)),
    enabled: Boolean(activeResource),
  })

  const booked = useMemo(() => {
    const map = new Map<string, ApiBooking>()
    for (const booking of availability?.bookings ?? []) {
      if (booking.status === 'cancelled') continue
      const start = new Date(booking.start_time)
      const end = new Date(booking.end_time)
      // Mark every whole hour the booking touches, so a 2-hour booking paints two cells.
      for (let cursor = new Date(start); cursor < end; cursor.setHours(cursor.getHours() + 1)) {
        map.set(`${cursor.toDateString()}|${cursor.getHours()}`, booking)
      }
    }
    return map
  }, [availability])

  const create = useMutation({
    mutationFn: (slot: Selection) =>
      api.createBooking({
        resource: Number(activeResource),
        resident: residentId,
        start_time: localIso(slot.start),
        end_time: localIso(slot.end),
        purpose: 'Reserved from the calendar',
      }),
    onSuccess: () => {
      toast.success('Booked.')
      setSelection(null)
      queryClient.invalidateQueries({ queryKey: ['availability'] })
      queryClient.invalidateQueries({ queryKey: ['bookings'] })
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not book that slot.'),
  })

  const days = Array.from({ length: 7 }, (_, i) => addDays(weekStart, i))
  const isPast = (date: Date) => date < new Date()

  const onCellDown = (day: Date, hour: number) => {
    const start = slotAt(day, hour)
    if (isPast(start) || booked.has(`${day.toDateString()}|${hour}`)) return
    setDragFrom(start)
    setSelection({ start, end: slotAt(day, hour + 1) })
  }

  /** Dragging down the column extends the selection — the "drag to book" of the plan. */
  const onCellEnter = (day: Date, hour: number) => {
    if (!dragFrom || day.toDateString() !== dragFrom.toDateString()) return
    const hovered = slotAt(day, hour + 1)
    if (hovered > dragFrom) setSelection({ start: dragFrom, end: hovered })
  }

  useEffect(() => {
    const stop = () => setDragFrom(null)
    window.addEventListener('mouseup', stop)
    return () => window.removeEventListener('mouseup', stop)
  }, [])

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <PageHeader
        title="Bookings"
        subtitle="Drag across empty hours to reserve a slot. Busy hours are blocked."
        actions={
          <div className="flex flex-wrap gap-2">
            <select
              aria-label="Resource"
              value={activeResource}
              onChange={(e) => setResourceId(e.target.value)}
              className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
            >
              {(resources?.results ?? []).map((resource) => (
                <option key={resource.id} value={resource.id}>{resource.name}</option>
              ))}
            </select>
            <Button variant="secondary" onClick={() => setWeekStart((w) => addDays(w, -7))}>←</Button>
            <Button variant="secondary" onClick={() => setWeekStart(startOfWeek(new Date()))}>Today</Button>
            <Button variant="secondary" onClick={() => setWeekStart((w) => addDays(w, 7))}>→</Button>
          </div>
        }
      />

      {resourcesLoading ? (
        <Card><Skeleton className="h-64 w-full" /></Card>
      ) : !resources?.results.length ? (
        <EmptyState
          icon="🏛️"
          title="No bookable resources"
          body="An admin can add the rooftop lounge, the gym or the community hall, and residents can book them here."
        />
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-3xl border-collapse text-sm">
            <thead>
              <tr>
                <th scope="col" className="w-16 border-b border-slate-200 bg-slate-50 p-2 text-xs text-slate-500">
                  Time
                </th>
                {days.map((day) => (
                  <th
                    key={day.toDateString()}
                    scope="col"
                    className="border-b border-l border-slate-200 bg-slate-50 p-2 text-xs font-semibold text-slate-700"
                  >
                    {day.toLocaleDateString('en-GB', { weekday: 'short' })}
                    <span className="ml-1 font-normal text-slate-500">{day.getDate()}</span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {HOURS.map((hour) => (
                <tr key={hour}>
                  <th scope="row" className="border-b border-slate-100 p-1 text-right align-top text-xs font-normal text-slate-400">
                    {String(hour).padStart(2, '0')}:00
                  </th>
                  {days.map((day) => {
                    const key = `${day.toDateString()}|${hour}`
                    const booking = booked.get(key)
                    const cellStart = slotAt(day, hour)
                    const selected =
                      selection &&
                      cellStart >= selection.start &&
                      cellStart < selection.end
                    const past = isPast(cellStart)

                    return (
                      <td
                        key={key}
                        onMouseDown={() => onCellDown(day, hour)}
                        onMouseEnter={() => onCellEnter(day, hour)}
                        title={booking ? `${booking.resident_name} — ${booking.purpose ?? 'reserved'}` : undefined}
                        className={`h-8 select-none border-b border-l border-slate-100 px-1 text-[11px] transition
                          ${booking
                            ? 'cursor-not-allowed bg-brand-100 text-brand-900'
                            : past
                              ? 'cursor-not-allowed bg-slate-50'
                              : selected
                                ? 'cursor-grabbing bg-brand-600'
                                : 'cursor-pointer hover:bg-brand-50'}`}
                      >
                        {booking && <span className="line-clamp-1">{booking.resident_name}</span>}
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
          {isLoading && <div className="border-t border-slate-100 p-2"><Skeleton className="h-4 w-32" /></div>}
        </Card>
      )}

      <UpcomingBookings buildingId={currentId} />

      {selection && (
        <ConfirmBooking
          selection={selection}
          resourceId={Number(activeResource)}
          residentId={residentId}
          pending={create.isPending}
          onCancel={() => setSelection(null)}
          onConfirm={() => create.mutate(selection)}
        />
      )}
    </div>
  )
}

/**
 * The confirm step also runs the server's own validation via `quote/` before anything is written,
 * so a clash shows up here rather than as a 400 after the user presses Book.
 */
function ConfirmBooking({ selection, resourceId, residentId, pending, onCancel, onConfirm }: {
  selection: Selection
  resourceId: number
  residentId: number | undefined
  pending: boolean
  onCancel: () => void
  onConfirm: () => void
}) {
  const { data: quote, isLoading, error } = useQuery({
    queryKey: ['quote', resourceId, selection.start.toISOString(), selection.end.toISOString()],
    queryFn: () =>
      api.quoteBooking({
        resource: resourceId,
        resident: residentId,
        start_time: localIso(selection.start),
        end_time: localIso(selection.end),
      }),
    retry: false,
    enabled: Boolean(residentId),
  })

  const conflict = error instanceof ApiError ? error.message : null
  const hours = Math.round((selection.end.getTime() - selection.start.getTime()) / 3_600_000)

  return (
    <Modal
      open
      title="Confirm booking"
      onClose={onCancel}
      footer={
        <>
          <Button variant="secondary" onClick={onCancel}>Cancel</Button>
          <Button
            loading={pending}
            disabled={isLoading || Boolean(conflict) || !residentId}
            onClick={onConfirm}
          >
            Book {hours}h
          </Button>
        </>
      }
    >
      <div className="text-sm text-slate-700">
        <p><span className="text-slate-500">From</span> {formatDateTime(selection.start.toISOString())}</p>
        <p className="mt-1"><span className="text-slate-500">To</span> {formatDateTime(selection.end.toISOString())}</p>
      </div>

      {!residentId && (
        <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
          Only residents of this building can book. You are viewing it as staff.
        </p>
      )}

      {isLoading && <Skeleton className="h-10 w-full" />}

      {conflict && (
        <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          {conflict}
        </p>
      )}

      {quote?.available && (
        <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
          That slot is free.
        </p>
      )}
    </Modal>
  )
}

function UpcomingBookings({ buildingId }: { buildingId: number | undefined }) {
  const toast = useToast()
  const queryClient = useQueryClient()

  const { data } = useQuery({
    queryKey: ['bookings', buildingId],
    queryFn: () => api.bookings({ building_id: buildingId }),
    enabled: Boolean(buildingId),
  })

  const cancel = useMutation({
    mutationFn: (id: number) => api.cancelBooking(id),
    onSuccess: () => {
      toast.success('Booking cancelled.')
      queryClient.invalidateQueries({ queryKey: ['bookings'] })
      queryClient.invalidateQueries({ queryKey: ['availability'] })
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not cancel.'),
  })

  const upcoming = (data?.results ?? [])
    .filter((booking) => booking.status !== 'cancelled' && new Date(booking.end_time) >= new Date())
    .slice(0, 6)

  if (!upcoming.length) return null

  return (
    <Card>
      <h2 className="text-sm font-semibold text-slate-900">Upcoming bookings</h2>
      <ul className="mt-3 divide-y divide-slate-100">
        {upcoming.map((booking) => (
          <li key={booking.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
            <div className="min-w-0">
              <p className="font-medium text-slate-800">{booking.resource_name}</p>
              <p className="text-xs text-slate-500">
                {formatDateTime(booking.start_time)} → {formatDateTime(booking.end_time)} · {booking.resident_name}
              </p>
            </div>
            <Button
              variant="ghost"
              className="px-2 py-1 text-xs text-red-700 hover:bg-red-50"
              disabled={cancel.isPending}
              onClick={() => cancel.mutate(booking.id)}
            >
              Cancel
            </Button>
          </li>
        ))}
      </ul>
    </Card>
  )
}
