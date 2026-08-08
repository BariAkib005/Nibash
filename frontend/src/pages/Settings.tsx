import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, ApiError, setToken } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import { useToast } from '../lib/toast'
import PageHeader from '../components/PageHeader'
import { Button, Card, Field } from '../components/ui'

export default function Settings() {
  const { user } = useAuth()
  const { current, currentId } = useBuilding()
  const toast = useToast()
  const queryClient = useQueryClient()

  const canManageBuilding = user?.role === 'admin' || user?.role === 'committee'

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <PageHeader title="Settings" subtitle="Your profile, your password, and this building's details." />
      <ProfileCard
        initialName={user?.name ?? ''}
        initialPhone={user?.phone ?? ''}
        onSaved={() => queryClient.invalidateQueries({ queryKey: ['session'] })}
        toast={toast}
      />
      <PasswordCard toast={toast} />
      {current && (
        <BuildingCard
          key={currentId}
          buildingId={current.id}
          initial={{
            name: current.name,
            address: current.address,
            website: current.website ?? '',
            num_floors: current.num_floors?.toString() ?? '',
            total_units: current.total_units?.toString() ?? '',
            year_built: current.year_built?.toString() ?? '',
          }}
          disabled={!canManageBuilding}
          toast={toast}
          onSaved={() => queryClient.invalidateQueries({ queryKey: ['buildings'] })}
        />
      )}
    </div>
  )
}

type Toast = ReturnType<typeof useToast>

function ProfileCard({ initialName, initialPhone, onSaved, toast }: {
  initialName: string
  initialPhone: string
  onSaved: () => void
  toast: Toast
}) {
  const [name, setName] = useState(initialName)
  const [phone, setPhone] = useState(initialPhone)

  const save = useMutation({
    mutationFn: () => api.updateProfile({ name, phone }),
    onSuccess: (result) => {
      toast.success(result.detail)
      onSaved()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not save.'),
  })

  return (
    <Card>
      <h2 className="text-base font-semibold text-slate-900">Profile</h2>
      <form
        className="mt-4 space-y-4"
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          save.mutate()
        }}
      >
        <Field label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
        <Field label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+8801…" />
        <Button type="submit" loading={save.isPending}>Save profile</Button>
      </form>
    </Card>
  )
}

function PasswordCard({ toast }: { toast: Toast }) {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')

  const change = useMutation({
    mutationFn: () => api.changePassword(current, next),
    onSuccess: (result) => {
      // The server rotates the token, so store the new one or the next request 401s.
      setToken(result.token)
      setCurrent('')
      setNext('')
      toast.success(`${result.detail} You stay signed in on this device.`)
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not change password.'),
  })

  return (
    <Card>
      <h2 className="text-base font-semibold text-slate-900">Password</h2>
      <p className="mt-1 text-sm text-slate-600">
        Changing your password signs out every other device — this one keeps working.
      </p>
      <form
        className="mt-4 space-y-4"
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          change.mutate()
        }}
      >
        <Field
          label="Current password"
          type="password"
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          required
        />
        <Field
          label="New password"
          type="password"
          autoComplete="new-password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          required
        />
        <Button type="submit" loading={change.isPending}>Change password</Button>
      </form>
    </Card>
  )
}

function BuildingCard({ buildingId, initial, disabled, toast, onSaved }: {
  buildingId: number
  initial: Record<string, string>
  disabled: boolean
  toast: Toast
  onSaved: () => void
}) {
  const [form, setForm] = useState(initial)
  const set = (key: string) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }))

  const save = useMutation({
    mutationFn: () => api.updateBuilding(buildingId, form),
    onSuccess: (result) => {
      toast.success(result.detail)
      onSaved()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not save.'),
  })

  return (
    <Card>
      <h2 className="text-base font-semibold text-slate-900">Building</h2>
      {disabled && (
        <p className="mt-1 text-sm text-slate-500">
          Only admins and committee members can edit building details.
        </p>
      )}
      <form
        className="mt-4 space-y-4"
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          save.mutate()
        }}
      >
        <Field label="Name" value={form.name} onChange={set('name')} disabled={disabled} />
        <Field label="Address" value={form.address} onChange={set('address')} disabled={disabled} />
        <Field label="Website" value={form.website} onChange={set('website')} disabled={disabled} placeholder="https://" />
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Floors" type="number" value={form.num_floors} onChange={set('num_floors')} disabled={disabled} />
          <Field label="Total units" type="number" value={form.total_units} onChange={set('total_units')} disabled={disabled} />
          <Field label="Year built" type="number" value={form.year_built} onChange={set('year_built')} disabled={disabled} />
        </div>
        {!disabled && <Button type="submit" loading={save.isPending}>Save building</Button>}
      </form>
    </Card>
  )
}
