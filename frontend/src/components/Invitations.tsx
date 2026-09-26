import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useToast } from '../lib/toast'
import { formatDate } from '../lib/format'
import { ROLE_LABEL } from '../lib/invitations'
import { Badge } from './DataTable'
import { Button, Card, Field, Modal, Select } from './ui'
import Icon from './Icon'
import type { ApiInvitation, InvitableRole, IssuedInvitation } from '../types'

/** Staff records' roles also drive ticket auto-assignment, so offer the ones tickets are filed under. */
const STAFF_JOBS = ['Cleaning', 'Maintenance', 'Plumbing', 'Electrical', 'Lift Operator', 'Gardening', 'Driver']

/**
 * Inviting someone into the building. The server returns a one-time link (/join#token) — shown here
 * once, to copy and send however suits — and emails it as well when the server has SMTP.
 */
export function InviteDialog({
  open,
  buildingId,
  roles,
  onClose,
}: {
  open: boolean
  buildingId: number | undefined
  roles: InvitableRole[]
  onClose: () => void
}) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [role, setRole] = useState<InvitableRole>(roles[0])
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [unit, setUnit] = useState('')
  const [owner, setOwner] = useState(false)
  const [job, setJob] = useState(STAFF_JOBS[0])
  const [designation, setDesignation] = useState('')
  const [issued, setIssued] = useState<IssuedInvitation | null>(null)

  const livesHere = role === 'resident' || role === 'committee'
  const { data: units } = useQuery({
    queryKey: ['units', buildingId, 'invite-picker'],
    queryFn: () => api.units({ building_id: buildingId, page_size: 500 }),
    enabled: open && livesHere && Boolean(buildingId),
  })

  const reset = () => {
    setName('')
    setEmail('')
    setPhone('')
    setUnit('')
    setOwner(false)
    setDesignation('')
    setIssued(null)
  }
  const close = () => {
    reset()
    onClose()
  }

  const invite = useMutation({
    mutationFn: () =>
      api.invite({
        building: buildingId,
        role,
        name: name.trim(),
        email: email.trim(),
        phone: phone.trim() || undefined,
        ...(livesHere
          ? { unit: unit ? Number(unit) : undefined, is_owner: owner }
          : { staff_role: role === 'guard' ? 'Security' : job, designation: designation.trim() || undefined }),
      }),
    onSuccess: (result) => {
      setIssued(result)
      queryClient.invalidateQueries({ queryKey: ['invitations'] })
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not send the invitation.'),
  })

  const validEmail = /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim())

  return (
    <Modal
      open={open}
      title={issued ? 'Invitation ready' : `Invite ${roles.length === 1 ? ROLE_LABEL[roles[0]].toLowerCase() : 'someone'}`}
      onClose={close}
      footer={
        issued ? (
          <>
            <Button variant="secondary" onClick={reset}>Invite someone else</Button>
            <Button onClick={close}>Done</Button>
          </>
        ) : (
          <>
            <Button variant="secondary" onClick={close}>Cancel</Button>
            <Button loading={invite.isPending} disabled={!name.trim() || !validEmail} onClick={() => invite.mutate()}>
              <Icon name="send" size={16} />
              Create invitation
            </Button>
          </>
        )
      }
    >
      {issued ? (
        <LinkShare issued={issued} />
      ) : (
        <>
          {roles.length > 1 && (
            <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="Joining as">
              {roles.map((option) => (
                <button
                  key={option}
                  type="button"
                  role="radio"
                  aria-checked={role === option}
                  onClick={() => setRole(option)}
                  className={`rounded-lg border px-3 py-2.5 text-left text-sm transition ${
                    role === option
                      ? 'border-brand-600 bg-brand-50 font-semibold text-brand-800'
                      : 'border-slate-300 bg-white text-slate-700 hover:border-brand-400'
                  }`}
                >
                  {ROLE_LABEL[option]}
                </button>
              ))}
            </div>
          )}
          <Field label="Full name" autoComplete="off" value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
          <div className="grid gap-3 sm:grid-cols-2">
            <Field
              label="Email"
              type="email"
              autoComplete="off"
              value={email}
              maxLength={150}
              onChange={(e) => setEmail(e.target.value)}
              error={email && !validEmail ? 'Enter a valid email address' : undefined}
            />
            <Field label="Phone (optional)" inputMode="tel" value={phone} maxLength={20} onChange={(e) => setPhone(e.target.value)} />
          </div>

          {livesHere ? (
            <>
              <Select label="Flat" value={unit} onChange={(e) => setUnit(e.target.value)}>
                <option value="">Not tied to a flat</option>
                {(units?.results ?? []).map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.unit_number} · {u.type}{u.status !== 'available' ? ` · ${u.status}` : ''}
                  </option>
                ))}
              </Select>
              <label className="flex cursor-pointer items-center gap-2.5 text-sm text-slate-700">
                <input
                  type="checkbox"
                  checked={owner}
                  onChange={(e) => setOwner(e.target.checked)}
                  className="h-4 w-4 rounded border-slate-300 text-brand-700 focus:ring-brand-500"
                />
                They own the flat (not renting it)
              </label>
            </>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2">
              {role === 'staff' ? (
                <Select label="Job" value={job} onChange={(e) => setJob(e.target.value)}>
                  {STAFF_JOBS.map((j) => (
                    <option key={j} value={j}>{j}</option>
                  ))}
                </Select>
              ) : (
                <Field label="Job" value="Security" readOnly disabled />
              )}
              <Field
                label="Title (optional)"
                placeholder={role === 'guard' ? 'Night shift guard' : 'Head cleaner'}
                value={designation}
                maxLength={100}
                onChange={(e) => setDesignation(e.target.value)}
              />
            </div>
          )}

          <p className="text-xs leading-relaxed text-slate-500">
            They get a link to set their own password. It works once and expires after seven days.
          </p>
        </>
      )}
    </Modal>
  )
}

