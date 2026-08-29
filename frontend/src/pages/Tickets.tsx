import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useBuilding } from '../lib/building'
import { useToast } from '../lib/toast'
import PageHeader from '../components/PageHeader'
import { Badge } from '../components/DataTable'
import { Button, Card, EmptyState, Field, Modal, Select, Skeleton, TextArea } from '../components/ui'
import { formatDateTime } from '../lib/format'
import TicketDetail from './TicketDetail'
import type { ApiTicket, TicketPriority, TicketStatus } from '../types'

const COLUMNS: { status: TicketStatus; label: string; hint: string }[] = [
  { status: 'open', label: 'Open', hint: 'Raised, not started' },
  { status: 'in_progress', label: 'In progress', hint: 'Someone is on it' },
  { status: 'resolved', label: 'Resolved', hint: 'Fixed, awaiting sign-off' },
  { status: 'closed', label: 'Closed', hint: 'Done and signed off' },
]

const PRIORITY_TONE: Record<TicketPriority, 'slate' | 'amber' | 'violet'> = {
  low: 'slate',
  medium: 'amber',
  high: 'violet',
}

export default function Tickets() {
  const { currentId } = useBuilding()
  const toast = useToast()
  const queryClient = useQueryClient()

  const [dragging, setDragging] = useState<number | null>(null)
  const [over, setOver] = useState<TicketStatus | null>(null)
  const [openTicket, setOpenTicket] = useState<number | null>(null)
  const [composerOpen, setComposerOpen] = useState(false)
  const [priority, setPriority] = useState('')

  const queryKey = ['tickets', currentId, 'board']

  const { data, isLoading } = useQuery({
    queryKey,
    // The board shows everything at once, so it asks for a page big enough to hold the columns.
    queryFn: () => api.tickets({ building_id: currentId }),
    enabled: Boolean(currentId),
  })

  /**
   * Dragging a card is the mutation. It applies optimistically — the card must land in the new
   * column the instant it is dropped, or the drag feels broken — and snaps back if the server says no.
   */
  const move = useMutation({
    mutationFn: ({ id, status }: { id: number; status: TicketStatus }) => api.setTicketStatus(id, status),
    onMutate: async ({ id, status }) => {
      await queryClient.cancelQueries({ queryKey })
      const previous = queryClient.getQueryData(queryKey)

      queryClient.setQueryData(queryKey, (old: typeof data) =>
        old && {
          ...old,
          results: old.results.map((ticket) => (ticket.id === id ? { ...ticket, status } : ticket)),
        },
      )
      return { previous }
    },
    onError: (error, _vars, context) => {
      queryClient.setQueryData(queryKey, context?.previous)
      toast.error(error instanceof ApiError ? error.message : 'Could not move ticket.')
    },
    onSuccess: (ticket) => toast.success(`#${ticket.id} moved to ${ticket.status.replace('_', ' ')}.`),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['tickets'] }),
  })

  const tickets = (data?.results ?? []).filter(
    (ticket) => !priority || ticket.priority === priority,
  )

  const onDrop = (status: TicketStatus) => {
    setOver(null)
    if (dragging === null) return
    const ticket = tickets.find((row) => row.id === dragging)
    setDragging(null)
    if (ticket && ticket.status !== status) move.mutate({ id: ticket.id, status })
  }

  return (
    <div className="mx-auto max-w-7xl space-y-5">
      <PageHeader
        title="Maintenance"
        subtitle={
          data ? `${data.count} ticket${data.count === 1 ? '' : 's'} · drag a card to change its status`
               : 'Work orders raised by residents'
        }
        actions={
          <>
            <select
              aria-label="Filter by priority"
              value={priority}
              onChange={(e) => setPriority(e.target.value)}
              className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
            >
              <option value="">All priorities</option>
              <option value="high">High</option>
              <option value="medium">Medium</option>
              <option value="low">Low</option>
            </select>
            <Button onClick={() => setComposerOpen(true)}>Raise ticket</Button>
          </>
        }
      />

      {isLoading ? (
        <div className="grid gap-4 lg:grid-cols-4">
          {COLUMNS.map((column) => (
            <Card key={column.status}>
              <Skeleton className="h-4 w-24" />
              <Skeleton className="mt-3 h-20 w-full" />
              <Skeleton className="mt-2 h-20 w-full" />
            </Card>
          ))}
        </div>
      ) : !tickets.length ? (
        <EmptyState
          icon="🔧"
          title={priority ? `No ${priority}-priority tickets` : 'No tickets yet'}
          body={
            priority
              ? 'Try a different priority filter.'
              : 'When a resident reports a problem it lands here and is assigned automatically.'
          }
          action={
            priority
              ? <Button variant="secondary" onClick={() => setPriority('')}>Clear filter</Button>
              : <Button onClick={() => setComposerOpen(true)}>Raise ticket</Button>
          }
        />
      ) : (
        <div className="grid gap-4 lg:grid-cols-4">
          {COLUMNS.map((column) => {
            const cards = tickets.filter((ticket) => ticket.status === column.status)
            return (
              <section
                key={column.status}
                onDragOver={(e) => {
                  e.preventDefault()
                  setOver(column.status)
                }}
                onDragLeave={() => setOver((current) => (current === column.status ? null : current))}
                onDrop={() => onDrop(column.status)}
                className={`flex min-h-48 flex-col gap-2 rounded-xl border p-3 transition
                  ${over === column.status
                    ? 'border-brand-500 bg-brand-50/70 ring-2 ring-brand-200'
                    : 'border-slate-200 bg-slate-50/60'}`}
              >
                <header className="flex items-baseline justify-between">
                  <h2 className="text-sm font-semibold text-slate-900">{column.label}</h2>
                  <span className="rounded-full bg-white px-2 py-0.5 text-xs font-medium text-slate-600 ring-1 ring-slate-200">
                    {cards.length}
                  </span>
                </header>
                <p className="-mt-1 text-xs text-slate-500">{column.hint}</p>

                {cards.map((ticket) => (
                  <TicketCard
                    key={ticket.id}
                    ticket={ticket}
                    dragging={dragging === ticket.id}
                    onDragStart={() => setDragging(ticket.id)}
                    onDragEnd={() => setDragging(null)}
                    onOpen={() => setOpenTicket(ticket.id)}
                    onMove={(status) => move.mutate({ id: ticket.id, status })}
                  />
                ))}

                {!cards.length && (
                  <p className="rounded-lg border border-dashed border-slate-300 px-3 py-6 text-center text-xs text-slate-400">
                    Drop a ticket here
                  </p>
                )}
              </section>
            )
          })}
        </div>
      )}

      {openTicket !== null && (
        <TicketDetail ticketId={openTicket} onClose={() => setOpenTicket(null)} />
      )}

      <TicketComposer
        open={composerOpen}
        buildingId={currentId}
        onClose={() => setComposerOpen(false)}
        onCreated={() => queryClient.invalidateQueries({ queryKey: ['tickets'] })}
      />
    </div>
  )
}

