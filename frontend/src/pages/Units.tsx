import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useBuilding } from '../lib/building'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'
import DataTable, { Badge } from '../components/DataTable'
import type { Column } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import { Button } from '../components/ui'
import type { ApiUnit, UnitStatus } from '../types'

const STATUSES: UnitStatus[] = ['available', 'occupied', 'sold', 'rented']

const STATUS_TONE: Record<UnitStatus, 'green' | 'amber' | 'blue' | 'violet'> = {
  available: 'green',
  occupied: 'blue',
  rented: 'amber',
  sold: 'violet',
}

export default function Units() {
  const { currentId } = useBuilding()
  const { user } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()

  const [page, setPage] = useState(1)
  const [status, setStatus] = useState<string>('')

  const canManage = user?.role === 'admin' || user?.role === 'committee'

  const { data, isLoading } = useQuery({
    queryKey: ['units', currentId, page, status],
    queryFn: () => api.units({ page, building_id: currentId, status: status || undefined }),
    enabled: Boolean(currentId),
  })

  // Optimistic status change: the badge flips instantly, and rolls back if the server refuses.
  const changeStatus = useMutation({
    mutationFn: ({ id, next }: { id: number; next: UnitStatus }) => api.updateUnit(id, { status: next }),
    onSuccess: (_result, variables) => {
      queryClient.invalidateQueries({ queryKey: ['units'] })
      toast.success(`Unit marked ${variables.next}.`)
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Update failed.'),
  })

  const columns: Column<ApiUnit>[] = [
    {
      key: 'unit_number',
      header: 'Unit',
      render: (unit) => <span className="font-medium text-slate-900">{unit.unit_number}</span>,
    },
    { key: 'floor', header: 'Floor', render: (unit) => unit.floor ?? '—' },
    { key: 'type', header: 'Type', render: (unit) => unit.type },
    {
      key: 'size',
      header: 'Size',
      render: (unit) => (unit.size_sqft ? `${Number(unit.size_sqft).toLocaleString()} sqft` : '—'),
    },
    {
      key: 'price',
      header: 'Price',
      render: (unit) => (unit.price ? `৳ ${Number(unit.price).toLocaleString('en-BD')}` : '—'),
    },
    {
      key: 'status',
      header: 'Status',
      render: (unit) => <Badge tone={STATUS_TONE[unit.status]}>{unit.status}</Badge>,
    },
    {
      key: 'actions',
      header: '',
      className: 'text-right',
      render: (unit) =>
        canManage ? (
          <select
            aria-label={`Change status of unit ${unit.unit_number}`}
            value={unit.status}
            disabled={changeStatus.isPending}
            onChange={(e) => changeStatus.mutate({ id: unit.id, next: e.target.value as UnitStatus })}
            className="rounded-md border border-slate-300 bg-white px-2 py-1 text-xs"
          >
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        ) : null,
    },
  ]

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <PageHeader
        title="Units"
        subtitle={data ? `${data.count} unit${data.count === 1 ? '' : 's'} in this building` : 'Unit inventory and occupancy'}
        actions={
          <select
            aria-label="Filter by status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value)
              setPage(1)
            }}
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          >
            <option value="">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        }
      />

      <DataTable
        columns={columns}
        rows={data?.results ?? []}
        rowKey={(unit) => unit.id}
        loading={isLoading}
        page={page}
        count={data?.count ?? 0}
        onPageChange={setPage}
        empty={{
          icon: '🏢',
          title: status ? `No ${status} units` : 'No units yet',
          body: status
            ? 'Try a different status filter.'
            : 'Units define the building’s inventory — residents, invoices and meters all hang off them.',
          action: status ? <Button variant="secondary" onClick={() => setStatus('')}>Clear filter</Button> : undefined,
        }}
      />
    </div>
  )
}
