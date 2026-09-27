import { useState } from 'react'
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
import { formatDate, formatDateTime } from '../lib/format'
import type { ApiAccessCard, ApiIntercomDevice, ApiIntercomLog } from '../types'

const CARD_TONE = { active: 'green', lost: 'amber', revoked: 'slate' } as const
const EVENT_TONE: Record<string, 'green' | 'amber' | 'blue' | 'slate' | 'red'> = {
  ring: 'blue',
  card_allowed: 'green',
  card_denied: 'red',
  remote_open: 'amber',
}

/**
 * Safety & access: the numbers to call in an emergency (everyone), the intercom's panels and event
 * log (guards and managers), and door access cards (managers issue and revoke; residents see their
 * own). Panels connected to the device gateway show as online, and a guard can release the door
 * from here; the page re-checks every few seconds so a panel going on- or offline shows up.
 */
export default function Safety() {
  const { user } = useAuth()
  const { currentId } = useBuilding()
  const { residentId } = useCurrentResident()
  const toast = useToast()
  const queryClient = useQueryClient()
  const canManage = user?.role === 'admin' || user?.role === 'committee'
  const seesIntercom = canManage || user?.role === 'guard'
  const [issuing, setIssuing] = useState(false)
  const [logPage, setLogPage] = useState(1)

  const { data: contacts, isLoading: contactsLoading } = useQuery({
    queryKey: ['emergency-contacts', currentId],
    queryFn: () => api.emergencyContacts(currentId),
    enabled: Boolean(currentId),
  })
  const { data: devices } = useQuery({
    queryKey: ['intercom-devices', currentId],
    queryFn: () => api.intercomDevices(currentId),
    enabled: seesIntercom && Boolean(currentId),
    refetchInterval: 10_000,
  })
  const { data: gateway } = useQuery({
    queryKey: ['device-gateway'],
    queryFn: () => api.deviceGateway(),
    enabled: canManage,
    staleTime: 5 * 60 * 1000,
  })
  const { data: logs, isLoading: logsLoading } = useQuery({
    queryKey: ['intercom-logs', currentId, logPage],
    queryFn: () => api.intercomLogs({ building_id: currentId, page: logPage }),
    enabled: seesIntercom && Boolean(currentId),
    refetchInterval: 10_000,
  })

  const openDoor = useMutation({
    mutationFn: (device: ApiIntercomDevice) => api.openDoor(device.id),
    onSuccess: (reply) => {
      toast.success(reply.detail)
      queryClient.invalidateQueries({ queryKey: ['intercom-logs'] })
    },
    onError: (error) => {
      toast.error(error instanceof ApiError ? error.message : 'Could not reach the panel.')
      queryClient.invalidateQueries({ queryKey: ['intercom-devices'] })
    },
  })
  const { data: cards, isLoading: cardsLoading } = useQuery({
    queryKey: ['access-cards', currentId],
    queryFn: () => api.accessCards({ building_id: currentId }),
    enabled: Boolean(currentId),
  })

  const setCardStatus = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) => api.updateAccessCard(id, status),
    onSuccess: (card) => {
      toast.success(`Card ${card.card_number} is now ${card.status}.`)
      queryClient.invalidateQueries({ queryKey: ['access-cards'] })
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not update the card.'),
  })

  const ownCards = (cards?.results ?? []).filter((c) => c.resident === residentId)

  const logColumns: Column<ApiIntercomLog>[] = [
    { key: 'event', header: 'Event', render: (l) => <Badge tone={EVENT_TONE[l.event_type] ?? 'slate'}>{l.event_type.replace(/_/g, ' ')}</Badge> },
    { key: 'device', header: 'Panel', render: (l) => l.device_name },
    { key: 'details', header: 'Details', render: (l) => l.details ?? <span className="text-slate-400">—</span> },
    { key: 'when', header: 'When', render: (l) => formatDateTime(l.timestamp) },
  ]

  const cardColumns: Column<ApiAccessCard>[] = [
    { key: 'number', header: 'Card', render: (c) => <span className="font-medium tabular-nums text-slate-900">{c.card_number}</span> },
    {
      key: 'holder',
      header: 'Holder',
      render: (c) => (
        <span>
          {c.resident_name}
          {c.unit_number && <span className="text-slate-500"> · {c.unit_number}</span>}
        </span>
      ),
    },
    { key: 'issued', header: 'Issued', render: (c) => formatDate(c.issued_at) },
    {
      key: 'status',
      header: 'Status',
      render: (c) => (
        <select
          aria-label={`Status of card ${c.card_number}`}
          value={c.status}
          disabled={setCardStatus.isPending}
          onChange={(e) => setCardStatus.mutate({ id: c.id, status: e.target.value })}
          className="rounded-md border border-slate-300 bg-white px-2 py-1 text-xs"
        >
          <option value="active">Active</option>
          <option value="lost">Lost</option>
          <option value="revoked">Revoked</option>
        </select>
      ),
    },
  ]

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Safety & access"
        subtitle="Who to call in an emergency, the intercom, and door access cards."
        actions={
          canManage && (
            <Button onClick={() => setIssuing(true)}>
              <Icon name="card" size={16} />
              Issue a card
            </Button>
          )
        }
      />

      <section className="space-y-3">
        <h2 className="dashboard-section-title">Emergency numbers</h2>
        {contactsLoading ? (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-28 w-full" />)}</div>
        ) : contacts?.results.length ? (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {contacts.results.map((contact) => (
              <a
                key={contact.id}
                href={`tel:${contact.phone.replace(/\s/g, '')}`}
                className="ui-card block transition hover:border-brand-400"
              >
                <div className="metric-top">
                  <p className="capitalize">{contact.type}</p>
                  <Icon name="phone" size={17} />
                </div>
                <p className="mt-4 text-sm font-semibold text-slate-900">{contact.name}</p>
                <p className="mt-1 text-lg font-semibold tabular-nums tracking-tight text-brand-800">{contact.phone}</p>
              </a>
            ))}
          </div>
        ) : (
          <Card><p className="py-4 text-center text-sm text-slate-500">No emergency numbers on file yet.</p></Card>
        )}
      </section>

      {!canManage && residentId !== undefined && (
        <Card>
          <h2 className="dashboard-section-title">Your access card</h2>
          {cardsLoading ? (
            <Skeleton className="mt-4 h-12 w-full" />
          ) : ownCards.length ? (
            <ul className="mt-4 divide-y divide-slate-100">
              {ownCards.map((c) => (
                <li key={c.id} className="flex items-center justify-between gap-3 py-3">
                  <span className="flex items-center gap-3">
                    <Icon name="card" size={18} className="text-brand-700" />
                    <span className="text-xs font-medium tabular-nums text-slate-800">{c.card_number}</span>
                  </span>
                  <Badge tone={CARD_TONE[c.status]}>{c.status}</Badge>
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-3 text-sm text-slate-500">No card issued to you yet — ask the committee.</p>
          )}
          <p className="mt-3 text-[11px] text-slate-500">Lost your card? Tell the committee so it can be switched off.</p>
        </Card>
      )}

      {seesIntercom && (
        <section className="space-y-3">
          <div className="flex flex-wrap items-end justify-between gap-2">
            <div>
              <h2 className="dashboard-section-title">Intercom</h2>
              <p className="mt-1 text-[11px] text-slate-500">
                {devices?.results.length
                  ? 'Panels connect to the building’s device gateway; an online panel can be opened from here.'
                  : 'No panels registered.'}
                {gateway?.enabled && ` Point panels at this server, port ${gateway.port}.`}
              </p>
            </div>
          </div>
          {Boolean(devices?.results.length) && (
            <div className="grid gap-3 md:grid-cols-2">
              {devices!.results.map((device) => (
                <Card key={device.id} className="flex items-center gap-3">
                  <span
                    className={`grid h-10 w-10 shrink-0 place-items-center rounded-full ${
                      device.online ? 'bg-brand-100 text-brand-700' : 'bg-slate-100 text-slate-400'
                    }`}
                  >
                    <Icon name={device.online ? 'phone' : 'lock'} size={18} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="flex flex-wrap items-center gap-2 text-sm font-semibold text-slate-900">
                      <span className="truncate">{device.device_name}</span>
                      <Badge tone={device.online ? 'green' : 'slate'}>{device.online ? 'online' : 'offline'}</Badge>
                    </p>
                    <p className="mt-0.5 truncate text-[11px] text-slate-500">
                      {device.ip_address}
                      {device.online && device.last_seen ? ` · last heard ${formatDateTime(device.last_seen)}` : ''}
                    </p>
                  </div>
                  <Button
                    variant={device.online ? 'primary' : 'secondary'}
                    className="shrink-0 px-3 py-2 text-xs"
                    disabled={!device.online || openDoor.isPending}
                    onClick={() => openDoor.mutate(device)}
                  >
                    <Icon name="lock" size={14} />
                    Open door
                  </Button>
                </Card>
              ))}
            </div>
          )}
          <DataTable
            columns={logColumns}
            rows={logs?.results ?? []}
            rowKey={(l) => l.id}
            loading={logsLoading}
            page={logPage}
            count={logs?.count ?? 0}
            onPageChange={setLogPage}
            empty={{
              icon: 'phone',
              title: 'No intercom activity',
              body: 'Rings and door releases reported by the panels appear here as they happen.',
            }}
          />
        </section>
      )}

      {canManage && (
        <section className="space-y-3">
          <h2 className="dashboard-section-title">Access cards</h2>
          <DataTable
            columns={cardColumns}
            rows={cards?.results ?? []}
            rowKey={(c) => c.id}
            loading={cardsLoading}
            empty={{
              icon: 'card',
              title: 'No cards issued',
              body: 'Issue a door card to a resident; mark it lost or revoked the moment it goes missing.',
              action: <Button onClick={() => setIssuing(true)}>Issue a card</Button>,
            }}
          />
        </section>
      )}

      <IssueCardDialog
        open={issuing}
        buildingId={currentId}
        onClose={() => setIssuing(false)}
        onDone={(card) => {
          toast.success(`Card ${card.card_number} issued to ${card.resident_name}.`)
          queryClient.invalidateQueries({ queryKey: ['access-cards'] })
          setIssuing(false)
        }}
      />
    </div>
  )
}

function IssueCardDialog({
  open,
  buildingId,
  onClose,
  onDone,
}: {
  open: boolean
  buildingId: number | undefined
  onClose: () => void
  onDone: (card: ApiAccessCard) => void
}) {
  const toast = useToast()
  const [resident, setResident] = useState('')
  const [number, setNumber] = useState('')

  const { data: residents } = useQuery({
    queryKey: ['residents', buildingId, 'card-holder'],
    queryFn: () => api.residents({ building_id: buildingId }),
    enabled: open && Boolean(buildingId),
  })
  const selected = resident || String(residents?.results[0]?.id ?? '')

  const issue = useMutation({
    mutationFn: () => api.createAccessCard({ resident: Number(selected), card_number: number.trim() }),
    onSuccess: (card) => {
      setNumber('')
      onDone(card)
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not issue the card.'),
  })

  return (
    <Modal
      open={open}
      title="Issue an access card"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={issue.isPending} disabled={!selected || !number.trim()} onClick={() => issue.mutate()}>Issue card</Button>
        </>
      }
    >
      <Select label="Resident" value={selected} onChange={(e) => setResident(e.target.value)}>
        {(residents?.results ?? []).map((r) => (
          <option key={r.id} value={r.id}>
            {r.resident_name}
            {r.unit_number ? ` · ${r.unit_number}` : ''}
          </option>
        ))}
      </Select>
      <Field label="Card number" placeholder="GLH-AC-0002" value={number} onChange={(e) => setNumber(e.target.value)} />
    </Modal>
  )
}
