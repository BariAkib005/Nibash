import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import { useCurrentResident } from '../lib/resident'
import { useToast } from '../lib/toast'
import DataTable, { Badge } from '../components/DataTable'
import type { Column } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import { Button, Card, Field, Modal, Select, Skeleton } from '../components/ui'
import Icon from '../components/Icon'
import type { IconName } from '../components/Icon'
import type { ApiParkingSlot, ApiVehicle, SlotStatus } from '../types'

const SLOT_STYLE: Record<SlotStatus, string> = {
  available: 'border-slate-200 bg-white text-slate-600 hover:border-brand-400',
  occupied: 'border-brand-700 bg-brand-700 text-white hover:bg-brand-800',
  reserved: 'border-brand-200 bg-brand-100 text-brand-900 hover:border-brand-400',
}

/**
 * The parking grid (spec §8.21): the building's rows × columns drawn as bays, coloured by status.
 * The committee assigns and releases vehicles by clicking a bay; residents register their own
 * vehicles and see where everything is parked.
 */
export default function Parking() {
  const { user } = useAuth()
  const { currentId } = useBuilding()
  const toast = useToast()
  const queryClient = useQueryClient()
  const canManage = user?.role === 'admin' || user?.role === 'committee'
  const [openSlot, setOpenSlot] = useState<ApiParkingSlot | null>(null)
  const [layoutOpen, setLayoutOpen] = useState(false)
  const [vehicleOpen, setVehicleOpen] = useState(false)

  const { data: layout, isLoading: layoutLoading } = useQuery({
    queryKey: ['parking-layout', currentId],
    queryFn: () => api.parkingLayout(currentId),
    enabled: Boolean(currentId),
  })
  const { data: slots, isLoading: slotsLoading } = useQuery({
    queryKey: ['parking-slots', currentId],
    queryFn: () => api.parkingSlots(currentId),
    enabled: Boolean(currentId),
  })
  const { data: vehicles, isLoading: vehiclesLoading } = useQuery({
    queryKey: ['vehicles', currentId],
    queryFn: () => api.vehicles({ building_id: currentId }),
    enabled: Boolean(currentId),
  })

  const slotList = useMemo(() => slots?.results ?? [], [slots])
  const vehicleList = useMemo(() => vehicles?.results ?? [], [vehicles])
  const bySlot = useMemo(() => new Map(vehicleList.filter((v) => v.parking_slot).map((v) => [v.parking_slot!, v])), [vehicleList])
  const counts = useMemo(() => {
    const tally = { available: 0, occupied: 0, reserved: 0 }
    for (const slot of slotList) tally[slot.status] += 1
    return tally
  }, [slotList])

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['parking-slots'] })
    queryClient.invalidateQueries({ queryKey: ['parking-layout'] })
    queryClient.invalidateQueries({ queryKey: ['vehicles'] })
  }

  const metrics: { label: string; value: number; hint: string; icon: IconName }[] = [
    { label: 'Total bays', value: slotList.length, hint: layout ? `${layout.rows} rows × ${layout.columns} columns` : '—', icon: 'overview' },
    { label: 'Occupied', value: counts.occupied, hint: 'A vehicle is parked', icon: 'car' },
    { label: 'Reserved', value: counts.reserved, hint: 'Held for the committee', icon: 'lock' },
    { label: 'Available', value: counts.available, hint: 'Ready to allocate', icon: 'box' },
  ]

  const columns: Column<ApiVehicle>[] = [
    {
      key: 'number',
      header: 'Vehicle',
      render: (v) => (
        <div>
          <p className="font-medium text-slate-900">{v.vehicle_number}</p>
          <p className="text-[11px] capitalize text-slate-500">{v.type}</p>
        </div>
      ),
    },
    {
      key: 'owner',
      header: 'Owner',
      render: (v) => (
        <span>
          {v.resident_name}
          {v.unit_number && <span className="text-slate-500"> · {v.unit_number}</span>}
        </span>
      ),
    },
    {
      key: 'slot',
      header: 'Bay',
      render: (v) =>
        v.slot_number ? (
          <span className="whitespace-nowrap">
            <Badge tone="blue">{v.slot_number}</Badge>
          </span>
        ) : (
          <span className="text-slate-400">Not assigned</span>
        ),
    },
  ]

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Parking"
        subtitle={canManage ? 'Every bay at a glance. Click a bay to assign, release or reserve it.' : 'Every bay at a glance, and the vehicles registered to your building.'}
        actions={
          <>
            {canManage && (
              <Button variant="secondary" onClick={() => setLayoutOpen(true)}>
                Generate layout
              </Button>
            )}
            <Button onClick={() => setVehicleOpen(true)}>
              <Icon name="plus" size={16} />
              Register vehicle
            </Button>
          </>
        }
      />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {metrics.map((metric) => (
          <Card key={metric.label}>
            <div className="metric-top">
              <p>{metric.label}</p>
              <Icon name={metric.icon} size={17} />
            </div>
            {slotsLoading ? <Skeleton className="mt-5 h-10 w-16" /> : <p className="metric-value">{metric.value}</p>}
            <p className="metric-hint">{metric.hint}</p>
          </Card>
        ))}
      </div>

      <Card>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="dashboard-section-title">Parking map</h2>
          <ul className="flex flex-wrap gap-3 text-[11px] text-slate-600">
            {(['available', 'occupied', 'reserved'] as const).map((status) => (
              <li key={status} className="flex items-center gap-1.5 capitalize">
                <span aria-hidden="true" className={`h-3 w-3 rounded-sm border ${SLOT_STYLE[status]}`} />
                {status}
              </li>
            ))}
          </ul>
        </div>
        <p className="mt-2 text-[11px] text-slate-500">
          {layout ? `Bays are numbered ${layout.prefix}{row}-{column}.` : 'Loading the layout…'}
        </p>

        {layoutLoading || slotsLoading ? (
          <Skeleton className="mt-6 h-56 w-full" />
        ) : layout && slotList.length ? (
          <ParkingGrid
            rows={layout.rows}
            columns={layout.columns}
            prefix={layout.prefix}
            slots={slotList}
            vehicles={bySlot}
            onSelect={setOpenSlot}
          />
        ) : (
          <div className="py-12 text-center">
            <Icon name="car" className="mx-auto text-slate-400" size={28} />
            <p className="mt-3 text-sm text-slate-600">No bays yet.</p>
            {canManage && (
              <Button className="mt-4" variant="secondary" onClick={() => setLayoutOpen(true)}>
                Generate the layout
              </Button>
            )}
          </div>
        )}
      </Card>

      <section className="space-y-3">
        <h2 className="dashboard-section-title">Registered vehicles</h2>
        <DataTable
          columns={columns}
          rows={vehicleList}
          rowKey={(v) => v.id}
          loading={vehiclesLoading}
          empty={{
            icon: 'car',
            title: 'No vehicles registered',
            body: 'Register a vehicle so the committee can allocate it a bay.',
            action: <Button onClick={() => setVehicleOpen(true)}>Register vehicle</Button>,
          }}
        />
      </section>

      {openSlot && (
        <SlotDialog
          slot={openSlot}
          parked={bySlot.get(openSlot.id) ?? null}
          unassigned={vehicleList.filter((v) => !v.parking_slot)}
          canManage={canManage}
          onClose={() => setOpenSlot(null)}
          onChanged={(message) => {
            toast.success(message)
            refresh()
            setOpenSlot(null)
          }}
        />
      )}
      <LayoutDialog
        open={layoutOpen}
        buildingId={currentId}
        initial={layout}
        onClose={() => setLayoutOpen(false)}
        onDone={(count) => {
          toast.success(`Layout saved — ${count} bays ready.`)
          refresh()
          setLayoutOpen(false)
        }}
      />
      <VehicleDialog
        open={vehicleOpen}
        buildingId={currentId}
        canManage={canManage}
        onClose={() => setVehicleOpen(false)}
        onDone={(vehicle) => {
          toast.success(`${vehicle.vehicle_number} registered.`)
          refresh()
          setVehicleOpen(false)
        }}
      />
    </div>
  )
}

