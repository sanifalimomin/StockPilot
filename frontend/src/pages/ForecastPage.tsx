import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { TrendingUp } from 'lucide-react';
import { api } from '../api';
import { PageHeader } from '../components/Layout';
import { Badge } from '../components/StatusBadge';
import { useProductsAll } from '../lib/queries';

export function ForecastPage() {
  const products = useProductsAll();
  const [sku, setSku] = useState('');
  const [days, setDays] = useState(30);

  const effectiveSku = sku || products.data?.[0]?.sku || '';

  const forecast = useQuery({
    queryKey: ['forecast', effectiveSku, days],
    queryFn: () => api.forecast(effectiveSku, days),
    enabled: !!effectiveSku,
  });

  return (
    <div>
      <PageHeader title="Forecast" subtitle="Projected demand by SKU" />

      <div className="card mb-4 flex flex-wrap items-end gap-3 p-4">
        <div className="min-w-64">
          <label className="label">Product (SKU)</label>
          <select className="input" value={effectiveSku} onChange={(e) => setSku(e.target.value)}>
            {products.data?.map((p) => (
              <option key={p.id} value={p.sku}>
                {p.sku} — {p.name}
              </option>
            ))}
          </select>
        </div>
        <div className="min-w-32">
          <label className="label">Horizon (days)</label>
          <select className="input" value={days} onChange={(e) => setDays(Number(e.target.value))}>
            {[14, 30, 60, 90].map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </select>
        </div>
        {forecast.data && (
          <div className="ml-auto flex items-center gap-2">
            <span className="text-sm text-slate-500">Method</span>
            <Badge tone="accent">{forecast.data.method}</Badge>
          </div>
        )}
      </div>

      <div className="card p-5">
        <div className="mb-4 flex items-center gap-2">
          <TrendingUp className="h-5 w-5 text-accent-600" />
          <h3 className="text-sm font-semibold text-slate-700">
            Forecasted Demand — {effectiveSku}
          </h3>
        </div>
        {forecast.isLoading && <p className="text-sm text-slate-400">Loading forecast…</p>}
        {forecast.error && (
          <p className="text-sm text-red-500">
            {forecast.error instanceof Error ? forecast.error.message : 'Failed to load forecast.'}
          </p>
        )}
        {forecast.data && (
          <div className="h-80">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={forecast.data.points}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="date" tick={{ fontSize: 11, fill: '#64748b' }} minTickGap={24} />
                <YAxis tick={{ fontSize: 12, fill: '#64748b' }} />
                <Tooltip />
                <Line
                  type="monotone"
                  dataKey="qty"
                  stroke="#6366f1"
                  strokeWidth={2}
                  dot={false}
                  activeDot={{ r: 4 }}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>
    </div>
  );
}
