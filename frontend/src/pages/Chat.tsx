import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError, chatSocketUrl } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import { useCurrentResident } from '../lib/resident'
import { useToast } from '../lib/toast'
import PageHeader from '../components/PageHeader'
import { Button, Card, Field, Modal, Skeleton } from '../components/ui'
import Icon from '../components/Icon'
import type { ApiChatRoom, ApiMessage, ChatFrame } from '../types'

type Connection = 'connecting' | 'live' | 'reconnecting'

/**
 * Group chat. Messages persist through the REST API; the room's WebSocket pushes every saved
 * message back to everyone in it — the sender included — so what appears on screen is always what
 * was stored. Messages are merged by id, which makes the REST reply and the socket echo harmless
 * duplicates of each other.
 */
export default function Chat() {
  const { user } = useAuth()
  const { currentId } = useBuilding()
  const { resident } = useCurrentResident()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [roomId, setRoomId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)

  const { data: rooms, isLoading: roomsLoading } = useQuery({
    queryKey: ['chat-rooms', currentId],
    queryFn: () => api.chatRooms(currentId),
    enabled: Boolean(currentId),
  })

  const roomList = useMemo(() => rooms?.results ?? [], [rooms])
  const activeRoom = roomList.find((room) => room.id === roomId) ?? roomList[0] ?? null
  const canManage = user?.role === 'admin' || user?.role === 'committee'

  const createRoom = useMutation({
    mutationFn: (body: { name: string; is_public: boolean }) =>
      api.createChatRoom({ building: currentId!, ...body }),
    onSuccess: (room) => {
      toast.success(`#${room.name} is open.`)
      queryClient.invalidateQueries({ queryKey: ['chat-rooms'] })
      setRoomId(room.id)
      setCreating(false)
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not open the room.'),
  })

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Chat"
        subtitle="Talk with your neighbours. Messages arrive live — no refreshing."
        actions={
          (resident || canManage) && (
            <Button variant="secondary" onClick={() => setCreating(true)}>
              <Icon name="plus" size={16} />
              New room
            </Button>
          )
        }
      />

      <div className="grid gap-4 lg:grid-cols-[250px_minmax(0,1fr)]">
        <Card className="!p-3">
          <p className="nav-group-label !mx-2 !mt-1">Rooms</p>
          {roomsLoading ? (
            <div className="space-y-2 p-2">
              {[0, 1, 2].map((i) => (
                <Skeleton key={i} className="h-9 w-full" />
              ))}
            </div>
          ) : roomList.length ? (
            <ul className="flex gap-1 overflow-x-auto lg:flex-col lg:overflow-visible">
              {roomList.map((room) => (
                <li key={room.id} className="shrink-0">
                  <button
                    type="button"
                    onClick={() => setRoomId(room.id)}
                    aria-current={activeRoom?.id === room.id}
                    className={`flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-xs font-medium transition
                      ${activeRoom?.id === room.id ? 'bg-brand-700 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
                  >
                    <span aria-hidden="true">#</span>
                    <span className="truncate">{room.name}</span>
                    {!room.is_public && <Icon name="lock" size={13} className="ml-auto shrink-0 opacity-70" />}
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="px-2 py-6 text-center text-xs text-slate-500">No rooms yet.</p>
          )}
        </Card>

        {activeRoom ? (
          <Conversation key={activeRoom.id} room={activeRoom} canPost={Boolean(resident)} />
        ) : (
          <Card className="grid min-h-[60vh] place-items-center text-center">
            <div>
              <Icon name="chat" size={28} className="mx-auto text-slate-400" />
              <p className="mt-3 text-sm text-slate-600">
                {roomsLoading ? 'Loading rooms…' : 'Open a room to start the conversation.'}
              </p>
            </div>
          </Card>
        )}
      </div>

      <NewRoomDialog
        open={creating}
        canMakePrivate={canManage}
        busy={createRoom.isPending}
        onClose={() => setCreating(false)}
        onCreate={(body) => createRoom.mutate(body)}
      />
    </div>
  )
}

function Conversation({ room, canPost }: { room: ApiChatRoom; canPost: boolean }) {
  const { user } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [messages, setMessages] = useState<ApiMessage[]>([])
  const [page, setPage] = useState(1)
  const [hasEarlier, setHasEarlier] = useState(false)
  const [draft, setDraft] = useState('')
  const [connection, setConnection] = useState<Connection>('connecting')
  const [typing, setTyping] = useState<string | null>(null)
  const socketRef = useRef<WebSocket | null>(null)
  const scroller = useRef<HTMLDivElement>(null)
  const lastTypingSent = useRef(0)
  const stickToBottom = useRef(true)

  const merge = (incoming: ApiMessage[]) => setMessages((current) => mergeMessages(current, incoming))

  const { data: history, isLoading } = useQuery({
    queryKey: ['chat-messages', room.id, page],
    queryFn: () => api.chatMessages(room.id, page),
  })

  // History pages fold into the same id-keyed stream the socket feeds.
  useEffect(() => {
    if (!history) return
    setMessages((current) => mergeMessages(current, history.results))
    setHasEarlier(Boolean(history.next))
  }, [history])

  // The live connection: reconnects with a gentle backoff until the room is left.
  useEffect(() => {
    let closedByUs = false
    let retry = 0
    let timer: number | undefined
    let typingTimer: number | undefined

    const connect = () => {
      const socket = new WebSocket(chatSocketUrl(room.id))
      socketRef.current = socket
      socket.onopen = () => {
        retry = 0
        setConnection('live')
      }
      socket.onmessage = (event) => {
        const frame = JSON.parse(event.data) as ChatFrame
        if (frame.type === 'message.created') {
          stickToBottom.current = true
          setMessages((current) => mergeMessages(current, [frame.message]))
          setTyping(null)
          queryClient.invalidateQueries({ queryKey: ['chat-rooms'] })
        } else if (frame.type === 'typing' && frame.user_id !== user?.id) {
          setTyping(frame.sender)
          window.clearTimeout(typingTimer)
          typingTimer = window.setTimeout(() => setTyping(null), 3000)
        }
      }
      socket.onclose = () => {
        if (closedByUs) return
        setConnection('reconnecting')
        retry = Math.min(retry + 1, 5)
        timer = window.setTimeout(connect, 1000 * retry)
      }
    }
    connect()

    return () => {
      closedByUs = true
      window.clearTimeout(timer)
      window.clearTimeout(typingTimer)
      socketRef.current?.close()
    }
  }, [room.id, user?.id, queryClient])

  // Keep the view pinned to the newest message unless the reader has scrolled up to read history.
  useLayoutEffect(() => {
    const el = scroller.current
    if (el && stickToBottom.current) el.scrollTop = el.scrollHeight
  }, [messages])

  const send = useMutation({
    mutationFn: (content: string) => api.sendChatMessage(room.id, content),
    onSuccess: (message) => {
      stickToBottom.current = true
      merge([message])
      setDraft('')
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Message not sent.'),
  })

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const content = draft.trim()
    if (content && !send.isPending) send.mutate(content)
  }

  const onDraftChange = (value: string) => {
    setDraft(value)
    const now = Date.now()
    if (value && now - lastTypingSent.current > 2000 && socketRef.current?.readyState === WebSocket.OPEN) {
      socketRef.current.send(JSON.stringify({ type: 'typing' }))
      lastTypingSent.current = now
    }
  }

  return (
    <Card className="flex h-[70vh] min-h-[420px] flex-col !p-0">
      <header className="flex items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
        <div className="min-w-0">
          <h2 className="dashboard-section-title flex items-center gap-2">
            <span className="truncate"># {room.name}</span>
            {!room.is_public && <Icon name="lock" size={14} className="text-slate-500" />}
          </h2>
          <p className="mt-1 text-[11px] text-slate-500">
            {room.is_public ? 'Open to everyone in the building' : 'Private — members only'}
          </p>
        </div>
        <span
          className="flex shrink-0 items-center gap-1.5 text-[11px] text-slate-500"
          aria-live="polite"
        >
          <span
            aria-hidden="true"
            className={`h-2 w-2 rounded-full ${connection === 'live' ? 'bg-brand-600' : 'bg-amber-400'}`}
          />
          {connection === 'live' ? 'Live' : connection === 'connecting' ? 'Connecting…' : 'Reconnecting…'}
        </span>
      </header>

      <div
        ref={scroller}
        onScroll={(event) => {
          const el = event.currentTarget
          stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
        }}
        className="flex-1 space-y-3 overflow-y-auto px-5 py-4"
        aria-live="polite"
      >
        {hasEarlier && (
          <div className="text-center">
            <button
              type="button"
              className="text-link !text-[11px]"
              onClick={() => {
                stickToBottom.current = false
                setPage((p) => p + 1)
              }}
            >
              Show earlier messages
            </button>
          </div>
        )}
        {isLoading && !messages.length ? (
          [0, 1, 2].map((i) => <Skeleton key={i} className={`h-12 ${i % 2 ? 'ml-auto w-2/3' : 'w-1/2'}`} />)
        ) : messages.length ? (
          messages.map((message) => {
            const mine = message.sender_user === user?.id
            return (
              <div key={message.id} className={`flex gap-2.5 ${mine ? 'flex-row-reverse' : ''}`}>
                {!mine && <span className="user-avatar !h-8 !w-8">{message.sender_name.charAt(0)}</span>}
                <div className={`max-w-[75%] ${mine ? 'text-right' : ''}`}>
                  <p className="mb-1 text-[10px] text-slate-500">
                    {mine ? 'You' : message.sender_name} · {timeOf(message.sent_at)}
                  </p>
                  <p
                    className={`inline-block whitespace-pre-wrap break-words rounded-lg px-3.5 py-2 text-left text-xs leading-relaxed
                      ${mine ? 'bg-brand-700 text-white' : 'border border-slate-200 bg-slate-50 text-slate-800'}`}
                  >
                    {message.content}
                  </p>
                </div>
              </div>
            )
          })
        ) : (
          <div className="grid h-full place-items-center text-center">
            <div>
              <Icon name="chat" size={26} className="mx-auto text-slate-400" />
              <p className="mt-3 text-sm text-slate-600">No messages yet — say hello.</p>
            </div>
          </div>
        )}
      </div>

      <p className="h-5 px-5 text-[11px] italic text-slate-500">{typing ? `${typing} is typing…` : ''}</p>

      <form onSubmit={onSubmit} className="flex items-end gap-2 border-t border-slate-200 p-4">
        <label htmlFor={`draft-${room.id}`} className="sr-only">
          Message #{room.name}
        </label>
        <textarea
          id={`draft-${room.id}`}
          rows={1}
          value={draft}
          disabled={!canPost}
          maxLength={4000}
          onChange={(e) => onDraftChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              onSubmit(e)
            }
          }}
          placeholder={canPost ? `Message #${room.name}` : 'Only residents can post — you can read public rooms.'}
          className="max-h-32 min-h-[42px] flex-1 resize-none rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none
            placeholder:text-slate-400 focus:border-brand-600 focus:ring-2 focus:ring-brand-500/40 disabled:bg-slate-50"
        />
        <Button type="submit" disabled={!canPost || !draft.trim()} loading={send.isPending} aria-label="Send message">
          <Icon name="send" size={16} />
          <span className="hidden sm:inline">Send</span>
        </Button>
      </form>
    </Card>
  )
}

function NewRoomDialog({
  open,
  canMakePrivate,
  busy,
  onClose,
  onCreate,
}: {
  open: boolean
  canMakePrivate: boolean
  busy: boolean
  onClose: () => void
  onCreate: (body: { name: string; is_public: boolean }) => void
}) {
  const [name, setName] = useState('')
  const [isPrivate, setIsPrivate] = useState(false)
  return (
    <Modal
      open={open}
      title="New room"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            loading={busy}
            disabled={!name.trim()}
            onClick={() => onCreate({ name: name.trim(), is_public: !isPrivate })}
          >
            Open room
          </Button>
        </>
      }
    >
      <Field label="Room name" placeholder="Parents of floor 3" value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
      {canMakePrivate && (
        <label className="flex items-start gap-2.5 text-sm text-slate-700">
          <input type="checkbox" className="mt-1" checked={isPrivate} onChange={(e) => setIsPrivate(e.target.checked)} />
          <span>
            Private room
            <span className="block text-xs text-slate-500">Only members and the committee can read it.</span>
          </span>
        </label>
      )}
    </Modal>
  )
}

/** One stream keyed by id, oldest first — a REST reply and its socket echo collapse into one. */
function mergeMessages(current: ApiMessage[], incoming: ApiMessage[]): ApiMessage[] {
  const byId = new Map(current.map((m) => [m.id, m]))
  for (const message of incoming) byId.set(message.id, message)
  return [...byId.values()].sort((a, b) => a.sent_at.localeCompare(b.sent_at) || a.id - b.id)
}

function timeOf(value: string): string {
  return new Date(value).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
}
