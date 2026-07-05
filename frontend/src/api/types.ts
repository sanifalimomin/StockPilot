export interface Category {
  id: string;
  name: string;
  description?: string;
}

export interface Supplier {
  id: string;
  name: string;
  contactEmail?: string;
  phone?: string;
  leadTimeDays?: number;
}

export interface Product {
  id: string;
  sku: string;
  name: string;
  description?: string;
  categoryId: string;
  supplierId: string;
  unitCost: number;
  unitPrice: number;
  reorderPoint: number;
  reorderQty: number;
}

export interface Warehouse {
  id: string;
  name: string;
  location?: string;
}

export interface InventoryLevel {
  sku: string;
  warehouseId: string;
  onHand: number;
  reserved: number;
  available: number;
}

export interface ConsolidatedInventory {
  sku: string;
  productName: string;
  totalOnHand: number;
  reorderPoint: number;
  belowReorder: boolean;
}

export type MovementType = 'INBOUND' | 'OUTBOUND' | 'TRANSFER' | 'ADJUSTMENT';

export interface Movement {
  id: string;
  sku: string;
  warehouseId: string;
  type: MovementType;
  qty: number;
  fromWarehouseId?: string;
  toWarehouseId?: string;
  idempotencyKey: string;
  createdAt: string;
}

export interface CreateMovementRequest {
  sku: string;
  warehouseId: string;
  type: MovementType;
  qty: number;
  fromWarehouseId?: string;
  toWarehouseId?: string;
  idempotencyKey: string;
}

export type PurchaseOrderStatus = 'DRAFT' | 'ORDERED' | 'RECEIVED' | 'CANCELLED';

export interface PurchaseOrderLine {
  sku: string;
  productName: string;
  qty: number;
  unitCost: number;
}

export interface PurchaseOrder {
  id: string;
  reference: string;
  supplierId: string;
  status: PurchaseOrderStatus;
  lines: PurchaseOrderLine[];
  total: number;
  createdAt: string;
  expectedAt?: string;
}

export interface CreatePurchaseOrderLine {
  productId: string;
  qty: number;
  unitCost?: number;
}

export interface CreatePurchaseOrderRequest {
  supplierId: string;
  lines: CreatePurchaseOrderLine[];
}

export interface Alert {
  id: string;
  sku: string;
  productName: string;
  warehouseId: string;
  onHand: number;
  reorderPoint: number;
  suggestedReorderQty: number;
  resolved: boolean;
  createdAt: string;
}

export interface ValuationReport {
  reportId: string;
  /** Logical file name identifying the report type, e.g. "valuation.csv". */
  filename?: string;
  location: string;
  generatedAt: string;
  totalValue: number;
  /** Time-limited presigned S3 GET URL; absent when running locally. */
  downloadUrl?: string;
}

export interface CreateReportResponse {
  reportId: string;
  filename?: string;
  location: string;
  downloadUrl?: string;
}

export interface ForecastPoint {
  date: string;
  qty: number;
}

export interface Forecast {
  sku: string;
  method: string;
  points: ForecastPoint[];
}

export interface HealthStatus {
  status: string;
  version?: string;
}

export interface ProductFilters {
  categoryId?: string;
  supplierId?: string;
  q?: string;
}
