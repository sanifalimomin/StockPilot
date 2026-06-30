import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Pencil, Trash2 } from 'lucide-react';
import { api } from '../api';
import type { Product, ProductFilters } from '../api/types';
import { PageHeader } from '../components/Layout';
import { DataTable, type Column } from '../components/DataTable';
import { Modal } from '../components/Modal';
import { FormField } from '../components/FormField';
import { useToast } from '../components/Toast';
import { useCategories, useSuppliers } from '../lib/queries';
import { formatCurrency } from '../lib/utils';

type FormState = Omit<Product, 'id'>;

const emptyForm: FormState = {
  sku: '',
  name: '',
  categoryId: '',
  supplierId: '',
  unitCost: 0,
  unitPrice: 0,
  reorderPoint: 0,
  reorderQty: 0,
};

export function ProductsPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const categories = useCategories();
  const suppliers = useSuppliers();

  const [filters, setFilters] = useState<ProductFilters>({});
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Product | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);

  const products = useQuery({
    queryKey: ['products', filters],
    queryFn: () => api.listProducts(filters),
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['products'] });

  const saveMutation = useMutation({
    mutationFn: (input: FormState) =>
      editing ? api.updateProduct(editing.id, input) : api.createProduct(input),
    onSuccess: () => {
      invalidate();
      setModalOpen(false);
      toast(editing ? 'Product updated' : 'Product created');
    },
    onError: (e) => toast(e instanceof Error ? e.message : 'Save failed', 'error'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteProduct(id),
    onSuccess: () => {
      invalidate();
      toast('Product deleted');
    },
    onError: (e) => toast(e instanceof Error ? e.message : 'Delete failed', 'error'),
  });

  const catName = (id: string) => categories.data?.find((c) => c.id === id)?.name ?? id;
  const supName = (id: string) => suppliers.data?.find((s) => s.id === id)?.name ?? id;

  const openCreate = () => {
    setEditing(null);
    setForm({
      ...emptyForm,
      categoryId: categories.data?.[0]?.id ?? '',
      supplierId: suppliers.data?.[0]?.id ?? '',
    });
    setModalOpen(true);
  };

  const openEdit = (p: Product) => {
    setEditing(p);
    const { id: _id, ...rest } = p;
    void _id;
    setForm(rest);
    setModalOpen(true);
  };

  const columns: Column<Product>[] = [
    { key: 'sku', header: 'SKU', render: (p) => <span className="font-mono text-xs">{p.sku}</span> },
    { key: 'name', header: 'Name', render: (p) => <span className="font-medium">{p.name}</span> },
    { key: 'category', header: 'Category', render: (p) => catName(p.categoryId) },
    { key: 'supplier', header: 'Supplier', render: (p) => supName(p.supplierId) },
    { key: 'price', header: 'Price', render: (p) => formatCurrency(p.unitPrice) },
    { key: 'reorder', header: 'Reorder Pt', render: (p) => p.reorderPoint },
    {
      key: 'actions',
      header: '',
      className: 'text-right',
      render: (p) => (
        <div className="flex justify-end gap-1">
          <button
            className="rounded p-1.5 text-slate-400 hover:bg-slate-100 hover:text-accent-600"
            onClick={() => openEdit(p)}
          >
            <Pencil className="h-4 w-4" />
          </button>
          <button
            className="rounded p-1.5 text-slate-400 hover:bg-red-50 hover:text-red-600"
            onClick={() => {
              if (confirm(`Delete ${p.name}?`)) deleteMutation.mutate(p.id);
            }}
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Products"
        subtitle="Manage the product catalog"
        actions={
          <button className="btn-primary" onClick={openCreate}>
            <Plus className="h-4 w-4" /> New Product
          </button>
        }
      />

      <div className="card mb-4 flex flex-wrap items-end gap-3 p-4">
        <div className="flex-1 min-w-48">
          <label className="label">Search</label>
          <input
            className="input"
            placeholder="Name or SKU…"
            value={filters.q ?? ''}
            onChange={(e) => setFilters((f) => ({ ...f, q: e.target.value || undefined }))}
          />
        </div>
        <div className="min-w-44">
          <label className="label">Category</label>
          <select
            className="input"
            value={filters.categoryId ?? ''}
            onChange={(e) => setFilters((f) => ({ ...f, categoryId: e.target.value || undefined }))}
          >
            <option value="">All categories</option>
            {categories.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
        <div className="min-w-44">
          <label className="label">Supplier</label>
          <select
            className="input"
            value={filters.supplierId ?? ''}
            onChange={(e) => setFilters((f) => ({ ...f, supplierId: e.target.value || undefined }))}
          >
            <option value="">All suppliers</option>
            {suppliers.data?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      <DataTable
        columns={columns}
        rows={products.data ?? []}
        rowKey={(p) => p.id}
        loading={products.isLoading}
        error={products.error}
        emptyMessage="No products match your filters."
      />

      <Modal
        open={modalOpen}
        title={editing ? 'Edit Product' : 'New Product'}
        onClose={() => setModalOpen(false)}
        footer={
          <>
            <button className="btn-secondary" onClick={() => setModalOpen(false)}>
              Cancel
            </button>
            <button
              className="btn-primary"
              disabled={saveMutation.isPending}
              onClick={() => saveMutation.mutate(form)}
            >
              {saveMutation.isPending ? 'Saving…' : 'Save'}
            </button>
          </>
        }
      >
        <div className="grid grid-cols-2 gap-x-4">
          <FormField label="SKU">
            <input
              className="input"
              value={form.sku}
              onChange={(e) => setForm((f) => ({ ...f, sku: e.target.value }))}
            />
          </FormField>
          <FormField label="Name">
            <input
              className="input"
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            />
          </FormField>
          <FormField label="Category">
            <select
              className="input"
              value={form.categoryId}
              onChange={(e) => setForm((f) => ({ ...f, categoryId: e.target.value }))}
            >
              {categories.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Supplier">
            <select
              className="input"
              value={form.supplierId}
              onChange={(e) => setForm((f) => ({ ...f, supplierId: e.target.value }))}
            >
              {suppliers.data?.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Unit Cost">
            <input
              type="number"
              className="input"
              value={form.unitCost}
              onChange={(e) => setForm((f) => ({ ...f, unitCost: Number(e.target.value) }))}
            />
          </FormField>
          <FormField label="Unit Price">
            <input
              type="number"
              className="input"
              value={form.unitPrice}
              onChange={(e) => setForm((f) => ({ ...f, unitPrice: Number(e.target.value) }))}
            />
          </FormField>
          <FormField label="Reorder Point">
            <input
              type="number"
              className="input"
              value={form.reorderPoint}
              onChange={(e) => setForm((f) => ({ ...f, reorderPoint: Number(e.target.value) }))}
            />
          </FormField>
          <FormField label="Reorder Qty">
            <input
              type="number"
              className="input"
              value={form.reorderQty}
              onChange={(e) => setForm((f) => ({ ...f, reorderQty: Number(e.target.value) }))}
            />
          </FormField>
        </div>
      </Modal>
    </div>
  );
}
