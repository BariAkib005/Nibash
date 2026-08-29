import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useBuilding } from '../lib/building'
import { useToast } from '../lib/toast'
import DataTable, { Badge } from '../components/DataTable'
import type { Column } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import { Button, Card, Skeleton } from '../components/ui'
import { formatDateTime } from '../lib/format'
import type { ApiAttendance, ApiStaff } from '../types'

const ROLE_TONE: Record<string, 'green' | 'amber' | 'blue' | 'slate'> = {
  Security: 'blue',
  Cleaning: 'green',
  Maintenance: 'amber',
}

export default function StaffPage() {
  const { currentId } = useBuilding()
  const [page, setPage] = useState(1)

  const { data, isLoading } = useQuery({
    queryKey: ['staff', currentId, page],
    queryFn: () => api.staff({ page, building_id: currentId }),
    enabled: Boolean(currentId),
  })

  const columns: Column<ApiStaff>[] = [
    { key: 'name', header: 'Name', render: (s) => <span className="font-medium text-slate-900">{s.name}</span> },
    { key: 'role', header: 'Role', render: (s) => <Badge tone={ROLE_TONE[s.role] ?? 'slate'}>{s.role}</Badge> },
    { key: 'designation', header: 'Designation', render: (s) => s.designation ?? '—' },
    { key: 'contact', header: 'Contact', render: (s) => s.contact_info ?? '—' },
    {
      key: 'account',
      header: 'Login',
      render: (s) =>
        s.user ? <Badge tone="green">Has account</Badge> : <span className="text-xs text-slate-400">No account</span>,
    },
  ]

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <PageHeader title="Staff" subtitle="Employment records and today’s shifts" />

      <AttendanceBoard buildingId={currentId} staff={data?.results ?? []} loading={isLoading} />

      <DataTable
        columns={columns}
        rows={data?.results ?? []}
        rowKey={(s) => s.id}
        loading={isLoading}
        page={page}
        count={data?.count ?? 0}
        onPageChange={setPage}
        empty={{
          icon: '🧰',
          title: 'No staff yet',
          body: 'Staff can exist without a login account — useful for people who are scheduled but never use the app.',
        }}
      />
    </div>
  )
}

/**
 * Shift check-in and check-out.
 *
 * <p>Thumb-sized targets because this is used standing at a gate on a phone, not at a desk. The
 * check-in endpoint is idempotent, so a double tap is harmless — the same open shift comes back
 * rather than a second row appearing on the timesheet.
 */
function AttendanceBoard({ buildingId, staff, loading }: {
  buildingId: number | undefined
  staff: ApiStaff[]
  loading: boolean
}) {
  const toast = useToast()
  const queryClient = useQueryClient()

  const { data: records } = useQuery({
    queryKey: ['attendance', buildingId],
    queryFn: () => api.attendance({ building_id: buildingId }),
    enabled: Boolean(buildingId),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['attendance'] })

  const checkIn = useMutation({
    mutationFn: (staffId: number) => api.checkin(staffId),
    onSuccess: (record) => {
      toast.success(`${record.staff_name} is on shift.`)
      invalidate()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Check-in failed.'),
  })

  const checkOut = useMutation({
    mutationFn: (staffId: number) => api.checkoutShift(staffId),
    onSuccess: (record) => {
      toast.success(`${record.staff_name} checked out.`)
      invalidate()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Check-out failed.'),
  })

  /** The open shift per staff member, if any — what decides which button to show. */
  const openShifts = new Map<number, ApiAttendance>()
  for (const record of records?.results ?? []) {
    if (!record.checkout_time && !openShifts.has(record.staff)) openShifts.set(record.staff, record)
  }

  if (loading) {
    return <Card><Skeleton className="h-24 w-full" /></Card>
  }

  if (!staff.length) return null

  const onShift = openShifts.size

  return (
    <Card>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold text-slate-900">Attendance</h2>
        <p className="text-xs text-slate-500">
          {onShift} of {staff.length} on shift now
        </p>
      </div>

      <ul className="mt-3 grid gap-2 sm:grid-cols-2">
        {staff.map((member) => {
          const shift = openShifts.get(member.id)
          const busy = checkIn.isPending || checkOut.isPending

          return (
            <li
              key={member.id}
              className={`flex items-center justify-between gap-3 rounded-lg border p-3 transition
                ${shift ? 'border-emerald-200 bg-emerald-50/60' : 'border-slate-200 bg-white'}`}
            >
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-slate-900">{member.name}</p>
                <p className="truncate text-xs text-slate-500">
                  {shift ? `In since ${formatDateTime(shift.checkin_time)}` : member.role}
                </p>
              </div>

              {shift ? (
                <Button
                  variant="secondary"
                  className="shrink-0 px-4 py-2.5"
                  disabled={busy}
                  onClick={() => checkOut.mutate(member.id)}
                >
                  Check out
                </Button>
              ) : (
                <Button
                  className="shrink-0 px-4 py-2.5"
                  disabled={busy}
                  onClick={() => checkIn.mutate(member.id)}
                >
                  Check in
                </Button>
              )}
            </li>
          )
        })}
      </ul>
    </Card>
  )
}
