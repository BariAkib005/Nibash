import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import { useCurrentResident } from '../lib/resident'
import { useToast } from '../lib/toast'
import PageHeader from '../components/PageHeader'
import { Button, Card, EmptyState, Field, Modal, Skeleton } from '../components/ui'
import { formatDateTime } from '../lib/format'
import type { ApiPoll } from '../types'

export default function Polls() {
  const { currentId } = useBuilding()
  const { user } = useAuth()
  const { residentId } = useCurrentResident()
  const toast = useToast()
  const queryClient = useQueryClient()

  const [composerOpen, setComposerOpen] = useState(false)
  /** Which polls this browser has already voted in — see the note on `vote` below. */
  const [voted, setVoted] = useState<Record<number, number>>({})

  const canManage = user?.role === 'admin' || user?.role === 'committee'

  const { data, isLoading } = useQuery({
    queryKey: ['polls', currentId],
    queryFn: () => api.polls({ building_id: currentId }),
    enabled: Boolean(currentId),
  })

  /**
   * One vote per resident, enforced by the server. The client cannot know in advance whether the
   * caller has already voted — the list endpoint returns tallies, not ballots — so the honest
   * design is to let them try and to surface "Already voted" as an ordinary answer, not a failure.
   */
  const vote = useMutation({
    mutationFn: ({ pollId, optionId }: { pollId: number; optionId: number }) =>
      api.vote(pollId, optionId, residentId!),
    onSuccess: (_result, variables) => {
      setVoted((current) => ({ ...current, [variables.pollId]: variables.optionId }))
      toast.success('Vote recorded.')
      queryClient.invalidateQueries({ queryKey: ['polls'] })
    },
    onError: (error, variables) => {
      const message = error instanceof ApiError ? error.message : 'Could not record your vote.'
      if (message === 'Already voted') {
        setVoted((current) => ({ ...current, [variables.pollId]: -1 }))
        toast.info('You have already voted in this poll.')
      } else {
        toast.error(message)
      }
    },
  })

  const polls = data?.results ?? []

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <PageHeader
        title="Polls"
        subtitle="One vote per resident. Results update as people vote."
        actions={canManage ? <Button onClick={() => setComposerOpen(true)}>New poll</Button> : undefined}
      />

      {isLoading ? (
        <div className="space-y-3">
          {[0, 1].map((i) => (
            <Card key={i}>
              <Skeleton className="h-5 w-2/3" />
              <Skeleton className="mt-4 h-24 w-full" />
            </Card>
          ))}
        </div>
      ) : !polls.length ? (
        <EmptyState
          icon="🗳️"
          title="No polls yet"
          body="Ask the building a question — repainting, parking rules, a new caretaker."
          action={canManage ? <Button onClick={() => setComposerOpen(true)}>New poll</Button> : undefined}
        />
      ) : (
        <ul className="space-y-4">
          {polls.map((poll) => (
            <li key={poll.id}>
              <PollCard
                poll={poll}
                canVote={Boolean(residentId) && !poll.is_closed && voted[poll.id] === undefined}
                myOption={voted[poll.id]}
                pending={vote.isPending}
                noResidentRow={!residentId}
                onVote={(optionId) => vote.mutate({ pollId: poll.id, optionId })}
              />
            </li>
          ))}
        </ul>
      )}

      <PollComposer
        open={composerOpen}
        buildingId={currentId}
        onClose={() => setComposerOpen(false)}
        onCreated={() => queryClient.invalidateQueries({ queryKey: ['polls'] })}
      />
    </div>
  )
}

