import type { ApiClient } from './client';
import type {
  Alert,
  Category,
  ConsolidatedInventory,
  CreateMovementRequest,
  CreatePurchaseOrderRequest,
  CreateReportResponse,
  Forecast,
  HealthStatus,
  InventoryLevel,
  Movement,
  MovementType,
  Product,
  ProductFilters,
  PurchaseOrder,
  PurchaseOrderStatus,
  Supplier,
  ValuationReport,
  Warehouse,
} from './types';

const BASE = `${import.meta.env.VITE_API_BASE_URL ?? ''}/api/v1`;

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`API ${res.status} ${res.statusText}${text ? `: ${text}` : ''}`);
  }
  if (res.status === 204) return undefined as T;
  const ct = res.headers.get('content-type') ?? '';
  if (!ct.includes('application/json')) return undefined as T;
  return (await res.json()) as T;
}

function qs(params: Record<string, string | number | boolean | undefined>): string {
  const usp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== '') usp.set(k, String(v));
  }
  const s = usp.toString();
  return s ? `?${s}` : '';
}

interface RawCategory { id: number; name: string; description?: string }
interface RawSupplier { id: number; name: string; email?: string; phone?: string }
interface RawProduct {
  id: number;
  sku: string;
  name: string;
  categoryId: number;
  supplierId: number;
  unitCost: number;
  reorderPoint: number;
  reorderQty: number;
}
interface RawWarehouse { id: number; code: string; name: string; region?: string }
interface RawInventoryRow {
  sku: string;
  productId: number;
  warehouseId: number;
  quantityOnHand: number;
  quantityReserved: number;
}
interface RawMovement {
  movementId: string;
  sku: string;
  warehouseId: number;
  qty: number;
  fromWarehouseId?: number | null;
  toWarehouseId?: number | null;
  timestamp: string;
  idempotencyKey: string;
  processedSync: boolean;
}
interface RawAlert {
  id: number;
  productId: number;
  warehouseId: number;
  currentQty: number;
  reorderPoint: number;
  createdAt: string;
  resolved: boolean;
}
interface RawForecast { sku: string; method: string; points: { date: string; qty: number }[] }
interface RawReportDescriptor { reportId: string; filename?: string; location: string; sizeBytes: number; downloadUrl?: string | null }
interface RawPurchaseOrder {
  id: number;
  supplierId: number;
  status: PurchaseOrderStatus;
  createdAt: string;
  lines: { id: number; productId: number; qty: number; unitCost: number }[];
}

const str = (v: number | string | null | undefined): string | undefined =>
  v === null || v === undefined ? undefined : String(v);

const toCategory = (c: RawCategory): Category => ({
  id: String(c.id),
  name: c.name,
  description: c.description,
});

const toSupplier = (s: RawSupplier): Supplier => ({
  id: String(s.id),
  name: s.name,
  contactEmail: s.email,
  phone: s.phone,
});

const toProduct = (p: RawProduct): Product => ({
  id: String(p.id),
  sku: p.sku,
  name: p.name,
  categoryId: String(p.categoryId),
  supplierId: String(p.supplierId),
  unitCost: p.unitCost ?? 0,
  unitPrice: p.unitCost ?? 0,
  reorderPoint: p.reorderPoint,
  reorderQty: p.reorderQty,
});

const toWarehouse = (w: RawWarehouse): Warehouse => ({
  id: String(w.id),
  name: w.name,
  location: w.region,
});

const toInventoryLevel = (r: RawInventoryRow): InventoryLevel => ({
  sku: r.sku,
  warehouseId: String(r.warehouseId),
  onHand: r.quantityOnHand,
  reserved: r.quantityReserved,
  available: Math.max(0, r.quantityOnHand - r.quantityReserved),
});

function inferMovementType(m: RawMovement): MovementType {
  if (m.fromWarehouseId != null && m.toWarehouseId != null) return 'TRANSFER';
  if (m.qty < 0) return 'OUTBOUND';
  return 'INBOUND';
}

const toMovement = (m: RawMovement): Movement => ({
  id: m.movementId,
  sku: m.sku,
  warehouseId: String(m.warehouseId),
  type: inferMovementType(m),
  qty: m.qty,
  fromWarehouseId: str(m.fromWarehouseId),
  toWarehouseId: str(m.toWarehouseId),
  idempotencyKey: m.idempotencyKey,
  createdAt: m.timestamp,
});

