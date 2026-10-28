import { ChevronLeft, ChevronRight, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, errorMessage } from '../api/client'
import { CATEGORIES, type InvoiceStatus, type InvoiceSummary, type Page } from '../api/types'
import StatusBadge, { STATUS_LABELS } from '../components/StatusBadge'
import { date, money } from '../lib/format'

export default function InvoicesPage() {
  const [params, setParams] = useSearchParams()
  const status = (params.get('status') ?? '') as InvoiceStatus | ''
  const page = Number(params.get('page') ?? 0)
  const [q, setQ] = useState(params.get('q') ?? '')
  const [data, setData] = useState<Page<InvoiceSummary> | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api
      .get<Page<InvoiceSummary>>('/invoices', {
        params: { status: status || undefined, q: params.get('q') || undefined, page, size: 20 },
      })
      .then((r) => setData(r.data))
      .catch((e) => setError(errorMessage(e)))
  }, [params, status, page])

  const update = (patch: Record<string, string>) => {
    const next = new URLSearchParams(params)
    Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)))
    if (!('page' in patch)) next.delete('page')
    setParams(next)
  }

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-semibold">Bills</h1>

      <div className="flex flex-wrap gap-3">
        <form
          className="relative min-w-60 flex-1"
          onSubmit={(e) => {
            e.preventDefault()
            update({ q })
          }}
        >
          <Search className="absolute top-2.5 left-3 size-4 text-slate-400" />
          <input className="input pl-9" placeholder="Search supplier, GSTIN, invoice no…" value={q} onChange={(e) => setQ(e.target.value)} />
        </form>
        <select className="input w-auto" value={status} onChange={(e) => update({ status: e.target.value })}>
          <option value="">All statuses</option>
          {(Object.keys(STATUS_LABELS) as InvoiceStatus[]).map((s) => (
            <option key={s} value={s}>{STATUS_LABELS[s]}</option>
          ))}
        </select>
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <div className="card overflow-x-auto">
        <table className="w-full min-w-[800px]">
          <thead className="border-b border-slate-200 bg-slate-50">
            <tr>
              <th className="th">Date</th>
              <th className="th">Supplier</th>
              <th className="th">Invoice no.</th>
              <th className="th">Category</th>
              <th className="th text-right">Taxable</th>
              <th className="th text-right">GST</th>
              <th className="th text-right">Total</th>
              <th className="th">Checks</th>
              <th className="th">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {data?.content.map((i) => (
              <tr key={i.id} className="hover:bg-slate-50">
                <td className="td whitespace-nowrap">{date(i.invoiceDate)}</td>
                <td className="td">
                  <Link to={`/invoices/${i.id}`} className="font-medium text-slate-900 hover:text-brand-700">
                    {i.supplierName ?? i.fileName}
                  </Link>
                  <div className="font-mono text-xs text-slate-500">{i.supplierGstin}</div>
                </td>
                <td className="td">{i.invoiceNumber ?? '—'}</td>
                <td className="td text-xs">{i.category ? CATEGORIES[i.category] : '—'}</td>
                <td className="td text-right tabular-nums">{money(i.taxableValue)}</td>
                <td className="td text-right tabular-nums">{money(i.totalTax)}</td>
                <td className="td text-right font-medium tabular-nums">{money(i.totalAmount)}</td>
                <td className="td text-xs whitespace-nowrap">
                  {i.errorCount > 0 && <span className="text-red-600">{i.errorCount} error{i.errorCount > 1 && 's'} </span>}
                  {i.warningCount > 0 && <span className="text-amber-600">{i.warningCount} warning{i.warningCount > 1 && 's'}</span>}
                  {i.errorCount + i.warningCount === 0 && i.status !== 'PROCESSING' && <span className="text-emerald-600">All clear</span>}
                </td>
                <td className="td"><StatusBadge status={i.status} /></td>
              </tr>
            ))}
            {data?.content.length === 0 && (
              <tr>
                <td colSpan={9} className="td py-10 text-center text-slate-500">
                  No bills found. <Link to="/upload" className="text-brand-700">Scan your first bill →</Link>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-end gap-3 text-sm">
          <span className="text-slate-500">
            Page {data.number + 1} of {data.totalPages} · {data.totalElements} bills
          </span>
          <button className="btn-secondary" disabled={data.number === 0} onClick={() => update({ page: String(page - 1) })}>
            <ChevronLeft className="size-4" />
          </button>
          <button className="btn-secondary" disabled={data.number + 1 >= data.totalPages} onClick={() => update({ page: String(page + 1) })}>
            <ChevronRight className="size-4" />
          </button>
        </div>
      )}
    </div>
  )
}
