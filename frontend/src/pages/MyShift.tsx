import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import { useToast } from '../lib/toast'
import { Badge } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import { Button, Card, Skeleton } from '../components/ui'
import Icon from '../components/Icon'
import { formatDate, formatDateTime } from '../lib/format'
import type { ApiAttendance } from '../types'

/**
 * Self-service attendance for staff and guards (plan §7, journey 4): check in on arrival, check out
 * on leaving. Built for a phone. Check-in is idempotent on the server — pressing it twice returns the
 * shift that is already open rather than starting a second one.
 */
export default function MyShift() {
  const { user } = useAuth()
  const { currentId } = useBuilding()
  const toast = useToast()
  const queryClient = useQueryClient()

  const { data: staff, isLoading: staffLoading } = useQuery({
    queryKey: ['staff', currentId, 'me'],
    queryFn: () => api.staff({ building_id: currentId }),
    enabled: Boolean(currentId),
  })
  const me = staff?.results.find((s) => s.user === user?.id)

  const { data: shifts, isLoading: shiftsLoading } = useQuery({
    queryKey: ['attendance', currentId, 'me', me?.id],
    queryFn: () => api.attendance({ building_id: currentId, staff_id: me!.id }),
    enabled: Boolean(me),
  })

  const history = shifts?.results ?? []
  const open = history.find((s) => !s.checkout_time)
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['attendance'] })
  const fail = (fallback: string) => (error: unknown) =>
    toast.error(error instanceof ApiError ? error.message : fallback)

  const checkIn = useMutation({
    mutationFn: () => api.checkin(me!.id),
    onSuccess: (shift) => {
      toast.success(`On shift since ${timeOf(shift.checkin_time)}.`)
      refresh()
    },
    onError: fail('Check-in failed.'),
  })
  const checkOut = useMutation({
    mutationFn: () => api.checkoutShift(me!.id),
    onSuccess: (shift) => {
      toast.success(`Checked out — ${duration(shift)} on shift.`)
      refresh()
    },
    onError: fail('Check-out failed.'),
  })

  const weekStart = new Date()
  weekStart.setDate(weekStart.getDate() - 6)
  weekStart.setHours(0, 0, 0, 0)
  const weekMinutes = history
    .filter((s) => new Date(s.checkin_time) >= weekStart)
    .reduce((sum, s) => sum + minutesOf(s), 0)

  if (staffLoading) {
    return (
      <div className="mx-auto max-w-3xl space-y-6">
        <Skeleton className="h-8 w-40" />
        <Skeleton className="h-56 w-full" />
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader title="My shift" subtitle="Check in when you arrive and out when you leave." />

      {!me ? (
        <Card>
          <p className="py-6 text-center text-sm text-slate-600">
            You are not on this building's staff list — ask the committee to add you.
          </p>
        </Card>
      ) : (
        <>
          <Card className="text-center">
            <p className="text-[11px] uppercase tracking-widest text-slate-500">{me.name} · {me.role}</p>
            <div className="mt-6 flex justify-center">
              <span
                className={`grid h-20 w-20 place-items-center rounded-full ${open ? 'bg-brand-700 text-white' : 'bg-slate-100 text-slate-500'}`}
              >
                <Icon name={open ? 'check' : 'calendar'} size={34} />
              </span>
            </div>
            <p className="metric-value !mt-5">{open ? 'On shift' : 'Off shift'}</p>
            <p className="metric-hint">
              {open ? `Since ${formatDateTime(open.checkin_time)} · ${duration(open)} so far` : 'Not checked in'}
            </p>
            <div className="mt-7">
              {open ? (
                <Button variant="secondary" className="w-full max-w-xs py-4 text-base" loading={checkOut.isPending} onClick={() => checkOut.mutate()}>
                  Check out
                </Button>
              ) : (
                <Button className="w-full max-w-xs py-4 text-base" loading={checkIn.isPending} onClick={() => checkIn.mutate()}>
                  Check in
                </Button>
              )}
            </div>
          </Card>

          <Card>
            <div className="flex items-center justify-between gap-3">
              <h2 className="dashboard-section-title">Recent shifts</h2>
              <span className="text-[11px] text-slate-500">Last 7 days: {hoursLabel(weekMinutes)}</span>
            </div>
            {shiftsLoading ? (
              <Skeleton className="mt-5 h-24 w-full" />
            ) : history.length ? (
              <ul className="mt-4 divide-y divide-slate-100">
                {history.slice(0, 10).map((shift) => (
                  <li key={shift.id} className="flex items-center justify-between gap-3 py-3 text-xs">
                    <span>
                      <span className="font-medium text-slate-800">{formatDate(shift.checkin_time)}</span>
                      <span className="text-slate-500">
                        {' '}· {timeOf(shift.checkin_time)} – {shift.checkout_time ? timeOf(shift.checkout_time) : 'now'}
                      </span>
                    </span>
                    {shift.checkout_time ? (
                      <span className="tabular-nums text-slate-600">{duration(shift)}</span>
                    ) : (
                      <Badge tone="green">Open</Badge>
                    )}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="mt-4 text-sm text-slate-500">No shifts recorded yet.</p>
            )}
          </Card>
        </>
      )}
    </div>
  )
}

function minutesOf(shift: ApiAttendance): number {
  const end = shift.checkout_time ? new Date(shift.checkout_time) : new Date()
  return Math.max(0, Math.round((end.getTime() - new Date(shift.checkin_time).getTime()) / 60000))
}

function duration(shift: ApiAttendance): string {
  return hoursLabel(minutesOf(shift))
}

function hoursLabel(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h ? `${h} h ${m} min` : `${m} min`
}

function timeOf(value: string): string {
  return new Date(value).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
}
