import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle } from 'lucide-react';
import { api } from '../api';
import type { ConsolidatedInventory } from '../api/types';
import { PageHeader } from '../components/Layout';
import { DataTable, type Column } from '../components/DataTable';
import { Badge } from '../components/StatusBadge';
import { useWarehouses } from '../lib/queries';
import { formatNumber } from '../lib/utils';

export function InventoryPage() {
  const [warehouseId, setWarehouseId] = useState<string>('');
  const warehouses = useWarehouses();

  const consolidated = useQuery({
    queryKey: ['inventory', 'consolidated'],
    queryFn: () => api.consolidatedInventory(),
  });

  const perWarehouse = useQuery({
    queryKey: ['inventory', 'levels', warehouseId],
    queryFn: () => api.listInventory(warehouseId ? { warehouseId } : undefined),
  });

  const whName = (id: string) => warehouses.data?.find((w) => w.id === id)?.name ?? id;

  const consolidatedCols: Column<ConsolidatedInventory>[] = [
    { key: 'sku', header: 'SKU', render: (r) => <span className="font-mono text-xs">{r.sku}</span> },
    { key: 'name', header: 'Product', render: (r) => <span className="font-medium">{r.productName}</span> },
    { key: 'onhand', header: 'Total On-hand', render: (r) => formatNumber(r.totalOnHand) },
    { key: 'reorder', header: 'Reorder Point', render: (r) => r.reorderPoint },
    {
      key: 'status',
      header: 'Status',
      render: (r) =>
        r.belowReorder ? (
          <Badge tone="danger">
            <AlertTriangle className="mr-1 h-3 w-3" /> Below reorder
          </Badge>
        ) : (
          <Badge tone="success">OK</Badge>
        ),
    },
  ];

  return (
    <div>
      <PageHeader title="Inventory" subtitle="Consolidated stock and per-warehouse drill-down" />

      <h3 className="mb-2 text-sm font-semibold text-slate-700">Consolidated (all warehouses)</h3>
      <DataTable
        columns={consolidatedCols}
        rows={consolidated.data ?? []}
        rowKey={(r) => r.sku}
        loading={consolidated.isLoading}
        error={consolidated.error}
        rowClassName={(r) => (r.belowReorder ? 'bg-red-50/60' : '')}
      />

      <div className="mt-6 mb-2 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-700">Per-warehouse levels</h3>
        <select
          className="input max-w-56"
          value={warehouseId}
          onChange={(e) => setWarehouseId(e.target.value)}
        >
          <option value="">All warehouses</option>
          {warehouses.data?.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </select>
      </div>
      <DataTable
        columns={[
          { key: 'sku', header: 'SKU', render: (r) => <span className="font-mono text-xs">{r.sku}</span> },
          { key: 'wh', header: 'Warehouse', render: (r) => whName(r.warehouseId) },
          { key: 'onhand', header: 'On-hand', render: (r) => formatNumber(r.onHand) },
          { key: 'reserved', header: 'Reserved', render: (r) => formatNumber(r.reserved) },
          { key: 'available', header: 'Available', render: (r) => formatNumber(r.available) },
        ]}
        rows={perWarehouse.data ?? []}
        rowKey={(r) => `${r.sku}-${r.warehouseId}`}
        loading={perWarehouse.isLoading}
        error={perWarehouse.error}
      />
    </div>
  );
}
