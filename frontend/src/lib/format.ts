/**
 * Display formatters. Kept out of the component modules so those export components only, which is
 * what keeps Fast Refresh working during development.
 */

/** Money, in the one currency this product deals in. */
export function taka(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—'
  return `৳ ${Number(value).toLocaleString('en-BD', { maximumFractionDigits: 0 })}`
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('en-GB', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  return new Date(value).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
}
