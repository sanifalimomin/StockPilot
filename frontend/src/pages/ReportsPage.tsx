import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Download, FileBarChart, Plus } from 'lucide-react';
import { api } from '../api';
import type { ValuationReport } from '../api/types';
import { PageHeader } from '../components/Layout';
import { DataTable, type Column } from '../components/DataTable';
import { useToast } from '../components/Toast';
import { formatDateTime } from '../lib/utils';

export function ReportsPage() {
  const qc = useQueryClient();
  const { toast } = useToast();

  const reports = useQuery({ queryKey: ['reports'], queryFn: () => api.listReports() });

  const generate = useMutation({
    mutationFn: () => api.createDailyReports(),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['reports'] });
      toast(`${res.length} reports generated (valuation, low-stock, movement audit)`);
    },
    onError: (e) => toast(e instanceof Error ? e.message : 'Generation failed', 'error'),
  });

  const reportLabel = (r: ValuationReport): string => {
    const name = r.filename ?? r.location;
    if (name.includes('valuation')) return 'Valuation';
    if (name.includes('low-stock')) return 'Low stock';
    if (name.includes('movements')) return 'Movement audit';
    return r.filename ?? '—';
  };

  const columns: Column<ValuationReport>[] = [
    {
      key: 'type',
      header: 'Report',
      render: (r) => <span className="font-medium">{reportLabel(r)}</span>,
    },
    {
      key: 'id',
      header: 'Report ID',
      render: (r) => <span className="font-mono text-xs">{r.reportId}</span>,
    },
    {
      key: 'loc',
      header: 'Location',
      render: (r) => <span className="font-mono text-xs text-slate-500">{r.location}</span>,
    },
    { key: 'at', header: 'Generated', render: (r) => formatDateTime(r.generatedAt) },
    {
      key: 'download',
      header: 'Download',
      render: (r) =>
        r.downloadUrl ? (
          <a
            href={r.downloadUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1.5 rounded-lg bg-accent-50 px-2.5 py-1.5 text-xs font-medium text-accent-700 hover:bg-accent-100"
          >
            <Download className="h-3.5 w-3.5" />
            CSV
          </a>
        ) : (
          <span className="text-xs text-slate-400">—</span>
        ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Reports"
        subtitle="Valuation, low-stock and movement-audit reports — generated nightly, or on demand"
        actions={
          <button
            className="btn-primary"
            disabled={generate.isPending}
            onClick={() => generate.mutate()}
          >
            <Plus className="h-4 w-4" />
            {generate.isPending ? 'Generating…' : 'Generate Daily Reports'}
          </button>
        }
      />

      <div className="card mb-4 flex items-center gap-3 p-5">
        <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-accent-50 text-accent-600">
          <FileBarChart className="h-5 w-5" />
        </div>
        <p className="text-sm text-slate-600">
          Three reports are generated automatically every night by the scheduled task:
          <b> inventory valuation</b> (on-hand value per SKU/warehouse), <b>low stock</b>
          (items at/below reorder point with suggested order quantities), and a
          <b> movement audit</b> (all stock movements in the last 24 h). CSVs are stored in a
          private S3 bucket; downloads use presigned URLs that expire after 15 minutes —
          refresh the list for fresh links.
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
