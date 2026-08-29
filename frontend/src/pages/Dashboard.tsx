import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import { Card, Skeleton } from '../components/ui'
import PageHeader from '../components/PageHeader'
import { Badge } from '../components/DataTable'

/**
 * Occupancy metrics computed from the units the caller can actually see.
 *
 * <p>This page still makes several calls; the planned `/api/dashboard/summary/` folds them into one
 * and adds the remaining module sections.
 */
export default function Dashboard() {
  const { user } = useAuth()
  const { current, currentId, buildings } = useBuilding()

  const { data: units, isLoading } = useQuery({
    queryKey: ['units', currentId, 'all'],
    queryFn: () => api.units({ building_id: currentId }),
    enabled: Boolean(currentId),
  })

  const { data: directory } = useQuery({
    queryKey: ['directory', currentId, ''],
    queryFn: () => api.directory({ building_id: currentId }),
    enabled: Boolean(currentId),
  })

  const rows = units?.results ?? []
  const total = units?.count ?? 0
  // Page 1 gives at most 20 rows, so occupancy is computed over what we have and labelled honestly.
  const occupied = rows.filter((u) => ['occupied', 'sold', 'rented'].includes(u.status)).length
  const occupancy = rows.length ? Math.round((occupied / rows.length) * 100) : 0
  const available = rows.filter((u) => u.status === 'available').length

  const metrics = [
    { label: 'Total units', value: total ? String(total) : '—', hint: 'In this building' },
    { label: 'Occupancy', value: rows.length ? `${occupancy}%` : '—', hint: `${occupied} of ${rows.length} shown` },
    { label: 'Available', value: rows.length ? String(available) : '—', hint: 'Ready to allocate' },
    { label: 'Residents', value: directory ? String(directory.count) : '—', hint: 'Listed in the directory' },
  ]

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title={`Welcome, ${user?.name.split(' ')[0]}`}
        subtitle={
          current ? (
            <>
              You're viewing <span className="font-medium text-slate-800">{current.name}</span>
              {buildings.length > 1 && <> · {buildings.length} buildings available</>}
            </>
          ) : (
            'You are not attached to a building yet.'
          )
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {metrics.map((metric) => (
          <Card key={metric.label}>
            <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{metric.label}</p>
            {isLoading ? (
              <Skeleton className="mt-2 h-9 w-20" />
            ) : (
              <p className="mt-2 text-3xl font-bold text-slate-900">{metric.value}</p>
            )}
            <p className="mt-1 text-xs text-slate-500">{metric.hint}</p>
          </Card>
        ))}
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-900">Units by status</h2>
            <Link to="/app/units" className="text-xs font-semibold text-brand-700 hover:underline">
              View all →
            </Link>
          </div>
          {isLoading ? (
            <div className="mt-4 space-y-2">
              {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-6 w-full" />)}
            </div>
          ) : (
            <ul className="mt-4 space-y-2">
              {(['occupied', 'rented', 'available', 'sold'] as const).map((status) => {
                const n = rows.filter((u) => u.status === status).length
                const pct = rows.length ? (n / rows.length) * 100 : 0
                return (
                  <li key={status}>
                    <div className="flex justify-between text-xs">
                      <span className="capitalize text-slate-600">{status}</span>
                      <span className="font-medium text-slate-800">{n}</span>
                    </div>
                    <div className="mt-1 h-2 overflow-hidden rounded-full bg-slate-100">
                      <div className="h-full rounded-full bg-brand-600 transition-all" style={{ width: `${pct}%` }} />
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </Card>

        <Card>
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-900">Directory</h2>
            <Link to="/app/directory" className="text-xs font-semibold text-brand-700 hover:underline">
              Open →
            </Link>
          </div>
          <ul className="mt-4 space-y-2">
            {(directory?.results ?? []).slice(0, 5).map((entry) => (
              <li key={entry.id} className="flex items-center gap-3 text-sm">
                <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-brand-700 text-xs font-bold text-white">
                  {entry.name.charAt(0).toUpperCase()}
                </span>
                <span className="min-w-0 flex-1 truncate text-slate-800">{entry.name}</span>
                <span className="text-xs text-slate-500">{entry.unit_number ?? '—'}</span>
                <Badge tone={entry.is_owner ? 'blue' : 'slate'}>{entry.is_owner ? 'Owner' : 'Tenant'}</Badge>
              </li>
            ))}
            {!directory?.results.length && (
              <li className="text-sm text-slate-500">No listed residents yet.</li>
            )}
          </ul>
        </Card>
      </div>

      <Card className="border-dashed bg-slate-50/60">
        <h2 className="text-sm font-semibold text-slate-900">Coming next</h2>
        <p className="mt-1 text-sm text-slate-600">
          Live group chat and an interactive parking grid, plus a single dashboard call that replaces
          the several this page makes today.
        </p>
      </Card>
    </div>
  )
}
