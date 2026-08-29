import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useBuilding } from '../lib/building'
import { formatDateTime } from '../lib/format'

const TYPE_ICON: Record<string, string> = {
  sos: '🚨',
  chat: '💬',
  invoice: '🧾',
  booking: '🏛️',
}

/**
 * The notification bell.
 *
 * <p>The badge count is its own tiny endpoint on a 45-second poll, so the app stays current without
 * fetching the whole feed every time; the list itself is only fetched when the dropdown opens.
 * There is no WebSocket for notifications yet, and polling a count is cheap enough that adding one
 * would not pay for itself.
 */
export default function NotificationBell() {
  const { currentId } = useBuilding()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const container = useRef<HTMLDivElement>(null)

  const { data: badge } = useQuery({
    queryKey: ['notifications', currentId, 'unread'],
    queryFn: () => api.unreadCount(currentId),
    enabled: Boolean(currentId),
    refetchInterval: 45_000,
  })

  const { data: feed, isLoading } = useQuery({
    queryKey: ['notifications', currentId, 'feed'],
    queryFn: () => api.notifications({ building_id: currentId }),
    enabled: open && Boolean(currentId),
  })

  const markRead = useMutation({
    mutationFn: (id: number) => api.markNotificationRead(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  })

  // Close on an outside click or Escape — standard dropdown behaviour, done by hand to stay
  // dependency-free like the rest of the shell.
  useEffect(() => {
    if (!open) return
    const onPointer = (event: MouseEvent) => {
      if (container.current && !container.current.contains(event.target as Node)) setOpen(false)
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const unread = badge?.unread ?? 0
  const items = feed?.results ?? []

  return (
    <div ref={container} className="relative">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-label={unread ? `Notifications, ${unread} unread` : 'Notifications'}
        aria-expanded={open}
        className="relative grid h-9 w-9 place-items-center rounded-lg text-slate-600 transition hover:bg-slate-100"
      >
        <span aria-hidden="true" className="text-lg">🔔</span>
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 grid min-w-4.5 place-items-center rounded-full bg-red-600 px-1 text-[10px] font-bold text-white">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-30 mt-2 w-80 max-w-[calc(100vw-2rem)] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-lg">
          <header className="flex items-center justify-between border-b border-slate-100 px-3 py-2.5">
            <h2 className="text-sm font-semibold text-slate-900">Notifications</h2>
            {unread > 0 && (
              <span className="text-xs text-slate-500">{unread} unread</span>
            )}
          </header>

          <div className="max-h-96 overflow-y-auto">
            {isLoading && <p className="px-3 py-6 text-center text-sm text-slate-500">Loading…</p>}

            {!isLoading && !items.length && (
              <p className="px-3 py-8 text-center text-sm text-slate-500">
                Nothing yet. Alerts and messages land here.
              </p>
            )}

            <ul>
              {items.map((notification) => (
                <li key={notification.id}>
                  <button
                    type="button"
                    onClick={() => !notification.is_read && markRead.mutate(notification.id)}
                    className={`flex w-full gap-2.5 border-b border-slate-100 px-3 py-2.5 text-left transition
                      hover:bg-slate-50 ${notification.is_read ? '' : 'bg-brand-50/50'}`}
                  >
                    <span aria-hidden="true" className="text-base">
                      {TYPE_ICON[notification.type] ?? '🔔'}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className={`block text-sm ${notification.is_read ? 'text-slate-600' : 'font-medium text-slate-900'}`}>
                        {notification.message}
                      </span>
                      <span className="mt-0.5 block text-xs text-slate-400">
                        {formatDateTime(notification.sent_at)}
                      </span>
                    </span>
                    {!notification.is_read && (
                      <span aria-hidden="true" className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-brand-600" />
                    )}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}
    </div>
  )
}