/**
 * Draws the layout row by row. A bay missing from the database (the layout grew but was not
 * regenerated) shows as a dashed gap rather than disappearing, so the grid never misaligns. On a
 * phone the grid scrolls sideways inside its own container.
 */
function ParkingGrid({
  rows,
  columns,
  prefix,
  slots,
  vehicles,
  onSelect,
}: {
  rows: number
  columns: number
  prefix: string
  slots: ApiParkingSlot[]
  vehicles: Map<number, ApiVehicle>
  onSelect: (slot: ApiParkingSlot) => void
}) {
  const byNumber = new Map(slots.map((slot) => [slot.slot_number, slot]))
  return (
    <div className="mt-6 overflow-x-auto pb-2">
      <div className="grid min-w-max gap-2" style={{ gridTemplateColumns: `repeat(${columns}, minmax(84px, 1fr))` }}>
        {Array.from({ length: rows }, (_, r) =>
          Array.from({ length: columns }, (_, c) => {
            const number = `${prefix}${r + 1}-${String(c + 1).padStart(2, '0')}`
            const slot = byNumber.get(number)
            if (!slot) {
              return (
                <div key={number} className="grid h-16 place-items-center rounded-md border border-dashed border-slate-300 text-[10px] text-slate-400">
                  {number}
                </div>
              )
            }
            const vehicle = vehicles.get(slot.id)
            return (
              <button
                key={number}
                type="button"
                onClick={() => onSelect(slot)}
                className={`flex h-16 flex-col items-start justify-between rounded-md border px-2.5 py-2 text-left transition ${SLOT_STYLE[slot.status]}`}
                aria-label={`Bay ${slot.slot_number}, ${slot.status}${vehicle ? `, ${vehicle.vehicle_number}` : ''}`}
              >
                <span className="text-[11px] font-semibold tabular-nums">{slot.slot_number}</span>
                <span className="w-full truncate text-[10px] opacity-80">
                  {vehicle ? vehicle.vehicle_number : slot.status === 'reserved' ? 'Reserved' : 'Free'}
                </span>
              </button>
            )
          }),
        )}
      </div>
    </div>
  )
}

