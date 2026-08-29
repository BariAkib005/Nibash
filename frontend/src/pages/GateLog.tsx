import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useBuilding } from '../lib/building'
import { useToast } from '../lib/toast'
import DataTable, { Badge } from '../components/DataTable'
import type { Column } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import { Button, Card, Skeleton } from '../components/ui'
import { formatDateTime } from '../lib/format'
import type { ApiGateEvent, GateAnalyticsBucket } from '../types'

export default function GateLog() {
  const { currentId } = useBuilding()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)

  const { data, isLoading } = useQuery({
    queryKey: ['gate-events', currentId, page],
    queryFn: () => api.gateEvents({ page, building_id: currentId }),
    enabled: Boolean(currentId),
  })

  const { data: analytics, isLoading: analyticsLoading } = useQuery({
    queryKey: ['gate-analytics', currentId],
    queryFn: () => api.gateAnalytics(currentId),
    enabled: Boolean(currentId),
  })

  const log = useMutation({
    mutationFn: (type: 'open' | 'close') => api.logGateEvent(currentId!, type),
    onSuccess: (event) => {
      toast.success(`Gate ${event.event_type} logged.`)
      queryClient.invalidateQueries({ queryKey: ['gate-events'] })
      queryClient.invalidateQueries({ queryKey: ['gate-analytics'] })
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not log the event.'),
  })

  const columns: Column<ApiGateEvent>[] = [
    {
      key: 'event',
      header: 'Event',
      render: (event) => (
        <Badge tone={event.event_type === 'open' ? 'green' : 'slate'}>{event.event_type}</Badge>
      ),
    },
    { key: 'time', header: 'When', render: (event) => formatDateTime(event.timestamp) },
    {
      key: 'actor',
      header: 'Logged by',
      render: (event) => event.actor_name ?? <span className="text-slate-400">—</span>,
    },
  ]

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <PageHeader
        title="Gate log"
        subtitle="One tap per movement. The chart shows when the gate is busiest."
        actions={
          <div className="flex gap-2">
            <Button
              className="px-5 py-3 text-base"
              disabled={!currentId || log.isPending}
              onClick={() => log.mutate('open')}
            >
              Gate opened
            </Button>
            <Button
              variant="secondary"
              className="px-5 py-3 text-base"
              disabled={!currentId || log.isPending}
              onClick={() => log.mutate('close')}
            >
              Gate closed
            </Button>
          </div>
        }
      />

      <TrafficChart buckets={analytics?.results ?? []} loading={analyticsLoading} />

      <DataTable
        columns={columns}
        rows={data?.results ?? []}
        rowKey={(event) => event.id}
        loading={isLoading}
        page={page}
        count={data?.count ?? 0}
        onPageChange={setPage}
        empty={{
          icon: '🚧',
          title: 'No gate activity logged',
          body: 'Tap “Gate opened” or “Gate closed” as movements happen and the chart fills in.',
        }}
      />
    </div>
  )
}

/**
 * Traffic by hour of day, opens and closes side by side.
 *
 * <p>Twenty-four buckets on a phone would be unreadable, so the chart scrolls horizontally inside
 * its own container rather than squeezing the bars to nothing.
 */
function TrafficChart({ buckets, loading }: { buckets: GateAnalyticsBucket[]; loading: boolean }) {
  const { hours, max, total } = useMemo(() => {
    const byHour = new Map<number, { open: number; close: number }>()
    for (let hour = 0; hour < 24; hour++) byHour.set(hour, { open: 0, close: 0 })

    let sum = 0
    for (const bucket of buckets) {
      const entry = byHour.get(bucket.hour)
      if (!entry) continue
      entry[bucket.event_type] = bucket.total
      sum += bucket.total
    }

    const values = [...byHour.values()].map((entry) => Math.max(entry.open, entry.close))
    return { hours: [...byHour.entries()], max: Math.max(1, ...values), total: sum }
  }, [buckets])

  if (loading) {
    return <Card><Skeleton className="h-40 w-full" /></Card>
  }

  if (!total) {
    return (
      <Card className="border-dashed bg-slate-50/60">
        <h2 className="text-sm font-semibold text-slate-900">Hourly traffic</h2>
        <p className="mt-1 text-sm text-slate-600">
          The chart appears once the gate has been logged a few times.
        </p>
      </Card>
    )
  }

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-slate-900">Hourly gate traffic</h2>
        <ul className="flex gap-3 text-xs text-slate-600">
          <li className="flex items-center gap-1.5">
            <span aria-hidden="true" className="h-2.5 w-2.5 rounded-sm bg-brand-600" /> Opened
          </li>
          <li className="flex items-center gap-1.5">
            <span aria-hidden="true" className="h-2.5 w-2.5 rounded-sm bg-slate-400" /> Closed
          </li>
        </ul>
      </div>

      <div className="mt-4 overflow-x-auto">
        <div className="flex min-w-3xl items-end gap-1">
          {hours.map(([hour, counts]) => (
            <div key={hour} className="flex flex-1 flex-col items-center gap-1">
              <div className="flex h-28 w-full items-end justify-center gap-0.5">
                <div
                  className="w-1/2 rounded-t-sm bg-brand-600 transition-all"
                  style={{ height: `${(counts.open / max) * 100}%` }}
                  title={`${hour}:00 — ${counts.open} opened`}
                />
                <div
                  className="w-1/2 rounded-t-sm bg-slate-400 transition-all"
                  style={{ height: `${(counts.close / max) * 100}%` }}
                  title={`${hour}:00 — ${counts.close} closed`}
                />
              </div>
              <span className="text-[10px] tabular-nums text-slate-400">
                {String(hour).padStart(2, '0')}
              </span>
            </div>
          ))}
        </div>
      </div>

      <p className="mt-2 text-xs text-slate-500">{total} movements logged in total.</p>
    </Card>
  )
}
