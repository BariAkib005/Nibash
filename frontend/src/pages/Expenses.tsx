import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import { useToast } from '../lib/toast'
import DataTable from '../components/DataTable'
import type { Column } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import { Button, Card, Field, Modal, Skeleton, TextArea } from '../components/ui'
import { formatDate, taka } from '../lib/format'
import type { ApiExpense, MonthlyExpenseRow } from '../types'

/**
 * Categorical colours for the stacked chart. Fixed hues rather than a generated palette, so a
 * category keeps the same colour between renders and between months.
 */
const CATEGORY_COLORS = [
  '#0f766e', '#b45309', '#4338ca', '#be123c', '#0369a1', '#65a30d', '#7c3aed', '#a16207',
]

function colorFor(index: number): string {
  return CATEGORY_COLORS[index % CATEGORY_COLORS.length] ?? '#64748b'
}

export default function Expenses() {
  const { currentId } = useBuilding()
  const { user } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()

  const [page, setPage] = useState(1)
  const [formOpen, setFormOpen] = useState(false)

  const canManage = user?.role === 'admin' || user?.role === 'committee'

  const { data, isLoading } = useQuery({
    queryKey: ['expenses', currentId, page],
    queryFn: () => api.expenses({ page, building_id: currentId }),
    enabled: Boolean(currentId),
  })

  const { data: report, isLoading: reportLoading } = useQuery({
    queryKey: ['expense-report', currentId],
    queryFn: () => api.expenseReport(currentId),
    enabled: Boolean(currentId),
  })

  const remove = useMutation({
    mutationFn: (id: number) => api.deleteExpense(id),
    onSuccess: () => {
      toast.success('Expense deleted.')
      queryClient.invalidateQueries({ queryKey: ['expenses'] })
      queryClient.invalidateQueries({ queryKey: ['expense-report'] })
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Delete failed.'),
  })

  const columns: Column<ApiExpense>[] = [
    { key: 'date', header: 'Date', render: (expense) => formatDate(expense.date) },
    {
      key: 'category',
      header: 'Category',
      render: (expense) => <span className="font-medium text-slate-900">{expense.category}</span>,
    },
    {
      key: 'description',
      header: 'Description',
      render: (expense) => (
        <span className="line-clamp-1 text-slate-600">{expense.description || '—'}</span>
      ),
    },
    {
      key: 'amount',
      header: 'Amount',
      className: 'text-right tabular-nums',
      render: (expense) => taka(expense.amount),
    },
    {
      key: 'receipt',
      header: 'Receipt',
      render: (expense) =>
        expense.receipt_path ? (
          <a
            href={`/media/${expense.receipt_path}`}
            target="_blank"
            rel="noreferrer"
            className="text-xs font-semibold text-brand-700 hover:underline"
          >
            View
          </a>
        ) : (
          <span className="text-xs text-slate-400">None</span>
        ),
    },
    {
      key: 'actions',
      header: '',
      className: 'text-right',
      render: (expense) =>
        canManage ? (
          <Button
            variant="ghost"
            className="px-2 py-1 text-xs text-red-700 hover:bg-red-50"
            disabled={remove.isPending}
            onClick={() => remove.mutate(expense.id)}
          >
            Delete
          </Button>
        ) : null,
    },
  ]

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <PageHeader
        title="Expenses"
        subtitle={data ? `${data.count} recorded for this building` : 'What the building spends, and on what'}
        actions={canManage ? <Button onClick={() => setFormOpen(true)}>Record expense</Button> : undefined}
      />

      <MonthlyChart rows={report?.results ?? []} loading={reportLoading} />

      <DataTable
        columns={columns}
        rows={data?.results ?? []}
        rowKey={(expense) => expense.id}
        loading={isLoading}
        page={page}
        count={data?.count ?? 0}
        onPageChange={setPage}
        empty={{
          icon: '🧮',
          title: 'No expenses recorded',
          body: 'Log what the building spends so the monthly report has something to chart.',
          action: canManage ? <Button onClick={() => setFormOpen(true)}>Record expense</Button> : undefined,
        }}
      />

      <ExpenseForm
        open={formOpen}
        buildingId={currentId}
        onClose={() => setFormOpen(false)}
        onCreated={() => {
          queryClient.invalidateQueries({ queryKey: ['expenses'] })
          queryClient.invalidateQueries({ queryKey: ['expense-report'] })
        }}
      />
    </div>
  )
}

/**
 * Spend by month, stacked by category. Hand-drawn with divs rather than a charting dependency:
 * the shape is a handful of stacked bars, and a library would be more code than the chart.
 */
