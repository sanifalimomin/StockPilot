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
  Product,
  ProductFilters,
  PurchaseOrder,
  PurchaseOrderStatus,
  Supplier,
  ValuationReport,
  Warehouse,
} from './types';


export interface ApiClient {
  health(): Promise<HealthStatus>;

  listCategories(): Promise<Category[]>;
  createCategory(input: Omit<Category, 'id'>): Promise<Category>;
  updateCategory(id: string, input: Omit<Category, 'id'>): Promise<Category>;
  deleteCategory(id: string): Promise<void>;

  listSuppliers(): Promise<Supplier[]>;
  createSupplier(input: Omit<Supplier, 'id'>): Promise<Supplier>;
  updateSupplier(id: string, input: Omit<Supplier, 'id'>): Promise<Supplier>;
  deleteSupplier(id: string): Promise<void>;

  listProducts(filters?: ProductFilters): Promise<Product[]>;
  createProduct(input: Omit<Product, 'id'>): Promise<Product>;
  updateProduct(id: string, input: Omit<Product, 'id'>): Promise<Product>;
  deleteProduct(id: string): Promise<void>;

  listWarehouses(): Promise<Warehouse[]>;
  createWarehouse(input: Omit<Warehouse, 'id'>): Promise<Warehouse>;

  listInventory(params?: { warehouseId?: string; sku?: string }): Promise<InventoryLevel[]>;
  consolidatedInventory(sku?: string): Promise<ConsolidatedInventory[]>;

  createMovement(input: CreateMovementRequest): Promise<Movement>;
  listMovements(params?: { sku?: string; warehouseId?: string; limit?: number }): Promise<Movement[]>;

  listPurchaseOrders(): Promise<PurchaseOrder[]>;
  getPurchaseOrder(id: string): Promise<PurchaseOrder>;
  createPurchaseOrder(input: CreatePurchaseOrderRequest): Promise<PurchaseOrder>;
  transitionPurchaseOrder(id: string, status: PurchaseOrderStatus): Promise<PurchaseOrder>;

  listAlerts(resolved?: boolean): Promise<Alert[]>;

  createValuationReport(): Promise<CreateReportResponse>;
  createLowStockReport(): Promise<CreateReportResponse>;
  createMovementsReport(): Promise<CreateReportResponse>;
  /** Generate the full daily set: valuation + low-stock + movement audit. */
  createDailyReports(): Promise<CreateReportResponse[]>;
  listReports(): Promise<ValuationReport[]>;

  forecast(sku: string, days?: number): Promise<Forecast>;
}
