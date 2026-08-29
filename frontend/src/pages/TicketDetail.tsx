import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useBuilding } from '../lib/building'
import { useToast } from '../lib/toast'
import { Badge } from '../components/DataTable'
import { Button, Modal, Select, Skeleton } from '../components/ui'
import { formatDateTime } from '../lib/format'
import type { TicketStatus } from '../types'

const FLOW: TicketStatus[] = ['open', 'in_progress', 'resolved', 'closed']

const STATUS_LABEL: Record<TicketStatus, string> = {
  open: 'Open',
  in_progress: 'In progress',
  resolved: 'Resolved',
  closed: 'Closed',
}

/**
 * Ticket detail: photos, assignment and a status timeline.
 *
 * <p>Opened as a modal from the board rather than as its own route, so dragging a card and opening
 * one share the same screen and the board never loses its scroll position.
 */
export default function TicketDetail({ ticketId, onClose }: { ticketId: number; onClose: () => void }) {
  const { currentId } = useBuilding()
  const toast = useToast()
  const queryClient = useQueryClient()
  const fileInput = useRef<HTMLInputElement>(null)

  const [lightbox, setLightbox] = useState<string | null>(null)
  const [dropping, setDropping] = useState(false)

  const { data: ticket, isLoading } = useQuery({
    queryKey: ['ticket', ticketId],
    queryFn: () => api.ticket(ticketId),
  })

  const { data: staff } = useQuery({
    queryKey: ['staff', currentId, 'picker'],
    queryFn: () => api.staff({ building_id: currentId }),
    enabled: Boolean(currentId),
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['ticket', ticketId] })
    queryClient.invalidateQueries({ queryKey: ['tickets'] })
  }

  const assign = useMutation({
    mutationFn: (staffId: string) =>
      api.updateTicket(ticketId, { assigned_to: staffId === '' ? null : Number(staffId) }),
    onSuccess: (updated) => {
      toast.success(
        updated.assigned_to_name ? `Assigned to ${updated.assigned_to_name}.` : 'Assignment cleared.',
      )
      invalidate()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not assign.'),
  })

  const changeStatus = useMutation({
    mutationFn: (status: TicketStatus) => api.setTicketStatus(ticketId, status),
    onSuccess: (updated) => {
      toast.success(`Moved to ${STATUS_LABEL[updated.status].toLowerCase()}.`)
      invalidate()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not move ticket.'),
  })

  const addPhoto = useMutation({
    mutationFn: (file: File) => api.addTicketImage(ticketId, file),
    onSuccess: () => {
      toast.success('Photo attached.')
      invalidate()
    },
    // The server caps photos at 5 MB and says so; show its words, not ours.
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Upload failed.'),
  })

  const onFiles = (files: FileList | null) => {
    const file = files?.[0]
    if (file) addPhoto.mutate(file)
  }

  return (
    <Modal
      open
      title={ticket ? `Ticket #${ticket.id} · ${ticket.category}` : 'Ticket'}
      onClose={onClose}
      footer={<Button variant="secondary" onClick={onClose}>Close</Button>}
    >
      {isLoading || !ticket ? (
        <>
          <Skeleton className="h-4 w-48" />
          <Skeleton className="mt-3 h-24 w-full" />
        </>
      ) : (
        <>
          <p className="text-sm text-slate-700">{ticket.description}</p>

          <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
            <div>
              <dt className="text-xs uppercase tracking-wide text-slate-500">Raised by</dt>
              <dd className="font-medium text-slate-900">
                {ticket.resident_name}
                {ticket.unit_number && <span className="text-slate-500"> · {ticket.unit_number}</span>}
              </dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-slate-500">Priority</dt>
              <dd><Badge tone={ticket.priority === 'high' ? 'violet' : ticket.priority === 'medium' ? 'amber' : 'slate'}>{ticket.priority}</Badge></dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-slate-500">Raised</dt>
              <dd className="text-slate-700">{formatDateTime(ticket.created_at)}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-slate-500">Closed</dt>
              <dd className="text-slate-700">{formatDateTime(ticket.closed_at)}</dd>
            </div>
          </dl>

          {/* Status timeline — where the ticket is, and what comes next. */}
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Progress</p>
            <ol className="mt-2 flex items-center">
              {FLOW.map((step, index) => {
                const reached = FLOW.indexOf(ticket.status) >= index
                return (
                  <li key={step} className="flex flex-1 items-center last:flex-none">
                    <button
                      type="button"
                      onClick={() => changeStatus.mutate(step)}
                      disabled={changeStatus.isPending}
                      className={`grid h-7 w-7 shrink-0 place-items-center rounded-full text-xs font-bold transition
                        ${reached ? 'bg-brand-700 text-white' : 'bg-slate-200 text-slate-500 hover:bg-slate-300'}`}
                      title={`Move to ${STATUS_LABEL[step]}`}
                    >
                      {index + 1}
                    </button>
                    {index < FLOW.length - 1 && (
                      <span
                        aria-hidden="true"
                        className={`mx-1 h-0.5 flex-1 rounded ${
                          FLOW.indexOf(ticket.status) > index ? 'bg-brand-600' : 'bg-slate-200'
                        }`}
                      />
                    )}
                  </li>
                )
              })}
            </ol>
            <p className="mt-1.5 text-xs text-slate-500">
              Currently <span className="font-medium text-slate-700">{STATUS_LABEL[ticket.status]}</span>.
              Tap a step to move it.
            </p>
          </div>

          <Select
            label="Assigned to"
            value={ticket.assigned_to ? String(ticket.assigned_to) : ''}
            disabled={assign.isPending}
            onChange={(e) => assign.mutate(e.target.value)}
          >
            <option value="">Unassigned</option>
            {(staff?.results ?? []).map((member) => (
              <option key={member.id} value={member.id}>
                {member.name} · {member.role}
              </option>
            ))}
          </Select>

          {/* Photos: drag a file anywhere onto the panel, or use the button. */}
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Photos</p>
            <div
              onDragOver={(e) => {
                e.preventDefault()
                setDropping(true)
              }}
              onDragLeave={() => setDropping(false)}
              onDrop={(e) => {
                e.preventDefault()
                setDropping(false)
                onFiles(e.dataTransfer.files)
              }}
              className={`mt-2 rounded-lg border-2 border-dashed p-3 transition
                ${dropping ? 'border-brand-500 bg-brand-50' : 'border-slate-300 bg-slate-50/60'}`}
            >
              {ticket.images.length > 0 && (
                <div className="mb-3 flex flex-wrap gap-2">
                  {ticket.images.map((image) => (
                    <button
                      key={image.id}
                      type="button"
                      onClick={() => setLightbox(`/media/${image.image_path}`)}
                      className="overflow-hidden rounded-md border border-slate-200 transition hover:ring-2 hover:ring-brand-400"
                    >
                      <img
                        src={`/media/${image.image_path}`}
                        alt="Ticket attachment"
                        className="h-20 w-20 object-cover"
                      />
                    </button>
                  ))}
                </div>
              )}

              <div className="flex items-center justify-between gap-3">
                <p className="text-xs text-slate-500">
                  Drop an image here, up to 5 MB.
                </p>
                <Button
                  variant="secondary"
                  className="px-3 py-1.5 text-xs"
                  loading={addPhoto.isPending}
                  onClick={() => fileInput.current?.click()}
                >
                  Add photo
                </Button>
              </div>

              <input
                ref={fileInput}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={(e) => {
                  onFiles(e.target.files)
                  e.target.value = ''
                }}
              />
            </div>
          </div>

          {lightbox && (
            <button
              type="button"
              aria-label="Close photo"
              onClick={() => setLightbox(null)}
              className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/80 p-6"
            >
              <img src={lightbox} alt="Ticket attachment, full size" className="max-h-full max-w-full rounded-lg" />
            </button>
          )}
        </>
      )}
    </Modal>
  )
}