function MonthlyChart({ rows, loading }: { rows: MonthlyExpenseRow[]; loading: boolean }) {
  const { months, categories, max } = useMemo(() => {
    const byMonth = new Map<string, Map<string, number>>()
    const seen: string[] = []

    for (const row of rows) {
      if (!byMonth.has(row.month)) byMonth.set(row.month, new Map())
      byMonth.get(row.month)!.set(row.category, Number(row.total))
      if (!seen.includes(row.category)) seen.push(row.category)
    }

    // Oldest month on the left reads more naturally than the API's newest-first order.
    const ordered = [...byMonth.entries()].sort(([a], [b]) => a.localeCompare(b)).slice(-6)
    const totals = ordered.map(([, values]) => [...values.values()].reduce((a, b) => a + b, 0))

    return { months: ordered, categories: seen, max: Math.max(1, ...totals) }
  }, [rows])

  if (loading) {
    return (
      <Card>
        <Skeleton className="h-5 w-40" />
        <Skeleton className="mt-4 h-40 w-full" />
      </Card>
    )
  }

  if (!months.length) {
    return (
      <Card className="border-dashed bg-slate-50/60">
        <h2 className="text-sm font-semibold text-slate-900">Monthly spend</h2>
        <p className="mt-1 text-sm text-slate-600">
          The chart fills in as expenses are recorded, grouped by month and category.
        </p>
      </Card>
    )
  }

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-slate-900">Monthly spend by category</h2>
        <ul className="flex flex-wrap gap-x-3 gap-y-1">
          {categories.map((category, index) => (
            <li key={category} className="flex items-center gap-1.5 text-xs text-slate-600">
              <span
                aria-hidden="true"
                className="h-2.5 w-2.5 rounded-sm"
                style={{ backgroundColor: colorFor(index) }}
              />
              {category}
            </li>
          ))}
        </ul>
      </div>

      <div className="mt-5 flex items-end gap-4 overflow-x-auto pb-1">
        {months.map(([month, values]) => {
          const total = [...values.values()].reduce((a, b) => a + b, 0)
          return (
            <div key={month} className="flex min-w-16 flex-1 flex-col items-center gap-2">
              <span className="text-xs font-medium tabular-nums text-slate-700">{taka(total)}</span>
              <div
                className="flex h-40 w-full max-w-16 flex-col-reverse overflow-hidden rounded-t-md bg-slate-100"
                title={`${month}: ${taka(total)}`}
              >
                {categories.map((category, index) => {
                  const value = values.get(category) ?? 0
                  if (!value) return null
                  return (
                    <div
                      key={category}
                      className="w-full transition-all"
                      style={{
                        height: `${(value / max) * 100}%`,
                        backgroundColor: colorFor(index),
                      }}
                      title={`${category}: ${taka(value)}`}
                    />
                  )
                })}
              </div>
              <span className="text-xs text-slate-500">
                {new Date(month).toLocaleDateString('en-GB', { month: 'short', year: '2-digit' })}
              </span>
            </div>
          )
        })}
      </div>
    </Card>
  )
}

/** Create form. Mirrors both server rules inline, so the 400s are rarely reached. */
function ExpenseForm({ open, buildingId, onClose, onCreated }: {
  open: boolean
  buildingId: number | undefined
  onClose: () => void
  onCreated: () => void
}) {
  const toast = useToast()
  const today = new Date().toISOString().slice(0, 10)

  const [category, setCategory] = useState('')
  const [amount, setAmount] = useState('')
  const [date, setDate] = useState(today)
  const [description, setDescription] = useState('')
  const [receipt, setReceipt] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)

  const amountError = amount !== '' && Number(amount) <= 0 ? 'Amount must be positive.' : undefined
  const dateError = date > today ? 'Expense date cannot be in the future.' : undefined

  const reset = () => {
    setCategory('')
    setAmount('')
    setDate(today)
    setDescription('')
    setReceipt(null)
    setPreview(null)
  }

  const create = useMutation({
    mutationFn: () =>
      api.createExpense(
        { building: buildingId!, category, amount, date, description: description || undefined },
        receipt,
      ),
    onSuccess: () => {
      toast.success('Expense recorded.')
      onCreated()
      reset()
      onClose()
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not save expense.'),
  })

  const onPickReceipt = (file: File | null) => {
    setReceipt(file)
    setPreview(file && file.type.startsWith('image/') ? URL.createObjectURL(file) : null)
  }

  const valid = category.trim() && amount && !amountError && !dateError && buildingId

  return (
    <Modal
      open={open}
      title="Record an expense"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={create.isPending} disabled={!valid} onClick={() => create.mutate()}>
            Save expense
          </Button>
        </>
      }
    >
      <Field
        label="Category"
        placeholder="Lift Maintenance"
        value={category}
        onChange={(e) => setCategory(e.target.value)}
      />

      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          label="Amount (BDT)"
          type="number"
          min="0"
          step="0.01"
          placeholder="18500"
          value={amount}
          error={amountError}
          onChange={(e) => setAmount(e.target.value)}
        />
        <Field
          label="Date"
          type="date"
          max={today}
          value={date}
          error={dateError}
          onChange={(e) => setDate(e.target.value)}
        />
      </div>

      <TextArea
        label="Description"
        rows={3}
        placeholder="What was this for?"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />

      <div className="space-y-1.5">
        <label htmlFor="receipt" className="block text-sm font-medium text-slate-700">
          Receipt <span className="font-normal text-slate-500">(optional)</span>
        </label>
        <input
          id="receipt"
          type="file"
          accept="image/*,application/pdf"
          onChange={(e) => onPickReceipt(e.target.files?.[0] ?? null)}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm file:mr-3 file:rounded-md
            file:border-0 file:bg-brand-50 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-brand-800"
        />
        {preview && (
          <img
            src={preview}
            alt="Receipt preview"
            className="mt-2 max-h-40 rounded-lg border border-slate-200 object-contain"
          />
        )}
        {receipt && !preview && (
          <p className="text-xs text-slate-500">{receipt.name} attached.</p>
        )}
      </div>
    </Modal>
  )
}
