import type { ReactNode } from 'react';
import { Loader2 } from 'lucide-react';

export interface Column<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  className?: string;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  loading?: boolean;
  error?: unknown;
  emptyMessage?: string;
  rowClassName?: (row: T) => string;
}

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  loading,
  error,
  emptyMessage = 'No records found.',
  rowClassName,
}: DataTableProps<T>) {
  return (
    <div className="card overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              {columns.map((c) => (
                <th key={c.key} className={`px-4 py-3 ${c.className ?? ''}`}>
                  {c.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr>
                <td colSpan={columns.length} className="px-4 py-10 text-center text-slate-400">
                  <Loader2 className="mx-auto h-5 w-5 animate-spin" />
                  <span className="mt-2 block">Loading…</span>
                </td>
              </tr>
            )}
            {!loading && !!error && (
              <tr>
                <td colSpan={columns.length} className="px-4 py-10 text-center text-red-500">
                  {error instanceof Error ? error.message : 'Failed to load data.'}
                </td>
              </tr>
            )}
            {!loading && !error && rows.length === 0 && (
              <tr>
                <td colSpan={columns.length} className="px-4 py-10 text-center text-slate-400">
                  {emptyMessage}
                </td>
              </tr>
            )}
            {!loading &&
              !error &&
              rows.map((row) => (
                <tr
                  key={rowKey(row)}
                  className={`border-b border-slate-100 last:border-0 hover:bg-slate-50/60 ${
                    rowClassName?.(row) ?? ''
                  }`}
                >
                  {columns.map((c) => (
                    <td key={c.key} className={`px-4 py-3 ${c.className ?? ''}`}>
                      {c.render(row)}
                    </td>
                  ))}
                </tr>
              ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
