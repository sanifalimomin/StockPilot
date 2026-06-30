import { cn } from '../lib/utils';
import type { PurchaseOrderStatus } from '../api/types';

const poStyles: Record<PurchaseOrderStatus, string> = {
  DRAFT: 'bg-slate-100 text-slate-700',
  ORDERED: 'bg-amber-100 text-amber-800',
  RECEIVED: 'bg-emerald-100 text-emerald-800',
  CANCELLED: 'bg-red-100 text-red-700',
};

export function StatusBadge({ status }: { status: PurchaseOrderStatus }) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold',
        poStyles[status],
      )}
    >
      {status}
    </span>
  );
}

export function Badge({
  children,
  tone = 'neutral',
}: {
  children: React.ReactNode;
  tone?: 'neutral' | 'success' | 'warning' | 'danger' | 'accent';
}) {
  const tones = {
    neutral: 'bg-slate-100 text-slate-700',
    success: 'bg-emerald-100 text-emerald-800',
    warning: 'bg-amber-100 text-amber-800',
    danger: 'bg-red-100 text-red-700',
    accent: 'bg-accent-100 text-accent-800',
  };
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold',
        tones[tone],
      )}
    >
      {children}
    </span>
  );
}
