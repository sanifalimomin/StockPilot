import { NavLink, Outlet } from 'react-router-dom';
import {
  LayoutDashboard,
  Package,
  Boxes,
  ArrowLeftRight,
  ShoppingCart,
  Bell,
  TrendingUp,
  FileBarChart,
  Warehouse,
} from 'lucide-react';
import { cn } from '../lib/utils';

const nav = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/products', label: 'Products', icon: Package },
  { to: '/inventory', label: 'Inventory', icon: Boxes },
  { to: '/movements', label: 'Movements', icon: ArrowLeftRight },
  { to: '/purchase-orders', label: 'Purchase Orders', icon: ShoppingCart },
  { to: '/alerts', label: 'Alerts', icon: Bell },
  { to: '/forecast', label: 'Forecast', icon: TrendingUp },
  { to: '/reports', label: 'Reports', icon: FileBarChart },
];

export function Layout() {
  return (
    <div className="flex min-h-screen">
      <aside className="fixed inset-y-0 left-0 flex w-60 flex-col border-r border-slate-200 bg-white">
        <div className="flex items-center gap-2 px-5 py-5">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-accent-600 text-white">
            <Warehouse className="h-5 w-5" />
          </div>
          <div>
            <p className="text-sm font-bold text-slate-900">IMS</p>
            <p className="text-xs text-slate-400">Inventory Cloud</p>
          </div>
        </div>
        <nav className="flex-1 space-y-1 px-3">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition',
                  isActive
                    ? 'bg-accent-50 text-accent-700'
                    : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900',
                )
              }
            >
              <item.icon className="h-4.5 w-4.5" />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="px-5 py-4 text-xs text-slate-400">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-100 px-2 py-1 font-medium text-emerald-700">
            <span className="h-1.5 w-1.5 rounded-full bg-current" />
            Live backend
          </span>
        </div>
      </aside>

      <div className="flex flex-1 flex-col pl-60">
        <header className="sticky top-0 z-20 flex h-14 items-center justify-between border-b border-slate-200 bg-white/80 px-6 backdrop-blur">
          <h1 className="text-sm font-semibold text-slate-700">
            Cloud-Native Inventory Management System
          </h1>
          <span className="text-xs text-slate-400">CSCI 5411 · Demo</span>
        </header>
        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
}) {
  return (
    <div className="mb-5 flex items-start justify-between">
      <div>
        <h2 className="text-xl font-semibold text-slate-900">{title}</h2>
        {subtitle && <p className="mt-0.5 text-sm text-slate-500">{subtitle}</p>}
      </div>
      {actions && <div className="flex gap-2">{actions}</div>}
    </div>
  );
}
