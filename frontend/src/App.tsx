import { Routes, Route } from 'react-router-dom';
import { Layout } from './components/Layout';
import { DashboardPage } from './pages/DashboardPage';
import { ProductsPage } from './pages/ProductsPage';
import { InventoryPage } from './pages/InventoryPage';
import { MovementsPage } from './pages/MovementsPage';
import { PurchaseOrdersPage } from './pages/PurchaseOrdersPage';
import { AlertsPage } from './pages/AlertsPage';
import { ForecastPage } from './pages/ForecastPage';
import { ReportsPage } from './pages/ReportsPage';

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="products" element={<ProductsPage />} />
        <Route path="inventory" element={<InventoryPage />} />
        <Route path="movements" element={<MovementsPage />} />
        <Route path="purchase-orders" element={<PurchaseOrdersPage />} />
        <Route path="alerts" element={<AlertsPage />} />
        <Route path="forecast" element={<ForecastPage />} />
        <Route path="reports" element={<ReportsPage />} />
      </Route>
    </Routes>
  );
}
