import { FileText, LayoutDashboard, LogOut, PieChart, Upload, Users } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export default function Layout() {
  const { user, logout, can } = useAuth()
  const nav = [
    { to: '/', label: 'Dashboard', icon: LayoutDashboard, show: true },
    { to: '/upload', label: 'Scan bills', icon: Upload, show: can('ADMIN', 'ACCOUNTANT') },
    { to: '/invoices', label: 'Bills', icon: FileText, show: true },
    { to: '/reports', label: 'GST reports', icon: PieChart, show: true },
    { to: '/team', label: 'Team', icon: Users, show: can('ADMIN') },
  ]

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-60 shrink-0 flex-col border-r border-slate-200 bg-white md:flex">
        <div className="flex items-center gap-2 px-5 py-5">
          <img src="/favicon.svg" alt="" className="size-8" />
          <div>
            <div className="text-sm font-semibold">GST Invoice Agent</div>
            <div className="text-xs text-slate-500">Bills → GST-ready books</div>
          </div>
        </div>
        <nav className="flex-1 space-y-1 px-3">
          {nav.filter((n) => n.show).map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium ${
                  isActive ? 'bg-brand-50 text-brand-800' : 'text-slate-600 hover:bg-slate-50'
                }`
              }
            >
              <Icon className="size-4" />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-slate-200 p-4">
          <div className="truncate text-sm font-medium">{user?.businessName}</div>
          <div className="truncate text-xs text-slate-500">
            {user?.fullName} · {user?.role.toLowerCase()}
          </div>
          <button onClick={logout} className="btn-secondary mt-3 w-full">
            <LogOut className="size-4" /> Sign out
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3 md:hidden">
          <span className="font-semibold">GST Invoice Agent</span>
          <button onClick={logout} className="btn-secondary">
            <LogOut className="size-4" />
          </button>
        </header>
        <nav className="flex gap-1 overflow-x-auto border-b border-slate-200 bg-white px-2 py-2 md:hidden">
          {nav.filter((n) => n.show).map(({ to, label }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                `whitespace-nowrap rounded-lg px-3 py-1.5 text-sm ${isActive ? 'bg-brand-50 text-brand-800' : 'text-slate-600'}`
              }
            >
              {label}
            </NavLink>
          ))}
        </nav>
        <main className="mx-auto w-full max-w-7xl flex-1 p-4 md:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
