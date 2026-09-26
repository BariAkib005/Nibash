import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useBuilding } from '../lib/building'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'
import DataTable, { Badge } from '../components/DataTable'
import type { Column } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import { Button, Field, Modal, Select } from '../components/ui'
import Icon from '../components/Icon'
import type { ApiUnit, UnitStatus } from '../types'

const STATUSES: UnitStatus[] = ['available', 'occupied', 'sold', 'rented']
const TYPES = ['studio', '1BHK', '2BHK', '3BHK', 'duplex', 'shop', 'office']

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
  const [adding, setAdding] = useState(false)

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
          <>
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
            {canManage && (
              <Button onClick={() => setAdding(true)}>
                <Icon name="plus" size={16} />
                Add unit
              </Button>
            )}
          </>
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
          action: status ? (
            <Button variant="secondary" onClick={() => setStatus('')}>Clear filter</Button>
          ) : canManage ? (
            <Button onClick={() => setAdding(true)}>Add unit</Button>
          ) : undefined,
        }}
      />
      {canManage && <UnitDialog open={adding} buildingId={currentId} onClose={() => setAdding(false)} />}
    </div>
  )
}

/** Adding a flat, shop or office — how a new building gets its inventory before inviting anyone. */
function UnitDialog({ open, buildingId, onClose }: { open: boolean; buildingId: number | undefined; onClose: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [number, setNumber] = useState('')
  const [floor, setFloor] = useState('')
  const [type, setType] = useState('2BHK')
  const [size, setSize] = useState('')
  const [price, setPrice] = useState('')
  const [status, setStatus] = useState<UnitStatus>('available')

  const badFloor = floor !== '' && !/^-?\d+$/.test(floor)
  const badSize = size !== '' && !(Number(size) > 0)
  const badPrice = price !== '' && !(Number(price) >= 0)

  const create = useMutation({
    mutationFn: () =>
      api.createUnit({
        building: buildingId,
        unit_number: number.trim(),
        floor: floor === '' ? undefined : Number(floor),
        type,
        size_sqft: size || undefined,
        price: price || undefined,
        status,
      }),
    onSuccess: (unit) => {
      toast.success(`Unit ${unit.unit_number} added.`)
      queryClient.invalidateQueries({ queryKey: ['units'] })
      setNumber('')
      setFloor('')
      setSize('')
      setPrice('')
      onClose()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not add the unit.'),
  })

  return (
    <Modal
      open={open}
      title="Add unit"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            loading={create.isPending}
            disabled={!number.trim() || badFloor || badSize || badPrice}
            onClick={() => create.mutate()}
          >
            Add unit
          </Button>
        </>
      }
    >
      <div className="grid grid-cols-2 gap-3">
        <Field label="Unit number" placeholder="5A" value={number} maxLength={50} onChange={(e) => setNumber(e.target.value)} />
        <Field label="Floor" inputMode="numeric" value={floor} onChange={(e) => setFloor(e.target.value)} error={badFloor ? 'A whole number' : undefined} />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Select label="Type" value={type} onChange={(e) => setType(e.target.value)}>
          {TYPES.map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
        </Select>
        <Select label="Status" value={status} onChange={(e) => setStatus(e.target.value as UnitStatus)}>
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </Select>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Size (sq ft, optional)" inputMode="decimal" value={size} onChange={(e) => setSize(e.target.value)} error={badSize ? 'Must be positive' : undefined} />
        <Field label="Price (৳, optional)" inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} error={badPrice ? 'Enter an amount' : undefined} />
      </div>
    </Modal>
  )
}
