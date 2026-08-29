import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../lib/api'
import { useBuilding } from '../lib/building'
import { useToast } from '../lib/toast'
import DataTable, { Badge } from '../components/DataTable'
import type { Column } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import { Button } from '../components/ui'
import { formatDateTime } from '../lib/format'
import type { ApiVisitor, VisitorStatus } from '../types'

const STATUS_TONE: Record<VisitorStatus, 'green' | 'amber' | 'slate'> = {
  checked_in: 'green',
  pending: 'amber',
  checked_out: 'slate',
}

const STATUS_LABEL: Record<VisitorStatus, string> = {
  checked_in: 'inside',
  pending: 'expected',
  checked_out: 'left',
}

export default function Visitors() {
  const { currentId } = useBuilding()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)

  const { data, isLoading } = useQuery({
    queryKey: ['visitors', currentId, page],
    queryFn: () => api.visitors({ page, building_id: currentId }),
    enabled: Boolean(currentId),
    // A gate log goes stale fast; refresh while the screen is open.
    refetchInterval: 30_000,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['visitors'] })

  const checkIn = useMutation({
    mutationFn: (id: number) => api.visitorCheckin(id),
    onSuccess: (visitor) => {
      toast.success(`${visitor.visitor_name} checked in.`)
      invalidate()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Check-in failed.'),
  })

  const checkOut = useMutation({
    mutationFn: (id: number) => api.visitorCheckout(id),
    onSuccess: (visitor) => {
      toast.success(`${visitor.visitor_name} checked out.`)
      invalidate()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Check-out failed.'),
  })

  const columns: Column<ApiVisitor>[] = [
    {
      key: 'visitor',
      header: 'Visitor',
      render: (visitor) => (
        <div>
          <p className="font-medium text-slate-900">{visitor.visitor_name}</p>
          <p className="text-xs text-slate-500">{visitor.visitor_phone || 'No phone'}</p>
        </div>
      ),
    },
    {
      key: 'host',
      header: 'Visiting',
      render: (visitor) => (
        <span>
          {visitor.resident_name}
          {visitor.unit_number && <span className="text-slate-500"> · {visitor.unit_number}</span>}
        </span>
      ),
    },
    { key: 'in', header: 'In', render: (visitor) => formatDateTime(visitor.checkin_time) },
    { key: 'out', header: 'Out', render: (visitor) => formatDateTime(visitor.checkout_time) },
    {
      key: 'status',
      header: 'Status',
      render: (visitor) => <Badge tone={STATUS_TONE[visitor.status]}>{STATUS_LABEL[visitor.status]}</Badge>,
    },
    {
      key: 'actions',
      header: '',
      className: 'text-right',
      render: (visitor) => (
        <div className="flex justify-end gap-1">
          {visitor.status !== 'checked_in' && (
            <Button
              variant="secondary"
              className="px-2.5 py-1 text-xs"
              disabled={checkIn.isPending}
              onClick={() => checkIn.mutate(visitor.id)}
            >
              Check in
            </Button>
          )}
          {visitor.status === 'checked_in' && (
            <Button
              variant="secondary"
              className="px-2.5 py-1 text-xs"
              disabled={checkOut.isPending}
              onClick={() => checkOut.mutate(visitor.id)}
            >
              Check out
            </Button>
          )}
        </div>
      ),
    },
  ]

  const inside = (data?.results ?? []).filter((visitor) => visitor.status === 'checked_in').length

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <PageHeader
        title="Visitor log"
        subtitle={data ? `${inside} currently inside · ${data.count} logged` : 'Who came in, and when'}
        actions={
          <Link
            to="/app/scan"
            className="inline-flex items-center rounded-lg bg-brand-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-800"
          >
            Open gate scanner
          </Link>
        }
      />

      <DataTable
        columns={columns}
        rows={data?.results ?? []}
        rowKey={(visitor) => visitor.id}
        loading={isLoading}
        page={page}
        count={data?.count ?? 0}
        onPageChange={setPage}
        empty={{
          icon: '🛡️',
          title: 'Nobody has been scanned in yet',
          body: 'Visitors appear here once a guard scans their pass at the gate.',
          action: (
            <Link
              to="/app/scan"
              className="inline-flex items-center rounded-lg bg-brand-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-800"
            >
              Open gate scanner
            </Link>
          ),
        }}
      />
    </div>
  )
}
