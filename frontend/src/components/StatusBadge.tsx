import type { InvoiceStatus } from '../api/types'

const STYLES: Record<InvoiceStatus, string> = {
  PROCESSING: 'bg-sky-50 text-sky-700 ring-sky-200',
  NEEDS_REVIEW: 'bg-amber-50 text-amber-800 ring-amber-200',
  VALIDATED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  APPROVED: 'bg-brand-50 text-brand-800 ring-brand-100',
  DUPLICATE: 'bg-violet-50 text-violet-700 ring-violet-200',
  FAILED: 'bg-red-50 text-red-700 ring-red-200',
}

const LABELS: Record<InvoiceStatus, string> = {
  PROCESSING: 'Processing',
  NEEDS_REVIEW: 'Needs review',
  VALIDATED: 'Validated',
  APPROVED: 'Approved',
  DUPLICATE: 'Duplicate',
  FAILED: 'Failed',
}

export const STATUS_LABELS = LABELS

export default function StatusBadge({ status }: { status: InvoiceStatus }) {
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ${STYLES[status]}`}>
      {status === 'PROCESSING' && <span className="size-1.5 animate-pulse rounded-full bg-sky-500" />}
      {LABELS[status]}
    </span>
  )
}