function PollCard({ poll, canVote, myOption, pending, noResidentRow, onVote }: {
  poll: ApiPoll
  canVote: boolean
  myOption: number | undefined
  pending: boolean
  noResidentRow: boolean
  onVote: (optionId: number) => void
}) {
  return (
    <Card className={poll.is_closed ? 'opacity-80' : ''}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h2 className="text-base font-semibold text-slate-900">{poll.question}</h2>
        <span
          className={`rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${
            poll.is_closed
              ? 'bg-slate-100 text-slate-600 ring-slate-200'
              : 'bg-emerald-50 text-emerald-700 ring-emerald-200'
          }`}
        >
          {poll.is_closed ? 'Closed' : 'Open'}
        </span>
      </div>

      <p className="mt-1 text-xs text-slate-500">
        {poll.total_votes} vote{poll.total_votes === 1 ? '' : 's'}
        {poll.end_date && ` · ${poll.is_closed ? 'closed' : 'closes'} ${formatDateTime(poll.end_date)}`}
      </p>

      <ul className="mt-4 space-y-2.5">
        {poll.options.map((option) => {
          const percentage = Number(option.percentage)
          const mine = myOption === option.id
          return (
            <li key={option.id}>
              <div className="flex items-baseline justify-between gap-3 text-sm">
                <span className={`text-slate-800 ${mine ? 'font-semibold' : ''}`}>
                  {option.option_text}
                  {mine && <span className="ml-1.5 text-xs font-normal text-brand-700">· your vote</span>}
                </span>
                <span className="shrink-0 tabular-nums text-slate-600">
                  {option.votes} · {percentage.toFixed(0)}%
                </span>
              </div>

              <div className="mt-1 flex items-center gap-2">
                <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-slate-100">
                  <div
                    className={`h-full rounded-full transition-all duration-500 ${
                      mine ? 'bg-brand-700' : 'bg-brand-400'
                    }`}
                    style={{ width: `${percentage}%` }}
                  />
                </div>
                {canVote && (
                  <Button
                    variant="secondary"
                    className="shrink-0 px-2.5 py-1 text-xs"
                    disabled={pending}
                    onClick={() => onVote(option.id)}
                  >
                    Vote
                  </Button>
                )}
              </div>
            </li>
          )
        })}
      </ul>

      {poll.is_closed && (
        <p className="mt-3 text-xs text-slate-500">This poll has closed. Results are final.</p>
      )}
      {!poll.is_closed && myOption !== undefined && (
        <p className="mt-3 text-xs text-slate-500">Thanks — your vote is in. One vote per resident.</p>
      )}
      {!poll.is_closed && noResidentRow && (
        <p className="mt-3 text-xs text-slate-500">
          Only residents of this building can vote. You are viewing it as staff.
        </p>
      )}
    </Card>
  )
}

function PollComposer({ open, buildingId, onClose, onCreated }: {
  open: boolean
  buildingId: number | undefined
  onClose: () => void
  onCreated: () => void
}) {
  const toast = useToast()
  const [question, setQuestion] = useState('')
  const [endDate, setEndDate] = useState('')
  const [options, setOptions] = useState(['', ''])

  const filled = options.map((o) => o.trim()).filter(Boolean)

  const create = useMutation({
    mutationFn: () =>
      api.createPoll({
        building: buildingId,
        question,
        end_date: endDate ? `${endDate}T23:59:00` : undefined,
        options: filled.map((option_text) => ({ option_text })),
      }),
    onSuccess: () => {
      toast.success('Poll created.')
      setQuestion('')
      setEndDate('')
      setOptions(['', ''])
      onCreated()
      onClose()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not create poll.'),
  })

  const setOption = (index: number, value: string) =>
    setOptions((current) => current.map((option, i) => (i === index ? value : option)))

  return (
    <Modal
      open={open}
      title="Create a poll"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            loading={create.isPending}
            disabled={!question.trim() || filled.length < 2 || !buildingId}
            onClick={() => create.mutate()}
          >
            Create poll
          </Button>
        </>
      }
    >
      <Field
        label="Question"
        placeholder="Should we repaint the lobby this quarter?"
        value={question}
        onChange={(e) => setQuestion(e.target.value)}
      />

      <div className="space-y-2">
        <p className="text-sm font-medium text-slate-700">Options</p>
        {options.map((option, index) => (
          <input
            key={index}
            value={option}
            onChange={(e) => setOption(index, e.target.value)}
            placeholder={`Option ${index + 1}`}
            aria-label={`Option ${index + 1}`}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none
              focus:border-brand-600 focus:ring-2 focus:ring-brand-500/40"
          />
        ))}
        <Button
          variant="ghost"
          className="px-2 py-1 text-xs"
          onClick={() => setOptions((current) => [...current, ''])}
        >
          + Add option
        </Button>
        {filled.length < 2 && (
          <p className="text-xs text-slate-500">A poll needs at least two options.</p>
        )}
      </div>

      <Field
        label="Closes on"
        type="date"
        value={endDate}
        onChange={(e) => setEndDate(e.target.value)}
        hint="Leave empty to keep voting open indefinitely."
      />
    </Modal>
  )
}
