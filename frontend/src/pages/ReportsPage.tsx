import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FileBarChart, Plus } from 'lucide-react';
import { api } from '../api';
import type { ValuationReport } from '../api/types';
import { PageHeader } from '../components/Layout';
import { DataTable, type Column } from '../components/DataTable';
import { useToast } from '../components/Toast';
import { formatCurrency, formatDateTime } from '../lib/utils';

export function ReportsPage() {
  const qc = useQueryClient();
  const { toast } = useToast();

  const reports = useQuery({ queryKey: ['reports'], queryFn: () => api.listReports() });

  const generate = useMutation({
    mutationFn: () => api.createValuationReport(),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['reports'] });
      toast(`Report ${res.reportId} generated`);
    },
    onError: (e) => toast(e instanceof Error ? e.message : 'Generation failed', 'error'),
  });

  const columns: Column<ValuationReport>[] = [
    {
      key: 'id',
      header: 'Report ID',
      render: (r) => <span className="font-mono text-xs">{r.reportId}</span>,
    },
    {
      key: 'value',
      header: 'Total Valuation',
      render: (r) => <span className="font-semibold">{formatCurrency(r.totalValue)}</span>,
    },
    {
      key: 'loc',
      header: 'Location',
      render: (r) => <span className="font-mono text-xs text-slate-500">{r.location}</span>,
    },
    { key: 'at', header: 'Generated', render: (r) => formatDateTime(r.generatedAt) },
  ];

  return (
    <div>
      <PageHeader
        title="Reports"
        subtitle="Generate and review inventory valuation reports"
        actions={
          <button
            className="btn-primary"
            disabled={generate.isPending}
            onClick={() => generate.mutate()}
          >
            <Plus className="h-4 w-4" />
            {generate.isPending ? 'Generating…' : 'Generate Valuation'}
          </button>
        }
      />

      <div className="card mb-4 flex items-center gap-3 p-5">
        <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-accent-50 text-accent-600">
          <FileBarChart className="h-5 w-5" />
        </div>
        <p className="text-sm text-slate-600">
          Generating a valuation report computes the total on-hand value (unit cost × quantity)
          across all warehouses and stores the output at the returned location.
        </p>
      </div>

      <DataTable
        columns={columns}
        rows={reports.data ?? []}
        rowKey={(r) => r.reportId}
        loading={reports.isLoading}
        error={reports.error}
        emptyMessage="No reports generated yet."
      />
    </div>
  );
}
