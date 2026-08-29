import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import { useToast } from '../lib/toast'
import DataTable, { Badge } from '../components/DataTable'
import type { Column } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import { Button, Card, Field, Modal, Select } from '../components/ui'
import { formatDate, taka } from '../lib/format'
import type { ApiInvoice, InvoiceStatus } from '../types'

const STATUS_TONE: Record<InvoiceStatus, 'green' | 'amber' | 'slate'> = {
  paid: 'green',
  pending: 'amber',
  overdue: 'slate',
}

/** Today, as the `YYYY-MM` and `YYYY-MM-DD` the wizard's inputs expect. */
function thisMonth(): string {
  return new Date().toISOString().slice(0, 7)
}

function defaultDueDate(): string {
  const date = new Date()
  date.setMonth(date.getMonth() + 1)
  date.setDate(10)
  return date.toISOString().slice(0, 10)
}

export default function Invoices() {
  const { currentId } = useBuilding()
  const { user } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()

  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const [detail, setDetail] = useState<ApiInvoice | null>(null)
  const [wizardOpen, setWizardOpen] = useState(false)

  const canManage = user?.role === 'admin' || user?.role === 'committee'

  const { data, isLoading } = useQuery({
    queryKey: ['invoices', currentId, page, status],
    queryFn: () => api.invoices({ page, building_id: currentId, status: status || undefined }),
    enabled: Boolean(currentId),
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['invoices'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }

  /**
   * Optimistic checkout: the badge flips to *paid* the moment the button is pressed and rolls back
   * if the server refuses — which it will if someone else already paid this invoice.
   */
  const pay = useMutation({
    mutationFn: (invoiceId: number) => api.checkout(invoiceId),
    onMutate: async (invoiceId) => {
      await queryClient.cancelQueries({ queryKey: ['invoices'] })
      const key = ['invoices', currentId, page, status]
      const previous = queryClient.getQueryData(key)

      queryClient.setQueryData(key, (old: typeof data) =>
        old && {
          ...old,
          results: old.results.map((row) =>
            row.id === invoiceId ? { ...row, status: 'paid' as InvoiceStatus } : row,
          ),
        },
      )
      return { previous, key }
    },
    onError: (error, _id, context) => {
      if (context) queryClient.setQueryData(context.key, context.previous)
      toast.error(error instanceof ApiError ? error.message : 'Payment failed.')
    },
    onSuccess: (result) => {
      toast.success(`Paid. Transaction ${result.transaction_id}.`)
      setDetail(null)
    },
    onSettled: invalidate,
  })

  const remind = useMutation({
    mutationFn: (invoiceId: number) => api.remindInvoice(invoiceId),
    onSuccess: (result) => toast.success(result.detail),
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not queue reminder.'),
  })

  const columns: Column<ApiInvoice>[] = [
    {
      key: 'invoice_number',
      header: 'Invoice',
      render: (invoice) => (
        <button
          type="button"
          onClick={() => setDetail(invoice)}
          className="font-medium text-brand-800 hover:underline"
        >
          {invoice.invoice_number}
        </button>
      ),
    },
    {
      key: 'resident',
      header: 'Resident',
      render: (invoice) => (
        <span>
          {invoice.resident_name}
          {invoice.unit_number && <span className="text-slate-500"> · {invoice.unit_number}</span>}
        </span>
      ),
    },
    { key: 'due_date', header: 'Due', render: (invoice) => formatDate(invoice.due_date) },
    {
      key: 'amount',
      header: 'Amount',
      className: 'text-right tabular-nums',
      render: (invoice) => taka(invoice.amount),
    },
    {
      key: 'status',
      header: 'Status',
      render: (invoice) => <Badge tone={STATUS_TONE[invoice.status]}>{invoice.status}</Badge>,
    },
    {
      key: 'actions',
      header: '',
      className: 'text-right',
      render: (invoice) =>
        invoice.status === 'paid' ? (
          <span className="text-xs text-slate-400">Settled</span>
        ) : (
          <div className="flex justify-end gap-2">
            {canManage && (
              <Button
                variant="ghost"
                className="px-2 py-1 text-xs"
                disabled={remind.isPending}
                onClick={() => remind.mutate(invoice.id)}
              >
                Remind
              </Button>
            )}
            <Button
              variant="secondary"
              className="px-2.5 py-1 text-xs"
              disabled={pay.isPending}
              onClick={() => pay.mutate(invoice.id)}
            >
              Pay
            </Button>
          </div>
        ),
    },
  ]

  const outstanding = (data?.results ?? [])
    .filter((invoice) => invoice.status !== 'paid')
    .reduce((sum, invoice) => sum + Number(invoice.amount), 0)

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <PageHeader
        title="Invoices"
        subtitle={
          data
            ? `${data.count} invoice${data.count === 1 ? '' : 's'} · ${taka(outstanding)} outstanding on this page`
            : 'Service charges and utility bills'
        }
        actions={
          <>
            <select
              aria-label="Filter by status"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value)
                setPage(1)
              }}
              className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
            >
              <option value="">All statuses</option>
              <option value="pending">Pending</option>
              <option value="paid">Paid</option>
              <option value="overdue">Overdue</option>
            </select>
            {canManage && <Button onClick={() => setWizardOpen(true)}>Generate month</Button>}
          </>
        }
      />

      <DataTable
        columns={columns}
        rows={data?.results ?? []}
        rowKey={(invoice) => invoice.id}
        loading={isLoading}
        page={page}
        count={data?.count ?? 0}
        onPageChange={setPage}
        empty={{
          icon: '🧾',
          title: status ? `No ${status} invoices` : 'No invoices yet',
          body: status
            ? 'Try a different status filter.'
            : 'Run the monthly batch to raise a service charge for every resident.',
          action: canManage && !status
            ? <Button onClick={() => setWizardOpen(true)}>Generate month</Button>
            : undefined,
        }}
      />

      <InvoiceDetail
        invoice={detail}
        onClose={() => setDetail(null)}
        onPay={(id) => pay.mutate(id)}
        paying={pay.isPending}
      />

      <GenerateMonthlyWizard
        open={wizardOpen}
        buildingId={currentId}
        onClose={() => setWizardOpen(false)}
        onDone={invalidate}
      />
    </div>
  )
}

