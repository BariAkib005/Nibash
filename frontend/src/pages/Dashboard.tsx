import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import { Alert, Button, Card, Skeleton } from '../components/ui'
import PageHeader from '../components/PageHeader'
import { Badge } from '../components/DataTable'
import Icon from '../components/Icon'
import type { IconName } from '../components/Icon'

export default function Dashboard() {
  const { user } = useAuth()
  const { current, currentId, buildings } = useBuilding()
  const unitsQuery = useQuery({
    queryKey: ['units', currentId, 'all'],
    queryFn: () => api.units({ building_id: currentId }),
    enabled: Boolean(currentId),
  })
  const directoryQuery = useQuery({
    queryKey: ['directory', currentId, ''],
    queryFn: () => api.directory({ building_id: currentId }),
    enabled: Boolean(currentId),
  })
  const { data: units, isLoading } = unitsQuery
  const { data: directory } = directoryQuery
  const rows = units?.results ?? []
  const occupied = rows.filter((u) => ['occupied', 'sold', 'rented'].includes(u.status)).length
  const occupancy = rows.length ? Math.round((occupied / rows.length) * 100) : 0
  const canManage = user?.role === 'admin' || user?.role === 'committee'
  const metrics: { label: string; value: string; hint: string; icon: IconName; loading: boolean }[] = [
    {
      label: 'Total units',
      value: units ? String(units.count) : '—',
      hint: 'Across this building',
      icon: 'building',
      loading: isLoading,
    },
    {
      label: 'Occupancy',
      value: rows.length ? `${occupancy}%` : '—',
      hint: `${occupied} of ${rows.length} loaded units`,
      icon: 'overview',
      loading: isLoading,
    },
    {
      label: 'Available units',
      value: units ? String(rows.filter((u) => u.status === 'available').length) : '—',
      hint: 'Among loaded units',
      icon: 'box',
      loading: isLoading,
    },
    {
      label: 'Residents',
      value: directory ? String(directory.count) : '—',
      hint: 'Listed in the directory',
      icon: 'people',
      loading: directoryQuery.isLoading,
    },
  ]
  const shortcuts: { title: string; body: string; to: string; icon: IconName }[] = [
    { title: 'Maintenance', body: 'Track requests and repairs', to: '/app/tickets', icon: 'tool' },
    { title: 'Community notices', body: 'Catch up on building updates', to: '/app/notices', icon: 'notice' },
    {
      title: 'Resident directory',
      body: 'Connect with your neighbours',
      to: '/app/directory',
      icon: 'directory',
    },
  ]
  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Building overview"
        subtitle={
          current ? (
            <>
              {current.name}
              {buildings.length > 1 && ` · ${buildings.length} buildings in your workspace`}
            </>
          ) : (
            'You are not attached to a building yet.'
          )
        }
        actions={
          <span className="flex items-center gap-2 rounded-md border border-slate-200 bg-white px-3 py-2 text-xs text-slate-500">
            <Icon name="calendar" size={15} />
            {new Intl.DateTimeFormat('en-GB', {
              timeZone: 'Asia/Dhaka',
              day: 'numeric',
              month: 'short',
              year: 'numeric',
            }).format(new Date())}
          </span>
        }
      />
      <section className="dashboard-intro">
        <div>
          <p className="eyebrow !mt-0 !mb-3">YOUR EVERYDAY, AT A GLANCE</p>
          <h2>Welcome back, {user?.name.split(' ')[0] ?? 'neighbour'}.</h2>
          <p>A little clarity for the day ahead. Here’s what’s happening in your building.</p>
        </div>
        <Link to="/app/notices" className="action-link">
          View notices
          <Icon name="arrow" size={16} />
        </Link>
      </section>
      {(unitsQuery.isError || directoryQuery.isError) && (
        <Alert>
          <div>
            Some building information couldn’t be loaded.
            <Button
              variant="ghost"
              className="ml-2 !p-0 underline"
              onClick={() => {
                if (unitsQuery.isError) void unitsQuery.refetch()
                if (directoryQuery.isError) void directoryQuery.refetch()
              }}
            >
              Try again
            </Button>
          </div>
        </Alert>
      )}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {metrics.map((metric) => (
          <Card key={metric.label}>
            <div className="metric-top">
              <p>{metric.label}</p>
              <Icon name={metric.icon} size={17} />
            </div>
            {metric.loading ? (
              <Skeleton className="mt-5 h-10 w-20" />
            ) : (
              <p className="metric-value">{metric.value}</p>
            )}
            <p className="metric-hint">{metric.hint}</p>
          </Card>
        ))}
      </div>
      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <div className="flex items-center justify-between gap-3">
            <h2 className="dashboard-section-title">Occupancy breakdown</h2>
            {canManage && (
              <Link to="/app/units" className="text-link !text-[11px]">
                View units
                <Icon name="arrow" size={14} />
              </Link>
            )}
          </div>
          <p className="mt-2 text-[11px] text-slate-500">
            Status of the {rows.length} loaded units
            {units && units.count > rows.length ? ` · ${units.count} total` : ''}
          </p>
          {isLoading ? (
            <div className="mt-6 space-y-4">
              {[0, 1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-8 w-full" />
              ))}
            </div>
          ) : unitsQuery.isError ? (
            <p className="py-10 text-sm text-slate-500">Unit information is unavailable.</p>
          ) : rows.length ? (
            <ul className="mt-6 space-y-5">
              {(['occupied', 'rented', 'available', 'sold'] as const).map((status, i) => {
                const count = rows.filter((u) => u.status === status).length
                return (
                  <li key={status}>
                    <div className="mb-2 flex justify-between text-xs">
                      <span className="flex items-center gap-2 capitalize text-slate-600">
                        <span
                          className="h-2 w-2 rounded-sm"
                          style={{ background: ['#2f5643', '#7a956b', '#c4d3ab', '#b9a98a'][i] }}
                        />
                        {status}
                      </span>
                      <span className="tabular-nums text-slate-800">
                        {count}
                        <span className="ml-3 inline-block w-9 text-right text-slate-500">
                          {Math.round((count / rows.length) * 100)}%
                        </span>
                      </span>
                    </div>
                    <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
                      <div
                        className="h-full rounded-full"
                        style={{
                          width: `${(count / rows.length) * 100}%`,
                          background: ['#2f5643', '#7a956b', '#c4d3ab', '#b9a98a'][i],
                        }}
                      />
                    </div>
                  </li>
                )
              })}
            </ul>
          ) : (
            <div className="py-12 text-center">
              <Icon name="building" className="mx-auto text-slate-400" size={28} />
              <p className="mt-3 text-sm text-slate-500">No units in this building yet.</p>
            </div>
          )}
        </Card>
        <Card>
          <div className="flex items-center justify-between gap-3">
            <h2 className="dashboard-section-title">Your neighbours</h2>
            <Link to="/app/directory" className="text-link !text-[11px]">
              Directory
              <Icon name="arrow" size={14} />
            </Link>
          </div>
          <p className="mt-2 text-[11px] text-slate-500">The people who make this place home.</p>
          {directoryQuery.isLoading ? (
            <div className="mt-6 space-y-4">
              {[0, 1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          ) : (
            <ul className="mt-4 divide-y divide-slate-100">
              {(directory?.results ?? []).slice(0, 5).map((entry) => (
                <li key={entry.id} className="flex items-center gap-3 py-3">
                  <span className="user-avatar">{entry.name.charAt(0).toUpperCase()}</span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-xs font-medium text-slate-800">{entry.name}</p>
                    <p className="mt-1 text-[10px] text-slate-500">
                      {entry.unit_number ? `Unit ${entry.unit_number}` : 'Resident'}
                    </p>
                  </div>
                  <Badge tone={entry.is_owner ? 'blue' : 'slate'}>
                    {entry.is_owner ? 'Owner' : 'Tenant'}
                  </Badge>
                </li>
              ))}
              {!directory?.results.length && (
                <li className="py-12 text-center text-sm text-slate-500">
                  {directoryQuery.isError
                    ? 'Directory information is unavailable.'
                    : 'No listed residents yet.'}
                </li>
              )}
            </ul>
          )}
        </Card>
      </div>
      <section>
        <h2 className="dashboard-section-title mb-4">Around your building</h2>
        <div className="dashboard-links">
          {shortcuts.map((item) => (
            <Link to={item.to} key={item.to}>
              <Icon name={item.icon} size={21} />
              <div>
                <strong>{item.title}</strong>
                <p>{item.body}</p>
              </div>
              <Icon name="arrow" size={16} />
            </Link>
          ))}
        </div>
      </section>
    </div>
  )
}