/**
 * A board card. Draggable with the mouse, and every card also carries a status select — drag-and-drop
 * is unusable with a keyboard or on a phone, and this board has to work on both.
 */
function TicketCard({ ticket, dragging, onDragStart, onDragEnd, onOpen, onMove }: {
  ticket: ApiTicket
  dragging: boolean
  onDragStart: () => void
  onDragEnd: () => void
  onOpen: () => void
  onMove: (status: TicketStatus) => void
}) {
  return (
    <article
      draggable
      onDragStart={onDragStart}
      onDragEnd={onDragEnd}
      className={`cursor-grab rounded-lg border border-slate-200 bg-white p-3 shadow-sm transition
        hover:border-brand-300 active:cursor-grabbing ${dragging ? 'opacity-40' : ''}`}
    >
      <div className="flex items-start justify-between gap-2">
        <button
          type="button"
          onClick={onOpen}
          className="text-left text-sm font-medium text-slate-900 hover:text-brand-800 hover:underline"
        >
          #{ticket.id} {ticket.category}
        </button>
        <Badge tone={PRIORITY_TONE[ticket.priority]}>{ticket.priority}</Badge>
      </div>

      <p className="mt-1.5 line-clamp-2 text-xs text-slate-600">{ticket.description}</p>

      <dl className="mt-2 space-y-0.5 text-xs text-slate-500">
        <div className="flex justify-between gap-2">
          <dt>Raised by</dt>
          <dd className="truncate text-slate-700">{ticket.resident_name}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt>Assigned</dt>
          <dd className="truncate text-slate-700">{ticket.assigned_to_name ?? 'Unassigned'}</dd>
        </div>
      </dl>

      {ticket.images.length > 0 && (
        <p className="mt-2 text-xs text-slate-500">📎 {ticket.images.length} photo{ticket.images.length === 1 ? '' : 's'}</p>
      )}

      <select
        aria-label={`Move ticket ${ticket.id}`}
        value={ticket.status}
        onChange={(e) => onMove(e.target.value as TicketStatus)}
        className="mt-2 w-full rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-xs text-slate-700"
      >
        {COLUMNS.map((column) => (
          <option key={column.status} value={column.status}>{column.label}</option>
        ))}
      </select>

      <p className="mt-2 text-[11px] text-slate-400">{formatDateTime(ticket.created_at)}</p>
    </article>
  )
}