/** Line-item view. A modal rather than a route: it is read-only and always opened from the table. */
function InvoiceDetail({ invoice, onClose, onPay, paying }: {
  invoice: ApiInvoice | null
  onClose: () => void
  onPay: (id: number) => void
  paying: boolean
}) {
  const { data } = useQuery({
    queryKey: ['invoice', invoice?.id],
    queryFn: () => api.invoice(invoice!.id),
    enabled: Boolean(invoice),
    initialData: invoice ?? undefined,
  })

  if (!invoice) return null
  const current = data ?? invoice

  return (
    <Modal
      open
      title={`Invoice ${current.invoice_number}`}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Close</Button>
          {current.status !== 'paid' && (
            <Button loading={paying} onClick={() => onPay(current.id)}>
              Pay {taka(current.amount)}
            </Button>
          )}
        </>
      }
    >
      <div className="flex flex-wrap gap-x-8 gap-y-2 text-sm">
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">Resident</p>
          <p className="font-medium text-slate-900">{current.resident_name}</p>
        </div>
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">Unit</p>
          <p className="font-medium text-slate-900">{current.unit_number ?? '—'}</p>
        </div>
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">Due</p>
          <p className="font-medium text-slate-900">{formatDate(current.due_date)}</p>
        </div>
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">Status</p>
          <Badge tone={STATUS_TONE[current.status]}>{current.status}</Badge>
        </div>
      </div>

      <div className="overflow-hidden rounded-lg border border-slate-200">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <th scope="col" className="px-3 py-2 text-left">Description</th>
              <th scope="col" className="px-3 py-2 text-right">Qty</th>
              <th scope="col" className="px-3 py-2 text-right">Amount</th>
            </tr>
          </thead>
          <tbody>
            {current.items.map((item) => (
              <tr key={item.id} className="border-t border-slate-100">
                <td className="px-3 py-2 text-slate-700">{item.description}</td>
                <td className="px-3 py-2 text-right tabular-nums text-slate-600">{Number(item.quantity)}</td>
                <td className="px-3 py-2 text-right tabular-nums text-slate-800">{taka(item.total_amount)}</td>
              </tr>
            ))}
            {!current.items.length && (
              <tr>
                <td colSpan={3} className="px-3 py-4 text-center text-slate-500">No line items.</td>
              </tr>
            )}
          </tbody>
          <tfoot>
            <tr className="border-t border-slate-200 bg-slate-50">
              <td className="px-3 py-2 font-semibold text-slate-800" colSpan={2}>Total</td>
              <td className="px-3 py-2 text-right font-semibold tabular-nums text-slate-900">
                {taka(current.amount)}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
    </Modal>
  )
}

