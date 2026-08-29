import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import { useToast } from '../lib/toast'
import PageHeader from '../components/PageHeader'
import { Button, Card, EmptyState, Field, Modal, Skeleton, TextArea } from '../components/ui'
import { formatDateTime } from '../lib/format'
import type { ApiNotice } from '../types'

export default function Notices() {
  const { currentId } = useBuilding()
  const { user } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()

  const [search, setSearch] = useState('')
  const [includeArchived, setIncludeArchived] = useState(false)
  const [composerOpen, setComposerOpen] = useState(false)

  const canManage = user?.role === 'admin' || user?.role === 'committee'

  const { data, isLoading } = useQuery({
    queryKey: ['notices', currentId, search, includeArchived],
    queryFn: () =>
      api.notices({ building_id: currentId, search: search || undefined, include_archived: includeArchived }),
    enabled: Boolean(currentId),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['notices'] })

  const togglePin = useMutation({
    mutationFn: (notice: ApiNotice) => api.updateNotice(notice.id, { is_pinned: !notice.is_pinned }),
    onSuccess: (notice) => {
      toast.success(notice.is_pinned ? 'Pinned to the top.' : 'Unpinned.')
      invalidate()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not update notice.'),
  })

  const remove = useMutation({
    mutationFn: (id: number) => api.deleteNotice(id),
    onSuccess: () => {
      toast.success('Notice deleted.')
      invalidate()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Delete failed.'),
  })

  const notices = data?.results ?? []

  return (
    <div className="mx-auto max-w-4xl space-y-5">
      <PageHeader
        title="Notice board"
        subtitle={
          includeArchived
            ? 'Showing every notice, including expired ones'
            : 'Live notices — published, not yet expired'
        }
        actions={canManage ? <Button onClick={() => setComposerOpen(true)}>Post notice</Button> : undefined}
      />

      <div className="flex flex-wrap items-center gap-3">
        <input
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search notices…"
          aria-label="Search notices"
          className="min-w-48 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none
            focus:border-brand-600 focus:ring-2 focus:ring-brand-500/40"
        />
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={includeArchived}
            onChange={(e) => setIncludeArchived(e.target.checked)}
            className="h-4 w-4 rounded border-slate-300"
          />
          Include archived
        </label>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {[0, 1, 2].map((i) => (
            <Card key={i}>
              <Skeleton className="h-5 w-2/3" />
              <Skeleton className="mt-3 h-12 w-full" />
            </Card>
          ))}
        </div>
      ) : !notices.length ? (
        <EmptyState
          icon="📌"
          title={search ? 'No notices match that search' : 'The board is empty'}
          body={
            search
              ? 'Try a different word, or include archived notices.'
              : 'Post a notice and it appears here for everyone in the building.'
          }
          action={
            search
              ? <Button variant="secondary" onClick={() => setSearch('')}>Clear search</Button>
              : canManage
                ? <Button onClick={() => setComposerOpen(true)}>Post notice</Button>
                : undefined
          }
        />
      ) : (
        <ul className="space-y-3">
          {notices.map((notice) => {
            const expired = notice.expiry_date ? new Date(notice.expiry_date) < new Date() : false
            return (
              <li key={notice.id}>
                {/* Pinned notices get a visible left rail — the board is scanned, not read. */}
                <Card
                  className={
                    notice.is_pinned
                      ? 'border-l-4 border-l-brand-600 bg-brand-50/40'
                      : expired
                        ? 'opacity-70'
                        : ''
                  }
                >
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <h2 className="flex items-center gap-2 text-base font-semibold text-slate-900">
                        {notice.is_pinned && <span aria-label="Pinned" title="Pinned">📌</span>}
                        {notice.title}
                        {expired && (
                          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">
                            expired
                          </span>
                        )}
                      </h2>
                      <p className="mt-1.5 whitespace-pre-line text-sm text-slate-700">{notice.body}</p>
                      <p className="mt-2 text-xs text-slate-500">
                        {notice.created_by_name} · {formatDateTime(notice.publish_date)}
                        {notice.expiry_date && ` · expires ${formatDateTime(notice.expiry_date)}`}
                      </p>
                    </div>

                    {canManage && (
                      <div className="flex shrink-0 gap-1">
                        <Button
                          variant="ghost"
                          className="px-2 py-1 text-xs"
                          disabled={togglePin.isPending}
                          onClick={() => togglePin.mutate(notice)}
                        >
                          {notice.is_pinned ? 'Unpin' : 'Pin'}
                        </Button>
                        <Button
                          variant="ghost"
                          className="px-2 py-1 text-xs text-red-700 hover:bg-red-50"
                          disabled={remove.isPending}
                          onClick={() => remove.mutate(notice.id)}
                        >
                          Delete
                        </Button>
                      </div>
                    )}
                  </div>
                </Card>
              </li>
            )
          })}
        </ul>
      )}

      <NoticeComposer
        open={composerOpen}
        buildingId={currentId}
        onClose={() => setComposerOpen(false)}
        onPosted={invalidate}
      />
    </div>
  )
}

function NoticeComposer({ open, buildingId, onClose, onPosted }: {
  open: boolean
  buildingId: number | undefined
  onClose: () => void
  onPosted: () => void
}) {
  const toast = useToast()
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [pinned, setPinned] = useState(false)
  const [expiry, setExpiry] = useState('')

  const post = useMutation({
    mutationFn: () =>
      api.createNotice({
        building: buildingId,
        title,
        body,
        is_pinned: pinned,
        expiry_date: expiry ? `${expiry}T23:59:00` : undefined,
      }),
    onSuccess: () => {
      toast.success('Notice posted.')
      setTitle('')
      setBody('')
      setPinned(false)
      setExpiry('')
      onPosted()
      onClose()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not post notice.'),
  })

  return (
    <Modal
      open={open}
      title="Post a notice"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            loading={post.isPending}
            disabled={!title.trim() || !body.trim() || !buildingId}
            onClick={() => post.mutate()}
          >
            Post
          </Button>
        </>
      }
    >
      <Field
        label="Title"
        placeholder="Water supply interrupted on Friday"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
      />
      <TextArea
        label="Notice"
        placeholder="What do residents need to know, and when?"
        value={body}
        onChange={(e) => setBody(e.target.value)}
      />
      <Field
        label="Expires on"
        type="date"
        value={expiry}
        onChange={(e) => setExpiry(e.target.value)}
        hint="Leave empty for a notice that never expires. Expired notices drop off the board."
      />
      <label className="flex items-center gap-2 text-sm text-slate-700">
        <input
          type="checkbox"
          checked={pinned}
          onChange={(e) => setPinned(e.target.checked)}
          className="h-4 w-4 rounded border-slate-300"
        />
        Pin to the top of the board
      </label>
    </Modal>
  )
}
