import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useBuilding } from '../lib/building'
import { useToast } from '../lib/toast'
import DataTable, { Badge } from '../components/DataTable'
import type { Column } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import { Button, Card, Field, Modal, Select, Skeleton } from '../components/ui'
import Icon from '../components/Icon'
import type { IconName } from '../components/Icon'
import { formatDate, taka } from '../lib/format'
import type { ApiUtilityBill, ApiUtilityMeter } from '../types'

const STATUS_TONE = { pending: 'amber', billed: 'blue', paid: 'green' } as const
const METER_LABEL: Record<ApiUtilityMeter['type'], string> = { electricity: 'Electricity', water: 'Water', gas: 'Gas' }

/**
 * Utilities (spec §8.15): meters per unit and their bills. A bill moves pending → billed when the
 * monthly invoice batch picks it up, then → paid with that invoice, so each reading is charged once.
 */
export default function Utilities() {
  const { currentId } = useBuilding()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const [reading, setReading] = useState<ApiUtilityBill | null>(null)
  const [generateOpen, setGenerateOpen] = useState(false)
  const [meterOpen, setMeterOpen] = useState(false)

  const { data: bills, isLoading } = useQuery({
    queryKey: ['utility-bills', currentId, page, status],
    queryFn: () => api.utilityBills({ building_id: currentId, page, status: status || undefined }),
    enabled: Boolean(currentId),
  })
  const { data: pending } = useQuery({
    queryKey: ['utility-bills', currentId, 'pending-summary'],
    queryFn: () => api.utilityBills({ building_id: currentId, status: 'pending' }),
    enabled: Boolean(currentId),
  })
  const { data: meters, isLoading: metersLoading } = useQuery({
    queryKey: ['utility-meters', currentId],
    queryFn: () => api.utilityMeters({ building_id: currentId }),
    enabled: Boolean(currentId),
  })

  const pendingTotal = (pending?.results ?? []).reduce((sum, b) => sum + Number(b.amount), 0)
  const unitsMetered = new Set((meters?.results ?? []).map((m) => m.unit)).size
  const metrics: { label: string; value: string; hint: string; icon: IconName }[] = [
    { label: 'Meters', value: String(meters?.count ?? '—'), hint: `Across ${unitsMetered} unit${unitsMetered === 1 ? '' : 's'}`, icon: 'bolt' },
    { label: 'Awaiting billing', value: String(pending?.count ?? '—'), hint: 'Pending readings', icon: 'receipt' },
    { label: 'Pending amount', value: taka(pendingTotal), hint: 'Added to the next invoice run', icon: 'wallet' },
  ]

  const columns: Column<ApiUtilityBill>[] = [
    {
      key: 'unit',
      header: 'Unit',
      render: (b) => (
        <div>
          <p className="font-medium text-slate-900">{b.unit_number}</p>
          <p className="text-[11px] text-slate-500">
            {METER_LABEL[b.meter_type]} · {b.meter_number}
          </p>
        </div>
      ),
    },
    { key: 'date', header: 'Reading date', render: (b) => formatDate(b.reading_date) },
    { key: 'reading', header: 'Reading', className: 'text-right tabular-nums', render: (b) => Number(b.reading_value).toLocaleString('en-BD') },
    { key: 'amount', header: 'Amount', className: 'text-right', render: (b) => taka(b.amount) },
    { key: 'status', header: 'Status', render: (b) => <Badge tone={STATUS_TONE[b.status]}>{b.status}</Badge> },
    {
      key: 'actions',
      header: '',
      className: 'text-right',
      render: (b) =>
        b.status === 'pending' ? (
          <Button variant="secondary" className="px-2.5 py-1 text-xs" onClick={() => setReading(b)}>
            Enter reading
          </Button>
        ) : null,
    },
  ]

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Utilities"
        subtitle="Meter readings per unit. Pending bills join the next monthly invoice when utilities are included."
        actions={
          <>
            <Button variant="secondary" onClick={() => setMeterOpen(true)}>Add meter</Button>
            <Button onClick={() => setGenerateOpen(true)}>Open a month</Button>
          </>
        }
      />

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
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
        <div className="flex flex-wrap items-end justify-between gap-3">
          <h2 className="dashboard-section-title">Bills</h2>
          <div className="w-full max-w-[200px]">
            <Select
              label="Status"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value)
                setPage(1)
              }}
            >
              <option value="">All</option>
              <option value="pending">Pending</option>
              <option value="billed">On an invoice</option>
              <option value="paid">Paid</option>
            </Select>
          </div>
        </div>
        <DataTable
          columns={columns}
          rows={bills?.results ?? []}
          rowKey={(b) => b.id}
          loading={isLoading}
          page={page}
          count={bills?.count ?? 0}
          onPageChange={setPage}
          empty={{
            icon: 'bolt',
            title: status ? `No ${status} bills` : 'No utility bills yet',
            body: 'Open a month to create a reading for every meter, then enter the figures as they come in.',
            action: <Button onClick={() => setGenerateOpen(true)}>Open a month</Button>,
          }}
        />
      </section>

      <Card>
        <h2 className="dashboard-section-title">Meters</h2>
        <p className="mt-2 text-[11px] text-slate-500">One meter per unit and utility.</p>
        {metersLoading ? (
          <Skeleton className="mt-5 h-24 w-full" />
        ) : meters?.results.length ? (
          <ul className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
            {meters.results.map((m) => (
              <li key={m.id} className="flex items-center gap-3 rounded-md border border-slate-200 px-3 py-2.5">
                <Icon name="bolt" size={16} className="shrink-0 text-brand-700" />
                <div className="min-w-0">
                  <p className="text-xs font-medium text-slate-800">
                    {m.unit_number} · {METER_LABEL[m.type]}
                  </p>
                  <p className="truncate text-[10px] text-slate-500">{m.meter_number}</p>
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <p className="py-8 text-center text-sm text-slate-500">No meters yet.</p>
        )}
      </Card>

      {reading && (
        <ReadingDialog
          bill={reading}
          onClose={() => setReading(null)}
          onDone={() => {
            toast.success(`Reading saved for ${reading.unit_number}.`)
            queryClient.invalidateQueries({ queryKey: ['utility-bills'] })
            setReading(null)
          }}
        />
      )}
      <GenerateDialog
        open={generateOpen}
        buildingId={currentId}
        onClose={() => setGenerateOpen(false)}
        onDone={(created) => {
          toast.success(created ? `${created} reading${created === 1 ? '' : 's'} opened.` : 'That month is already open — nothing new created.')
          queryClient.invalidateQueries({ queryKey: ['utility-bills'] })
          setGenerateOpen(false)
        }}
      />
      <MeterDialog
        open={meterOpen}
        buildingId={currentId}
        onClose={() => setMeterOpen(false)}
        onDone={(meter) => {
          toast.success(`${METER_LABEL[meter.type]} meter added to ${meter.unit_number}.`)
          queryClient.invalidateQueries({ queryKey: ['utility-meters'] })
          setMeterOpen(false)
        }}
      />
    </div>
  )
}

function ReadingDialog({ bill, onClose, onDone }: { bill: ApiUtilityBill; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const [value, setValue] = useState(String(Number(bill.reading_value) || ''))
  const [amount, setAmount] = useState(String(Number(bill.amount) || ''))
  const invalid = (v: string) => v === '' || Number.isNaN(Number(v)) || Number(v) < 0

  const save = useMutation({
    mutationFn: () => api.updateUtilityBill(bill.id, { reading_value: value, amount }),
    onSuccess: onDone,
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not save the reading.'),
  })

  return (
    <Modal
      open
      title={`${bill.unit_number} · ${METER_LABEL[bill.meter_type]}`}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={save.isPending} disabled={invalid(value) || invalid(amount)} onClick={() => save.mutate()}>
            Save reading
          </Button>
        </>
      }
    >
      <p className="text-sm text-slate-600">
        Meter {bill.meter_number}, reading dated {formatDate(bill.reading_date)}.
      </p>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Meter reading" inputMode="decimal" value={value} onChange={(e) => setValue(e.target.value)} error={value && invalid(value) ? 'Zero or more' : undefined} />
        <Field label="Amount (৳)" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} error={amount && invalid(amount) ? 'Zero or more' : undefined} />
      </div>
    </Modal>
  )
}

