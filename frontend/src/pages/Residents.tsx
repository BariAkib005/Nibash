import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useBuilding } from '../lib/building'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'
import DataTable, { Badge } from '../components/DataTable'
import type { Column } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import type { ApiResident } from '../types'

export default function Residents() {
  const { currentId } = useBuilding()
  const { user } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)

  const canManage = user?.role === 'admin' || user?.role === 'committee'

  const { data, isLoading } = useQuery({
    queryKey: ['residents', currentId, page],
    queryFn: () => api.residents({ page, building_id: currentId }),
    enabled: Boolean(currentId),
  })

  const toggle = useMutation({
    mutationFn: ({ id, field, value }: { id: number; field: 'is_owner' | 'opt_in'; value: boolean }) =>
      api.updateResident(id, { [field]: value }),
    onSuccess: (_r, variables) => {
      queryClient.invalidateQueries({ queryKey: ['residents'] })
      queryClient.invalidateQueries({ queryKey: ['directory'] })
      toast.success(variables.field === 'opt_in' ? 'Directory visibility updated.' : 'Ownership updated.')
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Update failed.'),
  })

  const columns: Column<ApiResident>[] = [
    {
      key: 'name',
      header: 'Resident',
      render: (r) => (
        <div>
          <p className="font-medium text-slate-900">{r.resident_name}</p>
          <p className="text-xs text-slate-500">{r.resident_email}</p>
        </div>
      ),
    },
    { key: 'unit', header: 'Unit', render: (r) => r.unit_number ?? '—' },
    {
      key: 'is_owner',
      header: 'Tenure',
      render: (r) => <Badge tone={r.is_owner ? 'blue' : 'slate'}>{r.is_owner ? 'Owner' : 'Tenant'}</Badge>,
    },
    {
      key: 'opt_in',
      header: 'In directory',
      render: (r) =>
        canManage ? (
          <label className="inline-flex cursor-pointer items-center gap-2">
            <input
              type="checkbox"
              checked={r.opt_in}
              disabled={toggle.isPending}
              onChange={(e) => toggle.mutate({ id: r.id, field: 'opt_in', value: e.target.checked })}
              className="h-4 w-4 rounded border-slate-300 text-brand-700 focus:ring-brand-500"
            />
            <span className="text-xs text-slate-600">{r.opt_in ? 'Shared' : 'Hidden'}</span>
          </label>
        ) : (
          <Badge tone={r.opt_in ? 'green' : 'slate'}>{r.opt_in ? 'Shared' : 'Hidden'}</Badge>
        ),
    },
    {
      key: 'start_date',
      header: 'Since',
      render: (r) => (r.start_date ? new Date(r.start_date).toLocaleDateString() : '—'),
    },
  ]

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <PageHeader
        title="Residents"
        subtitle="Who lives in this building, and whether they share contact details in the directory."
      />
      <DataTable
        columns={columns}
        rows={data?.results ?? []}
        rowKey={(r) => r.id}
        loading={isLoading}
        page={page}
        count={data?.count ?? 0}
        onPageChange={setPage}
        empty={{
          icon: '🏠',
          title: 'No residents yet',
          body: 'A resident links a user account to this building, and optionally to a unit.',
        }}
      />
    </div>
  )
}
