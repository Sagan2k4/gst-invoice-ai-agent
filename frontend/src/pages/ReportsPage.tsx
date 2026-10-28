import { FileSpreadsheet, FileText } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, download, errorMessage } from '../api/client'
import type { GstSummary } from '../api/types'
import Stat from '../components/Stat'
import { currentMonth, date, money } from '../lib/format'

export default function ReportsPage() {
  const [month, setMonth] = useState(currentMonth())
  const [approvedOnly, setApprovedOnly] = useState(false)
  const [data, setData] = useState<GstSummary | null>(null)
  const [error, setError] = useState('')
  const params = { month, approvedOnly }

  useEffect(() => {
    setError('')
    api.get<GstSummary>('/reports/monthly', { params: { month, approvedOnly } })
      .then((r) => setData(r.data))
      .catch((e) => setError(errorMessage(e)))
  }, [month, approvedOnly])

  const get = (kind: 'pdf' | 'excel') =>
    download(`/reports/monthly/${kind}`, params, `gst-summary-${month}.${kind === 'pdf' ? 'pdf' : 'xlsx'}`)
      .catch((e) => setError(errorMessage(e)))

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Monthly GST summary</h1>
          <p className="text-sm text-slate-500">Purchase-side numbers for GSTR-3B input tax credit and GSTR-2B reconciliation.</p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <input type="month" className="input w-auto" value={month} onChange={(e) => setMonth(e.target.value)} />
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input type="checkbox" checked={approvedOnly} onChange={(e) => setApprovedOnly(e.target.checked)} /> Approved only
          </label>
          <button className="btn-secondary" onClick={() => get('pdf')}><FileText className="size-4" /> PDF</button>
          <button className="btn-primary" onClick={() => get('excel')}><FileSpreadsheet className="size-4" /> Excel</button>
        </div>
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      {data && (
        <>
          {data.pendingReviewCount > 0 && (
            <p className="rounded-lg bg-amber-50 px-4 py-2 text-sm text-amber-800">
              {data.pendingReviewCount} bill(s) dated this month still need review and are not included.{' '}
              <Link to="/invoices?status=NEEDS_REVIEW" className="font-medium underline">Review now</Link>
            </p>
          )}
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <Stat label="Bills included" value={data.invoiceCount} />
            <Stat label="Taxable value" value={money(data.totals.taxableValue)} />
            <Stat label="Eligible ITC" value={<span className="text-brand-700">{money(data.eligibleItc)}</span>} hint={`Blocked: ${money(data.blockedItc)}`} />
            <Stat label="Total purchases" value={money(data.totals.totalAmount)} />
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <Table title="Tax breakup" head={['Head', 'Amount']} rows={[
              ['CGST', money(data.totals.cgst)], ['SGST / UTGST', money(data.totals.sgst)],
              ['IGST', money(data.totals.igst)], ['Cess', money(data.totals.cess)], ['Total GST', money(data.totals.totalTax)],
            ]} />
            <Table title="Rate-wise" head={['Rate', 'Taxable', 'CGST', 'SGST', 'IGST']}
                   rows={data.byRate.map((r) => [`${r.gstRate}%`, money(r.taxableValue), money(r.cgst), money(r.sgst), money(r.igst)])} />
          </div>

          <Table title="HSN / SAC summary" head={['HSN/SAC', 'Description', 'Qty', 'Rate', 'Taxable', 'CGST', 'SGST', 'IGST']}
                 rows={data.byHsn.map((h) => [h.hsnSac, h.description ?? '', String(h.quantity), `${h.gstRate}%`,
                   money(h.taxableValue), money(h.cgst), money(h.sgst), money(h.igst)])} />

          <div className="grid gap-6 lg:grid-cols-2">
            <Table title="Category-wise" head={['Category', 'ITC', 'Bills', 'Taxable', 'GST']}
                   rows={data.byCategory.map((c) => [c.label, c.itcEligible ? 'Eligible' : 'Blocked', String(c.invoiceCount), money(c.taxableValue), money(c.totalTax)])} />
            <Table title="Supplier-wise" head={['Supplier', 'GSTIN', 'Bills', 'Total']}
                   rows={data.bySupplier.map((s) => [s.supplierName ?? '—', s.supplierGstin, String(s.invoiceCount), money(s.totalAmount)])} />
          </div>

          <Table title="Invoice register" head={['Date', 'Invoice no.', 'Supplier', 'Supply', 'Taxable', 'GST', 'Total']}
                 rows={data.invoices.map((i) => [date(i.invoiceDate), i.invoiceNumber ?? '—', i.supplierName ?? '—', i.supplyType ?? '—',
                   money(i.taxableValue), money(i.cgst + i.sgst + i.igst + i.cess), money(i.totalAmount)])} />
        </>
      )}
    </div>
  )
}

function Table({ title, head, rows }: { title: string; head: string[]; rows: string[][] }) {
  return (
    <div className="card overflow-hidden">
      <h2 className="border-b border-slate-200 px-4 py-3 text-sm font-semibold">{title}</h2>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="bg-slate-50">
            <tr>{head.map((h, i) => <th key={h} className={`th ${i > 0 && h !== 'Description' && h !== 'GSTIN' && h !== 'Supplier' ? 'text-right' : ''}`}>{h}</th>)}</tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.length === 0 && <tr><td colSpan={head.length} className="td text-center text-slate-500">No data</td></tr>}
            {rows.map((r, i) => (
              <tr key={i}>
                {r.map((c, j) => (
                  <td key={j} className={`td ${j > 0 && head[j] !== 'Description' && head[j] !== 'GSTIN' && head[j] !== 'Supplier' ? 'text-right tabular-nums' : ''}`}>{c}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
