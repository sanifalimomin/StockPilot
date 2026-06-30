import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Package, Boxes, Bell, ShoppingCart } from 'lucide-react';
import { api } from '../api';
import { PageHeader } from '../components/Layout';
import { StatCard } from '../components/StatCard';
import { Badge } from '../components/StatusBadge';
import { useWarehouses } from '../lib/queries';
import { formatDateTime, formatNumber } from '../lib/utils';

export function DashboardPage() {
  const warehouses = useWarehouses();
  const products = useQuery({ queryKey: ['products', 'all'], queryFn: () => api.listProducts() });
  const inventory = useQuery({ queryKey: ['inventory', 'all'], queryFn: () => api.listInventory() });
  const movements = useQuery({
    queryKey: ['movements', { limit: 6 }],
    queryFn: () => api.listMovements({ limit: 6 }),
  });
  const alerts = useQuery({ queryKey: ['alerts', false], queryFn: () => api.listAlerts(false) });
  const pos = useQuery({ queryKey: ['purchase-orders'], queryFn: () => api.listPurchaseOrders() });

  const totalSkus = products.data?.length ?? 0;
  const totalOnHand = inventory.data?.reduce((s, i) => s + i.onHand, 0) ?? 0;
  const lowStock = alerts.data?.length ?? 0;
  const openPos = pos.data?.filter((p) => p.status === 'DRAFT' || p.status === 'ORDERED').length ?? 0;

  const warehouseMap = new Map(warehouses.data?.map((w) => [w.id, w.name]));
  const stockByWarehouse = Array.from(
    (inventory.data ?? []).reduce((acc, i) => {
      acc.set(i.warehouseId, (acc.get(i.warehouseId) ?? 0) + i.onHand);
      return acc;
    }, new Map<string, number>()),
  ).map(([id, units]) => ({ name: warehouseMap.get(id) ?? id, units }));

  return (
    <div>
      <PageHeader title="Dashboard" subtitle="Operational overview across all warehouses" />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Total SKUs" value={totalSkus} icon={Package} tone="accent" />
        <StatCard label="On-hand Units" value={formatNumber(totalOnHand)} icon={Boxes} tone="emerald" />
        <StatCard label="Low-stock Alerts" value={lowStock} icon={Bell} tone="red" />
        <StatCard label="Open POs" value={openPos} icon={ShoppingCart} tone="amber" />
      </div>

      <div className="mt-5 grid grid-cols-1 gap-5 lg:grid-cols-3">
        <div className="card p-5 lg:col-span-2">
          <h3 className="mb-4 text-sm font-semibold text-slate-700">Stock by Warehouse</h3>
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={stockByWarehouse}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                <XAxis dataKey="name" tick={{ fontSize: 12, fill: '#64748b' }} />
                <YAxis tick={{ fontSize: 12, fill: '#64748b' }} />
                <Tooltip cursor={{ fill: '#f1f5f9' }} />
                <Bar dataKey="units" fill="#6366f1" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="card p-5">
          <h3 className="mb-3 text-sm font-semibold text-slate-700">Active Low-stock Alerts</h3>
          <div className="space-y-2">
            {(alerts.data ?? []).length === 0 && (
              <p className="text-sm text-slate-400">No active alerts.</p>
            )}
            {(alerts.data ?? []).map((a) => (
              <div
                key={a.id}
                className="flex items-center justify-between rounded-lg border border-red-100 bg-red-50 px-3 py-2"
              >
                <div>
                  <p className="text-sm font-medium text-slate-800">{a.productName}</p>
                  <p className="text-xs text-slate-500">{a.sku}</p>
                </div>
                <Badge tone="danger">
                  {a.onHand}/{a.reorderPoint}
                </Badge>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="mt-5 card p-5">
        <h3 className="mb-3 text-sm font-semibold text-slate-700">Recent Movements</h3>
        <div className="divide-y divide-slate-100">
          {(movements.data ?? []).length === 0 && (
            <p className="py-3 text-sm text-slate-400">No recent movements.</p>
          )}
          {(movements.data ?? []).map((m) => (
            <div key={m.id} className="flex items-center justify-between py-2.5">
              <div className="flex items-center gap-3">
                <Badge
                  tone={
                    m.type === 'INBOUND'
                      ? 'success'
                      : m.type === 'OUTBOUND'
                        ? 'danger'
                        : m.type === 'TRANSFER'
                          ? 'accent'
                          : 'warning'
                  }
                >
                  {m.type}
                </Badge>
                <span className="text-sm font-medium text-slate-800">{m.sku}</span>
                <span className="text-sm text-slate-500">qty {m.qty}</span>
              </div>
              <span className="text-xs text-slate-400">{formatDateTime(m.createdAt)}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
