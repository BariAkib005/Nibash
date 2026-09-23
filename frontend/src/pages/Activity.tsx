import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useBuilding } from '../lib/building'
import DataTable, { Badge } from '../components/DataTable'
import type { Column } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import { Select } from '../components/ui'
import { formatDateTime } from '../lib/format'
import type { ApiActivity } from '../types'

const ACTION_LABEL: Record<string, string> = {
  generate_monthly_invoices: 'Ran the monthly invoices',
  generate_utility_bills: 'Opened a utility month',
  paid: 'Recorded a payment',
  uploaded: 'Uploaded a document',
  new_version: 'Uploaded a new version',
  deleted: 'Deleted a document',
  parking_layout: 'Changed the parking layout',
}

/** The activity log (spec §8.23): who ran the batch jobs, took payments and changed documents. */
export default function Activity() {
  const { currentId } = useBuilding()
  const [page, setPage] = useState(1)
  const [entity, setEntity] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['activity', currentId, page, entity],
    queryFn: () => api.activity({ page, building_id: currentId, entity_type: entity || undefined }),
    enabled: Boolean(currentId),
  })

  const columns: Column<ApiActivity>[] = [
    { key: 'when', header: 'When', render: (a) => <span className="whitespace-nowrap">{formatDateTime(a.timestamp)}</span> },
    {
      key: 'who',
      header: 'Who',
      render: (a) => (
        <span className="flex items-center gap-2.5">
          <span className="user-avatar !h-7 !w-7">{a.user_name.charAt(0).toUpperCase()}</span>
          {a.user_name}
        </span>
      ),
    },
    {
      key: 'what',
      header: 'What',
      render: (a) => (
        <div>
          <p className="text-slate-800">{ACTION_LABEL[a.action] ?? a.action.replace(/_/g, ' ')}</p>
          <p className="mt-0.5 text-[11px] text-slate-500">{describe(a.details_json)}</p>
        </div>
      ),
    },
    { key: 'entity', header: 'Record', render: (a) => <Badge tone="slate">{a.entity_type}</Badge> },
  ]

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader title="Activity" subtitle="Who ran the batch jobs, took payments and changed documents or parking." />

      <div className="w-full max-w-[220px]">
        <Select
          label="Show"
          value={entity}
          onChange={(e) => {
            setEntity(e.target.value)
            setPage(1)
          }}
        >
          <option value="">Everything</option>
          <option value="invoice">Payments</option>
          <option value="document">Documents</option>
          <option value="building">Billing runs and parking</option>
        </Select>
      </div>

      <DataTable
        columns={columns}
        rows={data?.results ?? []}
        rowKey={(a) => a.id}
        loading={isLoading}
        page={page}
        count={data?.count ?? 0}
        onPageChange={setPage}
        empty={{
          icon: 'activity',
          title: 'Nothing recorded yet',
          body: 'Billing runs, payments, document uploads and parking changes are recorded here as they happen.',
        }}
      />
    </div>
  )
}

/** Turns the stored JSON details into "billing month 2026-11 · created 4". */
function describe(details: string | null): string {
  if (!details) return ''
  try {
    const parsed = JSON.parse(details) as Record<string, unknown>
    return Object.entries(parsed)
      .map(([key, value]) => `${key.replace(/_/g, ' ')} ${String(value)}`)
      .join(' · ')
  } catch {
    return ''
  }
}
