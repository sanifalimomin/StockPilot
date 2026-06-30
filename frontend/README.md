# IMS Frontend — Cloud-Native Inventory Management System

Demo dashboard for the CSCI 5411 Inventory Management System. Built with **React 18 + Vite +
TypeScript + React Router + TanStack Query + Tailwind CSS + recharts + lucide-react**.

The UI ships with a built-in **mock data layer**, so it is fully demoable without the backend
running.

## Quick start

```bash
cd frontend
npm install
cp .env.example .env   # VITE_USE_MOCKS=true by default
npm run dev            # http://localhost:5173
```

```bash
npm run build          # type-check + production build
npm run preview        # preview the built app
```

## Mock vs. real backend

Data access is centralized in `src/api/`. A single switch in `src/api/index.ts` chooses the
implementation based on the `VITE_USE_MOCKS` env var:

- `VITE_USE_MOCKS=true` (default) — uses `src/api/mock/mockClient.ts`, an in-memory store seeded
  with 3 categories, 3 suppliers, 8 products, 3 warehouses, inventory levels (several below their
  reorder point to trigger alerts), purchase orders, and recent movements. CRUD and movements
  mutate the in-memory state live during the session.
- `VITE_USE_MOCKS=false` — uses `src/api/http.ts`, which calls the real REST API at `/api/v1`.

Both implementations satisfy the same `ApiClient` interface (`src/api/client.ts`), so swapping is
one switch — no page code changes.

To point at the real backend:

```bash
# frontend/.env
VITE_USE_MOCKS=false
```

## Dev proxy

`vite.config.ts` proxies `/api` → `http://localhost:8080`, so when running against the real
backend the browser talks to `http://localhost:5173/api/v1/...` and Vite forwards to the Java
service on port 8080 (no CORS setup needed for local dev).

## API contract (`/api/v1`)

`GET /health`, CRUD for `/categories` `/suppliers` `/products` (filterable by
`categoryId`/`supplierId`/`q`), `/warehouses`, `GET /inventory`,
`GET /inventory/consolidated`, `POST/GET /movements`, `/purchase-orders` +
`/purchase-orders/{id}/transition`, `GET /alerts`, `POST /reports/valuation` + `GET /reports`,
`GET /forecast`. Types live in `src/api/types.ts`.

## Pages

1. **Dashboard** — KPI cards, stock-by-warehouse bar chart, recent movements, active alerts.
2. **Products** — searchable/filterable table; create/edit/delete via modal.
3. **Inventory** — consolidated view + per-warehouse drill-down; below-reorder rows highlighted.
4. **Movements** — record INBOUND/OUTBOUND/TRANSFER/ADJUSTMENT (conditional from/to fields,
   auto-generated idempotency key); recent-movements table refreshes on submit.
5. **Purchase Orders** — list with status badges, detail modal, lifecycle transitions
   (Draft → Ordered → Received).
6. **Alerts** — low-stock alerts with suggested reorder quantities.
7. **Forecast** — pick a SKU, recharts line chart of forecasted demand + method label.
8. **Reports** — generate a valuation report and list generated reports with their location.

## Project structure

```
src/
  api/            ApiClient interface, types, http client, mock layer + seed data, switch
  components/     Layout/Sidebar/Topbar, DataTable, Modal, StatCard, StatusBadge, FormField, Toast
  lib/            shared query hooks + formatting/idempotency utils
  pages/          one component per sidebar page
```