/**
 * The monthly batch, with a preview step. The endpoint is idempotent, so re-running is safe — the
 * result line says how many were actually created, which is 0 on a repeat.
 */
function GenerateMonthlyWizard({ open, buildingId, onClose, onDone }: {
  open: boolean
  buildingId: number | undefined
  onClose: () => void
  onDone: () => void
}) {
  const toast = useToast()
  const [month, setMonth] = useState(thisMonth)
  const [dueDate, setDueDate] = useState(defaultDueDate)
  const [billTypeId, setBillTypeId] = useState<string>('')
  const [includeUtilities, setIncludeUtilities] = useState(false)

  const { data: billTypes } = useQuery({
    queryKey: ['bill-types'],
    queryFn: () => api.billTypes(),
    enabled: open,
  })

  const { data: residents } = useQuery({
    queryKey: ['residents', buildingId, 'count'],
    queryFn: () => api.residents({ building_id: buildingId }),
    enabled: open && Boolean(buildingId),
  })

  const run = useMutation({
    mutationFn: () =>
      api.generateMonthly({
        building_id: buildingId!,
        bill_type_id: Number(billTypeId || billTypes?.results[0]?.id),
        billing_month: month,
        due_date: dueDate,
        include_utilities: includeUtilities,
      }),
    onSuccess: (result) => {
      const created = result.created_invoices.length
      toast.success(
        created === 0
          ? 'Nothing to do — every resident already has an invoice for that month.'
          : `Created ${created} invoice${created === 1 ? '' : 's'}.`,
      )
      onDone()
      onClose()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Batch failed.'),
  })

  const selectedBillType = billTypeId || String(billTypes?.results[0]?.id ?? '')

  return (
    <Modal
      open={open}
      title="Generate monthly invoices"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            loading={run.isPending}
            disabled={!buildingId || !selectedBillType}
            onClick={() => run.mutate()}
          >
            Run batch
          </Button>
        </>
      }
    >
      <Select label="Bill type" value={selectedBillType} onChange={(e) => setBillTypeId(e.target.value)}>
        {(billTypes?.results ?? []).map((type) => (
          <option key={type.id} value={type.id}>{type.name}</option>
        ))}
      </Select>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Billing month" type="month" value={month} onChange={(e) => setMonth(e.target.value)} />
        <Field label="Due date" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
      </div>

      <label className="flex items-start gap-2 text-sm text-slate-700">
        <input
          type="checkbox"
          checked={includeUtilities}
          onChange={(e) => setIncludeUtilities(e.target.checked)}
          className="mt-0.5 h-4 w-4 rounded border-slate-300"
        />
        <span>
          Roll pending utility bills into each invoice
          <span className="block text-xs text-slate-500">
            Adds one line per unpaid meter reading on the resident’s unit.
          </span>
        </span>
      </label>

      <Card className="bg-slate-50/70">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Preview</p>
        <p className="mt-1 text-sm text-slate-700">
          One invoice per resident of this building
          {residents ? ` — ${residents.count} resident${residents.count === 1 ? '' : 's'}` : ''}, at
          ৳ 2,000 service charge each, due {formatDate(dueDate)}.
        </p>
        <p className="mt-2 text-xs text-slate-500">
          Safe to re-run: invoices are keyed on resident and month, so anyone already billed for{' '}
          {month} is skipped.
        </p>
      </Card>
    </Modal>
  )
}
