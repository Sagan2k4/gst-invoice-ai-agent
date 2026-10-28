import { AlertTriangle, ArrowRight, Upload } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api, errorMessage } from '../api/client'
import type { Dashboard, InvoiceStatus } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import Stat from '../components/Stat'
import { STATUS_LABELS } from '../components/StatusBadge'
import { money, moneyCompact, monthLabel } from '../lib/format'

export default function DashboardPage() {
  const { user, can } = useAuth()
  const [data, setData] = useState<Dashboard | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get<Dashboard>('/reports/dashboard').then((r) => setData(r.data)).catch((e) => setError(errorMessage(e)))
  }, [])

  if (error) return <p className="text-red-600">{error}</p>
  if (!data) return <p className="text-sm text-slate-500">Loading dashboard…</p>

  const m = data.currentMonth
  const needsReview = data.statusCounts.NEEDS_REVIEW + data.statusCounts.DUPLICATE + data.statusCounts.FAILED
  const totalBills = Object.values(data.statusCounts).reduce((a, b) => a + b, 0)

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Namaste, {user?.fullName.split(' ')[0]}</h1>
          <p className="text-sm text-slate-500">This month's purchases at a glance for {user?.businessName}.</p>
        </div>
        {can('ADMIN', 'ACCOUNTANT') && (
          <Link to="/upload" className="btn-primary"><Upload className="size-4" /> Scan bills</Link>
        )}
      </div>

      {needsReview > 0 && (
        <Link to="/invoices?status=NEEDS_REVIEW" className="card flex items-center gap-3 border-amber-200 bg-amber-50 p-4 text-amber-900">
          <AlertTriangle className="size-5 shrink-0" />
          <span className="flex-1 text-sm">
            <b>{needsReview}</b> bill{needsReview > 1 ? 's' : ''} need attention (review, duplicates or failed scans).
          </span>
          <ArrowRight className="size-4" />
        </Link>
      )}

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Purchases (taxable)" value={moneyCompact(m.taxableValue)} hint={`Total incl. tax ${money(m.totalAmount)}`} />
        <Stat label="GST paid" value={moneyCompact(m.totalTax)} hint={`CGST ${moneyCompact(m.cgst)} · SGST ${moneyCompact(m.sgst)} · IGST ${moneyCompact(m.igst)}`} />
        <Stat label="Eligible ITC" value={<span className="text-brand-700">{moneyCompact(data.eligibleItc)}</span>} hint="Claimable input tax credit" />
        <Stat label="Blocked ITC" value={moneyCompact(data.blockedItc)} hint="Sec 17(5): food, vehicles…" />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="card p-5 lg:col-span-2">
          <h2 className="mb-4 text-sm font-semibold">Last 6 months</h2>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data.trend.map((t) => ({ ...t, month: monthLabel(t.month) }))}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                <XAxis dataKey="month" tick={{ fontSize: 12 }} />
                <YAxis tickFormatter={(v) => moneyCompact(v)} tick={{ fontSize: 12 }} width={70} />
                <Tooltip formatter={(v) => money(Number(v))} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Bar dataKey="taxableValue" name="Taxable value" fill="#0f766e" radius={[4, 4, 0, 0]} />
                <Bar dataKey="totalTax" name="GST" fill="#5eead4" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="card p-5">
          <h2 className="mb-4 text-sm font-semibold">Bills by status</h2>
          <ul className="space-y-2">
            {(Object.keys(data.statusCounts) as InvoiceStatus[]).map((s) => (
              <li key={s}>
                <Link to={`/invoices?status=${s}`} className="flex items-center justify-between rounded-lg px-2 py-1.5 text-sm hover:bg-slate-50">
                  <span className="text-slate-600">{STATUS_LABELS[s]}</span>
                  <span className="font-semibold tabular-nums">{data.statusCounts[s]}</span>
                </Link>
              </li>
            ))}
            <li className="flex justify-between border-t border-slate-100 px-2 pt-2 text-sm">
              <span className="text-slate-500">All bills</span>
              <span className="font-semibold tabular-nums">{totalBills}</span>
            </li>
          </ul>
        </div>
      </div>

      <div className="card p-5">
        <h2 className="mb-4 text-sm font-semibold">Where the money went this month</h2>
        {data.topCategories.length === 0 ? (
          <p className="text-sm text-slate-500">No validated bills this month yet.</p>
        ) : (
          <div className="space-y-3">
            {data.topCategories.map((c) => {
              const pct = m.taxableValue ? (c.taxableValue / m.taxableValue) * 100 : 0
              return (
                <div key={c.category}>
                  <div className="mb-1 flex justify-between text-sm">
                    <span>
                      {c.label}
                      {!c.itcEligible && <span className="ml-2 rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-500">ITC blocked</span>}
                    </span>
                    <span className="tabular-nums text-slate-600">{money(c.taxableValue)}</span>
                  </div>
                  <div className="h-2 rounded-full bg-slate-100">
                    <div className="h-2 rounded-full bg-brand-600" style={{ width: `${pct}%` }} />
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
