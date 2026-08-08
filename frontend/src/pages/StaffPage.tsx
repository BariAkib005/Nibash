import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useBuilding } from '../lib/building'
import DataTable, { Badge } from '../components/DataTable'
import type { Column } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import type { ApiStaff } from '../types'

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
      <PageHeader
        title="Staff"
        subtitle="Employment records for this building. Attendance check-in/out arrives in Week 3."
      />
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
