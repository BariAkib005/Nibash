import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { QRCodeCanvas } from 'qrcode.react'
import { api, ApiError } from '../lib/api'
import { useBuilding } from '../lib/building'
import { useCurrentResident } from '../lib/resident'
import { useToast } from '../lib/toast'
import DataTable from '../components/DataTable'
import type { Column } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import { Button, Field, Modal, Select } from '../components/ui'
import { formatDateTime } from '../lib/format'
import type { ApiAppointment } from '../types'

export default function Appointments() {
  const { currentId } = useBuilding()
  const toast = useToast()
  const queryClient = useQueryClient()

  const [page, setPage] = useState(1)
  const [formOpen, setFormOpen] = useState(false)
  const [pass, setPass] = useState<ApiAppointment | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['appointments', currentId, page],
    queryFn: () => api.appointments({ page, building_id: currentId }),
    enabled: Boolean(currentId),
  })

  const remove = useMutation({
    mutationFn: (id: number) => api.deleteAppointment(id),
    onSuccess: () => {
      toast.success('Appointment cancelled.')
      queryClient.invalidateQueries({ queryKey: ['appointments'] })
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not cancel.'),
  })

  const columns: Column<ApiAppointment>[] = [
    {
      key: 'visitor',
      header: 'Visitor',
      render: (appointment) => (
        <div>
          <p className="font-medium text-slate-900">{appointment.visitor_name}</p>
          <p className="text-xs text-slate-500">{appointment.visitor_phone || 'No phone'}</p>
        </div>
      ),
    },
    {
      key: 'host',
      header: 'Visiting',
      render: (appointment) => (
        <span>
          {appointment.resident_name}
          {appointment.unit_number && <span className="text-slate-500"> · {appointment.unit_number}</span>}
        </span>
      ),
    },
    {
      key: 'scheduled',
      header: 'Expected',
      render: (appointment) => formatDateTime(appointment.scheduled_time),
    },
    {
      key: 'actions',
      header: '',
      className: 'text-right',
      render: (appointment) => (
        <div className="flex justify-end gap-1">
          <Button variant="secondary" className="px-2.5 py-1 text-xs" onClick={() => setPass(appointment)}>
            Show pass
          </Button>
          <Button
            variant="ghost"
            className="px-2 py-1 text-xs text-red-700 hover:bg-red-50"
            disabled={remove.isPending}
            onClick={() => remove.mutate(appointment.id)}
          >
            Cancel
          </Button>
        </div>
      ),
    },
  ]

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <PageHeader
        title="Expected visitors"
        subtitle="Every appointment gets a QR pass the gate can scan"
        actions={<Button onClick={() => setFormOpen(true)}>Expect a visitor</Button>}
      />

      <DataTable
        columns={columns}
        rows={data?.results ?? []}
        rowKey={(appointment) => appointment.id}
        loading={isLoading}
        page={page}
        count={data?.count ?? 0}
        onPageChange={setPage}
        empty={{
          icon: '🎟️',
          title: 'No expected visitors',
          body: 'Add one and share the QR pass — the guard scans it at the gate and the visitor is checked in.',
          action: <Button onClick={() => setFormOpen(true)}>Expect a visitor</Button>,
        }}
      />

      <AppointmentForm
        open={formOpen}
        buildingId={currentId}
        onClose={() => setFormOpen(false)}
        onCreated={(created) => {
          queryClient.invalidateQueries({ queryKey: ['appointments'] })
          setPass(created)
        }}
      />

      {pass && <VisitorPass appointment={pass} onClose={() => setPass(null)} />}
    </div>
  )
}

/**
 * The pass itself. Printed or shown on a phone — either way what the gate reads is the token, so
 * the code is rendered large with the human-readable details underneath for the fallback case
 * where the camera will not focus and the guard types it in.
 */
