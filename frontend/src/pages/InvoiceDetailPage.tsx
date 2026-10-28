import { AlertCircle, AlertTriangle, ArrowLeft, Bot, CheckCircle2, Plus, RefreshCw, Save, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, errorMessage } from '../api/client'
import { CATEGORIES, type InvoiceDetail, type LineItem } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import StatusBadge from '../components/StatusBadge'
import { money } from '../lib/format'

type Form = Omit<InvoiceDetail, 'validationIssues'>

const EMPTY_LINE: LineItem = {
  description: '', hsnSac: '', quantity: 1, unitPrice: null, taxableValue: null, gstRate: 18, cgst: null, sgst: null, igst: null,
}

export default function InvoiceDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { can } = useAuth()
  const editable = can('ADMIN', 'ACCOUNTANT')
  const [inv, setInv] = useState<InvoiceDetail | null>(null)
  const [form, setForm] = useState<Form | null>(null)
  const [fileUrl, setFileUrl] = useState<string | null>(null)
  const [acceptIssues, setAcceptIssues] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)

  const load = useCallback(async () => {
    const { data } = await api.get<InvoiceDetail>(`/invoices/${id}`)
    setInv(data)
    setForm(data)
    return data
  }, [id])

  useEffect(() => {
    load().catch((e) => setMessage({ ok: false, text: errorMessage(e) }))
    let url: string | null = null
    api.get(`/invoices/${id}/file`, { responseType: 'blob' }).then((r) => {
      url = URL.createObjectURL(r.data as Blob)
      setFileUrl(url)
    })
    return () => {
      if (url) URL.revokeObjectURL(url)
    }
  }, [id, load])

  // Poll while the agent is still reading the bill.
  useEffect(() => {
    if (inv?.status !== 'PROCESSING') return
    const t = setTimeout(() => load(), 1500)
    return () => clearTimeout(t)
  }, [inv, load])

  const act = async (fn: () => Promise<unknown>, success: string) => {
    setBusy(true)
    setMessage(null)
    try {
      await fn()
      setMessage({ ok: true, text: success })
    } catch (e) {
      setMessage({ ok: false, text: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }

  if (!inv || !form) return <p className="text-sm text-slate-500">{message?.text ?? 'Loading…'}</p>

  const set = <K extends keyof Form>(k: K, v: Form[K]) => setForm({ ...form, [k]: v })
  const num = (v: string) => (v === '' ? null : Number(v))
  // Editing a line's value or rate recomputes its tax, split by supply type.
  const setLine = (i: number, patch: Partial<LineItem>) =>
    set('lineItems', form.lineItems.map((l, idx) => {
      if (idx !== i) return l
      const next = { ...l, ...patch }
      if (('taxableValue' in patch || 'gstRate' in patch) && next.taxableValue != null && next.gstRate != null) {
        const tax = Math.round(next.taxableValue * next.gstRate) / 100
        const half = Math.round(tax * 50) / 100
        Object.assign(next, inv.supplyType === 'INTER'
          ? { igst: tax, cgst: 0, sgst: 0 }
          : { cgst: half, sgst: half, igst: 0 })
      }
      return next
    }))

  const save = () =>
    act(async () => {
      const { data } = await api.put<InvoiceDetail>(`/invoices/${id}`, form)
      setInv(data)
      setForm(data)
    }, 'Saved and re-validated')

  const approve = () =>
    act(async () => {
      const { data } = await api.post<InvoiceDetail>(`/invoices/${id}/approve`, null, { params: { acceptIssues } })
      setInv(data)
      setForm(data)
    }, 'Approved — included in GST reports')

  const reprocess = () =>
    act(async () => {
      const { data } = await api.post<InvoiceDetail>(`/invoices/${id}/reprocess`)
      setInv(data)
    }, 'Sent back to the AI agent')

  const remove = () => act(async () => {
    await api.delete(`/invoices/${id}`)
    navigate('/invoices')
  }, 'Deleted')

  const errors = inv.validationIssues.filter((v) => v.severity === 'ERROR')
  const warnings = inv.validationIssues.filter((v) => v.severity === 'WARNING')
  const fieldHasError = (f: string) => errors.some((e) => e.field === f)
  const field = (f: string) => `input ${fieldHasError(f) ? 'border-red-400 bg-red-50' : ''}`
  const processing = inv.status === 'PROCESSING'

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-3">
        <Link to="/invoices" className="btn-secondary"><ArrowLeft className="size-4" /></Link>
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-xl font-semibold">{inv.supplierName ?? inv.fileName}</h1>
          <p className="text-xs text-slate-500">Bill #{inv.id} · {inv.fileName} · uploaded by {inv.uploadedBy}</p>
        </div>
        <StatusBadge status={inv.status} />
      </div>

      {message && (
        <div className={`rounded-lg px-4 py-2 text-sm ${message.ok ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-700'}`}>
          {message.text}
        </div>
      )}

      <div className="grid gap-5 xl:grid-cols-2">
        {/* Original document */}
        <div className="card overflow-hidden xl:sticky xl:top-4 xl:self-start">
          {fileUrl ? (
            inv.contentType === 'application/pdf' ? (
              <iframe src={fileUrl} title="Bill" className="h-[75vh] w-full" />
            ) : (
              <img src={fileUrl} alt="Bill" className="max-h-[75vh] w-full object-contain" />
            )
          ) : (
            <div className="p-10 text-center text-sm text-slate-500">Loading document…</div>
          )}
        </div>

        <div className="space-y-5">
          {processing && (
            <div className="card flex items-center gap-3 p-4 text-sm text-sky-800">
              <Bot className="size-5 animate-pulse" /> The AI agent is reading this bill and running GST checks…
            </div>
          )}
          {inv.status === 'FAILED' && (
            <div className="card border-red-200 p-4 text-sm text-red-700">Extraction failed: {inv.errorMessage}</div>
          )}

          {/* Agent findings */}
          {!processing && (
            <div className="card p-4">
              <div className="mb-3 flex items-center justify-between">
                <h2 className="flex items-center gap-2 text-sm font-semibold"><Bot className="size-4" /> Agent findings</h2>
                {inv.aiConfidence != null && (
                  <span className="text-xs text-slate-500">Confidence {(inv.aiConfidence * 100).toFixed(0)}%</span>
                )}
              </div>
              {inv.validationIssues.length === 0 ? (
                <p className="flex items-center gap-2 text-sm text-emerald-700"><CheckCircle2 className="size-4" /> All GST checks passed.</p>
              ) : (
                <ul className="space-y-2">
                  {[...errors, ...warnings].map((v, i) => (
                    <li key={i} className="flex gap-2 text-sm">
                      {v.severity === 'ERROR'
                        ? <AlertCircle className="mt-0.5 size-4 shrink-0 text-red-600" />
                        : <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-500" />}
                      <span>
                        {v.message}
                        {v.code === 'DUPLICATE_BILL' && inv.duplicateOfId && (
                          <Link to={`/invoices/${inv.duplicateOfId}`} className="ml-1 text-brand-700">View original →</Link>
                        )}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
              {inv.aiNotes && <p className="mt-3 border-t border-slate-100 pt-3 text-xs text-slate-500">Model notes: {inv.aiNotes}</p>}
            </div>
          )}

          {/* Editable extraction */}
          <fieldset disabled={!editable || processing || busy} className="card space-y-4 p-4">
            <h2 className="text-sm font-semibold">Bill details</h2>
            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="label">Supplier name</label>
                <input className="input" value={form.supplierName ?? ''} onChange={(e) => set('supplierName', e.target.value)} />
              </div>
              <div>
                <label className="label">Supplier GSTIN</label>
                <input className={`${field('supplierGstin')} font-mono`} value={form.supplierGstin ?? ''} onChange={(e) => set('supplierGstin', e.target.value.toUpperCase())} />
              </div>
              <div>
                <label className="label">Buyer name</label>
                <input className="input" value={form.buyerName ?? ''} onChange={(e) => set('buyerName', e.target.value)} />
              </div>
              <div>
                <label className="label">Buyer GSTIN</label>
                <input className={`${field('buyerGstin')} font-mono`} value={form.buyerGstin ?? ''} onChange={(e) => set('buyerGstin', e.target.value.toUpperCase())} />
              </div>
              <div>
                <label className="label">Invoice number</label>
                <input className={field('invoiceNumber')} value={form.invoiceNumber ?? ''} onChange={(e) => set('invoiceNumber', e.target.value)} />
              </div>
              <div>
                <label className="label">Invoice date</label>
                <input type="date" className={field('invoiceDate')} value={form.invoiceDate ?? ''} onChange={(e) => set('invoiceDate', e.target.value || null)} />
              </div>
              <div>
                <label className="label">Place of supply (state code)</label>
                <input className="input" maxLength={2} value={form.placeOfSupply ?? ''} onChange={(e) => set('placeOfSupply', e.target.value || null)} />
              </div>
              <div>
                <label className="label">Category</label>
                <select className="input" value={form.category ?? 'OTHER'} onChange={(e) => set('category', e.target.value)}>
                  {Object.entries(CATEGORIES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              {(['taxableValue', 'cgst', 'sgst', 'igst', 'cess', 'totalAmount'] as const).map((k) => (
                <div key={k}>
                  <label className="label">{{ taxableValue: 'Taxable value', cgst: 'CGST', sgst: 'SGST', igst: 'IGST', cess: 'Cess', totalAmount: 'Total' }[k]}</label>
                  <input type="number" step="0.01" className={`${field(k)} text-right tabular-nums`} value={form[k] ?? ''} onChange={(e) => set(k, num(e.target.value))} />
                </div>
              ))}
            </div>
            {inv.supplyType && (
              <p className="text-xs text-slate-500">
                Supply type: <b>{inv.supplyType === 'INTRA' ? 'Intra-state (CGST + SGST)' : 'Inter-state (IGST)'}</b>
                {!inv.itcEligible && ' · Input tax credit generally blocked for this category'}
              </p>
            )}

            <div>
              <div className="mb-2 flex items-center justify-between">
                <h3 className="text-sm font-semibold">Line items</h3>
                <button type="button" className="btn-secondary py-1 text-xs" onClick={() => set('lineItems', [...form.lineItems, { ...EMPTY_LINE }])}>
                  <Plus className="size-3" /> Add line
                </button>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[640px] text-sm">
                  <thead>
                    <tr className="border-b border-slate-200">
                      <th className="th">Description</th>
                      <th className="th">HSN/SAC</th>
                      <th className="th">Qty</th>
                      <th className="th">Taxable</th>
                      <th className="th">Rate %</th>
                      <th className="th">Tax</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {form.lineItems.map((l, i) => {
                      const lineErr = errors.some((e) => e.field.startsWith(`lineItems[${i}]`))
                      return (
                        <tr key={i} className={lineErr ? 'bg-red-50' : ''}>
                          <td className="py-1 pr-1"><input className="input py-1" value={l.description ?? ''} onChange={(e) => setLine(i, { description: e.target.value })} /></td>
                          <td className="py-1 pr-1"><input className="input w-24 py-1 font-mono" value={l.hsnSac ?? ''} onChange={(e) => setLine(i, { hsnSac: e.target.value })} /></td>
                          <td className="py-1 pr-1"><input type="number" className="input w-16 py-1" value={l.quantity ?? ''} onChange={(e) => setLine(i, { quantity: num(e.target.value) })} /></td>
                          <td className="py-1 pr-1"><input type="number" step="0.01" className="input w-28 py-1 text-right" value={l.taxableValue ?? ''} onChange={(e) => setLine(i, { taxableValue: num(e.target.value) })} /></td>
                          <td className="py-1 pr-1"><input type="number" step="0.01" className="input w-16 py-1" value={l.gstRate ?? ''} onChange={(e) => setLine(i, { gstRate: num(e.target.value) })} /></td>
                          <td className="py-1 pr-1 text-right text-xs tabular-nums text-slate-600">
                            {money((l.cgst ?? 0) + (l.sgst ?? 0) + (l.igst ?? 0))}
                          </td>
                          <td>
                            <button type="button" className="p-1 text-slate-400 hover:text-red-600" onClick={() => set('lineItems', form.lineItems.filter((_, idx) => idx !== i))}>
                              <Trash2 className="size-4" />
                            </button>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </fieldset>

          {editable && !processing && (
            <div className="card flex flex-wrap items-center gap-3 p-4">
              <button className="btn-primary" onClick={save} disabled={busy}><Save className="size-4" /> Save & re-check</button>
              {(inv.status === 'VALIDATED' || inv.status === 'NEEDS_REVIEW') && (
                <>
                  <button className="btn-secondary" onClick={approve} disabled={busy || (inv.status === 'NEEDS_REVIEW' && !acceptIssues)}>
                    <CheckCircle2 className="size-4" /> Approve
                  </button>
                  {inv.status === 'NEEDS_REVIEW' && (
                    <label className="flex items-center gap-2 text-xs text-slate-600">
                      <input type="checkbox" checked={acceptIssues} onChange={(e) => setAcceptIssues(e.target.checked)} />
                      Bill is correct as printed — approve despite errors
                    </label>
                  )}
                </>
              )}
              <button className="btn-secondary" onClick={reprocess} disabled={busy}><RefreshCw className="size-4" /> Re-scan with AI</button>
              {can('ADMIN') && (
                <button className="btn-danger ml-auto" onClick={remove} disabled={busy}><Trash2 className="size-4" /> Delete</button>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