function SlotDialog({
  slot,
  parked,
  unassigned,
  canManage,
  onClose,
  onChanged,
}: {
  slot: ApiParkingSlot
  parked: ApiVehicle | null
  unassigned: ApiVehicle[]
  canManage: boolean
  onClose: () => void
  onChanged: (message: string) => void
}) {
  const toast = useToast()
  const [vehicleId, setVehicleId] = useState('')
  const fail = (error: unknown) => toast.error(error instanceof ApiError ? error.message : 'Could not update the bay.')

  const assign = useMutation({
    mutationFn: () => api.assignVehicle(Number(vehicleId), slot.id),
    onSuccess: (v) => onChanged(`${v.vehicle_number} parked in ${slot.slot_number}.`),
    onError: fail,
  })
  const release = useMutation({
    mutationFn: () => api.assignVehicle(parked!.id, null),
    onSuccess: (v) => onChanged(`${v.vehicle_number} released from ${slot.slot_number}.`),
    onError: fail,
  })
  const reserve = useMutation({
    mutationFn: (status: string) => api.updateSlot(slot.id, { status }),
    onSuccess: (s) => onChanged(`${s.slot_number} is now ${s.status}.`),
    onError: fail,
  })

  return (
    <Modal open title={`Bay ${slot.slot_number}`} onClose={onClose}>
      <div className="flex items-center gap-2">
        <Badge tone={slot.status === 'occupied' ? 'blue' : slot.status === 'reserved' ? 'amber' : 'green'}>
          {slot.status}
        </Badge>
        {parked && (
          <span className="text-sm text-slate-700">
            {parked.vehicle_number} · {parked.resident_name}
            {parked.unit_number && ` · ${parked.unit_number}`}
          </span>
        )}
      </div>

      {!canManage ? (
        <p className="text-sm text-slate-600">Bays are allocated by the committee.</p>
      ) : parked ? (
        <Button variant="secondary" loading={release.isPending} onClick={() => release.mutate()}>
          Release {parked.vehicle_number}
        </Button>
      ) : (
        <>
          <Select label="Park a vehicle here" value={vehicleId} onChange={(e) => setVehicleId(e.target.value)}>
            <option value="">Choose a vehicle…</option>
            {unassigned.map((v) => (
              <option key={v.id} value={v.id}>
                {v.vehicle_number} — {v.resident_name}
              </option>
            ))}
          </Select>
          <div className="flex flex-wrap gap-2">
            <Button disabled={!vehicleId} loading={assign.isPending} onClick={() => assign.mutate()}>
              Assign bay
            </Button>
            <Button
              variant="secondary"
              loading={reserve.isPending}
              onClick={() => reserve.mutate(slot.status === 'reserved' ? 'available' : 'reserved')}
            >
              {slot.status === 'reserved' ? 'Remove reservation' : 'Reserve bay'}
            </Button>
          </div>
          {!unassigned.length && (
            <p className="text-xs text-slate-500">Every registered vehicle already has a bay.</p>
          )}
        </>
      )}
    </Modal>
  )
}