const toAlert = (a: RawAlert, p?: RawProduct): Alert => ({
  id: String(a.id),
  sku: p?.sku ?? `#${a.productId}`,
  productName: p?.name ?? `Product ${a.productId}`,
  warehouseId: String(a.warehouseId),
  onHand: a.currentQty,
  reorderPoint: a.reorderPoint,
  suggestedReorderQty: p?.reorderQty ?? 0,
  resolved: a.resolved,
  createdAt: a.createdAt,
});

const toForecast = (f: RawForecast): Forecast => ({
  sku: f.sku,
  method: f.method,
  points: f.points.map((pt) => ({ date: pt.date, qty: pt.qty })),
});

const toReport = (r: RawReportDescriptor): ValuationReport => ({
  reportId: r.reportId,
  filename: r.filename,
  location: r.location,
  generatedAt: '',
  totalValue: 0,
  downloadUrl: r.downloadUrl ?? undefined,
});

const toPurchaseOrder = (po: RawPurchaseOrder, productById: Map<string, RawProduct>): PurchaseOrder => {
  const lines = po.lines.map((l) => {
    const p = productById.get(String(l.productId));
    return {
      sku: p?.sku ?? `#${l.productId}`,
      productName: p?.name ?? `Product ${l.productId}`,
      qty: l.qty,
      unitCost: l.unitCost ?? 0,
    };
  });
  const total = lines.reduce((sum, l) => sum + l.qty * l.unitCost, 0);
  return {
    id: String(po.id),
    reference: `PO-${po.id}`,
    supplierId: String(po.supplierId),
    status: po.status,
    lines,
    total: Math.round(total * 100) / 100,
    createdAt: po.createdAt,
  };
};

const rawProducts = () => request<RawProduct[]>('/products');
async function productMapById(): Promise<Map<string, RawProduct>> {
  const products = await rawProducts();
  return new Map(products.map((p) => [String(p.id), p]));
}

