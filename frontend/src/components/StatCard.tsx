import type { LucideIcon } from 'lucide-react';
import { cn } from '../lib/utils';

interface StatCardProps {
  label: string;
  value: string | number;
  icon: LucideIcon;
  tone?: 'accent' | 'emerald' | 'amber' | 'red';
  hint?: string;
}

const tones = {
  accent: 'bg-accent-50 text-accent-600',
  emerald: 'bg-emerald-50 text-emerald-600',
  amber: 'bg-amber-50 text-amber-600',
  red: 'bg-red-50 text-red-600',
};

export function StatCard({ label, value, icon: Icon, tone = 'accent', hint }: StatCardProps) {
  return (
    <div className="card flex items-center gap-4 p-5">
      <div className={cn('flex h-12 w-12 items-center justify-center rounded-lg', tones[tone])}>
        <Icon className="h-6 w-6" />
      </div>
      <div>
        <p className="text-sm text-slate-500">{label}</p>
        <p className="text-2xl font-semibold text-slate-900">{value}</p>
        {hint && <p className="text-xs text-slate-400">{hint}</p>}
      </div>
    </div>
  );
}
