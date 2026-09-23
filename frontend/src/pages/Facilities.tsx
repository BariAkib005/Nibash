import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import { useToast } from '../lib/toast'
import DataTable, { Badge } from '../components/DataTable'
import type { Column } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import { Button, Card, Field, Modal, Select, Skeleton, TextArea } from '../components/ui'
import Icon from '../components/Icon'
import type { IconName } from '../components/Icon'
import { formatDate, formatDateTime, taka } from '../lib/format'
import type { ApiAsset, ApiAssetMaintenance, ApiLiftStatus, LiftStatus } from '../types'

const LIFT_LABEL: Record<LiftStatus, string> = {
  operational: 'Running',
  maintenance: 'Under maintenance',
  out_of_order: 'Out of order',
}
const LIFT_TONE = { operational: 'green', maintenance: 'amber', out_of_order: 'red' } as const
const WARRANTY_TONE = { active: 'green', expiring: 'amber', expired: 'red' } as const

/**
 * Facilities (spec §8.16–8.18): the lift board anyone can report on, equipment with warranty
 * warnings, the service schedule, and the waste calendar.
 */
export default function Facilities() {
  const { user } = useAuth()
  const { currentId } = useBuilding()
  const toast = useToast()
  const queryClient = useQueryClient()
  const canManage = user?.role === 'admin' || user?.role === 'committee'
  const [assetOpen, setAssetOpen] = useState(false)
  const [serviceOpen, setServiceOpen] = useState(false)
  const [wasteOpen, setWasteOpen] = useState(false)

  const { data: lifts, isLoading: liftsLoading } = useQuery({
    queryKey: ['lifts', currentId],
    queryFn: () => api.liftsCurrent(currentId),
    enabled: Boolean(currentId),
  })
  const { data: assets, isLoading: assetsLoading } = useQuery({
    queryKey: ['assets', currentId],
    queryFn: () => api.assets({ building_id: currentId }),
    enabled: Boolean(currentId),
  })
  const { data: services, isLoading: servicesLoading } = useQuery({
    queryKey: ['asset-maintenance', currentId],
    queryFn: () => api.assetMaintenance({ building_id: currentId }),
    enabled: Boolean(currentId),
  })
  const { data: waste, isLoading: wasteLoading } = useQuery({
    queryKey: ['waste', currentId],
    queryFn: () => api.wasteSchedules(currentId),
    enabled: Boolean(currentId),
  })

  const assetList = assets?.results ?? []
  const liftList = lifts?.results ?? []
  const attention = assetList.filter((a) => a.warranty_state === 'expiring' || a.warranty_state === 'expired')
  const upcomingWaste = [...(waste?.results ?? [])]
    .filter((w) => w.next_occurrence)
    .sort((a, b) => a.next_occurrence!.localeCompare(b.next_occurrence!))

  const fail = (fallback: string) => (error: unknown) =>
    toast.error(error instanceof ApiError ? error.message : fallback)

  const complete = useMutation({
    mutationFn: (row: ApiAssetMaintenance) => api.completeAssetMaintenance(row.id, new Date().toISOString().slice(0, 10)),
    onSuccess: (row) => {
      toast.success(`${row.asset_name}: service marked done.`)
      queryClient.invalidateQueries({ queryKey: ['asset-maintenance'] })
    },
    onError: fail('Could not update the service.'),
  })

  const running = liftList.filter((l) => l.status === 'operational').length
  const metrics: { label: string; value: string; hint: string; icon: IconName; loading: boolean }[] = [
    { label: 'Lifts running', value: liftList.length ? `${running}/${liftList.length}` : '—', hint: 'From the latest reports', icon: 'lift', loading: liftsLoading },
    { label: 'Equipment', value: String(assets?.count ?? '—'), hint: 'Tracked assets', icon: 'box', loading: assetsLoading },
    {
      label: 'Warranty alerts',
      value: String(attention.length),
      hint: 'Expired or ending within 60 days',
      icon: 'alert',
      loading: assetsLoading,
    },
    {
      label: 'Next collection',
      value: upcomingWaste[0] ? dayLabel(upcomingWaste[0].next_occurrence!) : '—',
      hint: upcomingWaste[0] ? timeLabel(upcomingWaste[0].next_occurrence!) : 'No schedule yet',
      icon: 'calendar',
      loading: wasteLoading,
    },
  ]

  const assetColumns: Column<ApiAsset>[] = [
    {
      key: 'name',
      header: 'Equipment',
      render: (a) => (
        <div>
          <p className="font-medium text-slate-900">{a.name}</p>
          <p className="text-[11px] text-slate-500">{a.type}</p>
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (a) => (
        <Badge tone={a.status === 'operational' ? 'green' : a.status === 'under_maintenance' ? 'amber' : 'slate'}>
          {a.status.replace(/_/g, ' ')}
        </Badge>
      ),
    },
    {
      key: 'warranty',
      header: 'Warranty',
      render: (a) =>
        a.warranty_expiry ? (
          <span className="flex flex-wrap items-center gap-2">
            {formatDate(a.warranty_expiry)}
            {a.warranty_state && a.warranty_state !== 'active' && (
              <Badge tone={WARRANTY_TONE[a.warranty_state]}>{a.warranty_state}</Badge>
            )}
          </span>
        ) : (
          <span className="text-slate-400">—</span>
        ),
    },
  ]

  const serviceColumns: Column<ApiAssetMaintenance>[] = [
    {
      key: 'asset',
      header: 'Service',
      render: (m) => (
        <div>
          <p className="font-medium text-slate-900">{m.asset_name}</p>
          <p className="text-[11px] text-slate-500">{m.description ?? '—'}</p>
        </div>
      ),
    },
    { key: 'when', header: 'Scheduled', render: (m) => formatDate(m.scheduled_date) },
    { key: 'vendor', header: 'Vendor', render: (m) => m.vendor_name ?? <span className="text-slate-400">—</span> },
    { key: 'cost', header: 'Cost', className: 'text-right', render: (m) => taka(m.cost) },
    {
      key: 'state',
      header: '',
      className: 'text-right',
      render: (m) =>
        m.completed_date ? (
          <Badge tone="green">Done {formatDate(m.completed_date)}</Badge>
        ) : canManage ? (
          <Button variant="secondary" className="px-2.5 py-1 text-xs" disabled={complete.isPending} onClick={() => complete.mutate(m)}>
            Mark done
          </Button>
        ) : (
          <Badge tone="slate">Upcoming</Badge>
        ),
    },
  ]

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Facilities"
        subtitle="Lifts, equipment, warranties and the waste calendar."
        actions={
          canManage && (
            <>
              <Button variant="secondary" onClick={() => setServiceOpen(true)}>Schedule service</Button>
              <Button onClick={() => setAssetOpen(true)}>
                <Icon name="plus" size={16} />
                Add equipment
              </Button>
            </>
          )
        }
      />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {metrics.map((metric) => (
          <Card key={metric.label}>
            <div className="metric-top">
              <p>{metric.label}</p>
              <Icon name={metric.icon} size={17} />
            </div>
            {metric.loading ? <Skeleton className="mt-5 h-10 w-20" /> : <p className="metric-value">{metric.value}</p>}
            <p className="metric-hint">{metric.hint}</p>
          </Card>
        ))}
      </div>

      {attention.length > 0 && (
        <Card className="flex items-start gap-3 !border-amber-200 !bg-amber-50/60">
          <Icon name="alert" size={18} className="mt-0.5 shrink-0 text-amber-700" />
          <div>
            <p className="text-sm font-semibold text-amber-900">
              {attention.length} warrant{attention.length === 1 ? 'y needs' : 'ies need'} attention
            </p>
            <ul className="mt-1 space-y-0.5 text-xs text-amber-800">
              {attention.map((a) => (
                <li key={a.id}>
                  {a.name} — {a.warranty_state === 'expired' ? 'expired' : 'expires'} {formatDate(a.warranty_expiry)}
                </li>
              ))}
            </ul>
          </div>
        </Card>
      )}

      <div className="grid gap-5 lg:grid-cols-2">
        <LiftBoard lifts={liftList} loading={liftsLoading} buildingId={currentId} />
        <WasteCard
          schedules={upcomingWaste}
          loading={wasteLoading}
          canManage={canManage}
          onAdd={() => setWasteOpen(true)}
        />
      </div>

      <section className="space-y-3">
        <h2 className="dashboard-section-title">Equipment</h2>
        <DataTable
          columns={assetColumns}
          rows={assetList}
          rowKey={(a) => a.id}
          loading={assetsLoading}
          empty={{
            icon: 'box',
            title: 'No equipment tracked',
            body: 'Add the lifts, generator and pumps so their warranties and services are never missed.',
            action: canManage ? <Button onClick={() => setAssetOpen(true)}>Add equipment</Button> : undefined,
          }}
        />
      </section>

      <section className="space-y-3">
        <h2 className="dashboard-section-title">Service schedule</h2>
        <DataTable
          columns={serviceColumns}
          rows={services?.results ?? []}
          rowKey={(m) => m.id}
          loading={servicesLoading}
          empty={{
            icon: 'tool',
            title: 'Nothing scheduled',
            body: 'Schedule the next AMC visit or a repair so the whole committee can see it.',
            action: canManage ? <Button onClick={() => setServiceOpen(true)}>Schedule service</Button> : undefined,
          }}
        />
      </section>

      <AssetDialog
        open={assetOpen}
        buildingId={currentId}
        onClose={() => setAssetOpen(false)}
        onDone={(asset) => {
          toast.success(`${asset.name} added.`)
          queryClient.invalidateQueries({ queryKey: ['assets'] })
          setAssetOpen(false)
        }}
      />
      <ServiceDialog
        open={serviceOpen}
        assets={assetList}
        buildingId={currentId}
        onClose={() => setServiceOpen(false)}
        onDone={() => {
          toast.success('Service scheduled.')
          queryClient.invalidateQueries({ queryKey: ['asset-maintenance'] })
          setServiceOpen(false)
        }}
      />
      <WasteDialog
        open={wasteOpen}
        buildingId={currentId}
        onClose={() => setWasteOpen(false)}
        onDone={() => {
          toast.success('Collection scheduled.')
          queryClient.invalidateQueries({ queryKey: ['waste'] })
          setWasteOpen(false)
        }}
      />
    </div>
  )
}