export const httpClient: ApiClient = {
  health: async () => {
    const h = await request<{ status: string; role?: string; profile?: string }>('/health');
    return { status: h.status, version: h.profile ?? h.role } as HealthStatus;
  },

  listCategories: async () => (await request<RawCategory[]>('/categories')).map(toCategory),
  createCategory: async (input) =>
    toCategory(
      await request<RawCategory>('/categories', {
        method: 'POST',
        body: JSON.stringify({ name: input.name, description: input.description }),
      }),
    ),
  updateCategory: async (id, input) =>
    toCategory(
      await request<RawCategory>(`/categories/${id}`, {
        method: 'PUT',
        body: JSON.stringify({ name: input.name, description: input.description }),
      }),
    ),
  deleteCategory: (id) => request<void>(`/categories/${id}`, { method: 'DELETE' }),

  listSuppliers: async () => (await request<RawSupplier[]>('/suppliers')).map(toSupplier),
  createSupplier: async (input) =>
    toSupplier(
      await request<RawSupplier>('/suppliers', {
        method: 'POST',
        body: JSON.stringify({ name: input.name, email: input.contactEmail, phone: input.phone }),
      }),
    ),
  updateSupplier: async (id, input) =>
    toSupplier(
      await request<RawSupplier>(`/suppliers/${id}`, {
        method: 'PUT',
        body: JSON.stringify({ name: input.name, email: input.contactEmail, phone: input.phone }),
      }),
    ),
  deleteSupplier: (id) => request<void>(`/suppliers/${id}`, { method: 'DELETE' }),

  listProducts: async (filters?: ProductFilters) =>
    (await request<RawProduct[]>(`/products${qs({ ...filters })}`)).map(toProduct),
  createProduct: async (input) =>
    toProduct(await request<RawProduct>('/products', { method: 'POST', body: productBody(input) })),
  updateProduct: async (id, input) =>
    toProduct(
      await request<RawProduct>(`/products/${id}`, { method: 'PUT', body: productBody(input) }),
    ),
  deleteProduct: (id) => request<void>(`/products/${id}`, { method: 'DELETE' }),

  listWarehouses: async () => (await request<RawWarehouse[]>('/warehouses')).map(toWarehouse),
  createWarehouse: async (input) =>
    toWarehouse(
      await request<RawWarehouse>('/warehouses', {
        method: 'POST',

        body: JSON.stringify({
          code: deriveWarehouseCode(input.name),
          name: input.name,
          region: input.location,
        }),
      }),
    ),

  listInventory: async (params) =>
    (await request<RawInventoryRow[]>(`/inventory${qs({ ...params })}`)).map(toInventoryLevel),

  consolidatedInventory: async (sku) => {
    const [rows, products] = await Promise.all([
      request<RawInventoryRow[]>(`/inventory${qs({ sku })}`),
      rawProducts(),
    ]);
    const bySku = new Map(products.map((p) => [p.sku, p]));
    const skus = sku ? [sku] : Array.from(new Set(rows.map((r) => r.sku)));
    return skus.map<ConsolidatedInventory>((s) => {
      const totalOnHand = rows
        .filter((r) => r.sku === s)
        .reduce((sum, r) => sum + r.quantityOnHand, 0);
      const product = bySku.get(s);
      const reorderPoint = product?.reorderPoint ?? 0;
      return {
        sku: s,
        productName: product?.name ?? s,
        totalOnHand,
        reorderPoint,
        belowReorder: totalOnHand < reorderPoint,
      };
    });
  },

  createMovement: async (input: CreateMovementRequest) =>
    toMovement(
      await request<RawMovement>('/movements', { method: 'POST', body: JSON.stringify(input) }),
    ),
  listMovements: async (params) =>
    (await request<RawMovement[]>(`/movements${qs({ ...params })}`)).map(toMovement),

  listPurchaseOrders: async () => {
    const [pos, byId] = await Promise.all([
      request<RawPurchaseOrder[]>('/purchase-orders'),
      productMapById(),
    ]);
    return pos.map((po) => toPurchaseOrder(po, byId));
  },
  getPurchaseOrder: async (id) => {
    const [po, byId] = await Promise.all([
      request<RawPurchaseOrder>(`/purchase-orders/${id}`),
      productMapById(),
    ]);
    return toPurchaseOrder(po, byId);
  },
  createPurchaseOrder: async (input: CreatePurchaseOrderRequest) => {
    const [po, byId] = await Promise.all([
      request<RawPurchaseOrder>('/purchase-orders', {
        method: 'POST',
        body: JSON.stringify(input),
      }),
      productMapById(),
    ]);
    return toPurchaseOrder(po, byId);
  },
  transitionPurchaseOrder: async (id, status: PurchaseOrderStatus) => {
    const [po, byId] = await Promise.all([
      request<RawPurchaseOrder>(`/purchase-orders/${id}/transition`, {
        method: 'POST',
        body: JSON.stringify({ status }),
      }),
      productMapById(),
    ]);
    return toPurchaseOrder(po, byId);
  },

  listAlerts: async (resolved) => {
    const [alerts, byId] = await Promise.all([
      request<RawAlert[]>(`/alerts${qs({ resolved })}`),
      productMapById(),
    ]);
    return alerts.map((a) => toAlert(a, byId.get(String(a.productId))));
  },

  createValuationReport: () =>
    request<CreateReportResponse>('/reports/valuation', { method: 'POST' }),
  createLowStockReport: () =>
    request<CreateReportResponse>('/reports/low-stock', { method: 'POST' }),
  createMovementsReport: () =>
    request<CreateReportResponse>('/reports/movements', { method: 'POST' }),
  createDailyReports: () =>
    request<CreateReportResponse[]>('/reports/daily', { method: 'POST' }),
  listReports: async () => (await request<RawReportDescriptor[]>('/reports')).map(toReport),

  forecast: async (sku, days = 30) =>
    toForecast(await request<RawForecast>(`/forecast${qs({ sku, days })}`)),
};

function productBody(input: Omit<Product, 'id'>): string {
  return JSON.stringify({
    sku: input.sku,
    name: input.name,
    categoryId: input.categoryId,
    supplierId: input.supplierId,
    unitCost: input.unitCost,
    reorderPoint: input.reorderPoint,
    reorderQty: input.reorderQty,
  });
}

function deriveWarehouseCode(name: string): string {
  const code = (name ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8);
  return code || `WH${Date.now() % 10000}`;
}