function GenerateDialog({
  open,
  buildingId,
  onClose,
  onDone,
}: {
  open: boolean
  buildingId: number | undefined
  onClose: () => void
  onDone: (created: number) => void
}) {
  const toast = useToast()
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7))
  const generate = useMutation({
    mutationFn: () => api.generateUtilityBills(buildingId!, month),
    onSuccess: (result) => onDone(result.created_bills.length),
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not open the month.'),
  })

  return (
    <Modal
      open={open}
      title="Open a billing month"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={generate.isPending} disabled={!month} onClick={() => generate.mutate()}>Open month</Button>
        </>
      }
    >
      <p className="text-sm text-slate-600">
        Creates one pending reading for every meter, dated the 28th. Opening the same month twice creates nothing new.
      </p>
      <Field label="Month" type="month" value={month} onChange={(e) => setMonth(e.target.value)} />
    </Modal>
  )
}

function MeterDialog({
  open,
  buildingId,
  onClose,
  onDone,
}: {
  open: boolean
  buildingId: number | undefined
  onClose: () => void
  onDone: (meter: ApiUtilityMeter) => void
}) {
  const toast = useToast()
  const [unit, setUnit] = useState('')
  const [type, setType] = useState('electricity')
  const [number, setNumber] = useState('')

  const { data: units } = useQuery({
    queryKey: ['units', buildingId, 'meter-picker'],
    queryFn: () => api.units({ building_id: buildingId }),
    enabled: open && Boolean(buildingId),
  })
  const selectedUnit = unit || String(units?.results[0]?.id ?? '')

  const create = useMutation({
    mutationFn: () => api.createUtilityMeter({ unit: Number(selectedUnit), type, meter_number: number.trim() }),
    onSuccess: (meter) => {
      setNumber('')
      onDone(meter)
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not add the meter.'),
  })

  return (
    <Modal
      open={open}
      title="Add a meter"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={create.isPending} disabled={!selectedUnit || !number.trim()} onClick={() => create.mutate()}>Add meter</Button>
        </>
      }
    >
      <Select label="Unit" value={selectedUnit} onChange={(e) => setUnit(e.target.value)}>
        {(units?.results ?? []).map((u) => (
          <option key={u.id} value={u.id}>{u.unit_number}</option>
        ))}
      </Select>
      <Select label="Utility" value={type} onChange={(e) => setType(e.target.value)}>
        <option value="electricity">Electricity</option>
        <option value="water">Water</option>
        <option value="gas">Gas</option>
      </Select>
      <Field label="Meter number" placeholder="GLH-E-03A" value={number} onChange={(e) => setNumber(e.target.value)} />
    </Modal>
  )
}