/** Anyone in the building can report a lift's state — whoever is standing in front of it. */
function LiftBoard({ lifts, loading, buildingId }: { lifts: ApiLiftStatus[]; loading: boolean; buildingId: number | undefined }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const report = useMutation({
    mutationFn: (body: { asset: number | null; status: LiftStatus }) => api.reportLift({ building: buildingId!, ...body }),
    onSuccess: (log) => {
      toast.success(`${log.name}: ${LIFT_LABEL[log.status].toLowerCase()}.`)
      queryClient.invalidateQueries({ queryKey: ['lifts'] })
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not report the lift.'),
  })

  return (
    <Card>
      <h2 className="dashboard-section-title">Lift status</h2>
      <p className="mt-2 text-[11px] text-slate-500">The latest report for each lift. Tap to update it.</p>
      {loading ? (
        <div className="mt-5 space-y-3">{[0, 1].map((i) => <Skeleton key={i} className="h-16 w-full" />)}</div>
      ) : lifts.length ? (
        <ul className="mt-4 divide-y divide-slate-100">
          {lifts.map((lift) => (
            <li key={`${lift.asset ?? 'b'}-${lift.id}`} className="py-3.5">
              <div className="flex items-center justify-between gap-3">
                <span className="flex items-center gap-2.5">
                  <Icon name="lift" size={18} className="text-brand-700" />
                  <span className="text-xs font-medium text-slate-800">{lift.name}</span>
                </span>
                <Badge tone={LIFT_TONE[lift.status]}>{LIFT_LABEL[lift.status]}</Badge>
              </div>
              <div className="mt-2 flex flex-wrap items-center justify-between gap-2 pl-7">
                <span className="text-[10px] text-slate-500">Updated {formatDateTime(lift.timestamp)}</span>
                <span className="flex gap-1">
                  {(Object.keys(LIFT_LABEL) as LiftStatus[])
                    .filter((status) => status !== lift.status)
                    .map((status) => (
                      <button
                        key={status}
                        type="button"
                        disabled={report.isPending}
                        onClick={() => report.mutate({ asset: lift.asset, status })}
                        className="rounded-md border border-slate-200 px-2 py-1 text-[10px] font-medium text-slate-600 transition hover:border-brand-400 hover:text-brand-800 disabled:opacity-50"
                      >
                        {LIFT_LABEL[status]}
                      </button>
                    ))}
                </span>
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <div className="py-10 text-center">
          <Icon name="lift" className="mx-auto text-slate-400" size={28} />
          <p className="mt-3 text-sm text-slate-500">No lift reports yet.</p>
        </div>
      )}
    </Card>
  )
}

function WasteCard({
  schedules,
  loading,
  canManage,
  onAdd,
}: {
  schedules: { id: number; next_occurrence: string | null; recurring: string | null }[]
  loading: boolean
  canManage: boolean
  onAdd: () => void
}) {
  return (
    <Card>
      <div className="flex items-center justify-between gap-3">
        <h2 className="dashboard-section-title">Waste collection</h2>
        {canManage && (
          <button type="button" className="text-link !text-[11px]" onClick={onAdd}>
            Add collection
            <Icon name="plus" size={13} />
          </button>
        )}
      </div>
      <p className="mt-2 text-[11px] text-slate-500">Put the bins out the evening before.</p>
      {loading ? (
        <div className="mt-5 space-y-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-10 w-full" />)}</div>
      ) : schedules.length ? (
        <ul className="mt-4 divide-y divide-slate-100">
          {schedules.slice(0, 5).map((w, i) => (
            <li key={w.id} className="flex items-center gap-3 py-3">
              <span
                className={`grid h-10 w-10 shrink-0 place-items-center rounded-md text-center text-[10px] font-semibold leading-tight
                  ${i === 0 ? 'bg-brand-700 text-white' : 'bg-slate-100 text-slate-600'}`}
              >
                {new Date(w.next_occurrence!).toLocaleDateString('en-GB', { day: '2-digit' })}
                <br />
                {new Date(w.next_occurrence!).toLocaleDateString('en-GB', { month: 'short' })}
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-xs font-medium text-slate-800">{dayLabel(w.next_occurrence!)}</p>
                <p className="mt-0.5 text-[10px] capitalize text-slate-500">
                  {timeLabel(w.next_occurrence!)} · {w.recurring ?? 'one-off'}
                </p>
              </div>
              {i === 0 && <Badge tone="blue">Next</Badge>}
            </li>
          ))}
        </ul>
      ) : (
        <div className="py-10 text-center">
          <Icon name="calendar" className="mx-auto text-slate-400" size={28} />
          <p className="mt-3 text-sm text-slate-500">No collections scheduled.</p>
        </div>
      )}
    </Card>
  )
}

function AssetDialog({
  open,
  buildingId,
  onClose,
  onDone,
}: {
  open: boolean
  buildingId: number | undefined
  onClose: () => void
  onDone: (asset: ApiAsset) => void
}) {
  const toast = useToast()
  const [name, setName] = useState('')
  const [type, setType] = useState('')
  const [purchased, setPurchased] = useState('')
  const [warranty, setWarranty] = useState('')
  const warrantyBeforePurchase = Boolean(purchased && warranty && warranty < purchased)

  const create = useMutation({
    mutationFn: () =>
      api.createAsset({
        building: buildingId,
        name: name.trim(),
        type: type.trim(),
        purchase_date: purchased || undefined,
        warranty_expiry: warranty || undefined,
      }),
    onSuccess: (asset) => {
      setName('')
      setType('')
      setPurchased('')
      setWarranty('')
      onDone(asset)
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not add the equipment.'),
  })

  return (
    <Modal
      open={open}
      title="Add equipment"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={create.isPending} disabled={!name.trim() || !type.trim() || warrantyBeforePurchase} onClick={() => create.mutate()}>
            Add
          </Button>
        </>
      }
    >
      <Field label="Name" placeholder="Lift C" value={name} onChange={(e) => setName(e.target.value)} />
      <Field label="Type" placeholder="Passenger lift" value={type} onChange={(e) => setType(e.target.value)} />
      <div className="grid grid-cols-2 gap-3">
        <Field label="Purchased" type="date" value={purchased} onChange={(e) => setPurchased(e.target.value)} />
        <Field
          label="Warranty until"
          type="date"
          value={warranty}
          onChange={(e) => setWarranty(e.target.value)}
          error={warrantyBeforePurchase ? 'Before the purchase date' : undefined}
        />
      </div>
    </Modal>
  )
}

function ServiceDialog({
  open,
  assets,
  buildingId,
  onClose,
  onDone,
}: {
  open: boolean
  assets: ApiAsset[]
  buildingId: number | undefined
  onClose: () => void
  onDone: () => void
}) {
  const toast = useToast()
  const [asset, setAsset] = useState('')
  const [date, setDate] = useState('')
  const [description, setDescription] = useState('')
  const [cost, setCost] = useState('')
  const [vendor, setVendor] = useState('')

  const { data: vendors } = useQuery({
    queryKey: ['vendors', buildingId, 'service-picker'],
    queryFn: () => api.vendors({ building_id: buildingId }),
    enabled: open && Boolean(buildingId),
  })

  const selectedAsset = asset || String(assets[0]?.id ?? '')
  const create = useMutation({
    mutationFn: () =>
      api.createAssetMaintenance({
        asset: Number(selectedAsset),
        scheduled_date: date,
        description: description.trim() || undefined,
        cost: cost || undefined,
        vendor: vendor ? Number(vendor) : undefined,
      }),
    onSuccess: () => {
      setDate('')
      setDescription('')
      setCost('')
      setVendor('')
      onDone()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not schedule the service.'),
  })

  return (
    <Modal
      open={open}
      title="Schedule a service"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={create.isPending} disabled={!selectedAsset || !date} onClick={() => create.mutate()}>
            Schedule
          </Button>
        </>
      }
    >
      <Select label="Equipment" value={selectedAsset} onChange={(e) => setAsset(e.target.value)}>
        {assets.map((a) => (
          <option key={a.id} value={a.id}>{a.name}</option>
        ))}
      </Select>
      <Field label="Date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
      <TextArea label="What needs doing" rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
      <div className="grid grid-cols-2 gap-3">
        <Field label="Estimated cost (৳)" inputMode="decimal" value={cost} onChange={(e) => setCost(e.target.value)} />
        <Select label="Vendor" value={vendor} onChange={(e) => setVendor(e.target.value)}>
          <option value="">None yet</option>
          {(vendors?.results ?? []).map((v) => (
            <option key={v.id} value={v.id}>{v.name}</option>
          ))}
        </Select>
      </div>
    </Modal>
  )
}

function WasteDialog({
  open,
  buildingId,
  onClose,
  onDone,
}: {
  open: boolean
  buildingId: number | undefined
  onClose: () => void
  onDone: () => void
}) {
  const toast = useToast()
  const [when, setWhen] = useState('')
  const [recurring, setRecurring] = useState('weekly')

  const create = useMutation({
    mutationFn: () => api.createWasteSchedule({ building: buildingId!, schedule_time: when, recurring }),
    onSuccess: () => {
      setWhen('')
      onDone()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not schedule the collection.'),
  })

  return (
    <Modal
      open={open}
      title="Add a waste collection"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={create.isPending} disabled={!when} onClick={() => create.mutate()}>Add</Button>
        </>
      }
    >
      <Field label="First collection" type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} />
      <Select label="Repeats" value={recurring} onChange={(e) => setRecurring(e.target.value)}>
        <option value="">Once</option>
        <option value="daily">Daily</option>
        <option value="weekly">Weekly</option>
        <option value="biweekly">Every two weeks</option>
        <option value="monthly">Monthly</option>
      </Select>
    </Modal>
  )
}

function dayLabel(value: string): string {
  const date = new Date(value)
  const today = new Date()
  const tomorrow = new Date()
  tomorrow.setDate(today.getDate() + 1)
  if (date.toDateString() === today.toDateString()) return 'Today'
  if (date.toDateString() === tomorrow.toDateString()) return 'Tomorrow'
  return date.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' })
}

function timeLabel(value: string): string {
  return new Date(value).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
}