function LayoutDialog({
  open,
  buildingId,
  initial,
  onClose,
  onDone,
}: {
  open: boolean
  buildingId: number | undefined
  initial: { rows: number; columns: number; prefix: string } | undefined
  onClose: () => void
  onDone: (count: number) => void
}) {
  const toast = useToast()
  const [rows, setRows] = useState(String(initial?.rows ?? 4))
  const [columns, setColumns] = useState(String(initial?.columns ?? 6))
  const [prefix, setPrefix] = useState(initial?.prefix ?? 'P')

  const generate = useMutation({
    mutationFn: () =>
      api.generateParkingLayout({ building_id: buildingId!, rows: Number(rows), columns: Number(columns), prefix: prefix.trim() || 'P' }),
    onSuccess: (result) => onDone(result.slots.length),
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not generate the layout.'),
  })

  const outOfRange = (value: string) => !(Number(value) >= 1 && Number(value) <= 12)

  return (
    <Modal
      open={open}
      title="Generate the parking layout"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={generate.isPending} disabled={outOfRange(rows) || outOfRange(columns)} onClick={() => generate.mutate()}>
            Generate
          </Button>
        </>
      }
    >
      <p className="text-sm text-slate-600">
        Existing bays keep their status — generating again only adds the bays that are missing.
      </p>
      <div className="grid grid-cols-3 gap-3">
        <Field label="Rows" type="number" min={1} max={12} value={rows} onChange={(e) => setRows(e.target.value)} error={outOfRange(rows) ? '1–12' : undefined} />
        <Field label="Columns" type="number" min={1} max={12} value={columns} onChange={(e) => setColumns(e.target.value)} error={outOfRange(columns) ? '1–12' : undefined} />
        <Field label="Prefix" value={prefix} maxLength={10} onChange={(e) => setPrefix(e.target.value)} />
      </div>
      <p className="text-xs text-slate-500">
        First bay: {prefix || 'P'}1-01 · {Number(rows) * Number(columns) || 0} bays in total
      </p>
    </Modal>
  )
}

function VehicleDialog({
  open,
  buildingId,
  canManage,
  onClose,
  onDone,
}: {
  open: boolean
  buildingId: number | undefined
  canManage: boolean
  onClose: () => void
  onDone: (vehicle: ApiVehicle) => void
}) {
  const toast = useToast()
  const { residentId } = useCurrentResident()
  const [number, setNumber] = useState('')
  const [type, setType] = useState('car')
  const [owner, setOwner] = useState('')

  const { data: residents } = useQuery({
    queryKey: ['residents', buildingId, 'vehicle-owner'],
    queryFn: () => api.residents({ building_id: buildingId }),
    enabled: open && canManage && Boolean(buildingId),
  })

  const create = useMutation({
    mutationFn: () =>
      api.createVehicle({
        building: buildingId,
        resident: canManage && owner ? Number(owner) : undefined,
        vehicle_number: number.trim(),
        type,
      }),
    onSuccess: (vehicle) => {
      setNumber('')
      onDone(vehicle)
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not register the vehicle.'),
  })

  const selectedOwner = owner || String(residentId ?? '')

  return (
    <Modal
      open={open}
      title="Register a vehicle"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={create.isPending} disabled={!number.trim() || (!canManage && !residentId)} onClick={() => create.mutate()}>
            Register
          </Button>
        </>
      }
    >
      <Field label="Registration number" placeholder="DHAKA METRO-GA 11-2345" value={number} onChange={(e) => setNumber(e.target.value)} />
      <Select label="Type" value={type} onChange={(e) => setType(e.target.value)}>
        <option value="car">Car</option>
        <option value="motorbike">Motorbike</option>
        <option value="bicycle">Bicycle</option>
        <option value="other">Other</option>
      </Select>
      {canManage && (
        <Select label="Owner" value={selectedOwner} onChange={(e) => setOwner(e.target.value)}>
          <option value="">Choose a resident…</option>
          {(residents?.results ?? []).map((r) => (
            <option key={r.id} value={r.id}>
              {r.resident_name}
              {r.unit_number ? ` · ${r.unit_number}` : ''}
            </option>
          ))}
        </Select>
      )}
      {!canManage && !residentId && (
        <p className="text-xs text-slate-500">Only residents can register a vehicle.</p>
      )}
    </Modal>
  )
}