function TicketComposer({ open, buildingId, onClose, onCreated }: {
  open: boolean
  buildingId: number | undefined
  onClose: () => void
  onCreated: () => void
}) {
  const toast = useToast()
  const [residentId, setResidentId] = useState('')
  const [category, setCategory] = useState('Maintenance')
  const [priority, setPriority] = useState('medium')
  const [description, setDescription] = useState('')

  const { data: residents } = useQuery({
    queryKey: ['residents', buildingId, 'picker'],
    queryFn: () => api.residents({ building_id: buildingId }),
    enabled: open && Boolean(buildingId),
  })

  const create = useMutation({
    mutationFn: () =>
      api.createTicket({
        resident: Number(residentId || residents?.results[0]?.id),
        category,
        priority,
        description,
      }),
    onSuccess: (ticket) => {
      toast.success(
        ticket.assigned_to_name
          ? `Ticket #${ticket.id} raised and assigned to ${ticket.assigned_to_name}.`
          : `Ticket #${ticket.id} raised.`,
      )
      setDescription('')
      onCreated()
      onClose()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not raise ticket.'),
  })

  const selectedResident = residentId || String(residents?.results[0]?.id ?? '')

  return (
    <Modal
      open={open}
      title="Raise a maintenance ticket"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            loading={create.isPending}
            disabled={!description.trim() || !selectedResident}
            onClick={() => create.mutate()}
          >
            Raise ticket
          </Button>
        </>
      }
    >
      <Select label="Resident" value={selectedResident} onChange={(e) => setResidentId(e.target.value)}>
        {(residents?.results ?? []).map((resident) => (
          <option key={resident.id} value={resident.id}>
            {resident.resident_name}{resident.unit_number ? ` · ${resident.unit_number}` : ''}
          </option>
        ))}
      </Select>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          label="Category"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          hint="Matched against staff roles to pick an assignee."
        />
        <Select label="Priority" value={priority} onChange={(e) => setPriority(e.target.value)}>
          <option value="low">Low</option>
          <option value="medium">Medium</option>
          <option value="high">High</option>
        </Select>
      </div>

      <TextArea
        label="What is wrong?"
        placeholder="Kitchen tap drips constantly, worse at night."
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />
    </Modal>
  )
}