/** The one moment the link exists in the clear: copy it now, or issue a new one later. */
function LinkShare({ issued }: { issued: IssuedInvitation }) {
  const [copied, setCopied] = useState(false)
  const link = `${window.location.origin}${issued.invite_path}`
  const { invitation } = issued

  async function copy() {
    try {
      await navigator.clipboard.writeText(link)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2500)
    } catch {
      // Clipboard access can be refused (http, permissions); the field is selectable as a fallback.
      document.getElementById('invite-link')?.focus()
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-start gap-3 rounded-lg border border-brand-200 bg-brand-50 p-3.5">
        <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-brand-700 text-white">
          <Icon name="check" size={18} />
        </span>
        <div className="min-w-0 text-sm">
          <p className="font-semibold text-slate-900">
            {invitation.name} · {ROLE_LABEL[invitation.role]}
            {invitation.unit_number ? ` · ${invitation.unit_number}` : ''}
          </p>
          <p className="mt-0.5 truncate text-xs text-slate-600">{invitation.email}</p>
        </div>
      </div>

      <div className="space-y-1.5">
        <label htmlFor="invite-link" className="block text-sm font-medium text-slate-700">Invitation link</label>
        <div className="flex gap-2">
          <input
            id="invite-link"
            readOnly
            value={link}
            onFocus={(e) => e.target.select()}
            className="min-w-0 flex-1 rounded-lg border border-slate-300 bg-slate-50 px-3 py-2.5 text-xs text-slate-700 outline-none focus:border-brand-600 focus:ring-2 focus:ring-brand-500/40"
          />
          <Button variant="secondary" className="shrink-0" onClick={copy}>
            <Icon name={copied ? 'check' : 'copy'} size={16} />
            {copied ? 'Copied' : 'Copy'}
          </Button>
        </div>
      </div>

      <p className="flex gap-2 text-xs leading-relaxed text-slate-600">
        <Icon name="mail" size={16} className="mt-px shrink-0 text-slate-400" />
        <span>
          {issued.emailed
            ? `We've also emailed it to ${invitation.email}. `
            : 'We couldn’t email it from here, so send the link yourself — WhatsApp or SMS is fine. '}
          It works once and expires on {formatDate(invitation.expires_at)}. This is the only time it’s shown — you can
          issue a new one from the list if it gets lost.
        </span>
      </p>
    </div>
  )
}

/** Invitations sent but not yet accepted, with a fresh link or a revoke for each. */
export function PendingInvitations({ buildingId, roles }: { buildingId: number | undefined; roles: InvitableRole[] }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [issued, setIssued] = useState<IssuedInvitation | null>(null)

  const { data } = useQuery({
    queryKey: ['invitations', buildingId, roles.join(',')],
    queryFn: () => api.invitations({ building_id: buildingId, roles: roles.join(',') }),
    enabled: Boolean(buildingId),
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['invitations'] })

  const renew = useMutation({
    mutationFn: (id: number) => api.renewInvitation(id),
    onSuccess: (result) => {
      setIssued(result)
      refresh()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not issue a new link.'),
  })
  const revoke = useMutation({
    mutationFn: (invitation: ApiInvitation) => api.revokeInvitation(invitation.id),
    onSuccess: (_r, invitation) => {
      toast.success(`${invitation.name}'s invitation was withdrawn.`)
      refresh()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not withdraw it.'),
  })

  const rows = data?.results ?? []
  if (!rows.length) return null

  return (
    <Card>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="dashboard-section-title">Waiting to join</h2>
        <span className="text-[11px] text-slate-500">
          {rows.length} invitation{rows.length === 1 ? '' : 's'} not yet accepted
        </span>
      </div>
      <ul className="mt-4 divide-y divide-slate-100">
        {rows.map((invitation) => (
          <li key={invitation.id} className="flex flex-wrap items-center gap-3 py-3">
            <span className="user-avatar">{invitation.name.charAt(0).toUpperCase()}</span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-medium text-slate-800">
                {invitation.name}
                <span className="font-normal text-slate-500">
                  {' '}· {ROLE_LABEL[invitation.role]}
                  {invitation.unit_number ? ` · ${invitation.unit_number}` : ''}
                  {invitation.staff_role && invitation.role === 'staff' ? ` · ${invitation.staff_role}` : ''}
                </span>
              </p>
              <p className="mt-0.5 truncate text-[10px] text-slate-500">
                {invitation.email} ·{' '}
                {invitation.status === 'expired'
                  ? `expired ${formatDate(invitation.expires_at)}`
                  : `expires ${formatDate(invitation.expires_at)}`}
              </p>
            </div>
            <Badge tone={invitation.status === 'expired' ? 'slate' : 'amber'}>{invitation.status}</Badge>
            <span className="flex items-center gap-3">
              <button
                type="button"
                className="text-link !text-[11px]"
                disabled={renew.isPending}
                onClick={() => renew.mutate(invitation.id)}
              >
                New link
              </button>
              <button
                type="button"
                className="text-link !text-[11px] text-slate-500"
                disabled={revoke.isPending}
                onClick={() => revoke.mutate(invitation)}
              >
                Withdraw
              </button>
            </span>
          </li>
        ))}
      </ul>

      <Modal
        open={Boolean(issued)}
        title="New invitation link"
        onClose={() => setIssued(null)}
        footer={<Button onClick={() => setIssued(null)}>Done</Button>}
      >
        {issued && <LinkShare issued={issued} />}
      </Modal>
    </Card>
  )
}
