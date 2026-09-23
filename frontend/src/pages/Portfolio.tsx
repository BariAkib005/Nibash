import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import { useBuilding } from '../lib/building'
import PageHeader from '../components/PageHeader'
import { Card, Skeleton } from '../components/ui'
import Icon from '../components/Icon'
import type { IconName } from '../components/Icon'
import { taka } from '../lib/format'

/**
 * The multi-building overview (spec §8.24): every building the caller manages, side by side.
 * Clicking a building switches the workspace to it and opens its overview.
 */
export default function Portfolio() {
  const { select, currentId } = useBuilding()
  const navigate = useNavigate()
  const { data, isLoading } = useQuery({ queryKey: ['analytics-overview'], queryFn: () => api.analyticsOverview() })

  const rows = data?.per_building ?? []
  const totalUnits = rows.reduce((sum, r) => sum + r.total_units, 0)
  const metrics: { label: string; value: string; hint: string; icon: IconName }[] = [
    { label: 'Buildings', value: String(rows.length), hint: 'In your workspace', icon: 'building' },
    {
      label: 'Occupancy',
      value: totalUnits ? `${Math.round(((data?.occupancy ?? 0) / totalUnits) * 100)}%` : '—',
      hint: `${data?.occupancy ?? 0} of ${totalUnits} units`,
      icon: 'overview',
    },
    { label: 'Collected', value: taka(data?.payments_sum ?? 0), hint: `Across ${data?.invoices ?? 0} invoices`, icon: 'wallet' },
    { label: 'Open tickets', value: String(data?.open_tickets ?? 0), hint: `${data?.bookings ?? 0} bookings in total`, icon: 'tool' },
  ]

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader title="Portfolio" subtitle="Every building you manage, side by side." />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {metrics.map((metric) => (
          <Card key={metric.label}>
            <div className="metric-top">
              <p>{metric.label}</p>
              <Icon name={metric.icon} size={17} />
            </div>
            {isLoading ? <Skeleton className="mt-5 h-10 w-24" /> : <p className="metric-value">{metric.value}</p>}
            <p className="metric-hint">{metric.hint}</p>
          </Card>
        ))}
      </div>

      <section className="space-y-3">
        <h2 className="dashboard-section-title">Buildings</h2>
        {isLoading ? (
          <div className="space-y-3">{[0, 1].map((i) => <Skeleton key={i} className="h-28 w-full" />)}</div>
        ) : (
          <div className="grid gap-3 lg:grid-cols-2">
            {rows.map((row) => {
              const occupancy = row.total_units ? Math.round((row.occupancy / row.total_units) * 100) : 0
              return (
                <button
                  key={row.building_id}
                  type="button"
                  onClick={() => {
                    select(row.building_id)
                    navigate('/app')
                  }}
                  className="ui-card block text-left transition hover:border-brand-400"
                >
                  <div className="flex items-start justify-between gap-3">
                    <span className="flex items-center gap-3">
                      <span className="grid h-10 w-10 place-items-center rounded-md border border-slate-200 text-brand-700">
                        <Icon name="building" size={18} />
                      </span>
                      <span>
                        <span className="block text-sm font-semibold text-slate-900">{row.name}</span>
                        <span className="mt-0.5 block text-[11px] text-slate-500">
                          {row.building_id === currentId ? 'You are viewing this building' : 'Open this building'}
                        </span>
                      </span>
                    </span>
                    <Icon name="arrow" size={16} className="mt-1 text-slate-400" />
                  </div>

                  <div className="mt-5">
                    <div className="mb-2 flex justify-between text-xs">
                      <span className="text-slate-600">Occupancy</span>
                      <span className="tabular-nums text-slate-800">
                        {row.occupancy}/{row.total_units}
                        <span className="ml-3 inline-block w-9 text-right text-slate-500">{occupancy}%</span>
                      </span>
                    </div>
                    <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
                      <div className="h-full rounded-full bg-brand-700" style={{ width: `${occupancy}%` }} />
                    </div>
                  </div>

                  <dl className="mt-5 grid grid-cols-3 gap-3 border-t border-slate-100 pt-4 text-xs">
                    <div>
                      <dt className="text-[10px] text-slate-500">Collected</dt>
                      <dd className="mt-1 font-semibold tabular-nums text-slate-800">{taka(row.payments_sum)}</dd>
                    </div>
                    <div>
                      <dt className="text-[10px] text-slate-500">Open tickets</dt>
                      <dd className="mt-1 font-semibold tabular-nums text-slate-800">{row.open_tickets}</dd>
                    </div>
                    <div>
                      <dt className="text-[10px] text-slate-500">Bookings</dt>
                      <dd className="mt-1 font-semibold tabular-nums text-slate-800">{row.bookings}</dd>
                    </div>
                  </dl>
                </button>
              )
            })}
          </div>
        )}
      </section>
    </div>
  )
}
