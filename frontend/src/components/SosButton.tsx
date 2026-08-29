import { useCallback, useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useBuilding } from '../lib/building'
import { useCurrentResident } from '../lib/resident'
import { useToast } from '../lib/toast'
import { Button, Modal } from './ui'

/** Long enough that a pocket-press cannot trigger it, short enough to be usable in a real panic. */
const HOLD_MS = 1500

/**
 * The SOS control.
 *
 * <p>Two deliberate decisions. It is **hold-to-confirm**, not a tap: a false alarm sends people
 * running, so the gesture has to be one nobody makes by accident. And it **never blocks on
 * geolocation** — coordinates are nice for security but the alert matters more than the map pin, so
 * a refused or slow permission still sends the alert, just without a position.
 */
export default function SosButton() {
  const { currentId } = useBuilding()
  const { residentId } = useCurrentResident()
  const toast = useToast()
  const queryClient = useQueryClient()

  const [open, setOpen] = useState(false)
  const [progress, setProgress] = useState(0)
  const timer = useRef<number | null>(null)
  const started = useRef<number>(0)

  const { data: contacts } = useQuery({
    queryKey: ['emergency-contacts', currentId],
    queryFn: () => api.emergencyContacts(currentId),
    enabled: open && Boolean(currentId),
  })

  const raise = useMutation({
    mutationFn: async () => {
      const coords = await currentPosition()
      return api.raiseSos(residentId!, coords)
    },
    onSuccess: () => {
      toast.success('SOS sent. Security has been notified.')
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      setOpen(false)
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not send the alert.'),
  })

  const stopHold = useCallback(() => {
    if (timer.current !== null) {
      cancelAnimationFrame(timer.current)
      timer.current = null
    }
    setProgress(0)
  }, [])

  const startHold = useCallback(() => {
    if (raise.isPending || !residentId) return
    started.current = Date.now()

    const tick = () => {
      const elapsed = Date.now() - started.current
      const ratio = Math.min(1, elapsed / HOLD_MS)
      setProgress(ratio)

      if (ratio >= 1) {
        stopHold()
        if (navigator.vibrate) navigator.vibrate(200)
        raise.mutate()
      } else {
        timer.current = requestAnimationFrame(tick)
      }
    }
    timer.current = requestAnimationFrame(tick)
  }, [raise, residentId, stopHold])

  useEffect(() => stopHold, [stopHold])

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="Emergency SOS"
        className="grid h-9 w-9 place-items-center rounded-lg text-lg transition hover:bg-red-50"
        title="Emergency SOS"
      >
        <span aria-hidden="true">🆘</span>
      </button>

      <Modal
        open={open}
        title="Emergency SOS"
        onClose={() => {
          stopHold()
          setOpen(false)
        }}
      >
        {!residentId ? (
          <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
            SOS is raised on behalf of a resident. You are viewing this building as staff, so use the
            emergency numbers below instead.
          </p>
        ) : (
          <>
            <p className="text-sm text-slate-700">
              This alerts building security immediately and records your location if you allow it.
              Hold the button for {HOLD_MS / 1000} seconds to confirm.
            </p>

            <button
              type="button"
              onPointerDown={startHold}
              onPointerUp={stopHold}
              onPointerLeave={stopHold}
              onPointerCancel={stopHold}
              disabled={raise.isPending}
              className="relative w-full overflow-hidden rounded-xl bg-red-600 py-6 text-lg font-bold text-white
                transition select-none hover:bg-red-700 disabled:opacity-70"
            >
              {/* The fill is the confirmation: you can see how long is left. */}
              <span
                aria-hidden="true"
                className="absolute inset-y-0 left-0 bg-red-800 transition-none"
                style={{ width: `${progress * 100}%` }}
              />
              <span className="relative">
                {raise.isPending ? 'Sending…' : progress > 0 ? 'Keep holding…' : 'Hold to send SOS'}
              </span>
            </button>
          </>
        )}

        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Emergency numbers</p>
          <ul className="mt-2 divide-y divide-slate-100">
            {(contacts?.results ?? []).map((contact) => (
              <li key={contact.id} className="flex items-center justify-between gap-3 py-2">
                <span className="text-sm text-slate-800">
                  {contact.name}
                  <span className="ml-2 text-xs uppercase tracking-wide text-slate-400">{contact.type}</span>
                </span>
                <a
                  href={`tel:${contact.phone}`}
                  className="rounded-md bg-slate-100 px-2.5 py-1 text-sm font-medium text-slate-800 hover:bg-slate-200"
                >
                  {contact.phone}
                </a>
              </li>
            ))}
            {!contacts?.results.length && (
              <li className="py-2 text-sm text-slate-500">No numbers recorded for this building yet.</li>
            )}
          </ul>
        </div>

        <div className="flex justify-end">
          <Button
            variant="secondary"
            onClick={() => {
              stopHold()
              setOpen(false)
            }}
          >
            Close
          </Button>
        </div>
      </Modal>
    </>
  )
}

/** Resolves to coordinates, or to undefined if the browser refuses or takes too long. */
function currentPosition(): Promise<{ latitude: number; longitude: number } | undefined> {
  if (!navigator.geolocation) return Promise.resolve(undefined)

  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (position) =>
        resolve({
          latitude: Number(position.coords.latitude.toFixed(6)),
          longitude: Number(position.coords.longitude.toFixed(6)),
        }),
      () => resolve(undefined),
      { timeout: 4000, maximumAge: 60_000 },
    )
  })
}
