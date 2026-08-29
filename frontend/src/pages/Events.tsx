import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import { useCurrentResident } from '../lib/resident'
import { useToast } from '../lib/toast'
import PageHeader from '../components/PageHeader'
import { Button, Card, EmptyState, Field, Modal, Skeleton, TextArea } from '../components/ui'
import { formatDateTime } from '../lib/format'
import type { ApiEvent, RsvpStatus } from '../types'

const RSVP_OPTIONS: { value: RsvpStatus; label: string; icon: string }[] = [
  { value: 'going', label: 'Going', icon: '✓' },
  { value: 'interested', label: 'Interested', icon: '★' },
  { value: 'not_going', label: "Can't make it", icon: '✕' },
]

export default function Events() {
  const { currentId } = useBuilding()
  const { user } = useAuth()
  const { residentId } = useCurrentResident()
  const toast = useToast()
  const queryClient = useQueryClient()

  const [composerOpen, setComposerOpen] = useState(false)

  const canManage = user?.role === 'admin' || user?.role === 'committee'

  const { data, isLoading } = useQuery({
    queryKey: ['events', currentId],
    queryFn: () => api.events({ building_id: currentId }),
    enabled: Boolean(currentId),
  })

  const events = data?.results ?? []

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <PageHeader
        title="Events"
        subtitle="What is happening in the building, and who is coming"
        actions={canManage ? <Button onClick={() => setComposerOpen(true)}>Create event</Button> : undefined}
      />

      {isLoading ? (
        <div className="space-y-3">
          {[0, 1].map((i) => (
            <Card key={i}>
              <Skeleton className="h-5 w-1/2" />
              <Skeleton className="mt-3 h-16 w-full" />
            </Card>
          ))}
        </div>
      ) : !events.length ? (
        <EmptyState
          icon="🎉"
          title="Nothing planned"
          body="Create an event and residents can RSVP from here."
          action={canManage ? <Button onClick={() => setComposerOpen(true)}>Create event</Button> : undefined}
        />
      ) : (
        <ul className="space-y-4">
          {events.map((event) => (
            <li key={event.id}>
              <EventCard event={event} canRsvp={Boolean(residentId)} />
            </li>
          ))}
        </ul>
      )}

      <EventComposer
        open={composerOpen}
        buildingId={currentId}
        onClose={() => setComposerOpen(false)}
        onCreated={() => {
          queryClient.invalidateQueries({ queryKey: ['events'] })
          toast.success('Event created.')
        }}
      />
    </div>
  )
}

function EventCard({ event, canRsvp }: { event: ApiEvent; canRsvp: boolean }) {
  const toast = useToast()
  const queryClient = useQueryClient()

  const { data: attendees } = useQuery({
    queryKey: ['event-attendees', event.id],
    queryFn: () => api.eventAttendees(event.id),
  })

  const rsvp = useMutation({
    mutationFn: (status: RsvpStatus) => api.rsvp(event.id, status),
    onSuccess: (result) => {
      toast.success(
        result.status === 'going' ? 'See you there.'
          : result.status === 'interested' ? 'Marked as interested.'
            : 'Marked as not going.',
      )
      queryClient.invalidateQueries({ queryKey: ['event-attendees', event.id] })
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not RSVP.'),
  })

  const going = (attendees?.results ?? []).filter((a) => a.status === 'going')
  const past = new Date(event.event_date) < new Date()

  return (
    <Card className={past ? 'opacity-75' : ''}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-base font-semibold text-slate-900">{event.title}</h2>
          <p className="mt-0.5 text-sm text-slate-600">{formatDateTime(event.event_date)}</p>
        </div>
        {past && (
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">
            past
          </span>
        )}
      </div>

      {event.description && (
        <p className="mt-2 whitespace-pre-line text-sm text-slate-700">{event.description}</p>
      )}

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <span className="text-xs text-slate-500">
          {going.length} going{going.length > 0 && ` · ${going.slice(0, 3).map((a) => a.resident_name).join(', ')}`}
          {going.length > 3 && ` +${going.length - 3}`}
        </span>
      </div>

      {canRsvp && !past && (
        <div className="mt-3 flex flex-wrap gap-2">
          {RSVP_OPTIONS.map((option) => (
            <Button
              key={option.value}
              variant="secondary"
              className="px-3 py-1.5 text-xs"
              disabled={rsvp.isPending}
              onClick={() => rsvp.mutate(option.value)}
            >
              <span aria-hidden="true">{option.icon}</span> {option.label}
            </Button>
          ))}
        </div>
      )}

      {!canRsvp && !past && (
        <p className="mt-3 text-xs text-slate-500">Only residents of this building can RSVP.</p>
      )}
    </Card>
  )
}

function EventComposer({ open, buildingId, onClose, onCreated }: {
  open: boolean
  buildingId: number | undefined
  onClose: () => void
  onCreated: () => void
}) {
  const toast = useToast()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [when, setWhen] = useState('')

  const create = useMutation({
    mutationFn: () =>
      api.createEvent({
        building: buildingId,
        title,
        description: description || undefined,
        event_date: when,
      }),
    onSuccess: () => {
      setTitle('')
      setDescription('')
      setWhen('')
      onCreated()
      onClose()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not create event.'),
  })

  return (
    <Modal
      open={open}
      title="Create an event"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            loading={create.isPending}
            disabled={!title.trim() || !when || !buildingId}
            onClick={() => create.mutate()}
          >
            Create
          </Button>
        </>
      }
    >
      <Field
        label="Title"
        placeholder="Eid Milad community lunch"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
      />
      <Field
        label="When"
        type="datetime-local"
        value={when}
        onChange={(e) => setWhen(e.target.value)}
      />
      <TextArea
        label="Details"
        rows={3}
        placeholder="Where in the building, what to bring, who to ask."
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />
    </Modal>
  )
}