function VisitorPass({ appointment, onClose }: { appointment: ApiAppointment; onClose: () => void }) {
  const toast = useToast()
  const token = appointment.qr_token ?? ''

  const share = async () => {
    const text = `Nibash gate pass for ${appointment.visitor_name} — code ${token}`
    try {
      if (navigator.share) {
        await navigator.share({ title: 'Nibash gate pass', text })
      } else {
        await navigator.clipboard.writeText(text)
        toast.success('Pass copied to the clipboard.')
      }
    } catch {
      // The user dismissed the share sheet; nothing to report.
    }
  }

  return (
    <Modal
      open
      title="Gate pass"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Close</Button>
          <Button variant="secondary" onClick={() => window.print()}>Print</Button>
          <Button onClick={share}>Share</Button>
        </>
      }
    >
      <div className="flex flex-col items-center gap-3 rounded-xl border border-slate-200 bg-white p-5">
        {token ? (
          <QRCodeCanvas value={token} size={200} level="M" includeMargin />
        ) : (
          <p className="text-sm text-slate-500">This appointment has no pass token.</p>
        )}

        <div className="text-center">
          <p className="text-lg font-semibold text-slate-900">{appointment.visitor_name}</p>
          <p className="text-sm text-slate-600">
            Visiting {appointment.resident_name}
            {appointment.unit_number && ` · ${appointment.unit_number}`}
          </p>
          <p className="mt-1 text-sm text-slate-600">{formatDateTime(appointment.scheduled_time)}</p>
        </div>

        {token && (
          <p className="select-all break-all rounded-md bg-slate-100 px-3 py-1.5 text-center font-mono text-xs text-slate-600">
            {token}
          </p>
        )}
      </div>

      <p className="text-xs text-slate-500">
        The pass is valid on the day it is scheduled for. A guard can scan it at any time that day.
      </p>
    </Modal>
  )
}

function AppointmentForm({ open, buildingId, onClose, onCreated }: {
  open: boolean
  buildingId: number | undefined
  onClose: () => void
  onCreated: (appointment: ApiAppointment) => void
}) {
  const toast = useToast()
  const { residentId } = useCurrentResident()

  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [when, setWhen] = useState('')
  const [hostId, setHostId] = useState('')

  const { data: residents } = useQuery({
    queryKey: ['residents', buildingId, 'picker'],
    queryFn: () => api.residents({ building_id: buildingId }),
    enabled: open && Boolean(buildingId),
  })

  // Default to the caller's own resident row: most appointments are your own visitors.
  const selectedHost = hostId || String(residentId ?? residents?.results[0]?.id ?? '')

  const create = useMutation({
    mutationFn: () =>
      api.createAppointment({
        resident: Number(selectedHost),
        visitor_name: name,
        visitor_phone: phone,
        scheduled_time: when,
      }),
    onSuccess: (appointment) => {
      toast.success('Pass created.')
      setName('')
      setPhone('')
      setWhen('')
      onCreated(appointment)
      onClose()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not create the pass.'),
  })

  return (
    <Modal
      open={open}
      title="Expect a visitor"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            loading={create.isPending}
            disabled={!name.trim() || !when || !selectedHost}
            onClick={() => create.mutate()}
          >
            Create pass
          </Button>
        </>
      }
    >
      <Field
        label="Visitor name"
        placeholder="Nafis Ahmed"
        value={name}
        onChange={(e) => setName(e.target.value)}
      />
      <Field
        label="Visitor phone"
        type="tel"
        placeholder="+8801700000000"
        value={phone}
        onChange={(e) => setPhone(e.target.value)}
      />
      <Field
        label="Expected at"
        type="datetime-local"
        value={when}
        onChange={(e) => setWhen(e.target.value)}
        hint="The pass works all day on this date."
      />
      <Select label="Visiting" value={selectedHost} onChange={(e) => setHostId(e.target.value)}>
        {(residents?.results ?? []).map((resident) => (
          <option key={resident.id} value={resident.id}>
            {resident.resident_name}{resident.unit_number ? ` · ${resident.unit_number}` : ''}
          </option>
        ))}
      </Select>
    </Modal>
  )
}
