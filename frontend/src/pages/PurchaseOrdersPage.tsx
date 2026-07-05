import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import { api } from '../api';
import type { CreatePurchaseOrderRequest, PurchaseOrder, PurchaseOrderStatus } from '../api/types';
import { PageHeader } from '../components/Layout';
import { DataTable, type Column } from '../components/DataTable';
import { FormField } from '../components/FormField';
import { Modal } from '../components/Modal';
import { StatusBadge } from '../components/StatusBadge';
import { useToast } from '../components/Toast';
import { useProductsAll, useSuppliers } from '../lib/queries';
import { formatCurrency, formatDate } from '../lib/utils';

const nextStatus: Partial<Record<PurchaseOrderStatus, PurchaseOrderStatus>> = {
  DRAFT: 'ORDERED',
  ORDERED: 'RECEIVED',
};

interface DraftLine {
  productId: string;
  qty: number;
  unitCost: string;
}

const emptyLine = (): DraftLine => ({ productId: '', qty: 1, unitCost: '' });

export function PurchaseOrdersPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const suppliers = useSuppliers();
  const products = useProductsAll();
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [supplierId, setSupplierId] = useState('');
  const [lines, setLines] = useState<DraftLine[]>([emptyLine()]);

  const pos = useQuery({ queryKey: ['purchase-orders'], queryFn: () => api.listPurchaseOrders() });

  const detail = useQuery({
    queryKey: ['purchase-orders', selectedId],
    queryFn: () => api.getPurchaseOrder(selectedId!),
    enabled: !!selectedId,
  });

  const transition = useMutation({
    mutationFn: ({ id, status }: { id: string; status: PurchaseOrderStatus }) =>
      api.transitionPurchaseOrder(id, status),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ['purchase-orders'] });
      toast(`PO moved to ${vars.status}`);
    },
    onError: (e) => toast(e instanceof Error ? e.message : 'Transition failed', 'error'),
  });

  const resetCreate = () => {
    setSupplierId('');
    setLines([emptyLine()]);
  };

  const create = useMutation({
    mutationFn: () => {
      const payload: CreatePurchaseOrderRequest = {
        supplierId,
        lines: lines
          .filter((l) => l.productId && l.qty > 0)
          .map((l) => ({
            productId: l.productId,
            qty: l.qty,
            unitCost: l.unitCost === '' ? undefined : Number(l.unitCost),
          })),
      };
      return api.createPurchaseOrder(payload);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['purchase-orders'] });
      toast('Purchase order created');
      setCreateOpen(false);
      resetCreate();
    },
    onError: (e) => toast(e instanceof Error ? e.message : 'Create failed', 'error'),
  });

  const updateLine = (idx: number, patch: Partial<DraftLine>) =>
    setLines((prev) => prev.map((l, i) => (i === idx ? { ...l, ...patch } : l)));
  const addLine = () => setLines((prev) => [...prev, emptyLine()]);
  const removeLine = (idx: number) =>
    setLines((prev) => (prev.length > 1 ? prev.filter((_, i) => i !== idx) : prev));

  const onPickProduct = (idx: number, productId: string) => {
    const product = products.data?.find((p) => p.id === productId);
    updateLine(idx, {
      productId,

      unitCost: product ? String(product.unitCost) : '',
    });
  };

  const draftTotal = lines.reduce((sum, l) => {
    const product = products.data?.find((p) => p.id === l.productId);
    const cost = l.unitCost === '' ? (product?.unitCost ?? 0) : Number(l.unitCost);
    return sum + (l.productId ? l.qty * cost : 0);
  }, 0);

  const validLines = lines.filter((l) => l.productId && l.qty > 0);
  const canCreate = !!supplierId && validLines.length > 0 && !create.isPending;

  const supName = (id: string) => suppliers.data?.find((s) => s.id === id)?.name ?? id;

  const columns: Column<PurchaseOrder>[] = [
    { key: 'ref', header: 'Reference', render: (p) => <span className="font-medium">{p.reference}</span> },
    { key: 'supplier', header: 'Supplier', render: (p) => supName(p.supplierId) },
    { key: 'lines', header: 'Lines', render: (p) => p.lines.length },
    { key: 'total', header: 'Total', render: (p) => formatCurrency(p.total) },
    { key: 'status', header: 'Status', render: (p) => <StatusBadge status={p.status} /> },
    { key: 'created', header: 'Created', render: (p) => formatDate(p.createdAt) },
    {
      key: 'actions',
      header: '',
      className: 'text-right',
      render: (p) => (
        <button className="btn-secondary py-1 text-xs" onClick={() => setSelectedId(p.id)}>
          View
        </button>
      ),
    },
  ];

  const po = detail.data;
  const next = po ? nextStatus[po.status] : undefined;

  return (
    <div>
      <PageHeader
        title="Purchase Orders"
        subtitle="Track procurement and order lifecycle"
        actions={
          <button className="btn-primary" onClick={() => setCreateOpen(true)}>
            <Plus className="mr-1.5 h-4 w-4" />
            New Purchase Order
          </button>
        }
      />

      <DataTable
        columns={columns}
        rows={pos.data ?? []}
        rowKey={(p) => p.id}
        loading={pos.isLoading}
        error={pos.error}
      />

      {}
      <Modal
        open={createOpen}
        title="New Purchase Order"
        onClose={() => {
          setCreateOpen(false);
          resetCreate();
        }}
        footer={
          <>
            <button
              className="btn-secondary"
              onClick={() => {
                setCreateOpen(false);
                resetCreate();
              }}
            >
              Cancel
            </button>
            <button className="btn-primary" disabled={!canCreate} onClick={() => create.mutate()}>
              {create.isPending ? 'Creating…' : 'Create PO'}
            </button>
          </>
        }
      >
        <FormField label="Supplier">
          <select className="input" value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
            <option value="">Select a supplier…</option>
            {suppliers.data?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </FormField>

        <div className="mb-2 flex items-center justify-between">
          <span className="label mb-0">Line items</span>
          <button type="button" className="btn-secondary py-1 text-xs" onClick={addLine}>
            <Plus className="mr-1 h-3.5 w-3.5" />
            Add line
          </button>
        </div>

        <div className="space-y-2">
          {lines.map((l, idx) => (
            <div key={idx} className="grid grid-cols-[1fr_4.5rem_5.5rem_auto] items-center gap-2">
              <select
                className="input"
                value={l.productId}
                onChange={(e) => onPickProduct(idx, e.target.value)}
              >
                <option value="">Product…</option>
                {products.data?.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.sku} — {p.name}
                  </option>
                ))}
              </select>
              <input
                type="number"
                min={1}
                className="input"
                aria-label="Quantity"
                value={l.qty}
                onChange={(e) => updateLine(idx, { qty: Number(e.target.value) })}
              />
              <input
                type="number"
                min={0}
                step="0.01"
                className="input"
                aria-label="Unit cost"
                placeholder="Cost"
                value={l.unitCost}
                onChange={(e) => updateLine(idx, { unitCost: e.target.value })}
              />
              <button
                type="button"
                className="text-slate-400 hover:text-red-600 disabled:opacity-30"
                onClick={() => removeLine(idx)}
                disabled={lines.length === 1}
                title="Remove line"
              >
                <Trash2 className="h-4 w-4" />
              </button>
            </div>
          ))}
        </div>

        <div className="mt-4 flex justify-between border-t border-slate-200 pt-3 text-sm">
          <span className="text-slate-500">Estimated total</span>
          <span className="font-semibold">{formatCurrency(draftTotal)}</span>
        </div>
      </Modal>

      {}
      <Modal
        open={!!selectedId}
        title={po ? po.reference : 'Purchase Order'}
        onClose={() => setSelectedId(null)}
        footer={
          <>
            <button className="btn-secondary" onClick={() => setSelectedId(null)}>
              Close
            </button>
            {po && next && (
              <button
                className="btn-primary"
                disabled={transition.isPending}
                onClick={() => transition.mutate({ id: po.id, status: next })}
              >
                Move to {next}
              </button>
            )}
          </>
        }
      >
        {detail.isLoading && <p className="text-sm text-slate-400">Loading…</p>}
        {po && (
          <div>
            <div className="mb-4 grid grid-cols-2 gap-3 text-sm">
              <div>
                <p className="text-xs text-slate-400">Supplier</p>
                <p className="font-medium">{supName(po.supplierId)}</p>
              </div>
              <div>
                <p className="text-xs text-slate-400">Status</p>
                <StatusBadge status={po.status} />
              </div>
              <div>
                <p className="text-xs text-slate-400">Created</p>
                <p>{formatDate(po.createdAt)}</p>
              </div>
              <div>
                <p className="text-xs text-slate-400">Expected</p>
                <p>{po.expectedAt ? formatDate(po.expectedAt) : '—'}</p>
              </div>
            </div>

            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-xs uppercase text-slate-500">
                  <th className="py-2">SKU</th>
                  <th className="py-2">Product</th>
                  <th className="py-2 text-right">Qty</th>
                  <th className="py-2 text-right">Unit Cost</th>
                  <th className="py-2 text-right">Line Total</th>
                </tr>
              </thead>
              <tbody>
                {po.lines.map((l) => (
                  <tr key={l.sku} className="border-b border-slate-100">
                    <td className="py-2 font-mono text-xs">{l.sku}</td>
                    <td className="py-2">{l.productName}</td>
                    <td className="py-2 text-right">{l.qty}</td>
                    <td className="py-2 text-right">{formatCurrency(l.unitCost)}</td>
                    <td className="py-2 text-right">{formatCurrency(l.qty * l.unitCost)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={4} className="py-2 text-right font-medium">
                    Total
                  </td>
                  <td className="py-2 text-right font-semibold">{formatCurrency(po.total)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        )}
      </Modal>
    </div>
  );
}
