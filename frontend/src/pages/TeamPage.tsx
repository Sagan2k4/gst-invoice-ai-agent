import { UserPlus } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { api, errorMessage } from '../api/client'
import type { Role, TeamUser } from '../api/types'
import { useAuth } from '../auth/AuthContext'

const ROLE_HELP: Record<Role, string> = {
  ADMIN: 'Everything, including team management',
  ACCOUNTANT: 'Scan, review, approve bills and download reports',
  VIEWER: 'Read-only access to bills and reports',
}

export default function TeamPage() {
  const { user, can } = useAuth()
  const [users, setUsers] = useState<TeamUser[]>([])
  const [form, setForm] = useState({ fullName: '', email: '', password: '', role: 'ACCOUNTANT' as Role })
  const [error, setError] = useState('')

  const load = () => api.get<TeamUser[]>('/users').then((r) => setUsers(r.data)).catch((e) => setError(errorMessage(e)))
  useEffect(() => {
    if (can('ADMIN')) load()
  }, [can])

  if (!can('ADMIN')) return <Navigate to="/" replace />

  const add = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      await api.post('/users', form)
      setForm({ fullName: '', email: '', password: '', role: 'ACCOUNTANT' })
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const update = async (u: TeamUser, patch: Partial<Pick<TeamUser, 'role' | 'active'>>) => {
    setError('')
    try {
      await api.put(`/users/${u.id}`, { role: u.role, active: u.active, ...patch })
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Team</h1>
        <p className="text-sm text-slate-500">Give your accountant or staff access with the right role.</p>
      </div>

      <form onSubmit={add} className="card grid gap-3 p-4 sm:grid-cols-5 sm:items-end">
        <div>
          <label className="label">Name</label>
          <input className="input" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required />
        </div>
        <div>
          <label className="label">Email</label>
          <input type="email" className="input" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
        </div>
        <div>
          <label className="label">Temporary password</label>
          <input type="password" minLength={8} className="input" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
        </div>
        <div>
          <label className="label">Role</label>
          <select className="input" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
            {(Object.keys(ROLE_HELP) as Role[]).map((r) => <option key={r} value={r}>{r.toLowerCase()}</option>)}
          </select>
        </div>
        <button className="btn-primary"><UserPlus className="size-4" /> Add member</button>
      </form>
      {error && <p className="text-sm text-red-600">{error}</p>}

      <div className="card overflow-x-auto">
        <table className="w-full min-w-[600px]">
          <thead className="border-b border-slate-200 bg-slate-50">
            <tr><th className="th">Member</th><th className="th">Role</th><th className="th">Access</th></tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {users.map((u) => (
              <tr key={u.id}>
                <td className="td">
                  <div className="font-medium">{u.fullName} {u.id === user?.id && <span className="text-xs text-slate-400">(you)</span>}</div>
                  <div className="text-xs text-slate-500">{u.email}</div>
                </td>
                <td className="td">
                  <select className="input w-auto py-1" value={u.role} disabled={u.id === user?.id} onChange={(e) => update(u, { role: e.target.value as Role })}>
                    {(Object.keys(ROLE_HELP) as Role[]).map((r) => <option key={r} value={r}>{r.toLowerCase()}</option>)}
                  </select>
                  <div className="mt-1 text-xs text-slate-500">{ROLE_HELP[u.role]}</div>
                </td>
                <td className="td">
                  <label className="flex items-center gap-2 text-sm">
                    <input type="checkbox" checked={u.active} disabled={u.id === user?.id} onChange={(e) => update(u, { active: e.target.checked })} />
                    {u.active ? 'Active' : 'Deactivated'}
                  </label>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
