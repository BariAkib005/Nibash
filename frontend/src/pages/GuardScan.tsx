import { useCallback, useEffect, useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Html5Qrcode } from 'html5-qrcode'
import { api, ApiError } from '../lib/api'
import { Button, Card } from '../components/ui'
import { formatDateTime } from '../lib/format'
import type { ApiVisitor } from '../types'

type Outcome =
  | { kind: 'idle' }
  | { kind: 'ok'; visitor: ApiVisitor }
  | { kind: 'fail'; message: string }

const READER_ID = 'nibash-qr-reader'

/**
 * The gate screen. Built for a guard holding a phone in one hand, so: one job per screen, targets
 * big enough to hit without looking, and an outcome you can read at arm's length in daylight.
 *
 * <p>The camera is the fast path, but it fails often in practice — a cracked lens, a scratched
 * pass, no camera permission — so the manual code entry beside it is a first-class path, not a
 * fallback bolted on.
 */
export default function GuardScan() {
  const [outcome, setOutcome] = useState<Outcome>({ kind: 'idle' })
  const [scanning, setScanning] = useState(false)
  const [manual, setManual] = useState('')
  const [cameraError, setCameraError] = useState<string | null>(null)

  const scannerRef = useRef<Html5Qrcode | null>(null)
  /** Guards against the same code firing many times a second while the pass is in frame. */
  const lastToken = useRef<string | null>(null)

  const scan = useMutation({
    mutationFn: (token: string) => api.scanVisitor(token),
    onSuccess: (visitor) => {
      setOutcome({ kind: 'ok', visitor })
      if (navigator.vibrate) navigator.vibrate(60)
    },
    onError: (error) => {
      setOutcome({
        kind: 'fail',
        message: error instanceof ApiError ? error.message : 'Could not read that pass.',
      })
      if (navigator.vibrate) navigator.vibrate([60, 40, 60])
    },
  })

  const submit = useCallback(
    (token: string) => {
      const trimmed = token.trim()
      if (!trimmed || trimmed === lastToken.current) return
      lastToken.current = trimmed
      scan.mutate(trimmed)
      // Let the same pass be re-scanned after a moment, in case the guard needs another go.
      setTimeout(() => {
        lastToken.current = null
      }, 2500)
    },
    [scan],
  )

  const stopCamera = useCallback(async () => {
    const scanner = scannerRef.current
    scannerRef.current = null
    setScanning(false)
    if (!scanner) return
    try {
      await scanner.stop()
      scanner.clear()
    } catch {
      // Already stopped, or the element is gone — nothing to clean up.
    }
  }, [])

  const startCamera = useCallback(async () => {
    setCameraError(null)
    try {
      const scanner = new Html5Qrcode(READER_ID)
      scannerRef.current = scanner
      setScanning(true)
      await scanner.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 240, height: 240 } },
        (decoded) => submit(decoded),
        () => {
          // Fired constantly for every frame without a code; not an error worth showing.
        },
      )
    } catch (error) {
      scannerRef.current = null
      setScanning(false)
      setCameraError(
        error instanceof Error && error.name === 'NotAllowedError'
          ? 'Camera permission was refused. Type the code instead.'
          : 'No camera available on this device. Type the code instead.',
      )
    }
  }, [submit])

  // Always release the camera when leaving the screen — a live stream drains a guard's battery.
  useEffect(() => () => void stopCamera(), [stopCamera])

  return (
    <div className="mx-auto max-w-md space-y-4">
      <header>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">Gate scan</h1>
        <p className="mt-1 text-sm text-slate-600">
          Point the camera at the visitor’s pass, or type the code.
        </p>
      </header>

      <Card className="p-3">
        <div
          id={READER_ID}
          className={`overflow-hidden rounded-lg bg-slate-900 ${scanning ? 'min-h-64' : 'hidden'}`}
        />

        {!scanning && (
          <div className="grid place-items-center rounded-lg border-2 border-dashed border-slate-300 bg-slate-50 py-10 text-center">
            <span className="text-4xl" aria-hidden="true">📷</span>
            <p className="mt-2 max-w-xs text-sm text-slate-600">
              {cameraError ?? 'The camera is off. Start it to scan a pass.'}
            </p>
          </div>
        )}

        <div className="mt-3">
          {scanning ? (
            <Button variant="secondary" className="w-full py-3 text-base" onClick={stopCamera}>
              Stop camera
            </Button>
          ) : (
            <Button className="w-full py-3 text-base" onClick={startCamera}>
              Start camera
            </Button>
          )}
        </div>
      </Card>

      <Card>
        <label htmlFor="manual-token" className="block text-sm font-medium text-slate-700">
          Or type the code
        </label>
        <div className="mt-2 flex gap-2">
          <input
            id="manual-token"
            value={manual}
            onChange={(e) => setManual(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                submit(manual)
                setManual('')
              }
            }}
            placeholder="32-character pass code"
            autoComplete="off"
            className="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-3 font-mono text-sm outline-none
              focus:border-brand-600 focus:ring-2 focus:ring-brand-500/40"
          />
          <Button
            className="shrink-0 px-4 py-3"
            loading={scan.isPending}
            disabled={!manual.trim()}
            onClick={() => {
              submit(manual)
              setManual('')
            }}
          >
            Check in
          </Button>
        </div>
      </Card>

      <ScanOutcome outcome={outcome} onClear={() => setOutcome({ kind: 'idle' })} />
    </div>
  )
}

/** The result panel — unmistakable at a glance, because that is the whole job of this screen. */
function ScanOutcome({ outcome, onClear }: { outcome: Outcome; onClear: () => void }) {
  if (outcome.kind === 'idle') {
    return (
      <p className="text-center text-sm text-slate-500">
        Scans appear here. The gate log records who was checked in and when.
      </p>
    )
  }

  if (outcome.kind === 'fail') {
    return (
      <div
        role="alert"
        className="rounded-xl border-2 border-red-300 bg-red-50 p-5 text-center"
      >
        <p className="text-5xl" aria-hidden="true">✕</p>
        <p className="mt-2 text-lg font-bold text-red-900">Do not admit</p>
        <p className="mt-1 text-sm text-red-800">{outcome.message}</p>
        <Button variant="secondary" className="mt-4" onClick={onClear}>Scan another</Button>
      </div>
    )
  }

  const { visitor } = outcome
  return (
    <div
      role="status"
      className="rounded-xl border-2 border-emerald-300 bg-emerald-50 p-5 text-center"
    >
      <p className="text-5xl" aria-hidden="true">✓</p>
      <p className="mt-2 text-lg font-bold text-emerald-900">Checked in</p>
      <p className="mt-2 text-xl font-semibold text-slate-900">{visitor.visitor_name}</p>
      <p className="text-sm text-slate-700">
        Visiting {visitor.resident_name}
        {visitor.unit_number && ` · ${visitor.unit_number}`}
      </p>
      <p className="mt-1 text-xs text-slate-600">Arrived {formatDateTime(visitor.checkin_time)}</p>
      <Button variant="secondary" className="mt-4" onClick={onClear}>Scan another</Button>
    </div>
  )
}
