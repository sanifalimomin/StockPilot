import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { RefreshCw } from 'lucide-react';
import { api } from '../api';
import type { CreateMovementRequest, MovementType } from '../api/types';
import { PageHeader } from '../components/Layout';
import { DataTable, type Column } from '../components/DataTable';
import { FormField } from '../components/FormField';
import { Badge } from '../components/StatusBadge';
import { useToast } from '../components/Toast';
import { useProductsAll, useWarehouses } from '../lib/queries';
import { formatDateTime, generateIdempotencyKey } from '../lib/utils';
import type { Movement } from '../api/types';

const types: MovementType[] = ['INBOUND', 'OUTBOUND', 'TRANSFER', 'ADJUSTMENT'];

export function MovementsPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const products = useProductsAll();
  const warehouses = useWarehouses();

  const [type, setType] = useState<MovementType>('INBOUND');
  const [sku, setSku] = useState('');
  const [warehouseId, setWarehouseId] = useState('');
  const [fromWarehouseId, setFromWarehouseId] = useState('');
  const [toWarehouseId, setToWarehouseId] = useState('');
  const [qty, setQty] = useState<number>(1);
  const [idempotencyKey, setIdempotencyKey] = useState(generateIdempotencyKey());

  const movements = useQuery({
    queryKey: ['movements', { limit: 25 }],
    queryFn: () => api.listMovements({ limit: 25 }),
  });

  const whName = (id?: string) =>
    id ? (warehouses.data?.find((w) => w.id === id)?.name ?? id) : '—';

  const submit = useMutation({
    mutationFn: () => {
      const payload: CreateMovementRequest = {
        sku,
        warehouseId: type === 'TRANSFER' ? fromWarehouseId : warehouseId,
        type,
        qty,
        idempotencyKey,
        ...(type === 'TRANSFER' ? { fromWarehouseId, toWarehouseId } : {}),
      };
      return api.createMovement(payload);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['movements'] });
      qc.invalidateQueries({ queryKey: ['inventory'] });
      toast('Movement recorded');
      setIdempotencyKey(generateIdempotencyKey());
      setQty(1);
    },
    onError: (e) => toast(e instanceof Error ? e.message : 'Submit failed', 'error'),
  });

  const isTransfer = type === 'TRANSFER';
  const canSubmit =
    !!sku &&
    qty !== 0 &&
    (isTransfer ? !!fromWarehouseId && !!toWarehouseId && fromWarehouseId !== toWarehouseId : !!warehouseId);

  const columns: Column<Movement>[] = [
    {
      key: 'type',
      header: 'Type',
      render: (m) => (
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
      ),
    },
    { key: 'sku', header: 'SKU', render: (m) => <span className="font-mono text-xs">{m.sku}</span> },
    { key: 'qty', header: 'Qty', render: (m) => m.qty },
    { key: 'from', header: 'From', render: (m) => whName(m.fromWarehouseId) },
    { key: 'to', header: 'To', render: (m) => whName(m.toWarehouseId ?? m.warehouseId) },
    { key: 'at', header: 'When', render: (m) => formatDateTime(m.createdAt) },
  ];

  return (
    <div>
      <PageHeader title="Movements" subtitle="Record stock movements and view recent activity" />

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-3">
        <div className="card p-5 lg:col-span-1">
          <h3 className="mb-3 text-sm font-semibold text-slate-700">Record Movement</h3>

          <FormField label="Type">
            <select className="input" value={type} onChange={(e) => setType(e.target.value as MovementType)}>
              {types.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </FormField>

          <FormField label="Product (SKU)">
            <select className="input" value={sku} onChange={(e) => setSku(e.target.value)}>
              <option value="">Select a product…</option>
              {products.data?.map((p) => (
                <option key={p.id} value={p.sku}>
                  {p.sku} — {p.name}
                </option>
              ))}
            </select>
          </FormField>

          {!isTransfer && (
            <FormField label="Warehouse">
              <select className="input" value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)}>
                <option value="">Select warehouse…</option>
                {warehouses.data?.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.name}
                  </option>
                ))}
              </select>
            </FormField>
          )}

          {isTransfer && (
            <div className="grid grid-cols-2 gap-x-3">
              <FormField label="From">
                <select
                  className="input"
                  value={fromWarehouseId}
                  onChange={(e) => setFromWarehouseId(e.target.value)}
                >
                  <option value="">From…</option>
                  {warehouses.data?.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.name}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField label="To">
                <select
                  className="input"
                  value={toWarehouseId}
                  onChange={(e) => setToWarehouseId(e.target.value)}
                >
                  <option value="">To…</option>
                  {warehouses.data?.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.name}
                    </option>
                  ))}
                </select>
              </FormField>
            </div>
          )}

          <FormField
            label="Quantity"
            hint={type === 'ADJUSTMENT' ? 'Use a negative value to decrease stock.' : undefined}
          >
            <input
              type="number"
              className="input"
              value={qty}
              onChange={(e) => setQty(Number(e.target.value))}
            />
          </FormField>

          <FormField label="Idempotency Key">
            <div className="flex gap-2">
              <input className="input font-mono text-xs" value={idempotencyKey} readOnly />
              <button
                type="button"
                className="btn-secondary shrink-0"
                onClick={() => setIdempotencyKey(generateIdempotencyKey())}
                title="Regenerate"
              >
                <RefreshCw className="h-4 w-4" />
              </button>
            </div>
          </FormField>

          <button
            className="btn-primary mt-2 w-full"
            disabled={!canSubmit || submit.isPending}
            onClick={() => submit.mutate()}
          >
            {submit.isPending ? 'Submitting…' : 'Record Movement'}
          </button>
        </div>

        <div className="lg:col-span-2">
          <h3 className="mb-2 text-sm font-semibold text-slate-700">Recent Movements</h3>
          <DataTable
            columns={columns}
            rows={movements.data ?? []}
            rowKey={(m) => m.id}
            loading={movements.isLoading}
            error={movements.error}
          />
        </div>
      </div>
    </div>
  );
}
