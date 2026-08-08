import type { ReactNode } from 'react'
import { EmptyState, Skeleton } from './ui'

export interface Column<T> {
  key: string
  header: string
  /** Cell renderer. Return a string, or any node for badges/actions. */
  render: (row: T) => ReactNode
  className?: string
}

interface DataTableProps<T> {
  columns: Column<T>[]
  rows: T[]
  rowKey: (row: T) => string | number
  loading?: boolean
  empty?: { icon: string; title: string; body: string; action?: ReactNode }
  /** Pagination (the API's DRF envelope, page size 20). */
  page?: number
  count?: number
  onPageChange?: (page: number) => void
}

const PAGE_SIZE = 20

/**
 * The shared table for every registry screen. Horizontal overflow is contained so the page body
 * never scrolls sideways on a phone.
 */
export default function DataTable<T>({
  columns, rows, rowKey, loading, empty, page = 1, count = 0, onPageChange,
}: DataTableProps<T>) {
  if (loading) {
    return (
      <div className="space-y-2 rounded-xl border border-slate-200 bg-white p-4">
        {Array.from({ length: 6 }).map((_, i) => (
          <Skeleton key={i} className="h-10 w-full" />
        ))}
      </div>
    )
  }

  if (!rows.length) {
    return (
      <EmptyState
        icon={empty?.icon ?? '📭'}
        title={empty?.title ?? 'Nothing here yet'}
        body={empty?.body ?? 'When there is data to show, it will appear here.'}
        action={empty?.action}
      />
    )
  }

  const totalPages = Math.max(1, Math.ceil(count / PAGE_SIZE))

  return (
    <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50">
              {columns.map((column) => (
                <th
                  key={column.key}
                  scope="col"
                  className={`px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 ${column.className ?? ''}`}
                >
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={rowKey(row)} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/70">
                {columns.map((column) => (
                  <td key={column.key} className={`px-4 py-3 text-slate-700 ${column.className ?? ''}`}>
                    {column.render(row)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {onPageChange && count > PAGE_SIZE && (
        <div className="flex items-center justify-between border-t border-slate-200 px-4 py-2.5 text-sm">
          <span className="text-slate-500">
            Page {page} of {totalPages} · {count} total
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={page <= 1}
              onClick={() => onPageChange(page - 1)}
              className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium disabled:opacity-40"
            >
              Previous
            </button>
            <button
              type="button"
              disabled={page >= totalPages}
              onClick={() => onPageChange(page + 1)}
              className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium disabled:opacity-40"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

/** Small coloured pill used for statuses and roles across the registry screens. */
export function Badge({ tone, children }: { tone: 'green' | 'amber' | 'blue' | 'slate' | 'violet'; children: ReactNode }) {
  const tones = {
    green: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
    amber: 'bg-amber-50 text-amber-700 ring-amber-200',
    blue: 'bg-brand-50 text-brand-800 ring-brand-200',
    slate: 'bg-slate-100 text-slate-700 ring-slate-200',
    violet: 'bg-violet-50 text-violet-700 ring-violet-200',
  }
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${tones[tone]}`}>
      {children}
    </span>
  )
}
