import { CheckCircle2, FileUp, Loader2 } from 'lucide-react'
import { useEffect, useRef, useState, type DragEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, errorMessage } from '../api/client'
import type { InvoiceDetail, InvoiceSummary } from '../api/types'
import StatusBadge from '../components/StatusBadge'
import { money } from '../lib/format'

const ACCEPT = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp']

export default function UploadPage() {
  const [items, setItems] = useState<InvoiceSummary[]>([])
  const [dragging, setDragging] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const input = useRef<HTMLInputElement>(null)

  // Poll bills the agent is still reading.
  useEffect(() => {
    const pending = items.filter((i) => i.status === 'PROCESSING')
    if (pending.length === 0) return
    const timer = setTimeout(async () => {
      const updates = await Promise.all(pending.map((i) => api.get<InvoiceDetail>(`/invoices/${i.id}`).then((r) => r.data)))
      setItems((prev) =>
        prev.map((p) => {
          const u = updates.find((x) => x.id === p.id)
          if (!u) return p
          const errors = u.validationIssues.filter((v) => v.severity === 'ERROR').length
          return { ...p, ...u, errorCount: errors, warningCount: u.validationIssues.length - errors, totalTax: null }
        }),
      )
    }, 1500)
    return () => clearTimeout(timer)
  }, [items])

  const upload = async (files: FileList | File[]) => {
    const list = Array.from(files).filter((f) => ACCEPT.includes(f.type))
    if (list.length === 0) {
      setError('Choose PDF, JPG, PNG or WEBP files')
      return
    }
    const form = new FormData()
    list.forEach((f) => form.append('files', f))
    setBusy(true)
    setError('')
    try {
      const { data } = await api.post<InvoiceSummary[]>('/invoices', form)
      setItems((prev) => [...data, ...prev])
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const onDrop = (e: DragEvent) => {
    e.preventDefault()
    setDragging(false)
    upload(e.dataTransfer.files)
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Scan bills</h1>
        <p className="text-sm text-slate-500">
          Upload photos or PDFs of purchase bills. The AI agent reads GSTIN, HSN/SAC, taxes and totals, then checks them.
        </p>
      </div>

      <div
        onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        onClick={() => input.current?.click()}
        className={`card flex cursor-pointer flex-col items-center justify-center gap-3 border-2 border-dashed px-6 py-14 text-center transition ${
          dragging ? 'border-brand-500 bg-brand-50' : 'border-slate-300 hover:border-brand-500'
        }`}
      >
        {busy ? <Loader2 className="size-10 animate-spin text-brand-600" /> : <FileUp className="size-10 text-brand-600" />}
        <div>
          <div className="font-medium">Drop bills here or click to choose</div>
          <div className="text-sm text-slate-500">PDF, JPG, PNG or WEBP · up to 10 MB each · 20 at a time</div>
        </div>
        <input
          ref={input}
          type="file"
          multiple
          accept={ACCEPT.join(',')}
          className="hidden"
          onChange={(e) => e.target.files && upload(e.target.files)}
        />
      </div>
      {error && <p className="text-sm text-red-600">{error}</p>}

      {items.length > 0 && (
        <div className="card divide-y divide-slate-100">
          {items.map((i) => (
            <Link key={i.id} to={`/invoices/${i.id}`} className="flex items-center gap-4 px-4 py-3 hover:bg-slate-50">
              {i.status === 'PROCESSING' ? (
                <Loader2 className="size-5 shrink-0 animate-spin text-sky-500" />
              ) : (
                <CheckCircle2 className="size-5 shrink-0 text-brand-600" />
              )}
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-medium">{i.supplierName ?? i.fileName}</div>
                <div className="truncate text-xs text-slate-500">
                  {i.status === 'PROCESSING'
                    ? 'Agent is reading the bill…'
                    : `${i.invoiceNumber ?? 'No invoice no.'} · ${i.errorCount} error(s), ${i.warningCount} warning(s)`}
                </div>
              </div>
              <span className="text-sm tabular-nums">{money(i.totalAmount)}</span>
              <StatusBadge status={i.status} />
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
