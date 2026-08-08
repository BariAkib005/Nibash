import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useBuilding } from '../lib/building'
import PageHeader from '../components/PageHeader'
import { Badge } from '../components/DataTable'
import { Card, EmptyState, Skeleton } from '../components/ui'

export default function Directory() {
  const { currentId } = useBuilding()
  const [search, setSearch] = useState('')
  const [debounced, setDebounced] = useState('')

  // Debounce so typing doesn't fire a request per keystroke.
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(search.trim()), 250)
    return () => clearTimeout(timer)
  }, [search])

  const { data, isLoading } = useQuery({
    queryKey: ['directory', currentId, debounced],
    queryFn: () => api.directory({ building_id: currentId, search: debounced || undefined }),
    enabled: Boolean(currentId),
  })

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <PageHeader
        title="Resident directory"
        subtitle="Contact details are shown only for residents who opted in."
      />

      <input
        type="search"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Search by name, unit number or email…"
        className="w-full rounded-lg border border-slate-300 px-3.5 py-2.5 text-sm outline-none focus:border-brand-600 focus:ring-2 focus:ring-brand-500/30"
      />

      {isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-24 w-full" />
          ))}
        </div>
      ) : !data?.results.length ? (
        <EmptyState
          icon="📇"
          title={debounced ? `No one matches “${debounced}”` : 'The directory is empty'}
          body={
            debounced
              ? 'Try a different name, unit number or email.'
              : 'Residents appear here once they are added to the building and listed.'
          }
        />
      ) : (
        <>
          <p className="text-sm text-slate-500">
            {data.count} resident{data.count === 1 ? '' : 's'}
          </p>
          <div className="grid gap-3 sm:grid-cols-2">
            {data.results.map((entry) => (
              <Card key={entry.id} className="flex gap-3">
                <span className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-brand-700 text-sm font-bold text-white">
                  {entry.name.charAt(0).toUpperCase()}
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <p className="truncate font-semibold text-slate-900">{entry.name}</p>
                    <Badge tone={entry.is_owner ? 'blue' : 'slate'}>{entry.is_owner ? 'Owner' : 'Tenant'}</Badge>
                  </div>
                  <p className="text-sm text-slate-500">
                    {entry.unit_number ? `Unit ${entry.unit_number}` : 'No unit assigned'}
                  </p>

                  {entry.opt_in ? (
                    <div className="mt-2 space-y-0.5 text-sm">
                      {entry.email && (
                        <a href={`mailto:${entry.email}`} className="block truncate text-brand-700 hover:underline">
                          {entry.email}
                        </a>
                      )}
                      {entry.phone && <p className="text-slate-600">{entry.phone}</p>}
                    </div>
                  ) : (
                    <p className="mt-2 text-xs italic text-slate-400">
                      Contact details hidden at this resident’s request
                    </p>
                  )}
                </div>
              </Card>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
