import { useQuery } from '@tanstack/react-query';
import { AlertTriangle } from 'lucide-react';
import { api } from '../api';
import type { Alert } from '../api/types';
import { PageHeader } from '../components/Layout';
import { DataTable, type Column } from '../components/DataTable';
import { Badge } from '../components/StatusBadge';
import { useWarehouses } from '../lib/queries';
import { formatDateTime } from '../lib/utils';

export function AlertsPage() {
  const warehouses = useWarehouses();
  const alerts = useQuery({ queryKey: ['alerts', false], queryFn: () => api.listAlerts(false) });
  const whName = (id: string) => warehouses.data?.find((w) => w.id === id)?.name ?? id;

  const columns: Column<Alert>[] = [
    {
      key: 'sku',
      header: 'SKU',
      render: (a) => <span className="font-mono text-xs">{a.sku}</span>,
    },
    { key: 'name', header: 'Product', render: (a) => <span className="font-medium">{a.productName}</span> },
    { key: 'wh', header: 'Warehouse', render: (a) => whName(a.warehouseId) },
    {
      key: 'level',
      header: 'On-hand / Reorder',
      render: (a) => (
        <Badge tone="danger">
          {a.onHand} / {a.reorderPoint}
        </Badge>
      ),
    },
    {
      key: 'suggest',
      header: 'Suggested Reorder',
      render: (a) => <span className="font-semibold text-accent-700">{a.suggestedReorderQty}</span>,
    },
    { key: 'at', header: 'Raised', render: (a) => formatDateTime(a.createdAt) },
  ];

  return (
    <div>
      <PageHeader
        title="Alerts"
        subtitle="Low-stock alerts with suggested reorder quantities"
        actions={
          <span className="inline-flex items-center gap-1.5 rounded-full bg-red-50 px-3 py-1.5 text-sm font-medium text-red-700">
            <AlertTriangle className="h-4 w-4" />
            {alerts.data?.length ?? 0} active
          </span>
        }
      />
      <DataTable
        columns={columns}
        rows={alerts.data ?? []}
        rowKey={(a) => a.id}
        loading={alerts.isLoading}
        error={alerts.error}
        emptyMessage="No active low-stock alerts."
        rowClassName={() => 'bg-red-50/40'}
      />
    </div>
  );
}
