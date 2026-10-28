import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { AuthShell } from './LoginPage'

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ businessName: '', businessGstin: '', fullName: '', email: '', password: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: k === 'businessGstin' ? e.target.value.toUpperCase() : e.target.value })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await register(form)
      navigate('/')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Create your business account" subtitle="You'll be the admin and can invite your team.">
      <form onSubmit={submit} className="space-y-4">
        <div>
          <label className="label" htmlFor="bn">Business name</label>
          <input id="bn" className="input" value={form.businessName} onChange={set('businessName')} required />
        </div>
        <div>
          <label className="label" htmlFor="gstin">Your GSTIN (optional)</label>
          <input id="gstin" className="input font-mono" maxLength={15} placeholder="29ABCDE1234F1Z5"
                 value={form.businessGstin} onChange={set('businessGstin')} />
          <p className="mt-1 text-xs text-slate-500">Used to decide CGST+SGST vs IGST and to check bills are addressed to you.</p>
        </div>
        <div>
          <label className="label" htmlFor="name">Your name</label>
          <input id="name" className="input" value={form.fullName} onChange={set('fullName')} required />
        </div>
        <div>
          <label className="label" htmlFor="email">Email</label>
          <input id="email" type="email" className="input" value={form.email} onChange={set('email')} required />
        </div>
        <div>
          <label className="label" htmlFor="pw">Password</label>
          <input id="pw" type="password" minLength={8} className="input" value={form.password} onChange={set('password')} required />
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button className="btn-primary w-full" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
        <p className="text-center text-sm text-slate-500">
          Already registered? <Link to="/login" className="font-medium text-brand-700">Sign in</Link>
        </p>
      </form>
    </AuthShell>
  )
}
